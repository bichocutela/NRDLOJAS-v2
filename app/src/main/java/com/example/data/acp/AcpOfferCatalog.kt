package com.example.data.acp

import java.text.Normalizer

/**
 * Catálogo leve da tela Consultar Preços.
 *
 * Cada toque faz uma única consulta paginada ao servidor ACP. O aparelho recebe somente
 * a página solicitada e nunca percorre o catálogo geral procurando ofertas.
 */
internal suspend fun AcpApi.offerCatalog(
    family: AcpOfferFamily,
    query: String = "",
    page: Int = 0,
    pageSize: Int = 20
): AcpProductPage {
    require(page >= 0 && pageSize in 10..50 && query.length <= 120)

    val cleanQuery = query.trim()
    val parameters = mutableListOf(
        "pageSize" to pageSize.toString(),
        "pageIndex" to page.toString()
    )
    if (cleanQuery.isNotBlank()) parameters += "description" to cleanQuery

    // As famílias promocionais são pedidas pela categoria correspondente na própria ACP.
    // Assim Product/all já devolve somente aquela seleção e a paginação continua no servidor.
    // Com uma busca preenchida, Product/all já restringe pelo texto/EAN. Evitamos
    // a consulta extra de categorias e validamos a família localmente no resultado.
    val category = if (cleanQuery.isBlank()) when (family) {
        AcpOfferFamily.CLUB -> findOfferCategory(setOf("clubedevantagens", "clubvantagens"))
        AcpOfferFamily.DE_POR -> findOfferCategory(setOf("depor"))
        AcpOfferFamily.TAKE_PAY -> findOfferCategory(setOf("levepague", "leveepague"))
        else -> null
    }
    if (family in setOf(AcpOfferFamily.CLUB, AcpOfferFamily.DE_POR, AcpOfferFamily.TAKE_PAY)) {
        category ?: throw AcpFailure("A categoria ${family.catalogLabel()} não foi localizada na ACP.")
        parameters += "productCategoryIds" to category.id
    }

    val parsed = AcpProductParser.page(get("Product/all", parameters), page)
    val items = when (family) {
        AcpOfferFamily.PRICE -> parsed.items.filter { it.value != null && it.value.signum() > 0 }
        else -> parsed.items.filter { product ->
            product.offers().any { it.family == family } || product.belongsToOfferFamily(family)
        }
    }.sortedBy { normalizeForSort(it.description) }

    return parsed.copy(items = items)
}

private fun AcpProduct.belongsToOfferFamily(family: AcpOfferFamily): Boolean {
    val expected = when (family) {
        AcpOfferFamily.CLUB -> setOf("clubedevantagens", "clubvantagens")
        AcpOfferFamily.DE_POR -> setOf("depor")
        AcpOfferFamily.TAKE_PAY -> setOf("levepague", "leveepague")
        else -> emptySet()
    }
    return categories.any { normalizeCategory(it) in expected }
}

private suspend fun AcpApi.findOfferCategory(names: Set<String>): AcpCategory? =
    liveProductCategories().firstOrNull { normalizeCategory(it.description) in names }

private fun normalizeCategory(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "")
    .lowercase()
    .replace(Regex("[^a-z0-9]"), "")

private fun normalizeForSort(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "")
    .lowercase()

private fun AcpOfferFamily.catalogLabel(): String = when (this) {
    AcpOfferFamily.CLUB -> "Clube"
    AcpOfferFamily.DE_POR -> "De/Por"
    AcpOfferFamily.TAKE_PAY -> "Leve/Pague"
    else -> "Preço normal"
}
