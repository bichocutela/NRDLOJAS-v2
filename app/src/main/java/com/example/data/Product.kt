package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "products",
    indices = [
        Index(value = ["searchName"]),
        Index(value = ["code"], unique = true),
        Index(value = ["category"])
    ]
)
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val code: String,
    val name: String,
    val searchName: String,
    val category: String,
    val isFavorite: Boolean = false,
    val searchCount: Int = 0,
    val lastSearchedAt: Long = 0,
    val unit: String = "un",
    val imageUrl: String? = null
)
