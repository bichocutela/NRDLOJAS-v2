package com.example.data.acp

import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode

internal enum class AcpSearchField(val parameter: String, val label: String) { BARCODE("barCode", "Cód. barras"), CODE("code", "Código"), DESCRIPTION("description", "Descrição") }
internal data class AcpCategory(val id: String, val description: String)
internal data class AcpProductPage(val items: List<AcpProduct>, val pageIndex: Int, val totalPages: Int, val totalCount: Int, val queriedAtMillis: Long = System.currentTimeMillis())
internal data class AcpOffer(val title: String, val detail: String, val price: BigDecimal? = null, val referencePrice: BigDecimal? = null, val headline: String? = null)

internal enum class AcpOfferFamily {
    DE_POR, CLUB, WHOLESALE, TAKE_PAY, SECOND_UNIT, CASHBACK, CASHBACK_VALUE, PRICE
}

internal val AcpOffer.family: AcpOfferFamily
    get() = when (title) {
        "De/Por" -> AcpOfferFamily.DE_POR
        "Clube de Vantagens" -> AcpOfferFamily.CLUB
        "Atacado" -> AcpOfferFamily.WHOLESALE
        "Leve/Pague" -> AcpOfferFamily.TAKE_PAY
        "Segunda unidade" -> AcpOfferFamily.SECOND_UNIT
        "Cashback" -> AcpOfferFamily.CASHBACK
        "Cashback em valor" -> AcpOfferFamily.CASHBACK_VALUE
        else -> AcpOfferFamily.PRICE
    }

/** Keeps every detected condition, while selecting one deterministic family for the automatic poster. */
internal fun List<AcpOffer>.forAutomaticDisplay(): List<AcpOffer> {
    val priority = mapOf(
        AcpOfferFamily.SECOND_UNIT to 0,
        AcpOfferFamily.TAKE_PAY to 1,
        AcpOfferFamily.CLUB to 2,
        AcpOfferFamily.DE_POR to 3,
        AcpOfferFamily.WHOLESALE to 4,
        AcpOfferFamily.CASHBACK to 5,
        AcpOfferFamily.CASHBACK_VALUE to 6,
        AcpOfferFamily.PRICE to 7
    )
    return distinctBy { Triple(it.title, it.price, it.detail) }
        .sortedWith(compareBy<AcpOffer> { priority[it.family] ?: Int.MAX_VALUE }.thenBy { it.title })
}

internal data class AcpProduct(
    val id: String, val code: String, val barcode: String, val description: String,
    val value: BigDecimal?, val previousValue: BigDecimal?, val clubValue: BigDecimal?,
    val wholesaleValue: BigDecimal?, val wholesaleQuantity: BigDecimal?, val quantityTake: BigDecimal?, val quantityPay: BigDecimal?,
    val cashback: BigDecimal?, val cashbackValue: BigDecimal?, val secondUnitDiscount: BigDecimal?,
    val unitLimitPerCPF: BigDecimal?, val unit: String?, val categories: List<String>,
    val stockQuantity: BigDecimal? = null, val dueDate: String? = null, val packageQuantity: BigDecimal? = null,
    val packageType: String? = null, val characteristic: String? = null, val contentQuantity: BigDecimal? = null,
    val contentUnit: String? = null, val productFamily: String? = null, val auxDescriptions: List<String> = emptyList()
) {
    private fun hasCategory(expected: String): Boolean {
        val normalizedExpected = expected.lowercase().replace(Regex("[^a-z0-9]"), "")
        return categories.any { category -> category.lowercase().replace(Regex("[^a-z0-9]"), "") == normalizedExpected }
    }

    fun offers(): List<AcpOffer> = buildList {
        if (hasCategory("De-Por") && previousValue != null && value != null && previousValue > value && value > BigDecimal.ZERO) {
            add(AcpOffer("De/Por", "De ${previousValue.brl()} por ${value.brl()}.", value, previousValue))
        }
        clubValue?.takeIf { it > BigDecimal.ZERO }?.let { clubPrice ->
            val reference = when {
                value != null && value > clubPrice -> value
                previousValue != null && previousValue > clubPrice -> previousValue
                else -> null
            }
            add(AcpOffer("Clube de Vantagens", "Preço Clube: ${clubPrice.brl()}. Condicionado ao Clube.", clubPrice, reference))
        }
        wholesaleValue?.takeIf { it > BigDecimal.ZERO }?.let { price ->
            val minimum = wholesaleQuantity?.takeIf { it > BigDecimal.ZERO }
            val condition = minimum?.let { "A partir de ${it.quantity()} unidades." } ?: "Quantidade mínima não informada."
            val headline = minimum?.let { "A PARTIR DE ${it.quantity()} UN." }
            add(AcpOffer("Atacado", "${price.brl()} por unidade. $condition", price, value?.takeIf { it > BigDecimal.ZERO }, headline))
        }
        if (quantityTake != null && quantityPay != null && quantityTake > quantityPay && quantityPay > BigDecimal.ZERO && quantityTake.stripTrailingZeros().scale() <= 0 && quantityPay.stripTrailingZeros().scale() <= 0) {
            val equivalent = value?.takeIf { it > BigDecimal.ZERO }?.multiply(quantityPay)?.divide(quantityTake, 2, RoundingMode.HALF_UP)
            val headline = "LEVE ${quantityTake.quantity()} • PAGUE ${quantityPay.quantity()}"
            val detail = "Leve ${quantityTake.quantity()}, pague ${quantityPay.quantity()}." +
                (equivalent?.let { " Média equivalente de ${it.brl()} por unidade ao completar a quantidade, calculada sobre o preço principal." } ?: "")
            add(AcpOffer("Leve/Pague", detail, equivalent, value?.takeIf { it > BigDecimal.ZERO }, headline))
        }
        secondUnitDiscount?.takeIf { it > BigDecimal.ZERO && it <= BigDecimal(100) }?.let { add(AcpOffer("Segunda unidade", "${it.quantity()}% de desconto na segunda unidade. Base de preço e combinação com Clube ainda não verificadas.", referencePrice = value?.takeIf { p -> p > BigDecimal.ZERO }, headline = "${it.quantity()}% DE DESCONTO")) }
        cashback?.takeIf { it > BigDecimal.ZERO && it <= BigDecimal(100) }?.let {
            add(AcpOffer("Cashback", "${it.quantity()}% de retorno. Não é desconto imediato; confira as condições de crédito.", referencePrice = value?.takeIf { p -> p > BigDecimal.ZERO }, headline = "${it.quantity()}% DE VOLTA"))
        }
        cashbackValue?.takeIf { it > BigDecimal.ZERO }?.let {
            add(AcpOffer("Cashback em valor", "${it.brl()} de retorno. Não é desconto imediato; confira as condições de crédito.", referencePrice = value?.takeIf { p -> p > BigDecimal.ZERO }, headline = "${it.brl()} DE VOLTA"))
        }
    }
}

