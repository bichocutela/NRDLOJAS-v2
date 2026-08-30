package com.example.data

import kotlin.math.ln
import kotlin.math.pow

data class GlobalProductUsage(
    val product: Product,
    val lastViewedAt: Long?
)

private const val GLOBAL_USAGE_HALF_LIFE_MILLIS = 7L * 24L * 60L * 60L * 1000L

internal fun rankGloballyMostUsedProducts(
    usage: List<GlobalProductUsage>,
    nowMillis: Long = System.currentTimeMillis()
): List<Product> = usage
    .asSequence()
    .filter { it.product.searchCount > 0 }
    .sortedWith(
        compareByDescending<GlobalProductUsage> { globalUsageScore(it, nowMillis) }
            .thenByDescending { it.product.searchCount }
            .thenByDescending { it.lastViewedAt ?: 0L }
            .thenBy { it.product.name }
            .thenBy { it.product.code }
    )
    .map { it.product }
    .toList()

private fun globalUsageScore(usage: GlobalProductUsage, nowMillis: Long): Double {
    val totalWeight = ln(usage.product.searchCount.toDouble() + 1.0)
    val lastViewedAt = usage.lastViewedAt ?: return 0.0
    val ageMillis = (nowMillis - lastViewedAt).coerceAtLeast(0L)
    val ageInHalfLives = ageMillis.toDouble() / GLOBAL_USAGE_HALF_LIFE_MILLIS.toDouble()
    return totalWeight * 0.5.pow(ageInHalfLives)
}