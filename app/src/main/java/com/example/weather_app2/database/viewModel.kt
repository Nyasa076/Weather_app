package com.example.weather_app2.database

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class viewModel(private val repository: repo) : ViewModel() {

    fun insertWeatherData(maxTemp: Double, minTemp: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertWeatherData(maxTemp, minTemp)
        }
    }

    suspend fun getWeatherDataByDate(date: String): data? {
        return repository.getWeatherDataByDate(date)
    }
}
