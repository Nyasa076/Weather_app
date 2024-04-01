package com.example.weather_app2.database

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class repo(private val weatherDao: weatherDAO) {

    suspend fun insertWeatherData(maxTemp: Double, minTemp: Double) {
        val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val weatherData = data(date = currentDate, maxTemperature = maxTemp, minTemperature = minTemp)
        weatherDao.insertWeatherData(weatherData)
    }

    suspend fun getWeatherDataByDate(date: String): data? {
        return weatherDao.getWeatherDataByDate(date)
    }
}
