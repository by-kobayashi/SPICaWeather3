package me.spica.spicaweather3.ui.app_widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

internal enum class WeatherWidgetType(val receiver: Class<out GlanceAppWidgetReceiver>) {
    TODAY(TodayInfoAppWidgetReceiver::class.java),
    WEEKLY(WeeklyWeatherAppWidgetReceiver::class.java),
    SCATTERED(ScatteredWeatherAppWidgetReceiver::class.java);

    fun createWidget(): GlanceAppWidget = when (this) {
        TODAY -> TodayInfoAppWidget()
        WEEKLY -> WeeklyWeatherAppWidget()
        SCATTERED -> ScatteredWeatherAppWidget()
    }
}

object WidgetUpdateHelper {
    fun requestUpdate(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        if (WeatherWidgetType.entries.none {
                manager.getAppWidgetIds(ComponentName(context, it.receiver)).isNotEmpty()
            }) return
        val request = OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
            .setInitialDelay(300, TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "weather-widget-update", ExistingWorkPolicy.KEEP, request
        )
    }

    internal suspend fun updateAllWidgets(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val glanceManager = GlanceAppWidgetManager(context)
        var failure: Exception? = null
        for (type in WeatherWidgetType.entries) {
            val component = ComponentName(context, type.receiver)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isEmpty()) continue
            val widget = type.createWidget()
            for (id in ids) {
                try {
                    if (manager.getAppWidgetInfo(id)?.provider != component) continue
                    widget.update(context, glanceManager.getGlanceIdBy(id))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    failure = error
                    Log.e("WidgetUpdate", "组件更新失败：${type.name}/$id", error)
                }
            }
        }
        failure?.let { throw it }
    }

    internal fun restoreWidgets(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        for (type in WeatherWidgetType.entries) {
            val component = ComponentName(context, type.receiver)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isEmpty()) continue
            // 通过 Receiver 更新旧版 Glance 类型映射。
            context.sendBroadcast(
                Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .setComponent(component)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            )
        }
    }
}

class WidgetUpdateWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        WidgetUpdateHelper.updateAllWidgets(applicationContext)
        Result.success()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Log.e("WidgetUpdate", "组件刷新任务失败", error)
        if (runAttemptCount < 2) Result.retry() else Result.failure()
    }
}
