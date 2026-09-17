package com.example.data.acp

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest

/** Validade comercial confirmada manualmente pelo Mestre. */
data class AcpOfferValidity(
    val startDate: String = "",
    val endDate: String = ""
)

object AcpOfferValidityStore {
    private const val COLLECTION = "config"
    private const val DOCUMENT = "acpOfferValidity"

    private fun key(productName: String, family: AcpOfferFamily): String {
        val raw = productName.trim().lowercase() + "|" + family.name
        return MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    fun observe(productName: String, family: AcpOfferFamily): Flow<AcpOfferValidity?> = callbackFlow {
        val field = key(productName, family)
        val registration = FirebaseFirestore.getInstance()
            .collection(COLLECTION)
            .document(DOCUMENT)
            .addSnapshotListener { snapshot, _ ->
                val raw = snapshot?.get(field) as? Map<*, *>
                trySend(
                    raw?.let {
                        AcpOfferValidity(
                            startDate = it["startDate"] as? String ?: "",
                            endDate = it["endDate"] as? String ?: ""
                        )
                    }
                )
            }
        awaitClose { registration.remove() }
    }

    suspend fun save(productName: String, family: AcpOfferFamily, startDate: String, endDate: String) {
        val field = key(productName, family)
        FirebaseFirestore.getInstance()
            .collection(COLLECTION)
            .document(DOCUMENT)
            .set(
                mapOf(field to mapOf("startDate" to startDate, "endDate" to endDate)),
                com.google.firebase.firestore.SetOptions.merge()
            )
            .await()
    }
}
