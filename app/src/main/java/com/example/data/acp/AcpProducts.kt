package com.example.data.acp

import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode

internal enum class AcpSearchField(val parameter: String, val label: String) {
    BARCODE("barCode", "Cód. barras"), CODE("code", "Código"), DESCRIPTION("description", "Descrição")
}

internal data class AcpCategory(val id: String, val description: String)
internal data class AcpProductPage(val items: List<AcpProduct>, val pageIndex: Int, val totalPages: Int)
internal data class AcpOffer(val title: String, val detail: String, val price: BigDecimal? = null,
    val referencePrice: BigDecimal? = null, val headline: String? = null)

internal data class AcpProduct(
    val id: String, val code: String, val barcode: String, val description: String,
    val value: BigDecimal?, val previousValue: BigDecimal?, val clubValue: BigDecimal?,
    val wholesaleValue: BigDecimal?, val wholesaleQuantity: BigDecimal?,
    val quantityTake: BigDecimal?, val quantityPay: BigDecimal?,
    val cashback: BigDecimal?, val cashbackValue: BigDecimal?, val secondUnitDiscount: BigDecimal?,
    val unitLimitPerCPF: BigDecimal?, val unit: String?, val categories: List<String>,
    val stockQuantity: BigDecimal? = null,
    val dueDate: String? = null,
    val packageQuantity: BigDecimal? = null,
    val packageType: String? = null,
    val characteristic: String? = null,
    val contentQuantity: BigDecimal? = null,
    val contentUnit: String? = null,
    val productFamily: String? = null,
    val auxDescriptions: List<String> = emptyList()
) {
    fun offers(): List<AcpOffer> = buildList {
        if (previousValue != null && value != null && previousValue > value && value > BigDecimal.ZERO) {
            add(AcpOffer("De/Por", "De ${previousValue.brl()} por ${value.brl()}.", value, previousValue))
        }
        if (clubValue != null && clubValue > BigDecimal.ZERO) {
            add(AcpOffer("Clube de Vantagens", "Preço Clube: ${clubValue.brl()}. Condicionado ao Clube.", clubValue, value?.takeIf { it > BigDecimal.ZERO }))
        }
        if (wholesaleValue != null && wholesaleValue > BigDecimal.ZERO) {
            val condition = wholesaleQuantity?.takeIf { it > BigDecimal.ZERO }
                ?.let { "A partir de ${it.quantity()} unidades." } ?: "Quantidade mínima não informada."
            add(AcpOffer("Atacado", "${wholesaleValue.brl()} por unidade. $condition", wholesaleValue, value?.takeIf { it > BigDecimal.ZERO }))
        }
        if (quantityTake != null && quantityPay != null && quantityTake > quantityPay &&
            quantityPay > BigDecimal.ZERO && quantityTake.stripTrailingZeros().scale() <= 0 &&
            quantityPay.stripTrailingZeros().scale() <= 0) {
            val equivalent = value?.takeIf { it > BigDecimal.ZERO }?.multiply(quantityPay)
                ?.divide(quantityTake, 2, RoundingMode.HALF_UP)
            add(AcpOffer("Leve/Pague", "Leve ${quantityTake.quantity()}, pague ${quantityPay.quantity()}." +
                (equivalent?.let { " Equivalente a ${it.brl()} por unidade ao completar a quantidade, calculado sobre o valor principal." } ?: "")))
        }
        secondUnitDiscount?.takeIf { it > BigDecimal.ZERO && it <= BigDecimal(100) }?.let {
            add(AcpOffer("Segunda unidade", "${it.quantity()}% de desconto na segunda unidade. Base de preço e combinação com Clube ainda não verificadas.", referencePrice = value?.takeIf { price -> price > BigDecimal.ZERO }, headline = "${it.quantity()}% DE DESCONTO"))
        }
        cashback?.takeIf { it > BigDecimal.ZERO && it <= BigDecimal(100) }?.let {
            add(AcpOffer("Cashback", "${it.quantity()}% de retorno. Não é desconto imediato; confira as condições de crédito."))
        }
        cashbackValue?.takeIf { it > BigDecimal.ZERO }?.let {
            add(AcpOffer("Cashback em valor", "${it.brl()} de retorno. Não é desconto imediato; confira as condições de crédito."))
        }
    }
}

internal fun BigDecimal.brl(): String = "R$ " + setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',')
internal fun BigDecimal.quantity(): String = stripTrailingZeros().toPlainString().replace('.', ',')

internal object AcpProductParser {
    fun page(root: JSONObject, requestedPage: Int): AcpProductPage {
        val array = root.optJSONArray("items") ?: throw AcpFailure("A ACP retornou produtos em um formato não reconhecido.")
        val items = (0 until array.length()).map { index ->
            product(array.optJSONObject(index) ?: throw AcpFailure("A ACP retornou um produto inválido."))
        }
        val page = root.optInt("pageIndex", requestedPage).coerceAtLeast(0)
        val total = root.optInt("totalPages", if (items.isEmpty()) 0 else page + 1).coerceAtLeast(0)
        return AcpProductPage(items, page, total)
    }

