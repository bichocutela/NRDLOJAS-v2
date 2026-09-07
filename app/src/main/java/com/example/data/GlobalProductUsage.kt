package com.example.data

private const val GLOBAL_RANKING_WINDOW_MS = 60 * 60 * 1000L

private data class GlobalRankingSnapshot(
    val hourBucket: Long,
    val products: List<Product>
)

@Volatile
private var globalRankingSnapshot: GlobalRankingSnapshot? = null

data class GlobalProductUsage(
    val product: Product,
    val lastViewedAt: Long?,
    val createdAt: Long? = null
)

/**
 * Ranking global exibido no carrossel "Mais Utilizados".
 *
 * Os contadores vêm da coleção global de produtos no Firestore, portanto somam
 * as consultas feitas por todos os usuários. Para evitar que o carrossel fique
 * mudando a cada pesquisa individual, o ranking fica congelado dentro da hora
 * corrente e só é recalculado quando entra uma nova janela de 1 hora.
 *
 * Ordem: maior searchCount primeiro; em empate, consulta global mais recente;
 * depois nome e código apenas para manter uma ordenação determinística.
 */
internal fun rankGloballyMostUsedProducts(
    usage: List<GlobalProductUsage>,
    nowMillis: Long = System.currentTimeMillis()
): List<Product> {
    val hourBucket = nowMillis / GLOBAL_RANKING_WINDOW_MS
    globalRankingSnapshot?.let { snapshot ->
        if (snapshot.hourBucket == hourBucket) return snapshot.products
    }

    val ranked = usage
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

    globalRankingSnapshot = GlobalRankingSnapshot(
        hourBucket = hourBucket,
        products = ranked
    )
    return ranked
}

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
