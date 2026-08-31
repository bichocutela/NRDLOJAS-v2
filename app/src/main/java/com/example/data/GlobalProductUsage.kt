package com.example.data

data class GlobalProductUsage(
    val product: Product,
    val lastViewedAt: Long?,
    val createdAt: Long? = null
)

internal fun rankGloballyMostUsedProducts(
    usage: List<GlobalProductUsage>
): List<Product> = usage
    .asSequence()
    .filter { it.product.searchCount > 0 }
    .sortedWith(
        compareByDescending<GlobalProductUsage> { it.product.searchCount }
            .thenByDescending { it.lastViewedAt ?: 0L }
            .thenBy { it.product.name }
            .thenBy { it.product.code }
    )
    .map { it.product }
    .toList()

internal fun rankLatestAddedProducts(
    usage: List<GlobalProductUsage>,
    limit: Int = 5
): List<Product> = usage
    .asSequence()
    .filter { it.createdAt != null }
    .sortedWith(
        compareByDescending<GlobalProductUsage> { it.createdAt ?: 0L }
            .thenBy { it.product.name }
            .thenBy { it.product.code }
    )
    .map { it.product }
    .take(limit.coerceAtLeast(0))
    .toList()
