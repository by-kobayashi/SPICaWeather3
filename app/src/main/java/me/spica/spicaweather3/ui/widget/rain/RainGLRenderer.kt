package me.spica.spicaweather3.ui.widget.rain

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.sqrt

/**
 * OpenGL ES 3.0 雨滴渲染器
 *
 * 多 pass 渲染策略：
 *  - screenRainProgram        : 全屏程序化雨幕，负责多层软边雨丝
 *  - dropProgram              : 背景雨线 → GL_TRIANGLES
 *  - frontDropProgram         : 前景雨滴 → GL_POINTS + 软边拉伸雨滴
 *  - metaballSplatProgram     : Metaball Pass1 → 水花法线、可见度与高斯密度 叠加渲染到离屏 RGBA16F FBO
 *  - metaballCompositeProgram : Metaball Pass2 → alpha 阈值裁切 + Phong 高光合成到屏幕
 *
 * Metaball 原理：
 *  Pass1 用 ONE/ONE 加法混合将法线 XY、可见度和密度累加到 FBO；
 *  重叠粒子区域密度累积超过阈值后，在 Pass2 被视为连续水体，
 *  形成无粒子感的液态水花；独立可见度用于渐隐，法线用于克制的边缘与镜面高光。
 *  若设备不支持 RGBA16F 可渲染格式，自动降级到单颗水珠材质（splashProgram）。
 */
class RainGLRenderer {

    // ───────── GLSL 着色器源码 ─────────

    // 下落雨滴顶点着色器（GL_TRIANGLES，忽略 gl_PointSize）
    private val DROP_VERT = """
        #version 300 es
        layout(location = 0) in vec2 a_position;
        layout(location = 1) in float a_alpha;
        uniform vec2 u_resolution;
        out float v_alpha;
        void main() {
            vec2 ndc = (a_position / u_resolution) * 2.0 - 1.0;
            ndc.y = -ndc.y;
            gl_Position = vec4(ndc, 0.0, 1.0);
            v_alpha = a_alpha;
        }
    """.trimIndent()

    // 下落雨滴片元着色器：白色半透明亮条
    private val DROP_FRAG = """
        #version 300 es
        precision mediump float;
        in float v_alpha;
        uniform vec4 u_color;
        out vec4 fragColor;
        void main() {
            fragColor = vec4(u_color.rgb, u_color.a * v_alpha);
        }
    """.trimIndent()

    // 前景雨滴顶点着色器：根据能量缩放点大小
    private val FRONT_DROP_VERT = """
        #version 300 es
        layout(location = 0) in vec2 a_position;
        layout(location = 1) in float a_alpha;
        uniform vec2 u_resolution;
        uniform float u_pt_base;
        uniform float u_pt_range;
        out float v_alpha;
        void main() {
            vec2 ndc = (a_position / u_resolution) * 2.0 - 1.0;
            ndc.y = -ndc.y;
            gl_Position = vec4(ndc, 0.0, 1.0);
            gl_PointSize = u_pt_base + a_alpha * u_pt_range;
            v_alpha = a_alpha;
        }
    """.trimIndent()

    // 前景雨滴片元着色器：极细纵向拉伸，与程序化雨幕风格保持一致
    // 不使用加法混合，因此多粒子叠加不会产生纯白高亮
    private val FRONT_DROP_FRAG = """
        #version 300 es
        precision mediump float;
        in float v_alpha;
        uniform vec4 u_color;
        out vec4 fragColor;
        void main() {
            vec2 p = gl_PointCoord * 2.0 - 1.0;
            // 高度拉伸：x 方向 6 倍压缩（极窄）；y 方向 0.55 倍（稍短），形成细线段
            float streak = 1.0 - smoothstep(0.12, 0.55, length(vec2(p.x * 6.0, p.y * 0.55)));
            // 头尾渐隐：沿 y 轴两端淡出，避免硬截断
            float taper  = 1.0 - p.y * p.y * 0.6;
            float fade   = streak * taper;
            fade = clamp(fade, 0.0, 1.0);
            // 限制单粒子最大贡献，避免多粒子叠加后饱和成白色
            float a = u_color.a * v_alpha * fade * 0.5;
            fragColor = vec4(u_color.rgb, a);
        }
    """.trimIndent()

