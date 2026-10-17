package me.spica.spicaweather3.ui.app_widget

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import me.spica.spicaweather3.presentation.theme.COLOR_WHITE_100
import me.spica.spicaweather3.presentation.theme.COLOR_WHITE_40


/**
 * 今日信息小组件
 * 显示当前天气、温度、城市信息等
 */
class TodayInfoAppWidget : GlanceAppWidget() {

  override val sizeMode = SizeMode.Exact
  override suspend fun provideGlance(
    context: Context,
    id: GlanceId
  ) {
    provideWeatherContent { cityEntity ->
      val weatherData = cityEntity?.weather
      val current = weatherData?.current
      val compact = widgetHeight() < 150f
      val minimal = widgetHeight() < 110f
      Box(
        modifier = GlanceModifier
          .fillMaxSize()
          .cornerRadius(16.dp)
          .padding(12.dp),
        contentAlignment = Alignment.Center
      ) {
        if (cityEntity != null && weatherData != null && current != null) {
          Row(
            modifier = GlanceModifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.Start
          ) {
            Column(
              modifier = GlanceModifier,
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Text(
                text = cityEntity.name,
                modifier = GlanceModifier,
                TextStyle(
                  fontSize = if (minimal) 14.sp else if (compact) 20.sp else 24.sp,
                  color = ColorProvider(COLOR_WHITE_100)
                ),
                maxLines = 1
              )
              Spacer(
                modifier = GlanceModifier
                  .height(4.dp)
              )
              Row {
                Text(
                  text = "${current.temperature}",
                  modifier = GlanceModifier,
                  style = TextStyle(
                    fontSize = if (minimal) 22.sp else if (compact) 36.sp else 54.sp,
                    color = ColorProvider(COLOR_WHITE_100)
                  )
                )
                Text(
                  text = "°C",
                  modifier = GlanceModifier,
                  style = TextStyle(
                    fontSize = if (minimal) 12.sp else if (compact) 16.sp else 24.sp,
                    color = ColorProvider(COLOR_WHITE_100)
                  )
                )
              }
              if (!minimal) {
                Spacer(
                  modifier = GlanceModifier.height(4.dp)
                )
                Text(
                  text = "${current.condition.orEmpty()} 风速${current.windSpeed}公里/小时",
                  modifier = GlanceModifier,
                  style = TextStyle(
                    fontSize = if (compact) 12.sp else 14.sp,
                    color = ColorProvider(COLOR_WHITE_40)
                  ),
                  maxLines = 1
                )
              }
            }
          }
        } else {
          Text(
            text = "没有数据 0_0",
            modifier = GlanceModifier,
            style = TextStyle(
              fontSize = if (compact) 16.sp else 32.sp,
              color = ColorProvider(COLOR_WHITE_100)
            )
          )
        }
      }
    }
  }

}



