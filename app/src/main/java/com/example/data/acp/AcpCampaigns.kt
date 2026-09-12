package com.example.data.acp

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode

internal data class AcpCampaignProductRule(
    val productId: String?,
    val productCode: String?,
    val barcode: String?,
    val value: BigDecimal?,
    val previousValue: BigDecimal?,
    val clubValue: BigDecimal?,
    val wholesaleValue: BigDecimal?,
    val wholesaleQuantity: BigDecimal?,
    val quantityTake: BigDecimal?,
    val quantityPay: BigDecimal?,
    val cashback: BigDecimal?,
    val cashbackValue: BigDecimal?,
    val secondUnitDiscount: BigDecimal?,
    val unitLimitPerCPF: BigDecimal?
) {
    fun matches(product: AcpProduct): Boolean =
        productId != null && productId == product.id ||
            productCode != null && product.code.isNotBlank() && productCode == product.code ||
            barcode != null && product.barcode.isNotBlank() && barcode == product.barcode
}

internal data class AcpCampaign(
    val id: String,
    val code: String?,
    val name: String,
    val description: String?,
    val startDate: String?,
    val endDate: String?,
    val active: Boolean?,
    val autoExclusion: Boolean?,
    val productIds: Set<String>,
    val productCodes: Set<String>,
    val barcodes: Set<String>,
    val productRules: List<AcpCampaignProductRule>
) {
    fun matches(product: AcpProduct): Boolean =
        product.id in productIds || product.code.isNotBlank() && product.code in productCodes ||
            product.barcode.isNotBlank() && product.barcode in barcodes

    fun offersFor(product: AcpProduct): List<AcpOffer> = productRules.filter { it.matches(product) }.flatMap { rule ->
        val source = buildString {
            append("Campanha: ").append(name)
            code?.let { append(" (#").append(it).append(')') }
            if (startDate != null || endDate != null) append(". Vigência: ").append(startDate ?: "?").append(" até ").append(endDate ?: "?")
            active?.let { append(if (it) ". Ativa" else ". Inativa") }
        }
        buildList {
            if (rule.previousValue != null && rule.value != null && rule.previousValue > rule.value && rule.value > BigDecimal.ZERO) {
                add(AcpOffer("De/Por", "De ${rule.previousValue.brl()} por ${rule.value.brl()}. $source", rule.value, rule.previousValue))
            }
            rule.clubValue?.takeIf { it > BigDecimal.ZERO }?.let {
                add(AcpOffer("Clube de Vantagens", "Preço Clube: ${it.brl()}. $source", it, rule.value?.takeIf { price -> price > BigDecimal.ZERO } ?: product.value))
            }
            rule.wholesaleValue?.takeIf { it > BigDecimal.ZERO }?.let {
                val minimum = rule.wholesaleQuantity?.takeIf { q -> q > BigDecimal.ZERO }?.let { q -> "A partir de ${q.quantity()} unidades. " }.orEmpty()
                add(AcpOffer("Atacado", "${it.brl()} por unidade. $minimum$source", it, rule.value?.takeIf { price -> price > BigDecimal.ZERO } ?: product.value))
            }
            if (rule.quantityTake != null && rule.quantityPay != null && rule.quantityTake > rule.quantityPay && rule.quantityPay > BigDecimal.ZERO) {
                val equivalent = (rule.value ?: product.value)?.takeIf { it > BigDecimal.ZERO }?.multiply(rule.quantityPay)
                    ?.divide(rule.quantityTake, 2, RoundingMode.HALF_UP)
                add(AcpOffer("Leve/Pague", "Leve ${rule.quantityTake.quantity()}, pague ${rule.quantityPay.quantity()}." +
                    (equivalent?.let { " Equivale a ${it.brl()} por unidade ao completar a quantidade." } ?: "") + " $source"))
            }
            rule.secondUnitDiscount?.takeIf { it > BigDecimal.ZERO && it <= BigDecimal(100) }?.let {
                add(AcpOffer("Segunda unidade", "${it.quantity()}% de desconto na segunda unidade. $source",
                    referencePrice = rule.value?.takeIf { price -> price > BigDecimal.ZERO } ?: product.value,
                    headline = "${it.quantity()}% DE DESCONTO"))
            }
            rule.cashback?.takeIf { it > BigDecimal.ZERO && it <= BigDecimal(100) }?.let {
                add(AcpOffer("Cashback", "${it.quantity()}% de retorno. Não é desconto imediato; confira as condições de crédito. $source",
                    headline = "${it.quantity()}% DE VOLTA"))
            }
            rule.cashbackValue?.takeIf { it > BigDecimal.ZERO }?.let {
                add(AcpOffer("Cashback em valor", "${it.brl()} de retorno. Não é desconto imediato; confira as condições de crédito. $source",
                    headline = "${it.brl()} DE VOLTA"))
            }
        }
    }.distinctBy { Triple(it.title, it.price, it.detail) }
}

