package com.example.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

private const val CATEGORY_MEMBERSHIP_SEPARATOR = "\u001F"

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
    val imageUrl: String? = null,
    @ColumnInfo(defaultValue = "''") val categoryMemberships: String = ""
)

fun normalizeProductCategories(categories: Collection<String>): List<String> = categories
    .map { it.trim().replace(Regex("\\s+"), " ") }
    .filter { it.isNotBlank() }
    .distinctBy { it.lowercase() }

fun encodeProductCategories(categories: Collection<String>): String =
    normalizeProductCategories(categories).joinToString(CATEGORY_MEMBERSHIP_SEPARATOR)

fun Product.categoryNames(): List<String> {
    val memberships = categoryMemberships
        .split(CATEGORY_MEMBERSHIP_SEPARATOR)
        .map { it.trim() }
        .filter { it.isNotBlank() }
    return normalizeProductCategories(listOf(category) + memberships)
}

fun Product.withCategoryNames(categories: Collection<String>): Product {
    val normalized = normalizeProductCategories(categories)
    require(normalized.isNotEmpty()) { "Produto precisa pertencer a pelo menos uma categoria." }
    return copy(category = normalized.first(), categoryMemberships = encodeProductCategories(normalized))
}
