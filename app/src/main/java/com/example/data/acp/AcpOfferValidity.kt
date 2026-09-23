package com.example.data.acp

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest

/** Validade comercial confirmada manualmente pelo Mestre. */
internal data class AcpOfferValidity(
    val startDate: String = "",
    val endDate: String = ""
)

internal object AcpOfferValidityStore {
    private const val COLLECTION = "config"
    private const val DOCUMENT = "acpOfferValidity"
    private const val MASTER_EMAIL = "mestre@nrdlojas.com"

    internal fun keyFor(productName: String, family: AcpOfferFamily): String {
        val raw = productName.trim().lowercase() + "|" + family.name
        return MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    /** One subscription for all offer dates shown in the consultation results. */
    fun observeAll(): Flow<Map<String, AcpOfferValidity>> = callbackFlow {
        val registration = FirebaseFirestore.getInstance()
            .collection(COLLECTION)
            .document(DOCUMENT)
            .addSnapshotListener { snapshot, _ ->
                val values = snapshot?.data.orEmpty().mapNotNull { (field, value) ->
                    val raw = value as? Map<*, *> ?: return@mapNotNull null
                    field to AcpOfferValidity(
                        startDate = raw["startDate"] as? String ?: "",
                        endDate = raw["endDate"] as? String ?: ""
                    )
                }.toMap()
                trySend(values)
            }
        awaitClose { registration.remove() }
    }

    private fun requireMaster() {
        val email = FirebaseAuth.getInstance().currentUser?.email?.trim()?.lowercase()
        if (email != MASTER_EMAIL) throw SecurityException("Somente o Mestre pode alterar a validade das ofertas.")
    }

    fun observe(productName: String, family: AcpOfferFamily): Flow<AcpOfferValidity?> = callbackFlow {
        val field = keyFor(productName, family)
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
        requireMaster()
        val field = keyFor(productName, family)
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