/** Product/integrationInfo is a global synchronization status, not product stock or offer validity. */
internal data class AcpIntegrationInfo(
    val status: Int?,
    val lastRun: String?,
    val lastCompleteRun: String?,
    val message: String?,
    val id: String?
)

internal object AcpCampaignParser {
    fun page(root: JSONObject): List<AcpCampaign> {
        val dataObject = root.optJSONObject("data")
        val items = root.optJSONArray("items") ?: root.optJSONArray("data") ?:
            dataObject?.optJSONArray("items") ?: dataObject?.optJSONArray("content") ?: dataObject?.optJSONArray("results") ?: JSONArray()
        return (0 until items.length()).mapNotNull { items.optJSONObject(it)?.let(::campaign) }
    }

    private fun campaign(item: JSONObject): AcpCampaign {
        val products = firstArray(item, "products", "campaignProducts", "productCampaigns", "items", "productsCampaign")
        val rules = mutableListOf<AcpCampaignProductRule>()
        if (products != null) for (i in 0 until products.length()) {
            val wrapper = products.optJSONObject(i) ?: continue
            val product = wrapper.optJSONObject("product") ?: wrapper.optJSONObject("productData") ?: wrapper
            val promotion = wrapper.optJSONObject("promotion") ?: wrapper.optJSONObject("offer") ?: wrapper.optJSONObject("condition") ?: wrapper.optJSONObject("rule") ?: wrapper
            val text = listOfNotNull(
                wrapper.text("description"), wrapper.text("name"), wrapper.text("promotionDescription"), wrapper.text("offerDescription"),
                item.text("description"), item.text("name")
            ).joinToString(" ")
            val objects = listOf(promotion, wrapper, product, item)
            val parsedSecondUnit = firstDecimal(objects, "secondUnitDiscount", "secondUnitPercentage", "secondUnitPercent", "percentageSecondUnit", "discountSecondUnit", "secondUnityDiscount", "discountSecondUnity")
                ?: explicitSecondUnitPercent(text)
            val parsedTake = firstDecimal(objects, "quantityTake", "takeQuantity", "quantityToTake", "buyQuantity", "take")
            val parsedPay = firstDecimal(objects, "quantityPay", "payQuantity", "quantityToPay", "pay")
            val textMultiBuy = explicitTakePay(text)
            rules += AcpCampaignProductRule(
                productId = firstText(product, "id", "productId") ?: firstText(wrapper, "productId"),
                productCode = firstText(product, "code", "productCode") ?: firstText(wrapper, "productCode", "codeProduct"),
                barcode = firstText(product, "barCode", "barcode", "ean") ?: firstText(wrapper, "barCode", "barcode", "ean"),
                value = firstDecimal(objects, "value", "currentValue", "campaignValue", "promotionalValue", "promotionValue", "price", "salePrice"),
                previousValue = firstDecimal(objects, "previousValue", "oldValue", "originalValue", "fromValue", "regularPrice"),
                clubValue = firstDecimal(objects, "clubValue", "clubPrice", "valueClub", "loyaltyValue"),
                wholesaleValue = firstDecimal(objects, "wholesaleValue", "wholesalePrice", "valueWholesale", "bulkValue"),
                wholesaleQuantity = firstDecimal(objects, "wholesaleQuantity", "wholesaleMinimumQuantity", "minimumWholesaleQuantity", "bulkQuantity"),
                quantityTake = parsedTake ?: textMultiBuy?.first,
                quantityPay = parsedPay ?: textMultiBuy?.second,
                cashback = firstDecimal(objects, "cashback", "cashbackPercent", "cashbackPercentage"),
                cashbackValue = firstDecimal(objects, "cashbackValue", "valueCashback"),
                secondUnitDiscount = parsedSecondUnit,
                unitLimitPerCPF = firstDecimal(objects, "unitLimitPerCPF", "unitLimit", "maxUnits", "maximumQuantity", "limitQuantity")
            )
        }
        val ids = rules.mapNotNull { it.productId }.toSet()
        val codes = rules.mapNotNull { it.productCode }.toSet()
        val bars = rules.mapNotNull { it.barcode }.toSet()
        return AcpCampaign(
            id = item.text("id") ?: item.text("code") ?: item.toString().hashCode().toString(),
            code = item.text("code"),
            name = item.text("name") ?: item.text("description") ?: "Campanha ACP",
            description = item.text("description"),
            startDate = firstText(item, "startDate", "initialDate", "dateStart", "beginDate", "initialValidity", "validFrom"),
            endDate = firstText(item, "endDate", "finalDate", "dateEnd", "finishDate", "finalValidity", "validTo"),
            active = firstBoolean(item, "active", "isActive"),
            autoExclusion = firstBoolean(item, "autoExclusion", "automaticExclusion", "isAutoExclusion"),
            productIds = ids, productCodes = codes, barcodes = bars, productRules = rules
        )
    }

