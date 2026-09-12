package com.example.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [Product::class, DynamicTab::class], version = 6, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun dynamicTabDao(): DynamicTabDao
}
