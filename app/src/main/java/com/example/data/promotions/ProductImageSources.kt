package com.example.data.promotions

import com.example.data.FirebaseService
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.tasks.await

internal object ProductImageSources {
    val defaults = listOf("promofarma", "raia", "mercadolivre", "cosmos", "carone", "mateus")
    val labels = mapOf("promofarma" to "PromoFarma", "raia" to "Droga Raia", "mercadolivre" to "Mercado Livre", "cosmos" to "Cosmos", "carone" to "Carone", "mateus" to "Mateus Mais")
    data class Policy(val order: List<String> = defaults, val enabled: Set<String> = defaults.toSet())
    fun parse(order: Any?, enabled: Any?): Policy {
        val list = (order as? List<*>)?.filterIsInstance<String>()?.filter { it in defaults }?.distinct()
        val active = (enabled as? List<*>)?.filterIsInstance<String>()?.filter { it in defaults }?.toSet()
        // Old saved policies predate these integrations. Append new sources while
        // retaining the existing order and explicit disabled choices.
        val additions = if (list != null && list.isNotEmpty()) listOf("carone", "mateus").filter { it !in list } else emptyList()
        return Policy((list?.takeIf { it.isNotEmpty() } ?: defaults) + additions,
            (active ?: defaults.toSet()) + additions)
    }
    data class Selection(val displayed: Boolean, val retryAt: Long? = null)
    suspend fun select(
        policy: Policy,
        ean: String?,
        lookup: suspend (String, String) -> PromofarmaImageRepository.Lookup,
        display: suspend (String) -> Boolean,
        now: () -> Long = System::currentTimeMillis
    ): Selection {
        if (ean == null || !Gtin.valid(ean)) return Selection(false)
        val retries = mutableListOf<Long>()
        for (source in policy.order.filter { it in policy.enabled && it in defaults }) {
            try {
                val result = lookup(source, ean)
                val url = result.url
                if (url != null && display(url)) return Selection(true)
                result.retryAt?.let(retries::add)
                if (url != null) retries += now() + 60_000
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { retries += now() + 300_000 }
        }
        return Selection(false, retries.minOrNull())
    }
    private fun document() = FirebaseFirestore.getInstance().collection("config").document("product_image_sources")
    val updates = callbackFlow {
        if (!FirebaseService.isFirebaseConfigured()) { trySend(Policy()); close(); return@callbackFlow }
        val listener = document().addSnapshotListener { snapshot, error ->
            if (error == null && snapshot != null) trySend(parse(snapshot.get("order"), snapshot.get("enabled")))
        }
        awaitClose { listener.remove() }
    }.shareIn(CoroutineScope(SupervisorJob() + Dispatchers.IO), SharingStarted.WhileSubscribed(5_000), replay = 1)

    suspend fun save(policy: Policy): Boolean = try {
        document().set(mapOf("order" to policy.order, "enabled" to policy.enabled.toList())).await()
        true
    } catch (_: Exception) { false }
}
