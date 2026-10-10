package me.spica.spicaweather3.ui.widget.rain

import android.content.Context
import android.graphics.SurfaceTexture
import android.view.TextureView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/** 主线程只发布场景和可见状态；每个 Surface 的物理、GL 和清理均由其渲染线程拥有。 */
class RainTextureView(context: Context) : TextureView(context), TextureView.SurfaceTextureListener {
    private var renderThread: RainRenderThread? = null
    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var collisionRect: FloatArray? = null
    private var textCollisions: List<RainTextCollision> = emptyList()
    private var contentVisible = false
    private var aggregatedVisible = false
    private var released = false
    private var lifecycle: Lifecycle? = null
    private val lifecycleObserver = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_DESTROY) release() else publishState()
    }

    init {
        isOpaque = false
        surfaceTextureListener = this
    }

    fun bindLifecycle(lifecycle: Lifecycle) {
        if (released || this.lifecycle === lifecycle) return
        this.lifecycle?.removeObserver(lifecycleObserver)
        this.lifecycle = lifecycle
        lifecycle.addObserver(lifecycleObserver)
        publishState()
    }

    fun setContentVisible(visible: Boolean) {
        if (contentVisible == visible) return
        contentVisible = visible
        publishState()
    }

    fun setCollisionRect(left: Float, top: Float, right: Float, bottom: Float, cornerRadius: Float = 0f) {
        val old = collisionRect
        if (old != null && old[0] == left && old[1] == top && old[2] == right &&
            old[3] == bottom && old[4] == cornerRadius) return
        collisionRect = floatArrayOf(left, top, right, bottom, cornerRadius)
        publishState()
    }

    fun clearCollisionRect() {
        if (collisionRect == null) return
        collisionRect = null
        publishState()
    }

    fun setTextCollisions(collisions: List<RainTextCollision>) {
        if (textCollisions === collisions) return
        textCollisions = collisions
        publishState()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        aggregatedVisible = isShown && windowVisibility == VISIBLE
        publishState()
    }

    override fun onDetachedFromWindow() {
        aggregatedVisible = false
        publishState()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        aggregatedVisible = isVisible
        publishState()
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        if (released) return
        surfaceWidth = width
        surfaceHeight = height
        renderThread = RainRenderThread(surface, currentState()).also { it.start() }
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
        publishState()
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        val thread = renderThread ?: return true
        renderThread = null
        thread.requestStop(releaseSurface = true)
        // 返回 false：线程结束使用 EGL 后负责 release，不在主线程 join。
        return false
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

    /** AndroidView 永久离开 composition 时调用；Surface 的所有权仍在销毁回调中交接。 */
    fun release() {
        if (released) return
        released = true
        lifecycle?.removeObserver(lifecycleObserver)
        lifecycle = null
        renderThread?.requestStop(releaseSurface = false)
    }

    private fun currentState() = RainRenderState(
        width = surfaceWidth,
        height = surfaceHeight,
        active = !released && contentVisible && aggregatedVisible && isAttachedToWindow &&
            lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true,
        collisionRect = collisionRect,
        textCollisions = textCollisions,
    )

    private fun publishState() {
        renderThread?.updateState(currentState())
    }
}
