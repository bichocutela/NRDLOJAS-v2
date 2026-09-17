package com.example.data.acp

/**
 * Catálogo leve para a tela Consultar Preços. A ACP entrega 20 itens por página e o filtro
 * visual é aplicado à família pedida. Quando uma página bruta não contém itens da família,
 * avançamos algumas páginas até encontrar resultados, sem baixar o catálogo inteiro.
 */
internal suspend fun AcpApi.offerCatalog(
    family: AcpOfferFamily,
    query: String = "",
    page: Int = 0,
    pageSize: Int = 20
): AcpProductPage {
    require(page >= 0 && pageSize in 10..50 && query.length <= 120)

    // Clube já possui uma rota otimizada dentro de AcpApi: a assinatura pageSize=100 sem
    // filtros é convertida para a categoria Clube de Vantagens no servidor. Mantemos esse
    // caminho e fazemos a paginação de 20 no resultado retornado.
    if (family == AcpOfferFamily.CLUB && query.isBlank()) {
        val root = get("Product/all", listOf("pageSize" to "100", "pageIndex" to page.toString()))
        val parsed = AcpProductParser.page(root, page)
        val filtered = parsed.items.filter { product -> product.offers().any { it.family == family } }
        return parsed.copy(items = filtered, totalCount = parsed.totalCount)
    }

    val parameters = mutableListOf("pageSize" to pageSize.toString(), "pageIndex" to page.toString())
    if (query.isNotBlank()) parameters += "description" to query.trim()
    val parsed = AcpProductParser.page(get("Product/all", parameters), page)
    val filtered = parsed.items.filter { product ->
        when (family) {
            AcpOfferFamily.PRICE -> product.value != null && product.value.signum() > 0
            else -> product.offers().any { it.family == family }
        }
    }
    return parsed.copy(items = filtered)
}
