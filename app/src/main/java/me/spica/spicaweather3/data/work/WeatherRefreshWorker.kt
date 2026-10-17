package me.spica.spicaweather3.data.work

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import me.spica.spicaweather3.domain.model.City
import me.spica.spicaweather3.domain.repository.IWeatherRepository
import org.koin.core.context.GlobalContext
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

class WeatherRefreshWorker(
  appContext: Context,
  workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

  override suspend fun doWork(): Result {
    return try {
      val repository = GlobalContext.get().get<IWeatherRepository>()
      val cities = repository.getAllCities().filter { it.requiresRefresh() }

      if (cities.isEmpty()) {
        return Result.success()
      }

      var hasError = false
      repository.refreshAllCitiesWeather(
        cities = cities,
        onError = {
          hasError = true
          Log.w(TAG, "后台天气更新失败：$it")
        },
        onSucceed = {
          hasError = false
        }
      )

      if (hasError) retryOrFail() else Result.success()
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (error: Exception) {
      Log.e(TAG, "后台天气更新失败", error)
      retryOrFail()
    }
  }

  private fun retryOrFail(): Result =
    if (runAttemptCount < 2) Result.retry() else Result.failure()

  private fun City.requiresRefresh(): Boolean {
    val weatherData = weather ?: return true
    val generatedAt = runCatching { LocalDateTime.parse(weatherData.generatedAt) }.getOrNull() ?: return true
    val threshold = LocalDateTime.now().minus(REFRESH_THRESHOLD)
    return generatedAt < threshold
  }

  companion object {
    private const val TAG = "WeatherRefreshWorker"
    private val REFRESH_THRESHOLD: Duration = Duration.ofHours(2)
  }
}

object WeatherRefreshScheduler {
  private const val UNIQUE_WORK_NAME = "weather-auto-refresh"
  private val WORK_INTERVAL: Duration = Duration.ofHours(2)

  fun enqueue(context: Context) {
    val request = PeriodicWorkRequestBuilder<WeatherRefreshWorker>(
      WORK_INTERVAL.toHours(),
      TimeUnit.HOURS
    )
      .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
      .setConstraints(
        Constraints.Builder()
          .setRequiredNetworkType(NetworkType.CONNECTED)
          .build()
      )
      .build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
      UNIQUE_WORK_NAME,
      ExistingPeriodicWorkPolicy.UPDATE,
      request
    )
  }
}
