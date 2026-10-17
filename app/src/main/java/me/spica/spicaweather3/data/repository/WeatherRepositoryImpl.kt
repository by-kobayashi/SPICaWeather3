package me.spica.spicaweather3.data.repository

import com.skydoves.sandwich.getOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import me.spica.spicaweather3.App
import me.spica.spicaweather3.R
import me.spica.spicaweather3.data.local.db.dao.CityDao
import me.spica.spicaweather3.data.mapper.toDomain
import me.spica.spicaweather3.data.mapper.toEntity
import me.spica.spicaweather3.data.remote.api.ApiService
import me.spica.spicaweather3.data.remote.api.model.BatchWeatherRequest
import me.spica.spicaweather3.domain.model.City
import me.spica.spicaweather3.domain.repository.IWeatherRepository
import me.spica.spicaweather3.ui.app_widget.WidgetUpdateHelper
import me.spica.spicaweather3.utils.StringProvider

/**
 * 天气数据仓库实现
 * 
 * 整合了本地数据库和远程 API 的访问，实现 IWeatherRepository 接口。
 * 负责处理所有与天气数据和城市管理相关的数据操作。
 * 
 * @param apiService 远程 API 服务
 * @param cityDao 本地城市数据库访问对象
 * @param stringProvider 字符串资源提供者
 */
class WeatherRepositoryImpl(
    private val apiService: ApiService,
    private val cityDao: CityDao,
    private val stringProvider: StringProvider
) : IWeatherRepository {

    override fun getAllCitiesFlow(): Flow<List<City>> {
        return cityDao.getAllFlow().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getAllCities(): List<City> {
        return cityDao.getAll().map { it.toDomain() }
    }

    override suspend fun refreshWeather(
        lat: String,
        lon: String,
        onError: (String?) -> Unit,
        onSucceed: () -> Unit
    ) = withContext(Dispatchers.IO) {
        // 查找所有城市，找到匹配的城市
        val allCities = cityDao.getAll()
        val cityEntity = allCities.find { it.lat == lat && it.lon == lon }
        
        if (cityEntity == null) {
            onError(stringProvider.getString(R.string.error_request_failed))
            return@withContext
        }

        val response = apiService.getWeather(
            BatchWeatherRequest(listOf(cityEntity.toWeatherRequestLocation()))
        ).getOrNull()

        if (response == null || response.code != 200 || response.data == null) {
            onError(stringProvider.getString(R.string.error_request_failed))
            return@withContext
        }

        val weatherData = response.data.results.orEmpty().filterNotNull().find { it.locationId == cityEntity.id }
        val freshWeather = weatherData?.data
        if (weatherData?.success == true && freshWeather?.current != null) {
            if (cityEntity.weather != freshWeather) {
                val updated = cityDao.updateWeather(cityEntity.id, lat, lon, freshWeather)
                if (updated > 0 && cityEntity.isUserLoc) WidgetUpdateHelper.requestUpdate(App.instance)
            }
            onSucceed()
        } else {
            onError(stringProvider.getString(R.string.error_request_failed))
        }
    }

    override suspend fun refreshAllCitiesWeather(
        cities: List<City>,
        onError: (String?) -> Unit,
        onSucceed: () -> Unit
    ) = withContext(Dispatchers.IO) {
        val requestedIds = cities.map { it.id }.toSet()
        val cityEntities = cityDao.getAll().filter { it.id in requestedIds }
        if (cityEntities.isEmpty()) {
            onSucceed()
            return@withContext
        }
        
        val response = apiService.getWeather(
            BatchWeatherRequest(cityEntities.map { it.toWeatherRequestLocation() })
        ).getOrNull()

        if (response == null) {
            onError(stringProvider.getString(R.string.error_request_failed))
            return@withContext
        }

        if (response.code != 200 || response.data == null) {
            onError(stringProvider.getString(R.string.error_request_failed))
            return@withContext
        }

        val results = response.data.results.orEmpty().filterNotNull().associateBy { it.locationId }
        var failedCount = 0
        val updatedEntities = cityEntities.mapNotNull { entity ->
            val result = results[entity.id]
            val freshWeather = result?.data
            if (result?.success != true || freshWeather?.current == null) {
                failedCount++
                null
            } else if (entity.weather != freshWeather) {
                entity.copy(weather = freshWeather)
            } else null
        }

        if (updatedEntities.isNotEmpty()) cityDao.updateWeather(updatedEntities)
        if (updatedEntities.any { it.isUserLoc }) WidgetUpdateHelper.requestUpdate(App.instance)
        if (failedCount > 0) {
            onError(stringProvider.getString(R.string.error_partial_weather_refresh, failedCount))
        } else {
            onSucceed()
        }
    }

    override suspend fun insertCity(city: City) = withContext(Dispatchers.IO) {
        cityDao.insert(city.toEntity())
    }

    override suspend fun deleteCity(city: City) = withContext(Dispatchers.IO) {
        cityDao.delete(city.toEntity())
        if (city.isUserLocation) WidgetUpdateHelper.requestUpdate(App.instance)
    }

    override suspend fun swapSort(city1: City, city2: City) =
        withContext(Dispatchers.IO) {
            cityDao.swapSort(city1.toEntity(), city2.toEntity())
        }

    override suspend fun getUserLoc(): City? = withContext(Dispatchers.IO) {
        return@withContext cityDao.getUserLoc()?.toDomain()
    }

    override suspend fun insertUserLoc(city: City) = withContext(Dispatchers.IO) {
        if (cityDao.updateUserLoc(city.toEntity())) WidgetUpdateHelper.requestUpdate(App.instance)
    }
}
