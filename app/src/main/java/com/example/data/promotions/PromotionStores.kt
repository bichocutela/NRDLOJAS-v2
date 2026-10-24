package com.example.data.promotions

import com.example.data.StoreCatalog
import com.example.data.flyer.FlyerAnalysisResult
import com.example.data.flyer.FlyerOfferType
import com.example.data.flyer.FlyerClubCondition
import com.google.firebase.auth.FirebaseAuth
import com.example.data.PromotionControlClient
import kotlinx.coroutines.flow.map
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class PromotionStore(
    val code: String,
    val enabled: Boolean = code == "0012",
    val revision: String = "",
    val pages: Int = 0,
    val count: Int = 0,
    val source: String = "",
    val updatedAt: Long = 0
)

@kotlinx.serialization.Serializable
internal data class StorePromotionRecord(
    val code: String,
    val barcode: String,
    val name: String,
    val price: Double,
    val previous: Double,
    val from: String?,
    val to: String?,
    val family: String = "De/Por"
)

/** Publish immutable pages first, then one pointer. Readers never see half an import. */
internal object PromotionStores {
    private val publicationMutex = Mutex()
    private const val config = "config/promotion_stores"
    val defaults get() = StoreCatalog.codes.map { PromotionStore(it) }

    private fun parse(data: Map<String, Any>?): List<PromotionStore> = StoreCatalog.codes.map { code ->
        val raw = data?.get(code) as? Map<*, *>
        PromotionStore(code, raw?.get("enabled") as? Boolean ?: (code == "0012"),
            raw?.get("revision") as? String ?: "", (raw?.get("pages") as? Number)?.toInt() ?: 0,
            (raw?.get("count") as? Number)?.toInt() ?: 0, raw?.get("source") as? String ?: "",
            (raw?.get("updatedAt") as? Number)?.toLong() ?: 0)
    }

    fun observe() = PromotionControlClient.observe(config).map { parse(it) }

    suspend fun read(forceRefresh: Boolean = false): List<PromotionStore> = parse(PromotionControlClient.read(config))

    private fun requireMaster(code: String) {
        check(FirebaseAuth.getInstance().currentUser?.email?.lowercase() == "mestre@nrdlojas.com") {
            "Somente o Mestre pode habilitar lojas."
        }
        require(code in StoreCatalog.codes)
    }

    suspend fun setEnabled(code: String, enabled: Boolean): List<PromotionStore> {
        requireMaster(code)
        val store = read(true).first { it.code == code }
        require(!enabled || code == "0012" || store.count > 0) {
            "Envie e publique o documento Visual Mix desta loja antes de habilitar."
        }
        PromotionControlClient.write(config, mapOf(code to mapOf("enabled" to enabled)), merge = true)
        val updated = read(true)
        check(updated.first { it.code == code }.enabled == enabled) { "Não foi possível confirmar a alteração da loja. Tente novamente." }
        return updated
    }

    fun records(analysis: FlyerAnalysisResult, includeClub: Boolean = true): List<StorePromotionRecord> = analysis.offers
        .filter { (it.type == FlyerOfferType.DE_POR || (includeClub && it.clubCondition == FlyerClubCondition.REQUIRED)) && it.reviewError() == null && it.regularPrice != null && it.flyerPrice != null && it.regularPrice > it.flyerPrice }
        .map { offer ->
            StorePromotionRecord(offer.productCodes.firstOrNull().orEmpty(), offer.barcodes.firstOrNull().orEmpty(),
                offer.sourceDescription, offer.flyerPrice!!, offer.regularPrice!!,
                offer.validFrom ?: analysis.validFrom, offer.validTo ?: analysis.validTo, if(includeClub && offer.clubCondition == FlyerClubCondition.REQUIRED) "Clube" else "De/Por")
        }.distinctBy { listOf(it.code, it.barcode, it.name, it.price, it.previous, it.from, it.to, it.family) }

    suspend fun publish(code: String, analysis: FlyerAnalysisResult): List<PromotionStore> = publicationMutex.withLock {
        requireMaster(code)
        val incoming = records(analysis, includeClub = code != "0012")
        val records = if(code == "0012") incoming else mergeStoreRecords(readRecords(read(true).first { it.code == code }), incoming)
        require(incoming.isNotEmpty()) { "O documento não contém ofertas válidas com código ou EAN." }
        require(records.isNotEmpty()) { "O documento contém somente ofertas vencidas. Confira as datas ou descarte este recebimento." }
        records.firstOrNull { record ->
            (!record.from.isNullOrBlank() && AcpPromotionsRepository.isoDate(record.from) == null) ||
                (!record.to.isNullOrBlank() && AcpPromotionsRepository.isoDate(record.to) == null) ||
                (!record.from.isNullOrBlank() && !record.to.isNullOrBlank() &&
                    (AcpPromotionsRepository.isoDate(record.from) ?: "") > (AcpPromotionsRepository.isoDate(record.to) ?: ""))
        }?.let { record ->
            error("Confira a validade de ${record.name}: ${record.from.orEmpty()} até ${record.to.orEmpty()}. Revise essa oferta no documento e envie novamente.")
        }
        require(records.size <= 10_000) { "O documento excede o limite de 10.000 ofertas." }
        val writer = StorePublicationWriter(
            write = { path, data, merge -> PromotionControlClient.write(path, data, merge) },
            read = { path -> PromotionControlClient.read(path) }
        )
        parse(writer.publish(code, UUID.randomUUID().toString(), records, analysis.sourceLabel, System.currentTimeMillis()))
    }

    suspend fun readRecords(store: PromotionStore): List<StorePromotionRecord> {
        if (store.revision.isBlank()) return emptyList()
        require(store.pages in 1..50)
        return buildList {
            repeat(store.pages) { index ->
                val snapshot = PromotionControlClient.read("config/promotion_${store.code}_${store.revision}_$index")
                val items = snapshot.get("items") as? List<*> ?: error("Documento de promoções incompleto.")
                items.forEach { value ->
                    val raw = value as? Map<*, *> ?: error("Oferta inválida.")
                    add(StorePromotionRecord(raw["code"] as? String ?: "", raw["barcode"] as? String ?: "",
                        raw["name"] as? String ?: "", (raw["price"] as? Number)?.toDouble() ?: 0.0,
                        (raw["previous"] as? Number)?.toDouble() ?: 0.0,
                        raw["from"] as? String, raw["to"] as? String, raw["family"] as? String ?: "De/Por"))
                }
            }
        }.also { require(it.size == store.count) { "Documento de promoções incompleto." } }
    }
}

/** Daily reports are deltas; a future campaign must not remove the current campaign. */
internal fun mergeStoreRecords(previous: List<StorePromotionRecord>, incoming: List<StorePromotionRecord>, now: Long = System.currentTimeMillis()): List<StorePromotionRecord> {
    val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).apply {
        timeZone = java.util.TimeZone.getTimeZone("America/Fortaleza")
    }.format(java.util.Date(now))
    fun key(item: StorePromotionRecord) = listOf(item.code.ifBlank { item.barcode }, item.family, AcpPromotionsRepository.isoDate(item.from))
    val merged = linkedMapOf<List<String?>, StorePromotionRecord>()
    (previous + incoming).forEach { item ->
        val end = AcpPromotionsRepository.isoDate(item.to)
        if(end == null || end >= today) merged[key(item)] = item
    }
    return merged.values.toList()
}
