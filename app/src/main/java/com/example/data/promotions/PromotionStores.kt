package com.example.data.promotions

import com.example.data.StoreCatalog
import com.example.data.flyer.FlyerAnalysisResult
import com.example.data.flyer.FlyerOfferType
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

internal data class PromotionStore(
    val code: String,
    val enabled: Boolean = code == "0012",
    val revision: String = "",
    val pages: Int = 0,
    val count: Int = 0,
    val source: String = "",
    val updatedAt: Long = 0
)

internal data class StorePromotionRecord(
    val code: String,
    val barcode: String,
    val name: String,
    val price: Double,
    val previous: Double,
    val from: String?,
    val to: String?
)

/** Publish immutable pages first, then one pointer. Readers never see half an import. */
internal object PromotionStores {
    private val db get() = FirebaseFirestore.getInstance()
    private val config get() = db.collection("config").document("promotion_stores")
    val defaults get() = StoreCatalog.codes.map { PromotionStore(it) }

    private fun parse(data: Map<String, Any>?): List<PromotionStore> = StoreCatalog.codes.map { code ->
        val raw = data?.get(code) as? Map<*, *>
        PromotionStore(code, raw?.get("enabled") as? Boolean ?: (code == "0012"),
            raw?.get("revision") as? String ?: "", (raw?.get("pages") as? Number)?.toInt() ?: 0,
            (raw?.get("count") as? Number)?.toInt() ?: 0, raw?.get("source") as? String ?: "",
            (raw?.get("updatedAt") as? Number)?.toLong() ?: 0)
    }

    fun observe() = callbackFlow {
        val listener = config.addSnapshotListener { snapshot, error ->
            if (error == null) trySend(parse(snapshot?.data))
            // Keep the last known configuration on a transient listener error.
        }
        awaitClose { listener.remove() }
    }

    suspend fun read(): List<PromotionStore> = parse(config.get().await().data)

    private fun requireMaster(code: String) {
        check(FirebaseAuth.getInstance().currentUser?.email?.lowercase() == "mestre@nrdlojas.com") {
            "Somente o Mestre pode habilitar lojas."
        }
        require(code in StoreCatalog.codes)
    }

    suspend fun setEnabled(code: String, enabled: Boolean) {
        requireMaster(code)
        db.runTransaction { transaction ->
            val snapshot = transaction.get(config)
            val store = parse(snapshot.data).first { it.code == code }
            require(!enabled || code == "0012" || store.count > 0) {
                "Envie e publique o documento Visual Mix desta loja antes de habilitar."
            }
            transaction.set(config, mapOf(code to mapOf("enabled" to enabled)), SetOptions.merge())
        }.await()
    }

    fun records(analysis: FlyerAnalysisResult): List<StorePromotionRecord> = analysis.offers
        .filter { it.type == FlyerOfferType.DE_POR && it.reviewError() == null }
        .map { offer ->
            StorePromotionRecord(offer.productCodes.firstOrNull().orEmpty(), offer.barcodes.firstOrNull().orEmpty(),
                offer.sourceDescription, offer.flyerPrice!!, offer.regularPrice!!,
                offer.validFrom ?: analysis.validFrom, offer.validTo ?: analysis.validTo)
        }.distinctBy { listOf(it.code, it.barcode, it.name, it.price, it.previous, it.from, it.to) }

    suspend fun publish(code: String, analysis: FlyerAnalysisResult) {
        requireMaster(code)
        val records = records(analysis)
        require(records.isNotEmpty()) { "O documento não contém ofertas De/Por válidas com código ou EAN." }
        require(records.all { record ->
            (record.from.isNullOrBlank() || AcpPromotionsRepository.isoDate(record.from) != null) &&
                (record.to.isNullOrBlank() || AcpPromotionsRepository.isoDate(record.to) != null) &&
                (record.from.isNullOrBlank() || record.to.isNullOrBlank() ||
                    AcpPromotionsRepository.isoDate(record.from)!! <= AcpPromotionsRepository.isoDate(record.to)!!)
        }) { "Confira as datas do documento: há uma validade inválida." }
        require(records.size <= 10_000) { "O documento excede o limite de 10.000 ofertas." }
        val revision = UUID.randomUUID().toString()
        val pages = records.chunked(200)
        // Immutable page IDs prevent a late reader from mixing two revisions.
        pages.forEachIndexed { index, page ->
            db.collection("config").document("promotion_${code}_${revision}_$index").set(mapOf(
                "items" to page.map { mapOf("code" to it.code, "barcode" to it.barcode,
                    "name" to it.name, "price" to it.price, "previous" to it.previous,
                    "from" to it.from, "to" to it.to) }
            )).await()
        }
        config.set(mapOf(code to mapOf("enabled" to true, "revision" to revision,
            "pages" to pages.size, "count" to records.size, "source" to analysis.sourceLabel,
            "updatedAt" to System.currentTimeMillis())), SetOptions.merge()).await()
    }

    suspend fun readRecords(store: PromotionStore): List<StorePromotionRecord> {
        if (store.revision.isBlank()) return emptyList()
        require(store.pages in 1..50)
        return buildList {
            repeat(store.pages) { index ->
                val snapshot = db.collection("config")
                    .document("promotion_${store.code}_${store.revision}_$index").get().await()
                val items = snapshot.get("items") as? List<*> ?: error("Documento de promoções incompleto.")
                items.forEach { value ->
                    val raw = value as? Map<*, *> ?: error("Oferta inválida.")
                    add(StorePromotionRecord(raw["code"] as? String ?: "", raw["barcode"] as? String ?: "",
                        raw["name"] as? String ?: "", (raw["price"] as? Number)?.toDouble() ?: 0.0,
                        (raw["previous"] as? Number)?.toDouble() ?: 0.0,
                        raw["from"] as? String, raw["to"] as? String))
                }
            }
        }.also { require(it.size == store.count) { "Documento de promoções incompleto." } }
    }
}
