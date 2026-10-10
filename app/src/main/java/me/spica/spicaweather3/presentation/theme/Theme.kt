package me.spica.spicaweather3.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// MIUI 风格：蓝色强调色、中性灰背景、明亮卡片；固定配色保持视觉一致。
private val LightColors = lightColorScheme(
    primary = Color(0xFF3482FF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEAF2FF),
    onPrimaryContainer = Color(0xFF1677FF),
    inversePrimary = Color(0xFFAECDFF),
    secondary = Color(0xFFE6E6E6),
    onSecondary = Color(0xFF303030),
    secondaryContainer = Color(0xFFF0F0F0),
    onSecondaryContainer = Color(0xFF303030),
    tertiary = Color(0xFF3482FF),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEAF2FF),
    onTertiaryContainer = Color(0xFF3482FF),
    background = Color(0xFFF7F7F7),
    onBackground = Color.Black,
    surface = Color(0xFFF7F7F7),
    onSurface = Color.Black,
    surfaceVariant = Color.White,
    onSurfaceVariant = Color(0x99000000),
    surfaceDim = Color(0xFFE8E8E8),
    surfaceBright = Color.White,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F7F7),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFE8E8E8),
    surfaceContainerHighest = Color(0xFFE8E8E8),
    surfaceTint = Color.Transparent,
    inverseSurface = Color(0xFF242424),
    inverseOnSurface = Color(0xFFF2F2F2),
    outline = Color(0xFFD9D9D9),
    outlineVariant = Color(0xFFE0E0E0),
    error = Color(0xFFE94634),
    onError = Color.White,
    errorContainer = Color(0xFFFDF6F4),
    onErrorContainer = Color(0xFF410002),
    scrim = Color.Black,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF277AF7),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2B3B54),
    onPrimaryContainer = Color(0xFF99C7F1),
    inversePrimary = Color(0xFF3482FF),
    secondary = Color(0xFF505050),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF434343),
    onSecondaryContainer = Color(0xFFD9D9D9),
    tertiary = Color(0xFF4788FF),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF2B3B54),
    onTertiaryContainer = Color(0xFF99C7F1),
    background = Color.Black,
    onBackground = Color(0xFFF2F2F2),
    surface = Color.Black,
    onSurface = Color(0xFFF2F2F2),
    surfaceVariant = Color(0xFF242424),
    onSurfaceVariant = Color(0x80FFFFFF),
    surfaceDim = Color.Black,
    surfaceBright = Color(0xFF2D2D2D),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF171717),
    surfaceContainer = Color(0xFF242424),
    surfaceContainerHigh = Color(0xFF242424),
    surfaceContainerHighest = Color(0xFF2D2D2D),
    surfaceTint = Color.Transparent,
    inverseSurface = Color(0xFFF7F7F7),
    inverseOnSurface = Color.Black,
    outline = Color(0xFF404040),
    outlineVariant = Color(0xFF393939),
    error = Color(0xFFF12522),
    onError = Color.White,
    errorContainer = Color(0xFF2E0603),
    onErrorContainer = Color(0xFFFFDAD6),
    scrim = Color.Black,
)

private val WeatherShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun SpicaWeatherTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = WeatherTypography,
        shapes = WeatherShapes,
        content = content,
    )
}
