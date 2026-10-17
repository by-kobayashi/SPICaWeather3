package me.spica.spicaweather3.ui.widget.rain

import android.graphics.Bitmap
import org.jbox2d.collision.shapes.PolygonShape
import org.jbox2d.common.Settings
import org.jbox2d.common.Vec2
import org.jbox2d.dynamics.Body
import org.jbox2d.dynamics.BodyDef
import org.jbox2d.dynamics.FixtureDef
import org.jbox2d.dynamics.World

/** 每段文字独立持有局部坐标夹具，平移不重新读像素或创建夹具。仅在渲染线程使用。 */
internal class RainTextColliders(private val world: World, private val proportion: Float) {
    private class Entry(
        val bitmap: Bitmap,
        val generationId: Int,
        val body: Body,
        var left: Float,
        var top: Float,
    )

    private val entries = LinkedHashMap<String, Entry>()
    private var applied: List<RainTextCollision>? = null
    private val position = Vec2()

    fun update(collisions: List<RainTextCollision>) {
        if (collisions === applied) return

        val iterator = entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (collisions.none { it.id == entry.key }) {
                world.destroyBody(entry.value.body)
                iterator.remove()
            }
        }

        for (collision in collisions) {
            val bitmap = collision.bitmap
            val old = entries[collision.id]
            if (old == null || old.bitmap !== bitmap || old.generationId != bitmap.generationId) {
                old?.let { world.destroyBody(it.body) }
                val body = createBody(bitmap, collision.left, collision.top)
                entries[collision.id] = Entry(
                    bitmap, bitmap.generationId, body, collision.left, collision.top
                )
            } else if (old.left != collision.left || old.top != collision.top) {
                position.set(collision.left / proportion, collision.top / proportion)
                old.body.setTransform(position, 0f)
                old.left = collision.left
                old.top = collision.top
            }
        }
        applied = collisions
    }

    private fun createBody(bitmap: Bitmap, left: Float, top: Float): Body {
        val body = world.createBody(BodyDef().apply {
            position.set(left / proportion, top / proportion)
        })
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val sampleStepPx = (bitmap.width / 42).coerceIn(4, 8)

        for (xStart in 0 until bitmap.width step sampleStepPx) {
            val xEnd = minOf(bitmap.width, xStart + sampleStepPx)
            var runStart = -1
            for (y in 0 until bitmap.height) {
                val row = y * bitmap.width
                var opaque = false
                for (x in xStart until xEnd) {
                    if (pixels[row + x] ushr 24 >= 32) {
                        opaque = true
                        break
                    }
                }
                if (opaque) {
                    if (runStart < 0) runStart = y
                } else if (runStart >= 0) {
                    addFixture(body, xStart, xEnd, runStart, y)
                    runStart = -1
                }
            }
            if (runStart >= 0) addFixture(body, xStart, xEnd, runStart, bitmap.height)
        }
        // 空白位图同样缓存，避免每一帧重新尝试构建。
        return body
    }

    private fun addFixture(body: Body, xStart: Int, xEnd: Int, yStart: Int, yEnd: Int) {
        val runHeight = yEnd - yStart
        if (runHeight < 3) return
        val halfWidth = (xEnd - xStart) * 0.5f / proportion
        val halfHeight = runHeight * 0.5f / proportion
        if (halfWidth < Settings.linearSlop || halfHeight < Settings.linearSlop) return

        val shape = PolygonShape().apply {
            setAsBox(
                halfWidth, halfHeight,
                Vec2((xStart + xEnd) * 0.5f / proportion, (yStart + yEnd) * 0.5f / proportion),
                0f
            )
        }
        body.createFixture(FixtureDef().apply {
            this.shape = shape
            friction = 0.8f
            restitution = 0.15f
            filter.maskBits = 0b01
            filter.groupIndex = 0b01
        })
    }
}