    fun integration(root: JSONObject): AcpIntegrationInfo {
        val data = root.optJSONObject("data") ?: root
        return AcpIntegrationInfo(
            status = firstInt(data, "status"),
            lastRun = firstText(data, "lastRun"),
            lastCompleteRun = firstText(data, "lastCompleteRun"),
            message = firstText(data, "message"),
            id = firstText(data, "id")
        )
    }

    private fun JSONObject.text(key: String): String? = if (isNull(key)) null else opt(key)?.toString()?.trim()?.takeIf { it.isNotBlank() && it != "null" }
    private fun firstText(o: JSONObject, vararg keys: String) = keys.firstNotNullOfOrNull { o.text(it) }
    private fun firstInt(o: JSONObject, vararg keys: String): Int? = keys.firstNotNullOfOrNull { key ->
        if (!o.has(key) || o.isNull(key)) null else when (val value = o.opt(key)) {
            is Number -> value.toInt()
            is String -> value.trim().toIntOrNull()
            else -> null
        }
    }
    private fun firstBoolean(o: JSONObject, vararg keys: String): Boolean? = keys.firstNotNullOfOrNull { key ->
        if (!o.has(key) || o.isNull(key)) null else when (val v = o.opt(key)) { is Boolean -> v; is Number -> v.toInt() != 0; is String -> v.toBooleanStrictOrNull(); else -> null }
    }
    private fun firstArray(o: JSONObject, vararg keys: String): JSONArray? = keys.firstNotNullOfOrNull { o.optJSONArray(it) }
    private fun firstDecimal(objects: List<JSONObject>, vararg keys: String): BigDecimal? = objects.firstNotNullOfOrNull { obj ->
        keys.firstNotNullOfOrNull { key -> obj.decimal(key) }
    }
    private fun JSONObject.decimal(key: String): BigDecimal? {
        val raw = text(key) ?: return null
        return raw.replace("%", "").trim().replace(',', '.').toBigDecimalOrNull()
    }
    private fun explicitSecondUnitPercent(text: String): BigDecimal? {
        if (!Regex("(?i)(segunda|2[ªa]?|2a)\\s*unidade").containsMatchIn(text)) return null
        val match = Regex("(?i)(\\d+(?:[.,]\\d+)?)\\s*%").find(text) ?: return null
        return match.groupValues[1].replace(',', '.').toBigDecimalOrNull()
    }
    private fun explicitTakePay(text: String): Pair<BigDecimal, BigDecimal>? {
        val match = Regex("(?i)leve\\s*(\\d+(?:[.,]\\d+)?)\\s*(?:e|,)?\\s*pague\\s*(\\d+(?:[.,]\\d+)?)").find(text) ?: return null
        val take = match.groupValues[1].replace(',', '.').toBigDecimalOrNull() ?: return null
        val pay = match.groupValues[2].replace(',', '.').toBigDecimalOrNull() ?: return null
        return take to pay
    }
}

private suspend fun AcpApi.allCampaigns(): List<AcpCampaign> {
    val all = mutableListOf<AcpCampaign>()
    for (page in 0 until 30) {
        val root = get("Campaign/all", listOf("pageSize" to "100", "pageIndex" to page.toString()))
        val campaigns = AcpCampaignParser.page(root)
        all += campaigns
        val data = root.optJSONObject("data")
        val total = root.optInt("totalPages", data?.optInt("totalPages", 1) ?: 1)
        if (campaigns.isEmpty() || page + 1 >= total) break
    }
    return all.distinctBy { it.id }
}

internal suspend fun AcpApi.campaignsFor(product: AcpProduct): List<AcpCampaign> =
    allCampaigns().filter { it.matches(product) }

internal suspend fun AcpApi.campaignOffersFor(products: List<AcpProduct>): Map<String, List<AcpOffer>> {
    if (products.isEmpty()) return emptyMap()
    val campaigns = allCampaigns()
    return products.associate { product ->
        product.id to campaigns.asSequence().filter { it.matches(product) }.flatMap { it.offersFor(product).asSequence() }
            .distinctBy { Triple(it.title, it.price, it.detail) }.toList()
    }
}

internal suspend fun AcpApi.integrationInfo(): AcpIntegrationInfo? = try {
    AcpCampaignParser.integration(get("Product/integrationInfo", emptyList()))
} catch (_: AcpUnauthorized) {
    throw AcpUnauthorized()
} catch (_: Exception) {
    null
}
