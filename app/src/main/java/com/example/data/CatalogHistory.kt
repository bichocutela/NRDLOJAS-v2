package com.example.data

data class CatalogSnapshot(
    val id: String,
    val createdAt: Long,
    val productCount: Int,
    val createdBy: String,
    val reason: String = "manual",
    val restoredAt: Long? = null
)

data class CatalogRestoreResult(
    val success: Boolean,
    val restoredProductCount: Int = 0,
    val message: String,
    val restoredProducts: List<Product>? = null
)

const val MAX_CATALOG_HISTORY_PRODUCTS = 5000
const val MAX_CATALOG_HISTORY_ITEMS = 20
