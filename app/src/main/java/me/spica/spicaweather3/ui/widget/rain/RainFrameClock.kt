package me.spica.spicaweather3.ui.widget.rain

/** 将显示帧时间换算为固定物理步数；暂停后不补算后台时间。 */
internal class RainFrameClock {
    private var lastFrameNanos = Long.MIN_VALUE
    private var accumulatedTicks = 0L

    fun advance(frameTimeNanos: Long): Int {
        val previous = lastFrameNanos
        lastFrameNanos = frameTimeNanos
        if (previous == Long.MIN_VALUE) return 0

        val elapsed = (frameTimeNanos - previous).coerceIn(0L, MAX_FRAME_NANOS)
        // 用整数的 1/(120 × 10^9) 秒计数，避免 1/120 秒取整造成漏步。
        accumulatedTicks += elapsed * STEPS_PER_SECOND
        val steps = (accumulatedTicks / NANOS_PER_SECOND).toInt()
        accumulatedTicks %= NANOS_PER_SECOND
        return steps
    }

    fun reset() {
        lastFrameNanos = Long.MIN_VALUE
        accumulatedTicks = 0L
    }

    companion object {
        const val STEPS_PER_SECOND = 120
        const val STEP_SECONDS = 1f / STEPS_PER_SECOND
        private const val NANOS_PER_SECOND = 1_000_000_000L
        private const val MAX_FRAME_NANOS = 50_000_000L // 最多补算 6 步
    }
}