    private val SPLASH_VERT = RainSplashShaders.particleVertex
    private val SPLASH_FRAG = RainSplashShaders.fallbackFragment
    private val METABALL_SPLAT_VERT = RainSplashShaders.particleVertex
    private val METABALL_SPLAT_FRAG = RainSplashShaders.splatFragment

    // ── Metaball Pass2：FBO 纹理 → 屏幕合成 ──
    // 密度阈值形成液态水体边界；透明度独立于密度变化。
    private val METABALL_COMPOSITE_VERT = """
        #version 300 es
        out vec2 v_uv;
        const vec2 POS[6] = vec2[](
            vec2(-1.0,-1.0), vec2(1.0,-1.0), vec2(-1.0,1.0),
            vec2(-1.0, 1.0), vec2(1.0,-1.0), vec2(1.0, 1.0)
        );
        void main() {
            vec2 pos  = POS[gl_VertexID];
            gl_Position = vec4(pos, 0.0, 1.0);
            v_uv = pos * 0.5 + 0.5;
        }
    """.trimIndent()

    private val METABALL_COMPOSITE_FRAG = RainSplashShaders.compositeFragment

    // ───────── 常量 ─────────

    companion object {
        private const val FRONT_DROP_PT_BASE  = 3f    // 极小基础尺寸，接近雨幕线条宽度
        private const val FRONT_DROP_PT_RANGE = 7f    // 速度增量窄，最大 ~10px

        // 水花 GL_POINTS 大小（降级模式）
        private const val SPLASH_PT_BASE  = 14f
        private const val SPLASH_PT_RANGE = 7f

        // 初始尺寸仍允许相邻粒子融合，随后按各自的寿命缩小。
        private const val METABALL_PT_BASE      = 22f
        private const val METABALL_PT_RANGE     = 8f
        // 水体边界阈值：累积 alpha 超过此值 → 视为连续液体
        private const val METABALL_ALPHA_THRESHOLD = 0.42f

        private const val BACKGROUND_FLOATS_PER_VERTEX = 3 // x, y, alpha
        private const val SPLASH_FLOATS_PER_VERTEX = 5 // x, y, energy, opacity, scale
    }

    // ───────── GL 对象 ─────────

    private var dropProgram   = 0
    private var frontDropProgram = 0
    private var splashProgram = 0          // 降级用：单颗水珠材质
    private var metaballSplatProgram = 0   // Metaball Pass1
    private var metaballCompositeProgram = 0 // Metaball Pass2
    private var vboId         = 0

    // Metaball 离屏 FBO（RGBA16F）
    private var metaballFboId  = 0
    private var metaballTexId  = 0
    private var metaballFboW   = 0
    private var metaballFboH   = 0
    private var useMetaball    = false     // 是否成功启用 Metaball 管线

    private var initialized = false

    // 缓存 uniform 位置，避免每帧 glGetUniformLocation 查询
    private var dropResolutionLoc = -1
    private var dropColorLoc      = -1
    private var frontDropResolutionLoc = -1
    private var frontDropColorLoc      = -1
    private var frontDropPtBaseLoc     = -1
    private var frontDropPtRangeLoc    = -1
    private var splashResolutionLoc = -1
    private var splashPtBaseLoc     = -1
    private var splashPtRangeLoc    = -1
    // Metaball splat uniforms
    private var splatResLoc   = -1
    private var splatPtBaseLoc  = -1
    private var splatPtRangeLoc = -1
    // Metaball composite uniforms
    private var compFboTexLoc   = -1
    private var compThresholdLoc = -1

    // VBO 数据缓冲（复用，避免每帧 GC）
    private var floatBuffer: FloatBuffer? = null
    private var bufferCapacity = 0
    private var startTimeNs = 0L

