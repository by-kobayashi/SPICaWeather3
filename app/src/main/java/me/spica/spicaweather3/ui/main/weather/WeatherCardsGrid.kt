@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package me.spica.spicaweather3.ui.main.weather

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlin.random.Random
import me.spica.spicaweather3.common.model.WeatherCardConfig
import me.spica.spicaweather3.domain.model.WeatherData
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyStaggeredGridState

/**
 * 天气卡片网格
 * 
 * 展示多个天气卡片的网格布局，支持拖拽排序、入场动画、抖动效果。
 * 
 * @param cards 要显示的卡片配置列表
 * @param weatherData 天气数据
 * @param onReorder 卡片重新排序回调，参数为重新排序后的完整列表
 * @param scrollBehavior 滚动行为
 * @param modifier 修饰符
 */
@Composable
fun WeatherCardsGrid(
    cards: List<WeatherCardConfig>,
    cityId: String,
    weatherData: WeatherData,
    onReorder: (List<WeatherCardConfig>) -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
    paddingValues: PaddingValues
) {
    // 拖拽状态标记
    var isDragging by remember { mutableStateOf(false) }

    // 当前显示的卡片列表（用于拖拽过程中的实时更新）
    var displayCards by remember(cards) {
        mutableStateOf(cards)
    }
    // 拖拽手势可跨越列表更新，结束回调始终读取最新的列表状态。
    val finishDrag = rememberUpdatedState {
        isDragging = false
        onReorder(displayCards)
    }

    // iOS 风格的抖动动画 - 当有任何 item 被拖拽且当前 item 未被拖拽时启用
    val infiniteTransition = rememberInfiniteTransition(label = "shake")
    val shakeOffset by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(80, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shake_offset"
    )

    val listState = rememberLazyStaggeredGridState()
    val hapticFeedback = LocalHapticFeedback.current

    // 拖拽排序状态
    val reorderableState = rememberReorderableLazyStaggeredGridState(listState) { from, to ->
        val fromIndex = displayCards.indexOfFirst { it.cardType.key == from.key }
        val toIndex = displayCards.indexOfFirst { it.cardType.key == to.key }
        if (fromIndex < 0 || toIndex < 0 || fromIndex == toIndex) {
            return@rememberReorderableLazyStaggeredGridState
        }
        // 实时更新显示列表
        val mutableList = displayCards.toMutableList()
        val item = mutableList.removeAt(fromIndex)
        mutableList.add(toIndex, item)
        displayCards = mutableList

        // 触发触觉反馈
        hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }

    AdaptiveWeatherCardsGrid(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        state = listState,
        contentPadding = paddingValues,
    ) { layout ->
        displayCards.forEach { cardConfig ->
            item(
                key = cardConfig.cardType.key,
                contentType = cardConfig.cardType,
                span = layout.span(cardConfig.cardType),
            ) {
                // 优化延迟和持续时间，创造更流畅的波浪式入场效果
                val delayMillis = remember(cardConfig.cardType.key) { Random.nextInt(0, 280) }
                val durationMillis = remember(cardConfig.cardType.key) { Random.nextInt(100, 700) }

                ReorderableItem(
                    key = cardConfig.cardType.key,
                    state = reorderableState
                ) { isDraggingThis ->
                    val shouldShake = isDragging && !isDraggingThis

                    WeatherCardItem(
                        cardConfig = cardConfig,
                        cityId = cityId,
                        weatherData = weatherData,
                        aspectRatio = layout.aspectRatio(cardConfig.cardType),
                        delayMillis = delayMillis,
                        durationMillis = durationMillis,
                        shakeOffset = if (shouldShake) shakeOffset else 0f,
                        onDragStart = { isDragging = true },
                        onDragStop = { finishDrag.value() },
                    )
                }
            }
        }
        item(
            key = "weather_attribution",
            span = StaggeredGridItemSpan.FullLine,
        ) {
            Text(
                "感谢和风天气提供数据支持",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodySmall,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
