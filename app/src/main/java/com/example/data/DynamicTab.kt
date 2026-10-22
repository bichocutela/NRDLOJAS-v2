package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dynamic_tabs")
data class DynamicTab(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val type: String, // "text" ou "image"
    val content: String, // Texto ou URL da imagem
    val displayOrder: Int = 0
)
