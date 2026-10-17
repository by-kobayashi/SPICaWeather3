package me.spica.spicaweather3.ui.widget.rain

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.EGLExt
import android.opengl.GLES30
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Choreographer

internal data class RainRenderState(
    val width: Int,
    val height: Int,
    val active: Boolean,
    val collisionRect: FloatArray?,
    val textCollisions: List<RainTextCollision>,
)

/** 一个 Surface 对应一个线程，避免旧线程退出时释放新 Surface 的 EGL 句柄。 */
internal class RainRenderThread(
    private val surface: SurfaceTexture,
    initialState: RainRenderState,
) : HandlerThread("RainRenderThread"), Choreographer.FrameCallback {
    @Volatile private var pendingState = initialState
    @Volatile private var stopRequested = false
    @Volatile private var renderHandler: Handler? = null
    private val surfaceLock = Any()
    private var finished = false // surfaceLock 保护，仅用于协调 Surface 的最终所有权
    private var releaseSurfaceOnFinish = false
    private var surfaceReleased = false

    private var choreographer: Choreographer? = null
    private var frameScheduled = false
    private var failed = false
    private var wasActive = false
    private val clock = RainFrameClock()
    private val applyState = Runnable { updateFrameScheduling() }
    private var simulation: RainSimulation? = null
    private var streaks: BackgroundRainStreaks? = null
    private var renderer: RainGLRenderer? = null
    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

    fun updateState(state: RainRenderState) {
        pendingState = state
        renderHandler?.let {
            it.removeCallbacks(applyState)
            it.post(applyState)
        }
    }

    fun requestStop(releaseSurface: Boolean) {
        synchronized(surfaceLock) {
            stopRequested = true
            if (releaseSurface && !surfaceReleased) {
                if (finished) {
                    surface.release()
                    surfaceReleased = true
                } else {
                    releaseSurfaceOnFinish = true
                }
            }
        }
        renderHandler?.post { finishRendering() }
    }

    override fun onLooperPrepared() {
        renderHandler = Handler(looper)
        choreographer = Choreographer.getInstance()
        updateFrameScheduling()
    }

    private fun updateFrameScheduling() {
        if (stopRequested) {
            finishRendering()
            return
        }
        val state = pendingState
        val active = state.active && state.width > 0 && state.height > 0
        if (!active) {
            choreographer?.removeFrameCallback(this)
            frameScheduled = false
            wasActive = false
            clock.reset()
        } else {
            // 暂停后允许重试失效的 EGL；持续失败时不进行忙循环。
            if (!wasActive) failed = false
            wasActive = true
            if (!frameScheduled && !failed) {
                frameScheduled = true
                choreographer?.postFrameCallback(this)
            }
        }
    }

    override fun doFrame(frameTimeNanos: Long) {
        frameScheduled = false
        val state = pendingState
        if (stopRequested || !state.active || state.width <= 0 || state.height <= 0) {
            updateFrameScheduling()
            return
        }
        try {
            ensureScene(state)
            if (stopRequested || !pendingState.active) return
            val sim = simulation!!
            sim.pendingCollisionRect = state.collisionRect
            sim.pendingTextCollisions = state.textCollisions
            repeat(clock.advance(frameTimeNanos)) {
                sim.update()
                streaks!!.update(RainFrameClock.STEP_SECONDS)
            }
            GLES30.glViewport(0, 0, state.width, state.height)
            GLES30.glClearColor(0f, 0f, 0f, 0f)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
            renderer!!.draw(sim, state.width, state.height, streaks)
            checkEgl(EGL14.eglSwapBuffers(eglDisplay, eglSurface), "eglSwapBuffers")
        } catch (error: RuntimeException) {
            Log.e("RainRenderThread", "Rain rendering failed", error)
            failed = true
            releaseGraphics()
        } finally {
            updateFrameScheduling()
        }
    }

    private fun ensureScene(state: RainRenderState) {
        if (renderer == null) {
            initEgl()
            renderer = RainGLRenderer()
            renderer!!.init()
        }
        val current = simulation
        if (current == null || current.width != state.width || current.height != state.height) {
            // 初始化、文字像素扫描和粒子预热均在本线程；尺寸变化使用同一组尺寸重建。
            simulation = RainSimulation(state.width, state.height).also {
                it.pendingCollisionRect = state.collisionRect
                it.pendingTextCollisions = state.textCollisions
                it.init()
            }
            streaks = BackgroundRainStreaks(state.width, state.height)
            clock.reset()
        }
    }

    private fun finishRendering() {
        synchronized(surfaceLock) { if (finished) return }
        choreographer?.removeFrameCallback(this)
        frameScheduled = false
        try {
            releaseGraphics()
        } finally {
            synchronized(surfaceLock) {
                finished = true
                if (releaseSurfaceOnFinish && !surfaceReleased) {
                    surface.release()
                    surfaceReleased = true
                }
            }
            quitSafely()
        }
    }

    private fun initEgl() {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        checkEgl(eglDisplay != EGL14.EGL_NO_DISPLAY, "eglGetDisplay")
        checkEgl(EGL14.eglInitialize(eglDisplay, null, 0, null, 0), "eglInitialize")
        val attributes = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGLExt.EGL_OPENGL_ES3_BIT_KHR,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        checkEgl(
            EGL14.eglChooseConfig(eglDisplay, attributes, 0, configs, 0, 1, count, 0) && count[0] > 0,
            "eglChooseConfig"
        )
        val config = checkNotNull(configs[0])
        eglContext = EGL14.eglCreateContext(
            eglDisplay, config, EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE), 0
        )
        checkEgl(eglContext != EGL14.EGL_NO_CONTEXT, "eglCreateContext")
        eglSurface = EGL14.eglCreateWindowSurface(
            eglDisplay, config, surface, intArrayOf(EGL14.EGL_NONE), 0
        )
        checkEgl(eglSurface != EGL14.EGL_NO_SURFACE, "eglCreateWindowSurface")
        checkEgl(EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext), "eglMakeCurrent")
    }

    private fun checkEgl(success: Boolean, operation: String) {
        check(success) { "$operation failed: 0x${Integer.toHexString(EGL14.eglGetError())}" }
    }

    private fun releaseGraphics() {
        try {
            renderer?.release()
        } finally {
            renderer = null
            simulation = null
            streaks = null
            clock.reset()
            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(
                    eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT
                )
                if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, eglSurface)
                if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(eglDisplay, eglContext)
                EGL14.eglTerminate(eglDisplay)
            }
            eglSurface = EGL14.EGL_NO_SURFACE
            eglContext = EGL14.EGL_NO_CONTEXT
            eglDisplay = EGL14.EGL_NO_DISPLAY
        }
    }
}
