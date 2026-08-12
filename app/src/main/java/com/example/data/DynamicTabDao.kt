package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DynamicTabDao {
    @Query("SELECT * FROM dynamic_tabs ORDER BY displayOrder ASC")
    fun getAllTabs(): Flow<List<DynamicTab>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTab(tab: DynamicTab)

    @Update
    suspend fun updateTab(tab: DynamicTab)

    @Delete
    suspend fun deleteTab(tab: DynamicTab)
}
