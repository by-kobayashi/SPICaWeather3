package me.spica.spicaweather3.ui.widget.rain

import org.jbox2d.collision.shapes.PolygonShape
import org.jbox2d.common.Settings
import org.jbox2d.common.Vec2
import org.jbox2d.dynamics.Body
import org.jbox2d.dynamics.BodyDef
import org.jbox2d.dynamics.BodyType
import org.jbox2d.dynamics.FixtureDef
import org.jbox2d.dynamics.World
import org.jbox2d.particle.ParticleGroup
import org.jbox2d.particle.ParticleGroupDef
import org.jbox2d.particle.ParticleType
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 粒子组渲染信息：只有发生过刚体接触的组才绘制水花。
 * 使用可变字段 + 对象池，避免每帧分配新对象。
 */
class GroupRenderInfo {
    var isSplash: Boolean = false   // 所属粒子组是否已接触碰撞体
    var bufferStart: Int = 0        // 粒子在全局缓冲中的起始索引
    var particleCount: Int = 0      // 该组的粒子数量
}

private data class EmitterSlot(
    val laneIndex: Int,
    var group: ParticleGroup? = null,
    var spawnAtMs: Long = 0L,
    var startedAtMs: Long = 0L,
    var durationMs: Long = 0L,
    var hasCollided: Boolean = false,
)

/**
 * 在渲染线程中拥有物理世界。每次 update 推进 1/120 秒，发射和回收也使用模拟时间。
 * 渲染器直接读取同一线程内的粒子缓冲；水花由真实粒子-刚体接触触发。
 */
