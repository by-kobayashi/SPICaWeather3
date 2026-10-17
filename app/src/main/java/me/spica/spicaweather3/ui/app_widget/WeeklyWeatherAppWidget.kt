package me.spica.spicaweather3.ui.app_widget

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import me.spica.spicaweather3.presentation.theme.COLOR_WHITE_100
import me.spica.spicaweather3.presentation.theme.COLOR_WHITE_40
import me.spica.spicaweather3.presentation.theme.COLOR_WHITE_60

/**
 * 一周天气小组件
 * 显示未来7天的天气预报
 */
class WeeklyWeatherAppWidget : GlanceAppWidget() {

  override val sizeMode = SizeMode.Exact
  override suspend fun provideGlance(
    context: Context,
    id: GlanceId
  ) {
    provideWeatherContent { cityEntity ->
      val weatherData = cityEntity?.weather
      val current = weatherData?.current
      val weeklyWeather = weatherData?.forecast?.next7Days.orEmpty()
        .filterNotNull().filter { !it.date.isNullOrBlank() }.distinctBy { it.date }.take(7)
      val compact = widgetHeight() < 270f
      Box(
        modifier = GlanceModifier
          .fillMaxSize()
          .cornerRadius(16.dp)
          .padding(16.dp),
        contentAlignment = Alignment.TopStart
      ) {
        if (cityEntity != null && current != null) {
          Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.Top,
            horizontalAlignment = Alignment.Start
          ) {
            // 标题：城市名称 + 当前温度
            Row(
              modifier = GlanceModifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalAlignment = Alignment.Start
            ) {
              Column {
                Text(
                  text = cityEntity.name,
                  style = TextStyle(
                    fontSize = if (compact) 16.sp else 18.sp,
                    color = ColorProvider(COLOR_WHITE_100)
                  ),
                  maxLines = 1
                )
                Spacer(modifier = GlanceModifier.height(2.dp))
                Row {
                  Text(
                    text = "${current.temperature}",
                    style = TextStyle(
                      fontSize = if (compact) 24.sp else 32.sp,
                      color = ColorProvider(COLOR_WHITE_100)
                    )
                  )
                  Text(
                    text = "°C",
                    style = TextStyle(
                      fontSize = 16.sp,
                      color = ColorProvider(COLOR_WHITE_100)
                    )
                  )
                }
              }
            }

            Spacer(modifier = GlanceModifier.height(if (compact) 8.dp else 12.dp))

            // 一周天气列表
            if (weeklyWeather.isNotEmpty()) {
              LazyColumn(
                modifier = GlanceModifier.fillMaxWidth().defaultWeight()
              ) {
                items(weeklyWeather, itemId = { it.date.orEmpty().hashCode().toLong() }) { day ->
                  Row(
                    modifier = GlanceModifier
                      .fillMaxWidth()
                      .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalAlignment = Alignment.Start
                  ) {
                    // 日期标签（周几）- 固定宽度
                    val dayLabel = try {
                      if (day.isToday()) "今天" else day.getDayOfWeekLabel()
                    } catch (e: Exception) {
                      e.printStackTrace()
                      day.date.orEmpty()
                    }
                    
                    Box(
                      modifier = GlanceModifier.width(44.dp),
                      contentAlignment = Alignment.CenterStart
                    ) {
                      Text(
                        text = dayLabel,
                        style = TextStyle(
                          fontSize = 12.sp,
                          color = ColorProvider(COLOR_WHITE_60)
                        )
                      )
                    }

                    Box(
                      modifier = GlanceModifier.defaultWeight(),
                      contentAlignment = Alignment.CenterStart
                    ) {
                      Text(
                        text = day.dayCondition.orEmpty(),
                        style = TextStyle(
                          fontSize = 12.sp,
                          color = ColorProvider(COLOR_WHITE_40)
                        ),
                        maxLines = 1
                      )
                    }

                    // 温度范围 - 右对齐
                    Box(
                      modifier = GlanceModifier,
                      contentAlignment = Alignment.CenterEnd
                    ) {
                      Text(
                        text = "${day.tempMax}° / ${day.tempMin}°",
                        style = TextStyle(
                          fontSize = 12.sp,
                          color = ColorProvider(COLOR_WHITE_100)
                        )
                      )
                    }
                  }
                }
              }
            } else {
              Text(
                text = "暂无一周天气数据",
                style = TextStyle(
                  fontSize = 14.sp,
                  color = ColorProvider(COLOR_WHITE_40)
                )
              )
            }
          }
        } else {
          Text(
            text = "没有数据 0_0",
            modifier = GlanceModifier,
            style = TextStyle(
              fontSize = 20.sp,
              color = ColorProvider(COLOR_WHITE_100)
            )
          )
        }
      }
    }
  }
}
