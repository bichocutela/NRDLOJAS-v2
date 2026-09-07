package com.example.data

import androidx.room.Dao
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

    @Query("DELETE FROM dynamic_tabs WHERE id = :id")
    suspend fun deleteTabById(id: Int)

    @Query("DELETE FROM dynamic_tabs WHERE id = (SELECT MAX(id) FROM dynamic_tabs WHERE title = :title AND content = :content AND displayOrder = :displayOrder)")
    suspend fun deleteNewestGeneratedTab(title: String, content: String, displayOrder: Int)

    suspend fun deleteTab(tab: DynamicTab) {
        if (tab.id != 0) {
            deleteTabById(tab.id)
        } else {
            // Room gera o id depois do insert. Se a publicação remota falhar, o objeto
            // original ainda possui id=0; remova apenas a linha recém-gerada correspondente.
            deleteNewestGeneratedTab(tab.title, tab.content, tab.displayOrder)
        }
    }
}
