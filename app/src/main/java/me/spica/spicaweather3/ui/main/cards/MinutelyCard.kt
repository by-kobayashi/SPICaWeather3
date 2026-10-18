package me.spica.spicaweather3.ui.main.cards

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.spica.spicaweather3.R
import me.spica.spicaweather3.domain.model.MinutelyPrecip
import me.spica.spicaweather3.domain.model.MinutelyPrecipData
import me.spica.spicaweather3.domain.model.WeatherData
import me.spica.spicaweather3.presentation.theme.SpicaWeatherTheme
import me.spica.spicaweather3.presentation.theme.WIDGET_CARD_CORNER_SHAPE
import me.spica.spicaweather3.presentation.theme.WIDGET_CARD_PADDING
import me.spica.spicaweather3.presentation.theme.WIDGET_CARD_TITLE_TEXT_STYLE

/**
 * 分钟级降水信息卡片
 * 显示降水摘要和未来两小时的降水趋势，沿用统一的卡片样式
 *
 * @param weatherData 天气数据，包含分钟级降水预报
 * @param modifier 修饰符
 */
@Composable
fun MinutelyCard(
  weatherData: WeatherData,
  modifier: Modifier = Modifier
) {
  MinutelyCardContent(rainData = weatherData.minutelyPrecip, modifier = modifier)
}

@Composable
private fun MinutelyCardContent(
  rainData: MinutelyPrecipData?,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier.padding(WIDGET_CARD_PADDING),
    verticalArrangement = Arrangement.spacedBy(8.dp),
    horizontalAlignment = Alignment.Start,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Icon(
        painter = painterResource(id = R.drawable.material_symbols_outlined_rainy_light),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurface
      )
      Text(
        text = stringResource(R.string.minutely_info_title),
        color = MaterialTheme.colorScheme.onSurface,
        style = WIDGET_CARD_TITLE_TEXT_STYLE(),
      )
    }

    val summary = rainData?.summary?.takeIf { it.isNotBlank() }
    if (summary != null || rainData?.next2Hours.isNullOrEmpty()) {
      Text(
        text = summary ?: stringResource(R.string.minutely_no_data),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
      )
    }

    if (rainData != null && rainData.next2Hours.isNotEmpty()) {
      MinutelyPrecipitationChart(data = rainData.next2Hours)
    }
  }
}

/**
 * 未来两小时降水柱状图，雨量等级与柱形分区绘制
 */
