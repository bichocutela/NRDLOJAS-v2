package com.example.data.promotions

import com.example.data.FirebaseService
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.tasks.await

/** One small shared subscription, independent of catalog imports and offer expiry. */
internal object ManualProductPhotos {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private fun key(value: String) = value.trim().trimStart('0').ifBlank { "0" }
    private fun document() = FirebaseFirestore.getInstance().collection("config").document("product_photos")

    private val overrides = callbackFlow<Map<String, String>> {
        if (!FirebaseService.isFirebaseConfigured()) { close(); return@callbackFlow }
        val listener = document().addSnapshotListener { snapshot, error ->
            if (error == null && snapshot != null) {
                val photos = (snapshot.get("photos") as? Map<*, *>)?.entries
                    ?.mapNotNull { (code, url) -> if (code is String && url is String) code to url else null }
                    ?.toMap().orEmpty()
                trySend(photos)
            }
        }
        awaitClose { listener.remove() }
    }
    val updates = combine(overrides, FirebaseService.observeProducts().map { products ->
        products.mapNotNull { product -> product.imageUrl?.takeIf { it.isNotBlank() }
            ?.let { key(product.code) to it } }.toMap()
    }) { manual, legacy -> legacy + manual }
        .shareIn(scope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    // Empty values are intentional removals: stale catalog photos must not reappear.
    fun selected(photos: Map<String, String>, code: String, barcode: String = ""): String? =
        listOf(barcode, code).filter { it.isNotBlank() }.firstNotNullOfOrNull { photos[key(it)] }

    suspend fun save(code: String, name: String, url: String?): Boolean {
        if (!FirebaseService.isFirebaseConfigured() || code.isBlank()) return false
        val normalized = url?.trim().orEmpty()
        require(normalized.isEmpty() || normalized.startsWith("https://") || normalized.startsWith("http://"))
        // Resolve the ERP/EAN association once when editing, never by guessing a name.
        val ean = runCatching { StoreProductImageRepository.shared.lookup(code, name).ean }.getOrNull()
        return try {
            val ref = document()
            FirebaseFirestore.getInstance().runTransaction { transaction ->
                val photos = ((transaction.get(ref).get("photos") as? Map<*, *>)?.entries
                    ?.mapNotNull { (k, v) -> if (k is String && v is String) k to v else null }
                    ?.toMap().orEmpty()).toMutableMap()
                listOfNotNull(code, ean).forEach { photos[key(it)] = normalized }
                transaction.set(ref, mapOf("photos" to photos))
            }.await()
            true
        } catch (_: Exception) { false }
    }
}