    // ───────── 初始化 ─────────

    fun init() {
        dropProgram   = buildProgram(DROP_VERT,   DROP_FRAG)
        frontDropProgram = buildProgram(FRONT_DROP_VERT, FRONT_DROP_FRAG)
        splashProgram = buildProgram(SPLASH_VERT, SPLASH_FRAG)

        // Metaball 着色器程序
        metaballSplatProgram     = buildProgram(METABALL_SPLAT_VERT,     METABALL_SPLAT_FRAG)
        metaballCompositeProgram = buildProgram(METABALL_COMPOSITE_VERT, METABALL_COMPOSITE_FRAG)

        // 缓存所有 uniform 位置
        dropResolutionLoc   = GLES30.glGetUniformLocation(dropProgram,   "u_resolution")
        dropColorLoc        = GLES30.glGetUniformLocation(dropProgram,   "u_color")
        frontDropResolutionLoc = GLES30.glGetUniformLocation(frontDropProgram, "u_resolution")
        frontDropColorLoc      = GLES30.glGetUniformLocation(frontDropProgram, "u_color")
        frontDropPtBaseLoc     = GLES30.glGetUniformLocation(frontDropProgram, "u_pt_base")
        frontDropPtRangeLoc    = GLES30.glGetUniformLocation(frontDropProgram, "u_pt_range")
        splashResolutionLoc = GLES30.glGetUniformLocation(splashProgram, "u_resolution")
        splashPtBaseLoc     = GLES30.glGetUniformLocation(splashProgram, "u_pt_base")
        splashPtRangeLoc    = GLES30.glGetUniformLocation(splashProgram, "u_pt_range")
        // Metaball uniform 位置
        splatResLoc    = GLES30.glGetUniformLocation(metaballSplatProgram, "u_resolution")
        splatPtBaseLoc = GLES30.glGetUniformLocation(metaballSplatProgram, "u_pt_base")
        splatPtRangeLoc= GLES30.glGetUniformLocation(metaballSplatProgram, "u_pt_range")
        compFboTexLoc   = GLES30.glGetUniformLocation(metaballCompositeProgram, "u_fbo_tex")
        compThresholdLoc= GLES30.glGetUniformLocation(metaballCompositeProgram, "u_threshold")

        val ids = IntArray(1)
        GLES30.glGenBuffers(1, ids, 0)
        vboId = ids[0]

        startTimeNs = System.nanoTime()
        initialized = true
        // FBO 将在首帧 draw() 中按实际尺寸创建
    }

    // ───────── 每帧绘制 ─────────