@Composable
private fun MinutelyPrecipitationChart(
  data: List<MinutelyPrecip>,
  modifier: Modifier = Modifier
) {
  val textMeasurer = rememberTextMeasurer()
  val labelStyle = MaterialTheme.typography.labelSmall.copy(
    color = MaterialTheme.colorScheme.onSurfaceVariant
  )
  val intensityLabels = listOf(
    stringResource(R.string.rain_light),
    stringResource(R.string.rain_moderate),
    stringResource(R.string.rain_heavy),
  )
  val timeLabels = listOf(
    stringResource(R.string.minutely_now),
    stringResource(R.string.minutely_one_hour_later),
    stringResource(R.string.minutely_two_hours_later),
  )
  val chartDescription = stringResource(R.string.minutely_chart_description)
  val barColor = MaterialTheme.colorScheme.primary
  val gridColor = MaterialTheme.colorScheme.outlineVariant
  val axisColor = MaterialTheme.colorScheme.outline

  Box(
    modifier = modifier
      .fillMaxWidth()
      .heightIn(min = 128.dp, max = 180.dp)
      .aspectRatio(2.8f)
      .semantics { contentDescription = chartDescription }
      .drawWithCache {
        val intensityText = intensityLabels.map { textMeasurer.measure(it, labelStyle) }
        val timeText = timeLabels.map { textMeasurer.measure(it, labelStyle) }
        val labelGap = 8.dp.toPx()
        val axisWidth = 1.dp.toPx()
        val plotLeft = axisWidth / 2f
        // 右侧为雨量等级预留空间，避免柱形遮挡文字
        val plotRight = (size.width - intensityText.maxOf { it.size.width } - labelGap)
          .coerceAtLeast(plotLeft)
        val plotTop = axisWidth / 2f
        val plotBottom = (size.height - timeText.maxOf { it.size.height } - labelGap)
          .coerceAtLeast(plotTop)
        val plotWidth = plotRight - plotLeft
        val plotHeight = plotBottom - plotTop
        val itemWidth = plotWidth / data.size
        val barWidth = minOf(4.dp.toPx(), itemWidth * 0.6f)
        val barBrush = Brush.verticalGradient(
          colors = listOf(barColor.copy(alpha = 0.9f), barColor.copy(alpha = 0.55f)),
          startY = plotTop,
          endY = plotBottom,
        )
        val gridDash = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx()))

        onDrawBehind {
          if (plotWidth <= 0f || plotHeight <= 0f) return@onDrawBehind

          repeat(3) { index ->
            val y = plotBottom - plotHeight * (index + 1) / 3f
            drawLine(
              color = gridColor,
              start = Offset(plotLeft, y),
              end = Offset(plotRight, y),
              strokeWidth = axisWidth,
              pathEffect = gridDash,
            )
            // 雨量等级文字在对应的强度区间内垂直居中
            val label = intensityText[index]
            drawText(
              textLayoutResult = label,
              topLeft = Offset(
                plotRight + labelGap,
                plotBottom - plotHeight * (index + 0.5f) / 3f - label.size.height / 2f,
              ),
            )
          }

          data.forEachIndexed { index, minute ->
            val precipitation = minute.precipitation
            // 无雨时段不绘制柱形，避免在基线上留下圆点
            if (precipitation.isFinite() && precipitation > 0.0) {
              val barHeight = (plotHeight * precipitation.coerceIn(0.0, 1.0)).toFloat()
              drawRoundRect(
                brush = barBrush,
                topLeft = Offset(
                  plotLeft + itemWidth * (index + 0.5f) - barWidth / 2f,
                  plotBottom - barHeight,
                ),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
              )
            }
          }

          drawLine(
            color = axisColor,
            start = Offset(plotLeft, plotTop),
            end = Offset(plotLeft, plotBottom),
            strokeWidth = axisWidth,
          )
          drawLine(
            color = axisColor,
            start = Offset(plotLeft, plotBottom),
            end = Offset(plotRight, plotBottom),
            strokeWidth = axisWidth,
          )

          timeText.forEachIndexed { index, label ->
            val x = when (index) {
              0 -> plotLeft
              1 -> (plotLeft + plotRight - label.size.width) / 2f
              else -> plotRight - label.size.width
            }
            drawText(
              textLayoutResult = label,
              topLeft = Offset(x, plotBottom + labelGap),
            )
          }
        }
      }
  )
}

@Preview(name = "浅色主题", widthDp = 360)
@Preview(name = "深色主题", widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "窄屏", widthDp = 280)
@Preview(name = "大字体", widthDp = 360, fontScale = 1.5f)
@Composable
private fun MinutelyCardPreview() {
  SpicaWeatherTheme {
    Surface(
      color = MaterialTheme.colorScheme.surfaceContainer,
      shape = WIDGET_CARD_CORNER_SHAPE,
    ) {
      MinutelyCardContent(
        rainData = MinutelyPrecipData(
          summary = "25分钟左右雨会变小，40分钟左右雨渐停",
          isPrecipitating = true,
          precipType = "rain",
          currentIntensity = 0.7,
          next2Hours = List(24) { index ->
            MinutelyPrecip(
              time = "",
              precipitation = (0.7 - index * 0.1).coerceAtLeast(0.0),
              type = "rain",
            )
          },
        ),
      )
    }
  }
}
