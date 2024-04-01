package com.example.weather_app2.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "weather_data")
data class data(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: String,
    val maxTemperature: Double,
    val minTemperature: Double
)