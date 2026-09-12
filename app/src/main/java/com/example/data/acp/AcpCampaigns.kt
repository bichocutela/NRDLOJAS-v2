package com.example.data.acp

import org.json.JSONArray
import org.json.JSONObject

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
    val barcodes: Set<String>
)

internal data class AcpIntegrationInfo(
    val stock: String?,
    val dueDate: String?,
    val rawSummary: List<Pair<String, String>>
)

internal object AcpCampaignParser {
    fun page(root: JSONObject): List<AcpCampaign> {
        val items = root.optJSONArray("items") ?: root.optJSONArray("data") ?: JSONArray()
        return (0 until items.length()).mapNotNull { items.optJSONObject(it)?.let(::campaign) }
    }

    private fun campaign(item: JSONObject): AcpCampaign {
        val products = firstArray(item, "products", "campaignProducts", "productCampaigns")
        val ids = mutableSetOf<String>(); val codes = mutableSetOf<String>(); val bars = mutableSetOf<String>()
        if (products != null) for (i in 0 until products.length()) {
            val wrapper = products.optJSONObject(i) ?: continue
            val product = wrapper.optJSONObject("product") ?: wrapper
            product.text("id")?.let(ids::add)
            product.text("productId")?.let(ids::add)
            product.text("code")?.let(codes::add)
            product.text("barCode")?.let(bars::add)
        }
        return AcpCampaign(
            id = item.text("id") ?: item.text("code") ?: item.toString().hashCode().toString(),
            code = item.text("code"),
            name = item.text("name") ?: item.text("description") ?: "Campanha ACP",
            description = item.text("description"),
            startDate = firstText(item, "startDate", "initialDate", "dateStart", "beginDate"),
            endDate = firstText(item, "endDate", "finalDate", "dateEnd", "finishDate"),
            active = firstBoolean(item, "active", "isActive"),
            autoExclusion = firstBoolean(item, "autoExclusion", "automaticExclusion", "isAutoExclusion"),
            productIds = ids, productCodes = codes, barcodes = bars
        )
    }

    fun integration(root: JSONObject): AcpIntegrationInfo {
        val data = root.optJSONObject("data") ?: root
        val stock = firstText(data, "stock", "quantityStock", "stockQuantity", "inventory", "balance")
        val due = firstText(data, "dueDate", "expirationDate", "expiryDate", "validityDate")
        val summary = mutableListOf<Pair<String, String>>()
        data.keys().forEach { key ->
            val value = data.opt(key)
            if (value != null && value != JSONObject.NULL && value !is JSONObject && value !is JSONArray) summary += key to value.toString()
        }
        return AcpIntegrationInfo(stock, due, summary.take(20))
    }

    private fun JSONObject.text(key: String): String? = if (isNull(key)) null else opt(key)?.toString()?.trim()?.takeIf { it.isNotBlank() && it != "null" }
    private fun firstText(o: JSONObject, vararg keys: String) = keys.firstNotNullOfOrNull { o.text(it) }
    private fun firstBoolean(o: JSONObject, vararg keys: String): Boolean? = keys.firstNotNullOfOrNull { key ->
        if (!o.has(key) || o.isNull(key)) null else when (val v = o.opt(key)) { is Boolean -> v; is Number -> v.toInt() != 0; is String -> v.toBooleanStrictOrNull(); else -> null }
    }
    private fun firstArray(o: JSONObject, vararg keys: String): JSONArray? = keys.firstNotNullOfOrNull { o.optJSONArray(it) }
}

internal suspend fun AcpApi.campaignsFor(product: AcpProduct): List<AcpCampaign> {
    val matches = mutableListOf<AcpCampaign>()
    for (page in 0 until 30) {
        val root = get("Campaign/all", listOf("pageSize" to "100", "pageIndex" to page.toString()))
        val campaigns = AcpCampaignParser.page(root)
        matches += campaigns.filter { campaign ->
            product.id in campaign.productIds || product.code.isNotBlank() && product.code in campaign.productCodes ||
                product.barcode.isNotBlank() && product.barcode in campaign.barcodes
        }
        val total = root.optInt("totalPages", 1)
        if (campaigns.isEmpty() || page + 1 >= total) break
    }
    return matches.distinctBy { it.id }
}

internal suspend fun AcpApi.integrationInfo(product: AcpProduct): AcpIntegrationInfo? {
    val candidates = buildList {
        if (product.id.isNotBlank()) add("id" to product.id)
        if (product.code.isNotBlank()) add("code" to product.code)
        if (product.barcode.isNotBlank()) add("barCode" to product.barcode)
    }
    for (parameter in candidates) {
        try { return AcpCampaignParser.integration(get("Product/integrationInfo", listOf(parameter))) }
        catch (_: AcpUnauthorized) { throw AcpUnauthorized() }
        catch (_: Exception) { /* ACP installations differ; try the next known identifier. */ }
    }
    return null
}