    fun draw(simulation: RainSimulation, screenWidth: Int, screenHeight: Int,
             bgStreaks: BackgroundRainStreaks? = null) {
        if (!initialized || !simulation.initOK) return

        val positions  = simulation.positionBuffer
        val velocities = simulation.velocityBuffer
        val prop       = simulation.proportion
        val infos      = simulation.groupInfos
        val infoCount  = simulation.groupInfoCount

        // 下落粒子不绘制；只有实际接触过碰撞体的粒子组才生成水花。
        // 统计已碰撞组的粒子数，为背景雨线和水花准备共用 VBO。
        var splashCount = 0
        for (idx in 0 until infoCount) {
            val info = infos[idx]
            if (info.isSplash && info.splashAgeSeconds < info.splashDurationSeconds) {
                splashCount += info.particleCount
            }
        }

        // VBO 空间：背景雨线 + 水花上限（不含未碰撞的下落粒子）
        val bgFloats = if (bgStreaks != null) BackgroundRainStreaks.TOTAL_FLOATS else 0
        val totalFloats = bgFloats + splashCount * SPLASH_FLOATS_PER_VERTEX
        var bgVertCount = 0
        var splashByteOffset = 0
        var actualSplashCount = 0
        if (totalFloats > 0) {
            ensureBuffer(totalFloats)
            val buf = floatBuffer!!
            buf.clear()

            // ── 填充背景雨线顶点 ──
            bgVertCount = if (bgStreaks != null) {
                val posBefore = buf.position()
                bgStreaks.fillVertices(buf)
                (buf.position() - posBefore) / BACKGROUND_FLOATS_PER_VERTEX
            } else 0

            splashByteOffset = buf.position() * 4

            // 使用组内索引生成固定差异，粒子缓冲压缩或帧率变化都不会使外观跳动。
            for (idx in 0 until infoCount) {
                val info = infos[idx]
                if (!info.isSplash || info.splashAgeSeconds >= info.splashDurationSeconds) continue
                val start = info.bufferStart
                val end   = start + info.particleCount
                for (i in start until end) {
                    val particleInGroup = i - start
                    val lifetime = RainSplashAppearance.particleDurationSeconds(
                        info.splashSeed, particleInGroup, info.splashDurationSeconds
                    )
                    val opacity = RainSplashAppearance.opacity(info.splashAgeSeconds, lifetime)
                    if (opacity <= 0.001f) continue
                    val scale = RainSplashAppearance.sizeScale(
                        info.splashAgeSeconds, lifetime,
                        RainSplashAppearance.sizeVariation(info.splashSeed, particleInGroup)
                    )
                    val vx    = velocities[i].x
                    val vyVal = velocities[i].y
                    val px    = positions[i].x * prop
                    val py    = positions[i].y * prop
                    val spd   = sqrt(vx * vx + vyVal * vyVal)
                    val energy = (spd * prop * 0.00022f).coerceIn(0.25f, 1.0f)
                    buf.put(px); buf.put(py); buf.put(energy); buf.put(opacity); buf.put(scale)
                    actualSplashCount++
                }
            }

            buf.flip()
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)
            GLES30.glBufferData(
                GLES30.GL_ARRAY_BUFFER,
                buf.limit() * 4,
                buf,
                GLES30.GL_STREAM_DRAW
            )
        }

        val stride = BACKGROUND_FLOATS_PER_VERTEX * 4

        GLES30.glEnable(GLES30.GL_BLEND)

        if (totalFloats <= 0) {
            GLES30.glDisable(GLES30.GL_BLEND)
            return
        }

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glEnableVertexAttribArray(1)

        // ── Pass 2：背景雨线（普通混合，GL_TRIANGLES） ──
        if (bgVertCount > 0) {
            GLES30.glUseProgram(dropProgram)
            setAlphaBlending()
            GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, stride, 0)
            GLES30.glVertexAttribPointer(1, 1, GLES30.GL_FLOAT, false, stride, 8)

