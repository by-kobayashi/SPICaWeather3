package me.spica.spicaweather3.ui.main.cards

import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import me.spica.spicaweather3.R
import me.spica.spicaweather3.domain.model.WeatherData
import me.spica.spicaweather3.presentation.theme.SpicaWeatherTheme
import me.spica.spicaweather3.presentation.theme.WIDGET_CARD_CORNER_SHAPE
import me.spica.spicaweather3.presentation.theme.WIDGET_CARD_PADDING
import me.spica.spicaweather3.presentation.theme.WIDGET_CARD_TITLE_TEXT_STYLE

private val SolarTimeFormat = DateTimeFormatter.ofPattern("HH:mm")

/**
 * 日出日落信息卡片
 * 并排显示日出、日落时间，通过昼夜时间带展示当前时刻和日照时长
 *
 * @param weatherEntity 天气数据，包含当日的日出、日落时间
 * @param startAnim 是否开始播放入场动画
 * @param modifier 修饰符
 */
@Composable
fun SunriseCard(
  weatherEntity: WeatherData,
  startAnim: Boolean,
  modifier: Modifier = Modifier,
) {
  val today = weatherEntity.forecast.today
  val sunrise = remember(today.sunrise) { parseSolarTime(today.sunrise) }
  val sunset = remember(today.sunset) { parseSolarTime(today.sunset) }
  val forecastDate = remember(today.date) {
    try {
      LocalDate.parse(today.date)
    } catch (_: DateTimeParseException) {
      null
    }
  }
  // 优先使用观测时间中的城市时区偏移，解析失败时依次回退到生成时间和设备时区
  val zone = remember(weatherEntity.current.obsTime, weatherEntity.generatedAt) {
    sequenceOf(weatherEntity.current.obsTime, weatherEntity.generatedAt)
      .mapNotNull { timestamp ->
        try {
          OffsetDateTime.parse(timestamp).offset
        } catch (_: DateTimeParseException) {
          null
        }
      }
      .firstOrNull() ?: ZoneId.systemDefault()
  }
  val now by currentSolarTime(zone)

  SunriseCardContent(
    sunrise = sunrise,
    sunset = sunset,
    // 仅为城市当地当天的预报显示当前时刻，避免缓存数据产生误导
    currentTime = now.toLocalTime().takeIf { forecastDate == now.toLocalDate() },
    startAnim = startAnim,
    modifier = modifier,
  )
}

/**
 * 随界面生命周期按整分钟更新城市当地时间，进入后台后暂停更新
 */
@Composable
private fun currentSolarTime(zone: ZoneId): State<LocalDateTime> {
  val lifecycle = LocalLifecycleOwner.current.lifecycle
  return produceState(LocalDateTime.now(zone), zone, lifecycle) {
    lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
      while (isActive) {
        value = LocalDateTime.now(zone).withSecond(0).withNano(0)
        // 对齐下一整分钟，避免更新时刻随刷新次数逐渐偏移
        delay(60_000L - System.currentTimeMillis() % 60_000L)
      }
    }
  }
}

