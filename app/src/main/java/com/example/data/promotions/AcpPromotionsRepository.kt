package com.example.data.promotions

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
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
    internal val database = PromotionDatabase.get(context)
    internal val dao = database.promotions()
    fun observeOffers() = dao.observeOffers().map { rows -> rows.map { PromotionCodec.decode(it.payload) }
        .filter { active(it.validFrom, it.validTo) } }.distinctUntilChanged()
    fun observeLatestAdded() = dao.observeLatestAdded().map { values ->
        val array = JSONArray(values.firstOrNull()?.value ?: "[]")
        (0 until array.length()).map { array.getString(it) }.toSet()
    }
    suspend fun isInitialized(): Boolean = dao.metadata("initialized") == "1"
    suspend fun cached(): NossaGentePromotionsResult.Success? = if (isInitialized()) {
        NossaGentePromotionsResult.Success(dao.offers().map { PromotionCodec.decode(it.payload) }.filter { active(it.validFrom, it.validTo) },
            dao.metadata("fingerprint").orEmpty())
    } else null

    private suspend fun importedRecords(store: PromotionStore): List<StorePromotionRecord> {
        if (store.revision.isBlank()) return emptyList()
        val key = "import_${store.code}"
        if (dao.metadata(key + "_revision") == store.revision) {
            dao.metadata(key)?.let { return PromotionCodec.records(it) }
        }
        return PromotionStores.readRecords(store).also { records ->
            database.withTransaction {
                dao.put(PromotionMetadata(key, PromotionCodec.records(records)))
                dao.put(PromotionMetadata(key + "_revision", store.revision))
            }
        }
    }

    suspend fun fetchPromotions(
        forceRefresh: Boolean = false,
        onNetwork: (Boolean) -> Unit = {}
    ): NossaGentePromotionsResult = withContext(Dispatchers.IO) {
      syncMutex.withLock {
       try {
        val previousRows = dao.rows()
        var delta: PromotionDelta? = null
        var cityProducts = previousRows
        if (!isInitialized()) onNetwork(true)
        val stores = PromotionStores.read(forceRefresh).filter { it.enabled }
        val promotions = mutableListOf<Promotion>()
        for (store in stores) {
            val imported = importedRecords(store)
            if (store.code == "0012") {
                val validity = PromotionConfigCache.read(FirebaseFirestore.getInstance().collection("config")
                    .document("acpOfferValidity"), forceRefresh)
                val currentRevision = dao.metadata("acp_revision").orEmpty()
                val manifest = JSONArray().apply {
                    previousRows.forEach { put(JSONObject().put("id", it.id).put("hash", it.hash)) }
                }
                if (forceRefresh) {
                    onNetwork(true)
                    delta = PromotionDelta.parse(gateway.promotionRefresh(currentRevision, manifest))
                    cityProducts = delta!!.applyTo(previousRows)
                } else {
                    val status = gateway.promotionStatus()
                    val revision = status.getString("revision")
                    val serverCount = status.getInt("count")
                    require(Regex("[a-f0-9]{64}").matches(revision) && serverCount in 0..10_000)
                    if (currentRevision != revision || previousRows.size != serverCount) {
                        onNetwork(true)
                        delta = PromotionDelta.parse(gateway.promotionSync(currentRevision, manifest))
                        cityProducts = delta!!.applyTo(previousRows)
                    }
                }
                val uniqueProducts = AcpProductParser.page(JSONObject().put("items",
                    JSONArray().apply { cityProducts.forEach { put(JSONObject(it.payload)) } }), 0).items
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
                    val effective = product.offerValidityOr(fallback)
                    if (active(effective?.startDate, effective?.endDate)) {
                        promotions += promotion(store.code, product.code.ifBlank { product.barcode }, product.description,
                            offer.price!!, offer.referencePrice!!, effective?.startDate,
                            effective?.endDate, product.imageUrl, product.barcode, product.detailsJson,
                            product.detailsJson?.let { org.json.JSONObject(it).optString("nrdCategory") })
                    }
                }
            } else {
                imported.forEach { record ->
                    if (record.price.isFinite() && record.previous.isFinite() && record.price > 0 && record.previous > record.price &&
                        active(record.from, record.to)) {
                        promotions += promotion(store.code, record.code.ifBlank { record.barcode }, record.name,
                            BigDecimal.valueOf(record.price), BigDecimal.valueOf(record.previous), record.from, record.to, null, record.barcode, null)
                    }
                }
            }
        }
        val fingerprint = MessageDigest.getInstance("SHA-256").digest(promotions.sortedBy { it.id }.toString().toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        val previousOffers = dao.offers().associateBy { it.id }
        val nextOffers = promotions.associate { it.id to CachedOffer(it.id, PromotionCodec.encode(it)) }
        require(nextOffers.size == promotions.size) { "Existem ofertas duplicadas no documento." }
        val initialized = isInitialized()
        val changedOffers = nextOffers.values.filter { previousOffers[it.id]?.payload != it.payload }
        val removedOffers = previousOffers.keys - nextOffers.keys
        val added = addedOfferIds(previousOffers.keys, nextOffers.keys, initialized)
        val previousBatch = JSONArray(dao.metadata("latest_added") ?: "[]").let { array ->
            (0 until array.length()).map { array.getString(it) }.toSet()
        }
        val latestAdded = latestAddedOfferIds(previousBatch, added, nextOffers.keys, initialized)
        // ACP rows, domain offers, revision and latest additions become visible together.
        database.withTransaction {
            delta?.let { update ->
                dao.upsertRows(update.changed)
                update.removed.chunked(500).forEach { dao.deleteRows(it) }
                dao.put(PromotionMetadata("acp_revision", update.revision))
            }
            dao.upsertOffers(changedOffers)
            removedOffers.chunked(500).forEach { dao.deleteOffers(it) }
            if (!initialized || changedOffers.isNotEmpty() || removedOffers.isNotEmpty()) {
                dao.put(PromotionMetadata("latest_added", JSONArray(latestAdded.toList()).toString()))
                val cycle = java.util.UUID.randomUUID().toString()
                dao.put(PromotionMetadata("cycle", cycle))
                if (added.isNotEmpty()) {
                    dao.put(PromotionMetadata("outbox_$cycle", JSONObject().put("createdAt", System.currentTimeMillis())
                        .put("ids", JSONArray(added.toList())).toString()))
                    // Bound persistent events during prolonged remote-settings outages.
                    dao.outbox().sortedBy { JSONObject(it.value).optLong("createdAt") }.dropLast(100)
                        .forEach { dao.deleteMetadata(it.key) }
                }
            }
            dao.put(PromotionMetadata("fingerprint", fingerprint))
            dao.put(PromotionMetadata("initialized", "1"))
        }
        NossaGentePromotionsResult.Success(promotions, fingerprint)
       } catch (cancelled: CancellationException) {
        throw cancelled
       } catch (failure: Exception) {
        NossaGentePromotionsResult.Error(failure.message ?: "Não foi possível atualizar as ofertas do ACP.")
       } finally { onNetwork(false) }
      }
    }

    private fun promotion(store: String, code: String, name: String, price: BigDecimal, previous: BigDecimal,
        from: String?, to: String?, image: String?, barcode: String?, detailsJson: String?, categoryOverride: String? = null): Promotion {
        val category = categoryOverride?.takeIf { it in PromotionCategory.categories } ?: PromotionCategory.forDescription(name)
        val discount = previous.subtract(price).multiply(BigDecimal(100)).divide(previous, 0, RoundingMode.HALF_UP)
        return Promotion(offerIdentity(store, code), "De/Por", category, image, isoDate(from), isoDate(to),
            listOf(PromotionProduct(code, name, price.brl(), previous.brl(), "$discount%", store, image, barcode = barcode, detailsJson = detailsJson)))
    }

    companion object {
        private val syncMutex = Mutex()
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
