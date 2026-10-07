package com.example.data.promotions

import android.content.Context
import com.example.data.NossaGentePromotionsResult
import com.example.data.Promotion
import com.example.data.PromotionProduct
import com.example.data.acp.*
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.Date

internal class AcpPromotionsRepository(context: Context) {
    private val gateway = AcpGatewayClient(promotionsOnly = true)

    suspend fun fetchPromotions(): NossaGentePromotionsResult = try {
        val stores = PromotionStores.read().filter { it.enabled }
        val promotions = mutableListOf<Promotion>()
        for (store in stores) {
            val imported = PromotionStores.readRecords(store)
            if (store.code == "0012") {
                val validity = FirebaseFirestore.getInstance().collection("config")
                    .document("acpOfferValidity").get().await().data.orEmpty()
                val products = mutableListOf<AcpProduct>()
                var page = 0
                var expectedCount: Int? = null
                do {
                    val response = AcpProductParser.page(gateway.read("Promotion/all",
                        listOf("pageIndex" to page.toString(), "pageSize" to "250")), page)
                    require(response.pageIndex == page && response.totalPages <= 100) { "Paginação de promoções inválida." }
                    if (expectedCount == null) expectedCount = response.totalCount
                    require(expectedCount == response.totalCount) { "As ofertas mudaram durante a consulta. Tentando novamente na próxima atualização." }
                    products += response.items
                    page++
                    val more = page < response.totalPages
                    require(!more || response.items.isNotEmpty()) { "Consulta de promoções incompleta." }
                } while (more)
                val uniqueProducts = products.distinctBy { it.id }
                require(uniqueProducts.size == expectedCount) { "Consulta de promoções incompleta. Tente novamente." }
                uniqueProducts.forEach { product ->
                    val offer = product.offers().firstOrNull { it.family == AcpOfferFamily.DE_POR } ?: return@forEach
                    val document = imported.firstOrNull { record ->
                        BigDecimal.valueOf(record.price).setScale(2, RoundingMode.HALF_UP) == offer.price!!.setScale(2, RoundingMode.HALF_UP) &&
                        BigDecimal.valueOf(record.previous).setScale(2, RoundingMode.HALF_UP) == offer.referencePrice!!.setScale(2, RoundingMode.HALF_UP) &&
                        ((record.code.isNotBlank() && record.code == product.code) ||
                            (record.barcode.isNotBlank() && record.barcode == product.barcode))
                    }
                    val raw = validity[AcpOfferValidityStore.keyFor(product.description, AcpOfferFamily.DE_POR)] as? Map<*, *>
                    val fallback = AcpOfferValidity(document?.from ?: raw?.get("startDate") as? String ?: "",
                        document?.to ?: raw?.get("endDate") as? String ?: "")
                    if (product.isWithinOfferValidity(fallback)) {
                        promotions += promotion(store.code, product.code.ifBlank { product.barcode }, product.description,
                            offer.price!!, offer.referencePrice!!, product.validFrom ?: fallback.startDate,
                            product.validTo ?: fallback.endDate, product.imageUrl)
                    }
                }
            } else {
                imported.forEach { record ->
                    if (record.price.isFinite() && record.previous.isFinite() && record.price > 0 && record.previous > record.price &&
                        active(record.from, record.to)) {
                        promotions += promotion(store.code, record.code.ifBlank { record.barcode }, record.name,
                            BigDecimal.valueOf(record.price), BigDecimal.valueOf(record.previous), record.from, record.to, null)
                    }
                }
            }
        }
        val fingerprint = MessageDigest.getInstance("SHA-256").digest(promotions.sortedBy { it.id }.toString().toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        // Publish only complete snapshots; a failed page never removes visible offers.
        NossaGentePromotionsResult.Success(promotions, fingerprint)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        NossaGentePromotionsResult.Error(failure.message ?: "Não foi possível atualizar as ofertas do ACP.")
    }

    private fun promotion(store: String, code: String, name: String, price: BigDecimal, previous: BigDecimal,
        from: String?, to: String?, image: String?): Promotion {
        val category = PromotionCategory.forDescription(name)
        val discount = previous.subtract(price).multiply(BigDecimal(100)).divide(previous, 0, RoundingMode.HALF_UP)
        return Promotion("$store|$code|$name", "De/Por", category, image, isoDate(from), isoDate(to),
            listOf(PromotionProduct(code, name, price.brl(), previous.brl(), "$discount%", store, image)))
    }

    companion object {
        internal fun isoDate(raw: String?): String? {
            val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val text = value.take(10)
            val pattern = when {
                Regex("\\d{4}-\\d{2}-\\d{2}").matches(text) -> "yyyy-MM-dd"
                Regex("\\d{2}/\\d{2}/\\d{4}").matches(text) -> "dd/MM/yyyy"
                else -> return null
            }
            val zone = TimeZone.getTimeZone("America/Fortaleza")
            val date = runCatching { SimpleDateFormat(pattern, Locale.ROOT).apply {
                isLenient = false; timeZone = zone
            }.parse(text) }.getOrNull() ?: return null
            return SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { timeZone = zone }.format(date)
        }

        internal fun active(from: String?, to: String?, nowMillis: Long = System.currentTimeMillis()): Boolean {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply {
                timeZone = TimeZone.getTimeZone("America/Fortaleza")
            }.format(Date(nowMillis))
            val start = isoDate(from)
            val end = isoDate(to)
            return (start == null || today >= start) && (end == null || today <= end)
        }
    }
}