internal fun BigDecimal.brl(): String = "R$ " + setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',')
internal fun BigDecimal.quantity(): String = stripTrailingZeros().toPlainString().replace('.', ',')

internal fun acpDateLabel(raw: String?, includeTime: Boolean = false): String? {
    val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val match = Regex("^(\\d{4})-(\\d{2})-(\\d{2})(?:T(\\d{2}):(\\d{2})(?::\\d{2}(?:\\.\\d+)?)?(?:Z|[+-]\\d{2}:?\\d{2})?)?$").matchEntire(value) ?: return value
    val date = "${match.groupValues[3]}/${match.groupValues[2]}/${match.groupValues[1]}"
    return if (includeTime && match.groupValues[4].isNotEmpty()) "$date ${match.groupValues[4]}:${match.groupValues[5]}" else date
}

internal fun AcpProductPage.prioritizeExact(field: AcpSearchField, query: String): AcpProductPage {
    if (field == AcpSearchField.DESCRIPTION || items.size < 2) return this
    val exact: (AcpProduct) -> Boolean = when (field) {
        AcpSearchField.BARCODE -> { product -> product.barcode == query }
        AcpSearchField.CODE -> { product -> product.code == query }
        AcpSearchField.DESCRIPTION -> { _ -> false }
    }
    if (items.none(exact) || exact(items.first())) return this
    return copy(items = items.sortedByDescending(exact))
}

internal object AcpProductParser {
    fun page(root: JSONObject, requestedPage: Int): AcpProductPage {
        val array = root.optJSONArray("items") ?: throw AcpFailure("A ACP retornou produtos em um formato não reconhecido.")
        val items = (0 until array.length()).map { product(array.optJSONObject(it) ?: throw AcpFailure("A ACP retornou um produto inválido.")) }
        val page = root.optInt("pageIndex", requestedPage).coerceAtLeast(0)
        val totalPages = root.optInt("totalPages", if (items.isEmpty()) 0 else page + 1).coerceAtLeast(0)
        val totalCount = root.optInt("totalCount", items.size).coerceAtLeast(0)
        return AcpProductPage(items, page, totalPages, totalCount)
    }

