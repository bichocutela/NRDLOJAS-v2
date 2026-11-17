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
    val defaults = listOf("promofarma", "raia", "mercadolivre")
    val labels = mapOf("promofarma" to "PromoFarma", "raia" to "Droga Raia", "mercadolivre" to "Mercado Livre")
    data class Policy(val order: List<String> = defaults, val enabled: Set<String> = defaults.toSet())
    fun parse(order: Any?, enabled: Any?): Policy {
        val list = (order as? List<*>)?.filterIsInstance<String>()?.filter { it in defaults }?.distinct()
        val active = (enabled as? List<*>)?.filterIsInstance<String>()?.filter { it in defaults }?.toSet()
        return Policy(list?.takeIf { it.isNotEmpty() } ?: defaults, active ?: defaults.toSet())
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
