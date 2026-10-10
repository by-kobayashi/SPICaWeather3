package me.spica.spicaweather3.ui.main.cards

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.StyleSpan
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.spica.spicaweather3.R
import me.spica.spicaweather3.common.type.WeatherAnimType
import me.spica.spicaweather3.domain.model.WeatherData
import me.spica.spicaweather3.ui.widget.WeatherBackground
import me.spica.spicaweather3.ui.widget.rain.RainTextCollision

/**
 * 当前天气卡片
 * 展示当前实时天气信息的核心卡片组件，包含动态天气背景和主要天气指标
 * 特点：
 * - 动态天气背景动画（晴天、雨天、雪天等不同场景）
 * - 大号温度显示
 * - 体感温度和湿度信息
 * - 文字渐显和平移动画
 * - 毛玻璃模糊效果
 *
 * @param modifier 修饰符
 * @param weatherData 当前天气数据
 * @param startAnim 是否开始播放动画
 */
@Composable
fun NowCard(modifier: Modifier = Modifier, weatherData: WeatherData, startAnim: Boolean) {


    // 根据天气图标ID计算对应的天气动画类型（晴天、雨天、雪天等）
    val currentWeatherAnimType = WeatherAnimType.RainDark

    // ==================== 动画配置 ====================
    // 温度数字动画（无延迟，持续450ms）
    val textAnimValue1 = animateFloatAsState(
        if (startAnim) 1f else 0f,
        animationSpec = tween(durationMillis = 250, 0)
    ).value

    // 天气状况和体感温度动画（延迟50ms，持续550ms）
    val textAnimValue2 = animateFloatAsState(
        if (startAnim) 1f else 0f,
        animationSpec = tween(durationMillis = 350, 50)
    ).value

    // 湿度信息动画（延迟150ms，持续750ms）
    val textAnimValue3 = animateFloatAsState(
        if (startAnim) 1f else 0f,
        animationSpec = tween(durationMillis = 550, 150)
    ).value

    // 形状仅依赖文字内容和未裁剪的布局尺寸；滚动不改变卡片内部的碰撞坐标。
    val collisionLayout = remember { RainCollisionLayout() }
    val temperatureSize = collisionLayout.temperature.size
    val conditionSize = collisionLayout.condition.size
    val density = LocalDensity.current
    val tempTextSizePx = with(density) { 94.sp.toPx() }.toInt()
    val unitTextSizePx = with(density) { 45.sp.toPx() }.toInt()
    val conditionTextSizePx = with(density) { 16.sp.toPx() }.toInt()
    val conditionPaddingStartPx = with(density) { 12.dp.toPx() }.toInt()
    val translationDistancePx = with(density) { 12.dp.toPx() }
    val showRain = currentWeatherAnimType.showRain
    val temperatureBitmap = remember(
        showRain, weatherData.current.temperature, temperatureSize, tempTextSizePx, unitTextSizePx,
    ) {
        if (!showRain || temperatureSize.width == 0 || temperatureSize.height == 0) null
        else createTemperatureCollisionBitmap(
            temperature = weatherData.current.temperature,
            widthPx = temperatureSize.width,
            heightPx = temperatureSize.height,
            temperatureTextSizePx = tempTextSizePx,
            unitTextSizePx = unitTextSizePx,
        )
    }
    val conditionText = stringResource(
        R.string.now_card_condition,
        weatherData.current.condition,
        weatherData.current.feelsLike
    )
    val conditionBitmap = remember(
        showRain, conditionText, conditionSize, conditionTextSizePx, conditionPaddingStartPx,
    ) {
        if (!showRain || conditionSize.width == 0 || conditionSize.height == 0) null
        else createSolidCollisionBitmap(
            text = conditionText,
            widthPx = conditionSize.width,
            heightPx = conditionSize.height,
            textSizePx = conditionTextSizePx,
            paddingStartPx = conditionPaddingStartPx,
        )
    }
    val temperatureOffset = collisionLayout.temperature.offset
    val conditionOffset = collisionLayout.condition.offset
    val temperatureTop = temperatureOffset.y - translationDistancePx * (1f - textAnimValue1)
    val conditionTop = conditionOffset.y - translationDistancePx * (1f - textAnimValue2)
    val textCollisions = remember(
        temperatureBitmap, conditionBitmap, temperatureOffset.x, temperatureTop,
        conditionOffset.x, conditionTop,
    ) {
        listOfNotNull(
            temperatureBitmap?.let {
                RainTextCollision("temperature", it, temperatureOffset.x, temperatureTop)
            },
            conditionBitmap?.let {
                RainTextCollision("condition", it, conditionOffset.x, conditionTop)
            },
        )
    }

    // ==================== 主布局 ====================
    // 使用 Box 叠加布局：底层为天气背景动画，上层为天气信息文字
    Box(
        modifier = modifier
            .aspectRatio(1.21f) // 宽高比 1.21:1，保持卡片比例
            .onGloballyPositioned { collisionLayout.updateCard(it) }
    ) {
        // 动态天气背景（晴天、雨天、雪天等不同动画效果）
        WeatherBackground(
            currentWeatherType = currentWeatherAnimType,
            collapsedFraction = 0f, // 0=完全展开，1=完全折叠
            textCollisions = textCollisions,
            weatherData = weatherData
        )

        // 天气信息文字层
        Column(
            modifier = Modifier
                .padding(
                    12.dp
                )
                .padding(
                    horizontal = 12.dp,
                    vertical = 12.dp
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 当前温度（大号显示，带渐显和向上平移动画）
            Box(
                modifier = Modifier.onGloballyPositioned {
                    collisionLayout.temperature.update(it, collisionLayout.card)
                }
            ) {
                Text(
                    modifier = Modifier
                        .graphicsLayer {
                            alpha = textAnimValue1
                            translationY = -12.dp.toPx() * (1f - textAnimValue1)
                        },
                    text = buildAnnotatedString {
                        // 温度数字（94sp 超大字体）
                        withStyle(
                            style = SpanStyle(
                                color = Color.White,
                                fontSize = 94.sp,
                                fontWeight = FontWeight.W800
                            )
                        ) {
                            append(weatherData.current.temperature.toString())
                        }
                        // 温度单位（45sp 较小字体）
                        withStyle(
                            style = SpanStyle(
                                color = Color.White,
                                fontSize = 45.sp,
                                fontWeight = FontWeight.W800
                            )
                        ) {
                            append("°")
                        }
                    },
                )
            }

            // 天气状况和体感温度（带延迟渐显和向上平移动画）
            Box(
                modifier = Modifier.onGloballyPositioned {
                    collisionLayout.condition.update(it, collisionLayout.card)
                }
            ) {
                Text(
                    text = stringResource(
                        R.string.now_card_condition,
                        weatherData.current.condition,
                        weatherData.current.feelsLike
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    fontWeight = FontWeight.W600,
                    modifier = Modifier
                        .graphicsLayer {
                            alpha = textAnimValue2
                            translationY = -12.dp.toPx() * (1f - textAnimValue2)
                        }
                        .padding(start = 12.dp)
                )
            }

            // 湿度信息（带毛玻璃背景、延迟渐显和向上平移动画）
            Text(
                text = stringResource(
                    R.string.now_card_humidity,
                    weatherData.current.humidity
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                fontWeight = FontWeight.W600,
                modifier = Modifier
                    .graphicsLayer {
                        alpha = textAnimValue3
                        translationY = -12.dp.toPx() * (1f - textAnimValue3)
                    }
                    .padding(start = 12.dp)
                    .clip(
                        RoundedCornerShape(12.dp)
                    )
                    // 应用 Cupertino 风格超薄毛玻璃效果
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.2f),
                        CircleShape
                    )
                    .padding(
                        horizontal = 8.dp,
                        vertical = 4.dp
                    )
            )
        }
    }
}

/** 跟踪未参与文字平移动画的外层布局，避免重复计入 graphicsLayer 的位移。 */
private class RainCollisionLayout {
    var card: LayoutCoordinates? = null
        private set
    val temperature = TextBounds()
    val condition = TextBounds()

    fun updateCard(coordinates: LayoutCoordinates) {
        card = coordinates
        temperature.updateParent(coordinates)
        condition.updateParent(coordinates)
    }

    class TextBounds {
        private var coordinates: LayoutCoordinates? = null
        var size by mutableStateOf(IntSize.Zero)
            private set
        var offset by mutableStateOf(Offset.Zero)
            private set

        fun update(coordinates: LayoutCoordinates, parent: LayoutCoordinates?) {
            this.coordinates = coordinates
            updateParent(parent)
        }

        fun updateParent(parent: LayoutCoordinates?) {
            val child = coordinates ?: return
            if (parent?.isAttached != true || !child.isAttached) return
            // localPositionOf 消除共同祖先的滚动、缩放；size 不受祖先裁剪影响。
            offset = parent.localPositionOf(child, Offset.Zero)
            size = child.size
        }
    }
}

private fun createSolidCollisionBitmap(
    text: String,
    widthPx: Int,
    heightPx: Int,
    textSizePx: Int,
    paddingStartPx: Int = 0,
): Bitmap {
    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = textSizePx.toFloat()
        typeface = Typeface.DEFAULT_BOLD
    }
    val textWidthPx = (widthPx - paddingStartPx).coerceAtLeast(1)
    val layout = StaticLayout.Builder
        .obtain(text, 0, text.length, paint, textWidthPx)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setIncludePad(false)
        .build()

    return Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888).also { bitmap ->
        Canvas(bitmap).apply {
            val dy = if (layout.height < heightPx) (heightPx - layout.height) * 0.5f else 0f
            translate(paddingStartPx.toFloat(), dy)
            layout.draw(this)
        }
    }
}

private fun createTemperatureCollisionBitmap(
    temperature: Int,
    widthPx: Int,
    heightPx: Int,
    temperatureTextSizePx: Int,
    unitTextSizePx: Int,
): Bitmap {
    val text = SpannableStringBuilder("${temperature}°").apply {
        val tempEnd = temperature.toString().length
        setSpan(AbsoluteSizeSpan(temperatureTextSizePx), 0, tempEnd, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        setSpan(StyleSpan(Typeface.BOLD), 0, tempEnd, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        setSpan(AbsoluteSizeSpan(unitTextSizePx), tempEnd, length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        setSpan(StyleSpan(Typeface.BOLD), tempEnd, length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        typeface = Typeface.DEFAULT_BOLD
    }
    val layout = StaticLayout.Builder
        .obtain(text, 0, text.length, paint, widthPx)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setIncludePad(false)
        .build()

    return Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888).also { bitmap ->
        Canvas(bitmap).apply {
            if (layout.height < heightPx) {
                translate(0f, ((heightPx - layout.height) * 0.5f))
            }
            layout.draw(this)
        }
    }
}
