package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dynamic_tabs")
data class DynamicTab(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val type: String, // "text", "image", "video"
    val content: String, // Text, Image URL, or Video URL
    val displayOrder: Int = 0
)
