package me.spica.spicaweather3.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy.Companion.REPLACE
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.TypeConverters
import kotlinx.coroutines.flow.Flow
import me.spica.spicaweather3.data.local.db.entity.CityEntity
import me.spica.spicaweather3.data.local.db.type_converter.RoomTypeConverters
import me.spica.spicaweather3.data.remote.api.model.weather.AggregatedWeatherData

@Dao
interface CityDao {

  @Query("SELECT * FROM cityentity ORDER by sort ASC")
  fun getAll(): List<CityEntity>

  @Query("SELECT * FROM cityentity WHERE isUserLoc = 1 LIMIT 1")
  fun getUserLoc(): CityEntity?

  @Query("SELECT * FROM cityentity WHERE isUserLoc = 1 LIMIT 1")
  fun getUserLocFlow(): Flow<CityEntity?>

  @TypeConverters(RoomTypeConverters::class)
  @Query("UPDATE cityentity SET weather = :weather WHERE id = :id AND lat = :lat AND lon = :lon")
  fun updateWeather(id: String, lat: String, lon: String, weather: AggregatedWeatherData): Int

  @Transaction
  fun updateWeather(cities: List<CityEntity>) {
    cities.forEach { city ->
      city.weather?.let { updateWeather(city.id, city.lat, city.lon, it) }
    }
  }

  @Query("SELECT * FROM cityentity ORDER by sort ASC")
  fun getAllFlow(): Flow<List<CityEntity>>

  @Insert(onConflict = REPLACE)
  fun insert(cityEntity: CityEntity)

  @Insert(onConflict = REPLACE)
  fun insertAll(cityEntities: List<CityEntity>)

  @Transaction
  fun swapSort(city1: CityEntity, city2: CityEntity) {
    val tempSort = city1.sort
    city1.sort = city2.sort
    city2.sort = tempSort
    insertAll(listOf(city1, city2))
  }

  @Transaction
  fun updateUserLoc(cityEntity: CityEntity): Boolean {
    val userLoc = getUserLoc()
    if (userLoc == null) {
      cityEntity.isUserLoc = true
      insert(cityEntity)
      return true
    } else {
      // 只有在数据真正改变时才更新，避免触发不必要的Flow更新
      var hasChanged = false
      if (userLoc.lat != cityEntity.lat) {
        userLoc.lat = cityEntity.lat
        hasChanged = true
      }
      if (userLoc.lon != cityEntity.lon) {
        userLoc.lon = cityEntity.lon
        hasChanged = true
      }
      if (userLoc.name != cityEntity.name) {
        userLoc.name = cityEntity.name
        hasChanged = true
      }
      if (userLoc.adm1 != cityEntity.adm1) {
        userLoc.adm1 = cityEntity.adm1
        hasChanged = true
      }
      if (userLoc.adm2 != cityEntity.adm2) {
        userLoc.adm2 = cityEntity.adm2
        hasChanged = true
      }
      if (hasChanged) {
        insert(userLoc)
      }
      return hasChanged
    }
  }

  @Delete
  fun delete(cityEntity: CityEntity)

  @Query("DELETE FROM cityentity")
  fun deleteAll()

}
