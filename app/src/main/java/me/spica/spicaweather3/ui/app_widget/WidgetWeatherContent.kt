package me.spica.spicaweather3.ui.app_widget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.glance.GlanceComposable
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import me.spica.spicaweather3.data.local.db.dao.CityDao
import me.spica.spicaweather3.data.local.db.entity.CityEntity
import org.koin.core.context.GlobalContext

internal suspend fun GlanceAppWidget.provideWeatherContent(
    content: @Composable @GlanceComposable (CityEntity?) -> Unit
) {
    val cityDao = GlobalContext.get().get<CityDao>()
    val initialCity = withContext(Dispatchers.IO) { cityDao.getUserLoc() }
    val cities = cityDao.getUserLocFlow().distinctUntilChanged()
    provideContent {
        val city by cities.collectAsState(initialCity)
        content(city)
    }
}

@Composable
internal fun widgetHeight(): Float = LocalSize.current.height.value /
    LocalContext.current.resources.configuration.fontScale.coerceAtLeast(1f)
