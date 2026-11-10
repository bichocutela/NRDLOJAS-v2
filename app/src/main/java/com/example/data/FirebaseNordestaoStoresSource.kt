package com.example.data

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Read-only Firestore snapshot adapter for the future Nossas Lojas rollout.
 * Deliberately not wired to the screen yet: no Firestore reads are added until
 * the publisher, security rules and legacy-installation routing are deployed.
 *
 * The publisher must write config/nordestao_stores with the exact validated
 * JSON contract already consumed by NordestaoStoresCodec.
 */
internal object FirebaseNordestaoStoresSource {
    suspend fun load(): NordestaoStoresSnapshot {
        val snapshot = FirebaseFirestore.getInstance()
            .collection("config").document("nordestao_stores").get().await()
        require(snapshot.exists()) { "Catálogo de lojas ainda não publicado no Firebase" }
        val payload = snapshot.getString("payload")
            ?: error("Catálogo de lojas indisponível")
        require(payload.length <= 250_000) { "Catálogo de lojas excede limite de segurança" }
        return NordestaoStoresCodec.decode(payload)
    }
}