    private fun product(item: JSONObject): AcpProduct {
        val code = item.text("code").orEmpty()
        val barcode = item.text("barCode").orEmpty()
        val description = item.text("description") ?: throw AcpFailure("A ACP retornou um produto sem descrição.")
        val categories = item.optJSONArray("productCategories")
        val auxDescriptions = item.optJSONArray("auxDescriptions")
        return AcpProduct(
            id = item.text("id") ?: "$code|$barcode|$description", code = code, barcode = barcode, description = description,
            value = item.decimal("value"), previousValue = item.decimal("previousValue"), clubValue = item.decimal("clubValue"),
            wholesaleValue = item.decimal("wholesaleValue"), wholesaleQuantity = item.decimal("wholesaleQuantity"),
            quantityTake = item.decimal("quantityTake"), quantityPay = item.decimal("quantityPay"),
            cashback = item.decimal("cashback"), cashbackValue = item.decimal("cashbackValue"),
            secondUnitDiscount = item.decimal("secondUnitDiscount"), unitLimitPerCPF = item.decimal("unitLimitPerCPF"),
            unit = item.optJSONObject("unit")?.text("description"),
            categories = if (categories == null) emptyList() else (0 until categories.length()).mapNotNull {
                categories.optJSONObject(it)?.text("description")
            },
            stockQuantity = item.decimal("stockQuantity"), dueDate = item.text("dueDate"),
            packageQuantity = item.decimal("packageQuantity"), packageType = item.optJSONObject("packageType")?.text("description"),
            characteristic = item.text("characteristic"), contentQuantity = item.decimal("contentQuantity"),
            contentUnit = item.text("contentUnit"), productFamily = item.optJSONObject("productFamily")?.text("description"),
            auxDescriptions = if (auxDescriptions == null) emptyList() else (0 until auxDescriptions.length()).mapNotNull {
                auxDescriptions.optString(it).trim().takeIf { text -> text.isNotEmpty() && text != "null" }
            }
        )
    }

    private fun JSONObject.text(key: String): String? = if (isNull(key)) null else
        optString(key).trim().takeIf { it.isNotEmpty() && it != "null" }

    private fun JSONObject.decimal(key: String): BigDecimal? {
        val raw = text(key) ?: return null
        if (!Regex("-?[0-9]+([.,][0-9]+)?").matches(raw)) return null
        return raw.replace(',', '.').toBigDecimalOrNull()
    }
}

private suspend fun AcpApi.searchProductsOnce(field: AcpSearchField, query: String, category: AcpCategory?, page: Int): AcpProductPage {
    val parameters = mutableListOf("pageSize" to "20", "pageIndex" to page.toString(), field.parameter to query)
    category?.let { parameters.add("productCategoryIds" to it.id) }
    return AcpProductParser.page(get("Product/all", parameters), page)
}

internal suspend fun AcpApi.searchProducts(field: AcpSearchField, query: String, category: AcpCategory?, page: Int): AcpProductPage {
    require(query.isNotBlank() && query.length <= 200 && page >= 0)
    val clean = query.trim()
    val first = searchProductsOnce(field, clean, category, page)
    if (first.items.isNotEmpty() || page != 0 || field == AcpSearchField.DESCRIPTION) return first

    // A ACP já foi observada retornando vazio para identificadores existentes. Repetimos uma vez
    // e, para identificadores numéricos, tentamos o outro campo exato (EAN <-> código interno).
    val retry = searchProductsOnce(field, clean, category, 0)
    if (retry.items.isNotEmpty()) return retry
    val alternate = when (field) {
        AcpSearchField.BARCODE -> AcpSearchField.CODE
        AcpSearchField.CODE -> AcpSearchField.BARCODE
        AcpSearchField.DESCRIPTION -> return retry
    }
    return searchProductsOnce(alternate, clean, category, 0)
}

internal suspend fun AcpApi.categories(): List<AcpCategory> {
    val result = mutableListOf<AcpCategory>()
    for (page in 0 until 20) {
        val root = get("ProductCategory/all", listOf("pageSize" to "100", "pageIndex" to page.toString()))
        val items = root.optJSONArray("items") ?: throw AcpFailure("Não foi possível carregar os tipos de oferta.")
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            if (!item.isNull("id") && !item.isNull("description")) {
                result.add(AcpCategory(item.get("id").toString(), item.getString("description")))
            }
        }
        if (page + 1 >= root.optInt("totalPages", 1) || items.length() == 0) return result.distinctBy { it.id }
    }
    throw AcpFailure("A lista de tipos de oferta excedeu o limite da consulta.")
}
