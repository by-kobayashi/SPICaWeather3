package me.spica.spicaweather3.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val WeatherTypography = Typography(
    headlineLarge = TextStyle(fontSize = 32.sp),
    headlineMedium = TextStyle(fontSize = 24.sp, letterSpacing = 0.1.sp),
    headlineSmall = TextStyle(fontSize = 20.sp, letterSpacing = 0.15.sp),
    titleLarge = TextStyle(fontSize = 18.sp, letterSpacing = 0.1.sp),
    titleMedium = TextStyle(fontSize = 17.sp),
    titleSmall = TextStyle(fontSize = 16.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, letterSpacing = 0.5.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, letterSpacing = 0.4.sp),
    bodySmall = TextStyle(fontSize = 13.sp, letterSpacing = 0.5.sp),
    labelLarge = TextStyle(fontSize = 17.sp, letterSpacing = 0.4.sp),
    labelMedium = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.1.sp,
    ),
    labelSmall = TextStyle(fontSize = 11.sp, letterSpacing = 0.5.sp),
)


// 主页 新增按钮尺寸
val MAIN_PLUS_BUTTON_SIZE = 64.dp

// 卡片组件的Padding
val WIDGET_CARD_PADDING = 12.dp

// 卡片组件的圆角
val WIDGET_CARD_CORNER_SHAPE = RoundedCornerShape(12.dp)

// 卡片组件的标题

@Composable
fun WIDGET_CARD_TITLE_TEXT_STYLE(): TextStyle {
  return TextStyle(fontWeight = FontWeight.W700, color = MaterialTheme.colorScheme.onSurface)
}
