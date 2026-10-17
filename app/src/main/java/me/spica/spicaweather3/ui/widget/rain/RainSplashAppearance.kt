package me.spica.spicaweather3.ui.widget.rain

/** 水花外观只使用模拟时间与固定种子，不改变物理随机序列，也不会每帧重新随机大小。 */
internal object RainSplashAppearance {
    fun durationSeconds(seed: Int): Float = 0.42f + noise(seed xor 0x6d2b79f5) * 0.38f

    fun particleDurationSeconds(seed: Int, particleInGroup: Int, groupDuration: Float): Float =
        groupDuration * (0.75f + noise(seed * 31 + particleInGroup * 17) * 0.25f)

    fun sizeVariation(seed: Int, particleInGroup: Int): Float {
        val value = noise(seed * 13 + particleInGroup * 101 + 0x1b873593)
        // 九成是较小的水珠，少量稍大水珠作为点缀。
        return if (value < 0.9f) 0.78f + value / 0.9f * 0.22f
        else 1.03f + (value - 0.9f) / 0.1f * 0.13f
    }

    fun opacity(ageSeconds: Float, durationSeconds: Float): Float {
        if (ageSeconds <= 0f || ageSeconds >= durationSeconds) return 0f
        val progress = ageSeconds / durationSeconds
        val appear = smoothStep(ageSeconds / 0.025f)
        val disappear = 1f - smoothStep((progress - 0.12f) / 0.88f)
        return appear * disappear
    }

    fun sizeScale(ageSeconds: Float, durationSeconds: Float, variation: Float): Float {
        val progress = (ageSeconds / durationSeconds).coerceIn(0f, 1f)
        return variation * (1f - 0.78f * smoothStep(progress))
    }

    private fun smoothStep(value: Float): Float {
        val t = value.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun noise(seed: Int): Float {
        var bits = seed
        bits = (bits xor (bits ushr 16)) * 0x7feb352d
        bits = (bits xor (bits ushr 15)) * 0x846ca68b.toInt()
        bits = bits xor (bits ushr 16)
        return (bits ushr 8) / 16_777_215f
    }
}
