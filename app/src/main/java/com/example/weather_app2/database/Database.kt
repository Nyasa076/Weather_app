package com.example.weather_app2.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [data::class], version = 2)
abstract class Database : RoomDatabase() {
    abstract fun weatherDao(): weatherDAO
}
