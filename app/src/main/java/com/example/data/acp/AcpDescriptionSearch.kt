package com.example.data.acp

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.text.Normalizer
import java.util.Locale

internal data class AcpDescriptionSearchSnapshot(
    val query: String, val categoryId: String?, val items: List<AcpProduct>,
    val queriedAtMillis: Long, val provider: String?
)

internal fun List<AcpProduct>.orderedSearchMatches(query: String): List<AcpProduct> =
    filter { it.matchesAcpSearch(query) }.sortedWith(
        compareBy<AcpProduct> { Normalizer.normalize(it.description, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT) }
            .thenBy { it.code }.thenBy { it.barcode }.thenBy { it.id }
    )

internal fun AcpDescriptionSearchSnapshot.page(index: Int): AcpProductPage {
    val pages = (items.size + 19) / 20
    val current = index.coerceIn(0, (pages - 1).coerceAtLeast(0))
    return AcpProductPage(items.drop(current * 20).take(20), current, pages, items.size, queriedAtMillis)
}

/** Read only the filtered query, then filter/order before splitting the local pages. */
internal suspend fun AcpApi.descriptionSearch(query: String, category: AcpCategory?, page: Int, fresh: Boolean): AcpProductPage {
    val cached = descriptionSearchSnapshot
    if (!fresh && cached != null && cached.query == query && cached.categoryId == category?.id &&
        (page > 0 || System.currentTimeMillis() - cached.queriedAtMillis in 0L..60_000L)) {
        confirmAccess()
        return cached.page(page)
    }
    val upstreamQuery = acpSearchRequest(query).query
    if (upstreamQuery.isBlank()) throw AcpFailure("Digite uma palavra, código ou código de barras para pesquisar.")
    val parameters = buildList {
        add("description" to upstreamQuery)
        add("pageSize" to "250")
        category?.let { add("productCategoryIds" to it.id) }
    }
    descriptionSearchSnapshot = null // Release the previous query before collecting its replacement.
    val products = mutableListOf<AcpProduct>()
    var receivedCount = 0
    val ids = mutableSetOf<String>()
    var total: Int? = null
    var source: String? = null
    val queriedAt = System.currentTimeMillis()
    for (index in 0 until 40) {
        currentCoroutineContext().ensureActive()
        val root = getFresh("Product/all", parameters + ("pageIndex" to index.toString()))
        val result = AcpProductParser.page(root, index)
        val provider = root.optString("nrdProvider").takeIf { it.isNotBlank() }
        if (index == 0) { total = result.totalCount; source = provider }
        if (result.pageIndex != index || result.totalCount != total || provider != source ||
            result.items.any { !ids.add(it.id) }) {
            throw AcpFailure("O catálogo mudou durante a pesquisa. Pesquise novamente para atualizar a lista.")
        }
        receivedCount += result.items.size
        products += result.items.filter { it.matchesAcpSearch(query) }
        if (index + 1 >= result.totalPages) {
            if (receivedCount != total) throw AcpFailure("A Sistema retornou uma pesquisa incompleta. Tente novamente.")
            currentCoroutineContext().ensureActive()
            val snapshot = AcpDescriptionSearchSnapshot(query, category?.id, products.orderedSearchMatches(query), queriedAt, source)
            descriptionSearchSnapshot = snapshot
            return snapshot.page(page)
        }
        if (result.items.isEmpty()) throw AcpFailure("A Sistema retornou uma página incompleta. Tente novamente.")
    }
    throw AcpFailure("A pesquisa tem mais de 10.000 produtos. Acrescente outra palavra ou use # para refinar.")
}