    private fun product(item: JSONObject): AcpProduct {
        val code = item.text("code").orEmpty(); val barcode = item.text("barCode").orEmpty()
        val description = item.text("description") ?: throw AcpFailure("A ACP retornou um produto sem descrição.")
        val categories = item.optJSONArray("productCategories"); val aux = item.optJSONArray("auxDescriptions")
        return AcpProduct(
            id = item.text("id") ?: "$code|$barcode|$description", code = code, barcode = barcode, description = description,
            value = item.decimal("value"), previousValue = item.decimal("previousValue"), clubValue = item.decimal("clubValue"),
            wholesaleValue = item.decimal("wholesaleValue"), wholesaleQuantity = item.decimal("wholesaleQuantity"), quantityTake = item.decimal("quantityTake"), quantityPay = item.decimal("quantityPay"),
            cashback = item.decimal("cashback"), cashbackValue = item.decimal("cashbackValue"), secondUnitDiscount = item.decimal("secondUnitDiscount"), unitLimitPerCPF = item.decimal("unitLimitPerCPF"),
            unit = item.optJSONObject("unit")?.text("description"), categories = if (categories == null) emptyList() else (0 until categories.length()).mapNotNull { categories.optJSONObject(it)?.text("description") },
            stockQuantity = item.decimal("stockQuantity"), dueDate = item.text("dueDate"), packageQuantity = item.decimal("packageQuantity"), packageType = item.optJSONObject("packageType")?.text("description"),
            characteristic = item.text("characteristic"), contentQuantity = item.decimal("contentQuantity"), contentUnit = item.text("contentUnit"), productFamily = item.optJSONObject("productFamily")?.text("description"),
            auxDescriptions = if (aux == null) emptyList() else (0 until aux.length()).mapNotNull { aux.optString(it).trim().takeIf { text -> text.isNotEmpty() && text != "null" } }
        )
    }
    private fun JSONObject.text(key: String): String? = if (isNull(key)) null else optString(key).trim().takeIf { it.isNotEmpty() && it != "null" }
    private fun JSONObject.decimal(key: String): BigDecimal? { val raw = text(key) ?: return null; if (!Regex("[0-9]+([.,][0-9]+)?").matches(raw)) return null; return raw.replace(',', '.').toBigDecimalOrNull() }
}

private suspend fun AcpApi.searchProductsOnce(field: AcpSearchField, query: String, category: AcpCategory?, page: Int): AcpProductPage {
    val parameters = mutableListOf("pageSize" to "20", "pageIndex" to page.toString(), field.parameter to query)
    category?.let { parameters.add("productCategoryIds" to it.id) }
    return AcpProductParser.page(get("Product/all", parameters), page).prioritizeExact(field, query)
}

internal suspend fun AcpApi.searchProducts(field: AcpSearchField, query: String, category: AcpCategory?, page: Int): AcpProductPage {
    require(query.isNotBlank() && query.length <= 200 && page >= 0)
    val clean = query.trim(); val first = searchProductsOnce(field, clean, category, page)
    if (first.items.isNotEmpty() || page != 0 || field == AcpSearchField.DESCRIPTION) return first
    val retry = searchProductsOnce(field, clean, category, 0)
    if (retry.items.isNotEmpty()) return retry
    val alternate = if (field == AcpSearchField.BARCODE) AcpSearchField.CODE else AcpSearchField.BARCODE
    return searchProductsOnce(alternate, clean, category, 0)
}

/** Re-query the selected identity, never pick the first partial barcode match. */
internal suspend fun AcpApi.refreshProduct(selected: AcpProduct): AcpProduct {
    val filters = buildList {
        if (selected.code.isNotBlank()) add("code" to selected.code)
        if (selected.barcode.isNotBlank()) add("barCode" to selected.barcode)
    }
    if (filters.isEmpty()) throw AcpFailure("Produto sem código para atualizar. Faça uma nova busca.")
    val matches = mutableListOf<AcpProduct>()
    for (index in 0 until 10) {
        val page = AcpProductParser.page(get("Product/all", filters + listOf("pageIndex" to index.toString(), "pageSize" to "50")), index)
        matches.addAll(page.items.filter { candidate ->
            (selected.code.isBlank() || candidate.code == selected.code) &&
                (selected.barcode.isBlank() || candidate.barcode == selected.barcode)
        })
        if (matches.size > 1) throw AcpFailure("A ACP retornou mais de um cadastro com esses códigos. Confira o produto na ACP.")
        if (page.items.isEmpty() || index + 1 >= page.totalPages) {
            return matches.singleOrNull() ?: throw AcpFailure("Produto não encontrado na atualização. Faça uma nova busca.")
        }
    }
    throw AcpFailure("Não foi possível confirmar o produto entre os resultados da ACP. Refine a busca.")
}

internal suspend fun AcpApi.categories(): List<AcpCategory> {
    val result = mutableListOf<AcpCategory>()
    for (page in 0 until 20) {
        val root = get("ProductCategory/all", listOf("pageSize" to "100", "pageIndex" to page.toString()))
        val items = root.optJSONArray("items") ?: throw AcpFailure("Não foi possível carregar as categorias ACP.")
        for (index in 0 until items.length()) { val item = items.optJSONObject(index) ?: continue; if (!item.isNull("id") && !item.isNull("description")) result.add(AcpCategory(item.get("id").toString(), item.getString("description"))) }
        if (page + 1 >= root.optInt("totalPages", 1) || items.length() == 0) return result.distinctBy { it.id }
    }
    throw AcpFailure("A lista de categorias ACP excedeu o limite da consulta.")
}
