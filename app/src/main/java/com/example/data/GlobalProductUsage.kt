package com.example.data

data class GlobalProductUsage(
    val product: Product,
    val lastViewedAt: Long?,
    val createdAt: Long? = null
)

/**
 * Ranking global exibido no carrossel "Mais Utilizados".
 *
 * Os contadores continuam vindo da coleção global de produtos no Firestore,
 * mas a apresentação prioriza o uso mais recente para que a esquerda do
 * carrossel represente o que acabou de ser consultado. A quantidade total
 * de consultas funciona como desempate.
 *
 * Ordem: consulta global mais recente primeiro; em empate, maior searchCount;
 * depois nome e código apenas para manter uma ordenação determinística.
 */
@Suppress("UNUSED_PARAMETER")
internal fun rankGloballyMostUsedProducts(
    usage: List<GlobalProductUsage>,
    nowMillis: Long = System.currentTimeMillis()
): List<Product> = usage
    .asSequence()
    .filter { it.product.searchCount > 0 }
    .sortedWith(
        compareByDescending<GlobalProductUsage> { it.lastViewedAt ?: 0L }
            .thenByDescending { it.product.searchCount }
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