@Composable
private fun SunriseCardContent(
  sunrise: LocalTime?,
  sunset: LocalTime?,
  currentTime: LocalTime?,
  startAnim: Boolean,
  modifier: Modifier = Modifier,
) {
  // ==================== 动画配置 ====================
  val entrance = animateFloatAsState(
    targetValue = if (startAnim) 1f else 0f,
    animationSpec = tween(durationMillis = 300),
    label = "sunrise_content",
  )
  val hasDaylight = sunrise != null && sunset != null && sunset.isAfter(sunrise)

  // ==================== 主布局 ====================
  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(WIDGET_CARD_PADDING)
      .graphicsLayer {
        alpha = entrance.value
        translationY = 4.dp.toPx() * (1f - entrance.value)
      },
    verticalArrangement = Arrangement.SpaceBetween,
  ) {
    // 标题行（图标 + 文字）
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        painter = painterResource(R.drawable.material_symbols_outlined_sunny),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurface,
      )
      Text(
        text = stringResource(R.string.sunrise_title),
        style = WIDGET_CARD_TITLE_TEXT_STYLE(),
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }

    // 左侧显示日出时间，右侧显示日落时间
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      SolarTimeReadout(
        label = stringResource(R.string.sunrise_time_label),
        time = sunrise,
        description = stringResource(R.string.sunrise_label, sunrise?.format(SolarTimeFormat) ?: "—"),
        modifier = Modifier.weight(1f),
      )
      SolarTimeReadout(
        label = stringResource(R.string.sunset_time_label),
        time = sunset,
        description = stringResource(R.string.sunset_label, sunset?.format(SolarTimeFormat) ?: "—"),
        modifier = Modifier.weight(1f),
        alignment = Alignment.End,
      )
    }

    // 有效的日出、日落时段用于绘制昼夜时间带并计算日照时长
    if (hasDaylight) {
      val daylightMinutes = Duration.between(sunrise, sunset).toMinutes()
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(4.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "00",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clearAndSetSemantics { },
          )
          DaylightTimeline(
            sunrise = sunrise,
            sunset = sunset,
            currentTime = currentTime,
            modifier = Modifier.weight(1f),
          )
          Text(
            text = "24",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clearAndSetSemantics { },
          )
        }
        // 日照时长独占一行，较长文案随可用宽度缩放，避免大字体下被裁切
        Text(
          text = stringResource(
            R.string.sunrise_daylight_duration,
            daylightMinutes / 60,
            daylightMinutes % 60,
          ),
          modifier = Modifier.fillMaxWidth(),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
          autoSize = TextAutoSize.StepBased(
            minFontSize = MaterialTheme.typography.labelSmall.fontSize * 0.8f,
            maxFontSize = MaterialTheme.typography.labelSmall.fontSize,
          ),
          maxLines = 1,
          softWrap = false,
        )
      }
    } else {
      Text(
        text = stringResource(R.string.sunrise_no_data),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

/**
 * 日出或日落时间读数，合并标签与时间的无障碍描述
 */
@Composable
private fun SolarTimeReadout(
  label: String,
  time: LocalTime?,
  description: String,
  modifier: Modifier = Modifier,
  alignment: Alignment.Horizontal = Alignment.Start,
) {
  Column(
    modifier = modifier.clearAndSetSemantics { contentDescription = description },
    verticalArrangement = Arrangement.spacedBy(4.dp),
    horizontalAlignment = alignment,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
    )
    Text(
      text = time?.format(SolarTimeFormat) ?: "—",
      style = MaterialTheme.typography.headlineMedium.copy(
        fontWeight = FontWeight.SemiBold,
        fontFeatureSettings = "tnum",
      ),
      color = MaterialTheme.colorScheme.onSurface,
      autoSize = TextAutoSize.StepBased(
        minFontSize = MaterialTheme.typography.titleSmall.fontSize,
        maxFontSize = MaterialTheme.typography.headlineMedium.fontSize,
      ),
      maxLines = 1,
      softWrap = false,
    )
  }
}

/**
 * 将日出、日落与当前时刻映射到0至24时的昼夜时间带
 */
@Composable
private fun DaylightTimeline(
  sunrise: LocalTime,
  sunset: LocalTime,
  currentTime: LocalTime?,
  modifier: Modifier = Modifier,
) {
  val dayColor = MaterialTheme.colorScheme.primary
  val nightColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
  val surfaceColor = MaterialTheme.colorScheme.surfaceContainer
  val nightMarkerColor = MaterialTheme.colorScheme.onSurfaceVariant
  val description = if (currentTime == null) {
    stringResource(R.string.sunrise_timeline_description)
  } else {
    stringResource(R.string.sunrise_timeline_current_time, currentTime.format(SolarTimeFormat))
  }

  Canvas(
    modifier = modifier
      .fillMaxWidth()
      .height(16.dp)
      .semantics { contentDescription = description },
  ) {
    // 两端预留指示点半径，避免午夜附近的圆点被裁切
    val inset = 6.dp.toPx()
    val trackWidth = (size.width - inset * 2).coerceAtLeast(0f)
    val trackHeight = 6.dp.toPx()
    val top = center.y - trackHeight / 2f
    val radius = CornerRadius(trackHeight / 2f)
    val sunriseX = inset + trackWidth * sunrise.toSecondOfDay() / 86_400f
    val sunsetX = inset + trackWidth * sunset.toSecondOfDay() / 86_400f

    drawRoundRect(
      color = nightColor,
      topLeft = Offset(inset, top),
      size = Size(trackWidth, trackHeight),
      cornerRadius = radius,
    )
    drawRoundRect(
      color = dayColor,
      topLeft = Offset(sunriseX, top),
      size = Size(sunsetX - sunriseX, trackHeight),
      cornerRadius = radius,
    )
    if (currentTime != null) {
      val marker = Offset(inset + trackWidth * currentTime.toSecondOfDay() / 86_400f, center.y)
      val isDaytime = !currentTime.isBefore(sunrise) && currentTime.isBefore(sunset)
      drawCircle(color = surfaceColor, radius = 6.dp.toPx(), center = marker)
      drawCircle(
        color = if (isDaytime) dayColor else nightMarkerColor,
        radius = 4.dp.toPx(),
        center = marker,
      )
    }
  }
}

private fun parseSolarTime(value: String): LocalTime? = try {
  LocalTime.parse(value.trim())
} catch (_: DateTimeParseException) {
  null
}

@Preview(name = "浅色主题", widthDp = 360)
@Preview(name = "深色主题", widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "大字体", widthDp = 360, fontScale = 1.5f)
@Composable
private fun SunriseCardPreview() {
  SpicaWeatherTheme {
    Column(
      modifier = Modifier
        .background(MaterialTheme.colorScheme.background)
        .padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SunrisePreviewTile(LocalTime.of(11, 30), Modifier.weight(1f))
        Surface(
          modifier = Modifier.weight(1f).aspectRatio(1f),
          color = MaterialTheme.colorScheme.surfaceContainer,
          shape = WIDGET_CARD_CORNER_SHAPE,
        ) {
          PrecipitationCard(precipitation = 3, pop = 80)
        }
      }
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SunrisePreviewTile(LocalTime.of(5, 0), Modifier.weight(1f))
        SunrisePreviewTile(LocalTime.of(21, 0), Modifier.weight(1f))
      }
      Surface(
        modifier = Modifier.fillMaxWidth().height(150.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = WIDGET_CARD_CORNER_SHAPE,
      ) {
        SunriseCardContent(null, null, null, startAnim = true)
      }
    }
  }
}

@Composable
private fun SunrisePreviewTile(currentTime: LocalTime, modifier: Modifier = Modifier) {
  Surface(
    modifier = modifier.aspectRatio(1f),
    color = MaterialTheme.colorScheme.surfaceContainer,
    shape = WIDGET_CARD_CORNER_SHAPE,
  ) {
    SunriseCardContent(
      sunrise = LocalTime.of(6, 18),
      sunset = LocalTime.of(17, 42),
      currentTime = currentTime,
      startAnim = true,
    )
  }
}
