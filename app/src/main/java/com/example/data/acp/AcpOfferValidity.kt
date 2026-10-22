package com.example.data.acp

import com.google.firebase.auth.FirebaseAuth
import com.example.data.PromotionControlClient
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.Flow
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
    fun observeAll(): Flow<Map<String, AcpOfferValidity>> = PromotionControlClient.observe("config/$DOCUMENT").map { values ->
        values.mapNotNull { (field, value) ->
            val raw = value as? Map<*, *> ?: return@mapNotNull null
            field to AcpOfferValidity(raw["startDate"] as? String ?: "", raw["endDate"] as? String ?: "")
        }.toMap()
    }

    private fun requireMaster() {
        val email = FirebaseAuth.getInstance().currentUser?.email?.trim()?.lowercase()
        if (email != MASTER_EMAIL) throw SecurityException("Somente o Mestre pode alterar a validade das ofertas.")
    }

    fun observe(productName: String, family: AcpOfferFamily): Flow<AcpOfferValidity?> =
        observeAll().map { it[keyFor(productName, family)] }

    suspend fun save(productName: String, family: AcpOfferFamily, startDate: String, endDate: String) {
        requireMaster()
        val field = keyFor(productName, family)
        PromotionControlClient.write("config/$DOCUMENT", mapOf(field to mapOf("startDate" to startDate, "endDate" to endDate)), merge = true)
    }
}
