package me.spica.spicaweather3.ui.widget.rain

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable

@Immutable
data class RainTextCollision(
    val id: String,
    // 发布给渲染线程后保持只读；位置变化复用同一张位图。
    val bitmap: Bitmap,
    val left: Float,
    val top: Float,
)