class RainSimulation(
    val width: Int,
    val height: Int,
    val proportion: Float = 200f,
    private val random: Random = Random.Default,
) {
    companion object {
        /** JBox2D 允许的最大粒子数 */
        const val MAX_PARTICLES = 2800

        /** 同时活跃的最大粒子组数 */
        private const val MAX_GROUPS = 82

        private const val SEGMENTS_PER_CORNER = 4

        /** 项目最大雨档（bg type 8）迁移过来的主要参数 */
        private const val MAX_RAIN_GRAVITY_Y = 100.25f
        private const val MAX_RAIN_PRESSURE = 0.024f
        private const val MAX_RAIN_DAMPING = 1.0f
        private const val MAX_RAIN_VELOCITY_VARIANCE_RATIO = 0.15f
        private const val SPAWN_LANE_MIN_COUNT = 10
        private const val SPAWN_LANE_MAX_COUNT = 16
        private const val SPAWN_LANE_WIDTH_PX = 92
        private const val SPAWN_LANE_JITTER_RATIO = 0.32f
        private const val INITIAL_PREWARM_TOP_RATIO = 0.28f
        private const val INITIAL_PREWARM_SPAN_RATIO = 1.48f
        private const val RESPAWN_TOP_MIN_RATIO = 0.10f
        private const val RESPAWN_TOP_MAX_RATIO = 0.40f
        private const val RESPAWN_LANE_PHASE_RATIO = 0.22f
        private const val SLOT_RESPAWN_DELAY_MIN_MS = 38L
        private const val SLOT_RESPAWN_DELAY_MAX_MS = 60L
        private const val GROUP_LIFETIME_MIN_MS = 4200L
        private const val GROUP_LIFETIME_MAX_MS = 6200L
        private const val GROUP_RECYCLE_BOTTOM_RATIO = 1.30f

        init {
            // 提高 JBox2D 多边形顶点上限以支持更平滑的圆角碰撞体
            Settings.maxPolygonVertices = SEGMENTS_PER_CORNER * 4
        }
    }

    /** 初始化是否完成 */
    var initOK = false
        private set

    private lateinit var world: World
    private lateinit var textColliders: RainTextColliders
    private var elapsedSteps = 0L

    // 独立发射槽：每个槽位单独维护激活组和下次补发时间，避免整批刷出
    private val emitterSlots = ArrayList<EmitterSlot>(MAX_GROUPS)

    private val spawnLaneCount = (width / SPAWN_LANE_WIDTH_PX)
        .coerceIn(SPAWN_LANE_MIN_COUNT, SPAWN_LANE_MAX_COUNT)

    // ────────── 碰撞矩形（替代原底部地板碰撞） ──────────

    /**
     * 碰撞矩形（像素坐标：left, top, right, bottom）
     * 由外部设置，在渲染线程 [update] 中同步到物理世界
     */
    var pendingCollisionRect: FloatArray? = null

    var pendingTextCollisions: List<RainTextCollision> = emptyList()

    private var appliedCollisionRect: FloatArray? = null
    private var collisionBody: Body? = null

    // ────────── 供渲染器使用的访问器 ──────────

    /** 当前帧粒子数量（有效索引范围 [0, particleCount)） */
    val particleCount: Int get() = world.particleCount

    /**
     * JBox2D 内部粒子位置缓冲（世界坐标，Vec2 单位）
     * 渲染时需乘以 [proportion] 转换为屏幕像素坐标
     */
    val positionBuffer: Array<Vec2> get() = world.particlePositionBuffer

    /**
     * JBox2D 内部粒子速度缓冲（世界单位/秒）
     * 速度方向决定雨滴拖尾朝向，速度大小决定拖尾长度
     */
    val velocityBuffer: Array<Vec2> get() = world.particleVelocityBuffer

    // ────────── 粒子组渲染信息（预分配对象池，避免每帧 GC） ──────────

    private val _groupInfoPool = Array(MAX_GROUPS) { GroupRenderInfo() }
    private var _groupInfoCount = 0

    /** 每帧更新后的有效粒子组数量 */
    val groupInfoCount: Int get() = _groupInfoCount

    /** 预分配的粒子组渲染信息数组，有效范围 [0, groupInfoCount) */
    val groupInfos: Array<GroupRenderInfo> get() = _groupInfoPool

    // ────────── 初始化 ──────────

    /** 只在拥有本实例的渲染线程初始化。 */
    fun init() {
        world = World(Vec2(0f, MAX_RAIN_GRAVITY_Y))
        world.setParticlePressureStrength(MAX_RAIN_PRESSURE)
        world.setParticleDamping(MAX_RAIN_DAMPING)
        world.particleRadius = 6f / proportion
        world.particleMaxCount = MAX_PARTICLES
        textColliders = RainTextColliders(world, proportion)
        elapsedSteps = 0L
        appliedCollisionRect = null
        collisionBody = null
        syncCollisionBody()

        emitterSlots.clear()
        for (i in 0 until MAX_GROUPS) {
            val slot = EmitterSlot(laneIndex = i % spawnLaneCount)
            activateSlot(slot, now = 0L, prewarmIndex = i)
            emitterSlots.add(slot)
        }
        computeGroupInfos()
        initOK = true
    }

    /** 推进一个固定步；由 RainFrameClock 决定每个显示帧执行多少步。 */
    fun update() {
        if (!initOK) return
        syncCollisionBody()
        val now = ++elapsedSteps * 1000L / RainFrameClock.STEPS_PER_SECOND
        for (slot in emitterSlots) {
            val activeGroup = slot.group
            if (activeGroup != null && shouldRecycleGroup(activeGroup, slot, now)) {
                world.destroyParticlesInGroup(activeGroup)
                slot.group = null
                slot.spawnAtMs = now + nextRespawnDelayMs()
            }
            if (slot.group == null && now >= slot.spawnAtMs) activateSlot(slot, now)
        }

        world.step(RainFrameClock.STEP_SECONDS, 8, 3)
        val contacts = world.particleBodyContacts
        val groups = world.particleGroupBuffer
        for (i in 0 until world.particleBodyContactCount) {
            val slot = groups[contacts[i].index]?.userData as? EmitterSlot ?: continue
            slot.hasCollided = true
        }
        computeGroupInfos()
    }

    // ────────── 碰撞矩形同步 ──────────

    /**
     * 将 [pendingCollisionRect] 同步到物理世界：
     * 销毁旧碰撞体，在新矩形位置创建静态刚体，替代原底部地板碰撞
     */
    private fun applyCollisionRect() {
        val rect = pendingCollisionRect ?: return
        if (rect.contentEquals(appliedCollisionRect)) return

        val left = rect[0] / proportion
        val top = rect[1] / proportion
        val right = rect[2] / proportion
        val bottom = rect[3] / proportion
        val cx = (left + right) / 2f
        val cy = (top + bottom) / 2f
        val hw = (right - left) / 2f
        val hh = (bottom - top) / 2f

        // 跳过退化矩形，避免 JBox2D PolygonShape 断言失败
        if (hw < Settings.linearSlop || hh < Settings.linearSlop) return

        collisionBody?.let {
            world.destroyBody(it)
            collisionBody = null
        }

        val bodyDef = BodyDef().apply {
            type = BodyType.STATIC
            position.set(cx, cy)
        }
        collisionBody = world.createBody(bodyDef)
        collisionBody!!.createFixture(FixtureDef().apply {
            val cr = (if (rect.size > 4) rect[4] / proportion else 0f)
                .coerceAtMost(minOf(hw, hh))
            shape = PolygonShape().also {
                // 圆角半径必须远小于半宽/半高，否则角心重叠导致顶点退化
                if (cr > 0.01f && hw - cr > Settings.linearSlop && hh - cr > Settings.linearSlop) {
                    // 每个圆角用 SEGMENTS_PER_CORNER 段弧线近似，共 N*4 个顶点（CCW）
                    val n = SEGMENTS_PER_CORNER
                    val verts = ArrayList<Vec2>(n * 4)
                    val halfPi = (Math.PI / 2.0).toFloat()
                    for (corner in 0 until 4) {
                        // 四个角的圆心偏移和起始角度
                        val (ocx, ocy, startAngle) = when (corner) {
                            0 -> Triple(hw - cr, -(hh - cr), -halfPi)    // 右下
                            1 -> Triple(hw - cr, hh - cr, 0f)            // 右上
                            2 -> Triple(-(hw - cr), hh - cr, halfPi)     // 左上
                            else -> Triple(-(hw - cr), -(hh - cr), Math.PI.toFloat()) // 左下
                        }
                        for (s in 0 until n) {
                            val angle = startAngle + halfPi * s / n
                            verts.add(Vec2(ocx + cr * cos(angle), ocy + cr * sin(angle)))
                        }
                    }
                    it.set(verts.toTypedArray(), verts.size)
                } else {
                    it.setAsBox(hw, hh)
                }
            }
            friction = 0.8f
            restitution = 0.5f
            filter.maskBits = 0b01
            filter.groupIndex = 0b01
        })

        appliedCollisionRect = rect.clone()
    }

    private fun syncCollisionBody() {
        val collisions = pendingTextCollisions
        textColliders.update(collisions)
        if (collisions.isNotEmpty() || pendingCollisionRect == null) {
            collisionBody?.let { world.destroyBody(it) }
            collisionBody = null
            appliedCollisionRect = null
        } else {
            applyCollisionRect()
        }
    }

    private fun computeGroupInfos() {
        var infoIdx = 0
        for (slot in emitterSlots) {
            val group = slot.group ?: continue
            if (group.particleCount == 0) continue
            val info = _groupInfoPool[infoIdx++]
            info.isSplash = slot.hasCollided
            info.bufferStart = group.bufferIndex
            info.particleCount = group.particleCount
        }
        _groupInfoCount = infoIdx
    }

    // ────────── 私有：创建粒子组 ──────────

    private fun activateSlot(slot: EmitterSlot, now: Long, prewarmIndex: Int? = null) {
        val durationMs = nextGroupLifetimeMs()
        val progress = prewarmIndex?.let { (it + random.nextFloat()) / MAX_GROUPS.toFloat() }
        val startedAt = progress?.let { now - (durationMs * it).toLong() } ?: now
        val (xPos, yPos) = computeSpawnPosition(slot.laneIndex, progress)
        slot.hasCollided = false
        slot.group = createGroup(xPos, yPos, slot)
        slot.startedAtMs = startedAt
        slot.durationMs = durationMs
        slot.spawnAtMs = 0L
    }

    private fun shouldRecycleGroup(group: ParticleGroup, slot: EmitterSlot, now: Long): Boolean {
        if (group.particleCount <= 0) return true
        if (now - slot.startedAtMs >= slot.durationMs) return true
        return isGroupBelowRecycleLine(group)
    }

    private fun isGroupBelowRecycleLine(group: ParticleGroup): Boolean {
        val positions = world.particlePositionBuffer
        val start = group.bufferIndex
        val end = start + group.particleCount
        if (end <= start) return true
        var minY = Float.MAX_VALUE
        for (i in start until end) {
            if (positions[i].y < minY) minY = positions[i].y
        }
        return minY * proportion > height * GROUP_RECYCLE_BOTTOM_RATIO
    }

    /** 创建一个新的水粒子组 */
    private fun createGroup(xPos: Float, yPos: Float, slot: EmitterSlot): ParticleGroup {
        val def = ParticleGroupDef()

        // 粒子组形状：细长矩形，宽约 1/300 屏幕，高约 1/40 屏幕（缩短列高 = 更少粒子 = 更小水花）
        val shape = PolygonShape()
        shape.setAsBox(width / (300f * proportion), width / (40f * proportion))
        def.shape = shape
        def.flags = ParticleType.b2_waterParticle

        // 粒子引擎在步进中按直径 / dt 限速；预留首步的重力增量，保留初速度差异。
        val criticalVelocity = 2f * world.particleRadius / RainFrameClock.STEP_SECONDS
        val maxInitialVelocity = (criticalVelocity - MAX_RAIN_GRAVITY_Y * RainFrameClock.STEP_SECONDS)
            .coerceAtLeast(0f)
        val baseV = maxInitialVelocity / (1f + MAX_RAIN_VELOCITY_VARIANCE_RATIO)
        val varV = baseV * MAX_RAIN_VELOCITY_VARIANCE_RATIO
        def.linearVelocity.set(0f, baseV + (random.nextFloat() - 0.5f) * 2f * varV)
        def.userData = slot

        def.position.set(xPos, yPos)

        return world.createParticleGroup(def)
    }

    private fun computeSpawnPosition(laneIndex: Int, progress: Float?): Pair<Float, Float> {
        val x = nextSpawnXForLane(laneIndex)
        val y = if (progress != null) {
            height * (-INITIAL_PREWARM_TOP_RATIO + progress * INITIAL_PREWARM_SPAN_RATIO)
        } else {
            val lanePhase = ((laneIndex / spawnLaneCount.toFloat()) + random.nextFloat() * 0.35f) % 1f
            val baseRatio = RESPAWN_TOP_MIN_RATIO +
                random.nextFloat() * (RESPAWN_TOP_MAX_RATIO - RESPAWN_TOP_MIN_RATIO)
            -height * (baseRatio + lanePhase * RESPAWN_LANE_PHASE_RATIO)
        }
        return Pair(x, y / proportion)
    }

    private fun nextSpawnXForLane(laneIndex: Int): Float {
        // 只在屏幕左半区发射
        val spawnWidth = width * 0.5f
        val laneWidth = spawnWidth / spawnLaneCount
        val laneCenter = (laneIndex + 0.5f) * laneWidth
        val jitter = (random.nextFloat() - 0.5f) * laneWidth * SPAWN_LANE_JITTER_RATIO
        return (laneCenter + jitter).coerceIn(0f, spawnWidth) / proportion
    }

    private fun nextRespawnDelayMs(): Long {
        return SLOT_RESPAWN_DELAY_MIN_MS +
            random.nextLong(SLOT_RESPAWN_DELAY_MAX_MS - SLOT_RESPAWN_DELAY_MIN_MS + 1L)
    }

    private fun nextGroupLifetimeMs(): Long {
        return GROUP_LIFETIME_MIN_MS +
            random.nextLong(GROUP_LIFETIME_MAX_MS - GROUP_LIFETIME_MIN_MS + 1L)
    }
}
