package com.example.weather_app2.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface weatherDAO {

    @Insert
    suspend fun insertWeatherData(weatherData: data)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(data: data)

    @Query("SELECT * FROM weather_data WHERE date = :date")
    suspend fun getWeatherDataByDate(date: String): data?
}