            setResolution(dropResolutionLoc, screenWidth.toFloat(), screenHeight.toFloat())
            setColor(dropColorLoc, 0.75f, 0.85f, 0.98f, 1.0f)
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, bgVertCount)
        }

        // ── Pass 4：水花 Metaball 两 pass 或降级径向渐变 ──
        if (actualSplashCount > 0) {
            // 按需创建/重建 Metaball FBO（首帧或尺寸变化时）
            if (screenWidth != metaballFboW || screenHeight != metaballFboH) {
                createOrResizeMetaballFbo(screenWidth, screenHeight)
            }

            if (useMetaball) {
                // ── Pass 4a：粒子 → Metaball FBO（加法混合，球形法线 + 高斯 alpha） ──
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, metaballFboId)
                GLES30.glViewport(0, 0, metaballFboW, metaballFboH)
                GLES30.glClearColor(0f, 0f, 0f, 0f)
                GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

                GLES30.glUseProgram(metaballSplatProgram)
                GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE)   // 加法叠加

                bindSplashAttributes(splashByteOffset)

                setResolution(splatResLoc, screenWidth.toFloat(), screenHeight.toFloat())
                GLES30.glUniform1f(splatPtBaseLoc,  METABALL_PT_BASE)
                GLES30.glUniform1f(splatPtRangeLoc, METABALL_PT_RANGE)
                GLES30.glDrawArrays(GLES30.GL_POINTS, 0, actualSplashCount)
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
                GLES30.glViewport(0, 0, screenWidth, screenHeight)

                GLES30.glUseProgram(metaballCompositeProgram)
                setAlphaBlending()

                GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, metaballTexId)
                GLES30.glUniform1i(compFboTexLoc, 0)
                GLES30.glUniform1f(compThresholdLoc, METABALL_ALPHA_THRESHOLD)

                GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
                GLES30.glDisableVertexAttribArray(0)
                GLES30.glDisableVertexAttribArray(1)
                GLES30.glDisableVertexAttribArray(2)
                GLES30.glDisableVertexAttribArray(3)
                GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 6)

                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
            } else {
                // 降级：单颗水珠材质（不支持 RGBA16F FBO 时）
                GLES30.glUseProgram(splashProgram)
                setAlphaBlending()

                bindSplashAttributes(splashByteOffset)

                setResolution(splashResolutionLoc, screenWidth.toFloat(), screenHeight.toFloat())
                GLES30.glUniform1f(splashPtBaseLoc,  SPLASH_PT_BASE)
                GLES30.glUniform1f(splashPtRangeLoc, SPLASH_PT_RANGE)
                GLES30.glDrawArrays(GLES30.GL_POINTS, 0, actualSplashCount)

                GLES30.glDisableVertexAttribArray(0)
                GLES30.glDisableVertexAttribArray(1)
                GLES30.glDisableVertexAttribArray(2)
                GLES30.glDisableVertexAttribArray(3)
                GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
            }
        }

        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)
        GLES30.glDisableVertexAttribArray(2)
        GLES30.glDisableVertexAttribArray(3)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        GLES30.glDisable(GLES30.GL_BLEND)
    }

    /**
     * 创建或重建 Metaball RGBA16F 离屏帧缓冲。
     * 尝试 GL_RGBA16F + GL_HALF_FLOAT（GLES 3.0 + EXT_color_buffer_half_float）；
     * 不支持浮点 FBO 时使用单水珠材质；RGBA8 无法保存负法线与未饱和的密度。
     */
    private fun createOrResizeMetaballFbo(w: Int, h: Int) {
        // 释放旧资源
        if (metaballFboId != 0) {
            GLES30.glDeleteFramebuffers(1, intArrayOf(metaballFboId), 0)
            metaballFboId = 0
        }
        if (metaballTexId != 0) {
            GLES30.glDeleteTextures(1, intArrayOf(metaballTexId), 0)
            metaballTexId = 0
        }

        val fboIds = IntArray(1)
        val texIds = IntArray(1)
        GLES30.glGenFramebuffers(1, fboIds, 0)
        GLES30.glGenTextures(1, texIds, 0)

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texIds[0])
        // 尝试 RGBA16F（支持负值法线的有符号浮点格式）
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA16F,
            w, h, 0, GLES30.GL_RGBA, GLES30.GL_HALF_FLOAT, null
        )
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboIds[0])
        GLES30.glFramebufferTexture2D(
            GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
            GLES30.GL_TEXTURE_2D, texIds[0], 0
        )

        // 缓存失败的尺寸，避免不支持浮点 FBO 的设备每帧重新分配和探测。
        metaballFboW = w
        metaballFboH = h
        val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
        if (status != GLES30.GL_FRAMEBUFFER_COMPLETE) {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
            GLES30.glDeleteFramebuffers(1, fboIds, 0)
            GLES30.glDeleteTextures(1, texIds, 0)
            useMetaball = false
            return
        }

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)

        metaballFboId = fboIds[0]
        metaballTexId = texIds[0]
        metaballFboW  = w
        metaballFboH  = h
        useMetaball   = true
    }

    // ───────── 资源释放 ─────────

    fun release() {
        if (vboId != 0) {
            GLES30.glDeleteBuffers(1, intArrayOf(vboId), 0)
            vboId = 0
        }
        if (dropProgram != 0){ GLES30.glDeleteProgram(dropProgram);   dropProgram = 0 }
        if (frontDropProgram != 0) {
            GLES30.glDeleteProgram(frontDropProgram)
            frontDropProgram = 0
        }
        if (splashProgram != 0) { GLES30.glDeleteProgram(splashProgram); splashProgram = 0 }
        if (metaballSplatProgram != 0) {
            GLES30.glDeleteProgram(metaballSplatProgram)
            metaballSplatProgram = 0
        }
        if (metaballCompositeProgram != 0) {
            GLES30.glDeleteProgram(metaballCompositeProgram)
            metaballCompositeProgram = 0
        }
        if (metaballFboId != 0) {
            GLES30.glDeleteFramebuffers(1, intArrayOf(metaballFboId), 0)
            metaballFboId = 0
        }
        if (metaballTexId != 0) {
            GLES30.glDeleteTextures(1, intArrayOf(metaballTexId), 0)
            metaballTexId = 0
        }
        initialized = false
        useMetaball = false
        metaballFboW = 0
        metaballFboH = 0
    }

    // ───────── 私有工具 ─────────

    private fun bindSplashAttributes(byteOffset: Int) {
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)
        for (attribute in 0..3) GLES30.glEnableVertexAttribArray(attribute)
        val stride = SPLASH_FLOATS_PER_VERTEX * 4
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, stride, byteOffset)
        GLES30.glVertexAttribPointer(1, 1, GLES30.GL_FLOAT, false, stride, byteOffset + 8)
        GLES30.glVertexAttribPointer(2, 1, GLES30.GL_FLOAT, false, stride, byteOffset + 12)
        GLES30.glVertexAttribPointer(3, 1, GLES30.GL_FLOAT, false, stride, byteOffset + 16)
    }

    private fun setAlphaBlending() {
        // RGB 预乘一次，目标 alpha 按覆盖率累积，供透明 TextureView 正确合成。
        GLES30.glBlendFuncSeparate(
            GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA,
            GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA
        )
    }

    private fun ensureBuffer(floatCount: Int) {
        if (floatCount > bufferCapacity) {
            val capacity = (floatCount * 1.5f).toInt().coerceAtLeast(4096)
            floatBuffer = ByteBuffer
                .allocateDirect(capacity * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
            bufferCapacity = capacity
        }
    }

    private fun setResolution(loc: Int, w: Float, h: Float) {
        GLES30.glUniform2f(loc, w, h)
    }

    private fun setColor(loc: Int, r: Float, g: Float, b: Float, a: Float) {
        GLES30.glUniform4f(loc, r, g, b, a)
    }

    private fun buildProgram(vertSrc: String, fragSrc: String): Int {
        val vs = compileShader(GLES30.GL_VERTEX_SHADER,   vertSrc)
        val fs = compileShader(GLES30.GL_FRAGMENT_SHADER, fragSrc)
        return GLES30.glCreateProgram().also { prog ->
            GLES30.glAttachShader(prog, vs)
            GLES30.glAttachShader(prog, fs)
            GLES30.glLinkProgram(prog)
            val linkStatus = IntArray(1)
            GLES30.glGetProgramiv(prog, GLES30.GL_LINK_STATUS, linkStatus, 0)
            if (linkStatus[0] == 0) {
                val infoLog = GLES30.glGetProgramInfoLog(prog)
                GLES30.glDeleteProgram(prog)
                GLES30.glDeleteShader(vs)
                GLES30.glDeleteShader(fs)
                error("Failed to link rain program: $infoLog")
            }
            GLES30.glDeleteShader(vs)
            GLES30.glDeleteShader(fs)
        }
    }

    private fun compileShader(type: Int, src: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, src)
        GLES30.glCompileShader(shader)
        val compileStatus = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            val infoLog = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            error("Failed to compile rain shader: $infoLog")
        }
        return shader
    }
}
