package me.spica.spicaweather3.ui.main.weather

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import me.spica.spicaweather3.common.model.WeatherCardType

internal val WeatherCardSpacing = 12.dp

/** 宽窗口按完整卡片分列，避免把手机布局中的半宽单位直接扩展为多列。 */
internal enum class WeatherCardsLayout(
    val minimumColumnWidth: Dp,
    private val tileAspectRatio: Float,
) {
    Compact(156.dp, 1f),
    Wide(320.dp, 1.6f);

    fun span(cardType: WeatherCardType): StaggeredGridItemSpan =
        if (cardType == WeatherCardType.ALERT || (this == Compact && cardType.spanSize > 1)) {
            StaggeredGridItemSpan.FullLine
        } else {
            StaggeredGridItemSpan.SingleLane
        }

    fun aspectRatio(cardType: WeatherCardType): Float =
        if (cardType.spanSize > 1) 0f else tileAspectRatio

    companion object {
        fun forContentWidth(width: Dp): WeatherCardsLayout =
            if (width >= Wide.minimumColumnWidth * 2 + WeatherCardSpacing) Wide else Compact
    }
}

@Composable
internal fun AdaptiveWeatherCardsGrid(
    state: LazyStaggeredGridState,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    content: LazyStaggeredGridScope.(WeatherCardsLayout) -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val contentWidth = maxWidth -
            contentPadding.calculateLeftPadding(LayoutDirection.Ltr) -
            contentPadding.calculateRightPadding(LayoutDirection.Ltr)
        val layout = WeatherCardsLayout.forContentWidth(contentWidth)

        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Adaptive(layout.minimumColumnWidth),
            state = state,
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(WeatherCardSpacing),
            verticalItemSpacing = WeatherCardSpacing,
            modifier = Modifier.fillMaxSize(),
        ) {
            content(layout)
        }
    }
}
