package me.spica.spicaweather3.ui.widget

import androidx.compose.animation.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import kotlinx.coroutines.launch
import androidx.compose.ui.geometry.Rect
import me.spica.spicaweather3.common.type.WeatherAnimType
import me.spica.spicaweather3.domain.model.WeatherData
import me.spica.spicaweather3.ui.widget.cloud.CloudView
import me.spica.spicaweather3.ui.widget.galaxy.GalaxyView
import me.spica.spicaweather3.ui.widget.haze.HazeView
import me.spica.spicaweather3.ui.widget.rain.RainView
import me.spica.spicaweather3.ui.widget.rain.RainTextCollision
import me.spica.spicaweather3.ui.widget.snow.SnowView
import me.spica.spicaweather3.ui.widget.sun.SunView


@Composable
fun WeatherBackground(
  weatherData: WeatherData,
  collapsedFraction: Float,
  currentWeatherType: WeatherAnimType,
  collisionRect: Rect? = null,
  collisionCornerRadiusPx: Float = 0f,
  textCollisions: List<RainTextCollision> = emptyList(),
) {

  val currentTopColor = remember { Animatable(currentWeatherType.topColor) }
  val currentBottomColor = remember { Animatable(currentWeatherType.bottomColor) }

  LaunchedEffect(currentWeatherType) {
    launch {
      currentBottomColor.animateTo(currentWeatherType.bottomColor, tween(1000))
    }
    launch {
      currentTopColor.animateTo(currentWeatherType.topColor, tween(1000))
    }
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .drawBehind {
        drawRect(
          brush = Brush.verticalGradient(
            colors = listOf(
              currentTopColor.value,
              currentTopColor.value,
              currentTopColor.value,
              currentBottomColor.value
            )
          )
        )
      }
  ) {
    GalaxyView(collapsedFraction, currentWeatherType.showGalaxy)
    SnowView(show = currentWeatherType.showSnow)
    RainView(
      show = currentWeatherType.showRain,
      collisionRect = collisionRect,
      collisionCornerRadiusPx = collisionCornerRadiusPx,
      textCollisions = textCollisions
    )
    CloudView(collapsedFraction, currentWeatherType.showCloud)
    SunView(collapsedFraction, currentWeatherType.showSun)
    HazeView(show = currentWeatherType.showHaze)
  }

}


