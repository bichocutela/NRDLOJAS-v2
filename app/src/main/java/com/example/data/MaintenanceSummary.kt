package com.example.data

data class MaintenanceSummary(
    val localProductCount: Int = 0,
    val remoteProductCount: Int = 0,
    val localCategoryCounts: List<CategoryCount> = emptyList(),
    val remoteCategoryCounts: List<CategoryCount> = emptyList(),
    val dynamicTabCount: Int = 0,
    val pendingSuggestionCount: Int = 0,
    val lastRemoteProductUpdate: Long? = null,
    val checkedAt: Long = System.currentTimeMillis(),
    val remoteAvailable: Boolean = false
)
