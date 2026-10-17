package me.spica.spicaweather3.ui.app_widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import me.spica.spicaweather3.presentation.theme.COLOR_WHITE_10
import me.spica.spicaweather3.presentation.theme.COLOR_WHITE_100
import me.spica.spicaweather3.presentation.theme.COLOR_WHITE_40
import me.spica.spicaweather3.presentation.theme.COLOR_WHITE_50
import me.spica.spicaweather3.presentation.theme.COLOR_WHITE_60
import me.spica.spicaweather3.presentation.theme.COLOR_WHITE_70
import me.spica.spicaweather3.presentation.theme.COLOR_WHITE_80
import me.spica.spicaweather3.presentation.theme.COLOR_WHITE_90

/** 散排天气小组件 */
class ScatteredWeatherAppWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideWeatherContent { city ->
            val current = city?.weather?.current
            val today = city?.weather?.forecast?.today
            val height = widgetHeight()
            val compact = height < 200f
            val minimal = height < 100f
            Box(
                modifier = GlanceModifier.fillMaxSize().cornerRadius(20.dp)
                    .background(Color(0xCC1B2838))
            ) {
                if (city != null && current != null) {
                    Box(
                        modifier = GlanceModifier.fillMaxSize(),
                        contentAlignment = Alignment.BottomEnd
                    ) {
                        Text(
                            text = "${current.temperature}",
                            modifier = GlanceModifier.padding(end = 4.dp, bottom = 2.dp),
                            style = TextStyle(
                                fontSize = if (compact) 64.sp else 112.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorProvider(COLOR_WHITE_10)
                            )
                        )
                    }
                    Column(
                        modifier = GlanceModifier.fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = if (compact) 8.dp else 14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Row(modifier = GlanceModifier.fillMaxWidth()) {
                            Text(
                                text = city.name,
                                modifier = GlanceModifier.defaultWeight(),
                                style = TextStyle(fontSize = 14.sp, color = ColorProvider(COLOR_WHITE_70)),
                                maxLines = 1
                            )
                            Text(
                                text = today?.let { "${it.tempMax}°~${it.tempMin}°" } ?: "—",
                                style = TextStyle(fontSize = 12.sp, color = ColorProvider(COLOR_WHITE_50)),
                                maxLines = 1
                            )
                        }
                        Spacer(GlanceModifier.height(6.dp))
                        Row(
                            modifier = GlanceModifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = "${current.temperature}",
                                    style = TextStyle(
                                        fontSize = if (minimal) 24.sp else if (compact) 36.sp else 64.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ColorProvider(COLOR_WHITE_100)
                                    )
                                )
                                Text(
                                    text = "°",
                                    modifier = GlanceModifier.padding(bottom = 10.dp),
                                    style = TextStyle(
                                        fontSize = if (compact) 16.sp else 22.sp,
                                        color = ColorProvider(COLOR_WHITE_80)
                                    )
                                )
                            }
                            Spacer(GlanceModifier.defaultWeight())
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = current.condition.orEmpty(),
                                    style = TextStyle(
                                        fontSize = if (compact) 16.sp else 22.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = ColorProvider(COLOR_WHITE_90),
                                        textAlign = TextAlign.End
                                    ),
                                    maxLines = 1
                                )
                                if (!minimal) {
                                    Spacer(GlanceModifier.height(4.dp))
                                    Text(
                                        text = "湿度 ${current.humidity}%",
                                        style = TextStyle(fontSize = 11.sp, color = ColorProvider(COLOR_WHITE_50))
                                    )
                                }
                                if (!compact) {
                                    Text(
                                        text = "${current.windDirectionText.orEmpty()} ${current.windScale.orEmpty()}级",
                                        style = TextStyle(fontSize = 11.sp, color = ColorProvider(COLOR_WHITE_50))
                                    )
                                    Text(
                                        text = "空气 ${city.weather?.airQuality?.category.orEmpty().ifBlank { "暂无" }}",
                                        style = TextStyle(fontSize = 11.sp, color = ColorProvider(COLOR_WHITE_50))
                                    )
                                }
                            }
                        }
                        if (height >= 115f) {
                            Spacer(GlanceModifier.height(6.dp))
                            Row(
                                modifier = GlanceModifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Bottom
                            ) {
                                if (!compact) {
                                    Column {
                                        Text(
                                            text = "日出 ${today?.sunrise.orEmpty().ifBlank { "—" }}",
                                            style = TextStyle(fontSize = 10.sp, color = ColorProvider(COLOR_WHITE_60))
                                        )
                                        Text(
                                            text = "日落 ${today?.sunset.orEmpty().ifBlank { "—" }}",
                                            style = TextStyle(fontSize = 10.sp, color = ColorProvider(COLOR_WHITE_60))
                                        )
                                    }
                                }
                                Spacer(GlanceModifier.defaultWeight())
                                Text(
                                    text = "体感 ${current.feelsLike}°",
                                    style = TextStyle(fontSize = 10.sp, color = ColorProvider(COLOR_WHITE_40))
                                )
                            }
                        }
                    }
                } else {
                    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "没有数据 0_0",
                            style = TextStyle(fontSize = 20.sp, color = ColorProvider(COLOR_WHITE_60))
                        )
                    }
                }
            }
        }
    }
}
