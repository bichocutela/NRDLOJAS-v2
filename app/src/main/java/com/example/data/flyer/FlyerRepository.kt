package com.example.data.flyer

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Persiste encartes em /config/flyers, uma área que já faz parte do contrato
 * público do NRD: todos os aparelhos leem config e somente Mestre/Admin escreve.
 * Assim a função não depende da criação/deploy de uma nova regra de coleção.
 */
object FlyerRepository {
    private const val COLLECTION = "config"
    private const val DOCUMENT = "flyers"
    private const val FIELD = "campaigns"

    @Volatile var lastError: String? = null
        private set

    fun observeCampaigns(): Flow<List<FlyerCampaign>> = callbackFlow {
        val firestore = runCatching { FirebaseFirestore.getInstance() }.getOrElse {
            lastError = it.message
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        var registration: ListenerRegistration? = null
        registration = firestore.collection(COLLECTION).document(DOCUMENT)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    lastError = error.message
                    Log.e("FlyerRepository", "Erro ao observar encartes", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val campaigns = campaignMaps(snapshot?.get(FIELD))
                    .mapNotNull(::campaignFromMap)
                    .sortedWith(compareByDescending<FlyerCampaign> { it.validTo }.thenByDescending { it.createdAt })
                trySend(campaigns)
            }
        awaitClose { registration?.remove() }
    }

    suspend fun saveCampaign(campaign: FlyerCampaign): Boolean {
        if (!hasManagementAccess()) {
            lastError = "A importação de encartes exige acesso Mestre ou Admin."
            return false
        }
        return mutateCampaigns("Erro ao salvar encarte ${campaign.id}") { campaigns ->
            val mutable = campaigns.toMutableList()
            val index = mutable.indexOfFirst { it.id == campaign.id }
            if (index >= 0) mutable[index] = campaign else mutable += campaign
            mutable
        }
    }

    suspend fun setEnabled(id: String, enabled: Boolean): Boolean {
        if (!hasManagementAccess() || id.isBlank()) return false
        return mutateCampaigns("Erro ao alterar encarte $id") { campaigns ->
            campaigns.map { if (it.id == id) it.copy(enabled = enabled) else it }
        }
    }

    suspend fun deleteCampaign(id: String): Boolean {
        if (!hasManagementAccess() || id.isBlank()) return false
        return mutateCampaigns("Erro ao excluir encarte $id") { campaigns ->
            campaigns.filterNot { it.id == id }
        }
    }

    /**
     * Materializa a expiração quando um Mestre/Admin abre a tela. A consulta não
     * depende disso: isActiveAt() corta a oferta no aparelho assim que a data acaba.
     */
    suspend fun disableExpiredCampaigns(nowMillis: Long = System.currentTimeMillis()): Int {
        if (!hasManagementAccess()) return 0
        var disabled = 0
        val success = mutateCampaigns("Não foi possível materializar vencimentos") { campaigns ->
            campaigns.map { campaign ->
                if (campaign.enabled && campaign.statusAt(nowMillis) == FlyerCampaignStatus.EXPIRED) {
                    disabled++
                    campaign.copy(enabled = false)
                } else campaign
            }
        }
        return if (success) disabled else 0
    }

    private suspend fun mutateCampaigns(
        logMessage: String,
        transform: (List<FlyerCampaign>) -> List<FlyerCampaign>
    ): Boolean {
        return try {
            val firestore = FirebaseFirestore.getInstance()
            val ref = firestore.collection(COLLECTION).document(DOCUMENT)
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(ref)
                val current = campaignMaps(snapshot.get(FIELD)).mapNotNull(::campaignFromMap)
                val updated = transform(current)
                    .sortedWith(compareByDescending<FlyerCampaign> { it.validTo }.thenByDescending { it.createdAt })
                transaction.set(ref, mapOf(
                    FIELD to updated.map(::campaignToMap),
                    "updatedAt" to System.currentTimeMillis(),
                    "schemaVersion" to 1
                ))
            }.await()
            lastError = null
            true
        } catch (error: Exception) {
            lastError = error.message
            Log.e("FlyerRepository", logMessage, error)
            false
        }
    }

    private fun hasManagementAccess(): Boolean {
        val email = runCatching { FirebaseAuth.getInstance().currentUser?.email.orEmpty() }
            .getOrDefault("")
            .trim()
            .lowercase()
        return email == "admin@nrdlojas.com" || email == "mestre@nrdlojas.com"
    }

    private fun campaignToMap(campaign: FlyerCampaign): Map<String, Any?> = mapOf(
        "id" to campaign.id,
        "name" to campaign.name,
        "sourceType" to campaign.sourceType,
        "sourceLabel" to campaign.sourceLabel,
        "validFrom" to campaign.validFrom,
        "validTo" to campaign.validTo,
        "createdAt" to campaign.createdAt,
        "enabled" to campaign.enabled,
        "offers" to campaign.offers.map(::offerToMap)
    )

    private fun offerToMap(offer: FlyerOffer): Map<String, Any?> = mapOf(
        "id" to offer.id,
        "type" to offer.type.name,
        "scope" to offer.scope.name,
        "sourceDescription" to offer.sourceDescription,
        "detail" to offer.detail,
        "page" to offer.page,
        "confidence" to offer.confidence,
        "matchStatus" to offer.matchStatus.name,
        "productCodes" to offer.productCodes,
        "barcodes" to offer.barcodes,
        "matchedProductName" to offer.matchedProductName,
        "matchTerms" to offer.matchTerms,
        "flyerPrice" to offer.flyerPrice,
        "regularPrice" to offer.regularPrice,
        "secondUnitDiscountPercent" to offer.secondUnitDiscountPercent,
        "secondUnitPrice" to offer.secondUnitPrice,
        "equivalentUnitPrice" to offer.equivalentUnitPrice,
        "takeQuantity" to offer.takeQuantity,
        "payQuantity" to offer.payQuantity,
        "takeUnit" to offer.takeUnit,
        "payUnit" to offer.payUnit,
        "cashbackPercent" to offer.cashbackPercent,
        "cashbackValue" to offer.cashbackValue,
        "sourceText" to offer.sourceText,
        "reviewed" to offer.reviewed,
        "clubCondition" to offer.clubCondition.name
    )

    private fun campaignMaps(value: Any?): List<Map<*, *>> =
        (value as? List<*>).orEmpty().mapNotNull { it as? Map<*, *> }

    private fun campaignFromMap(data: Map<*, *>): FlyerCampaign? {
        val name = data["name"]?.toString()?.trim().orEmpty()
        val validFrom = data["validFrom"]?.toString()?.trim().orEmpty()
        val validTo = data["validTo"]?.toString()?.trim().orEmpty()
        if (name.isBlank() || parseIsoDate(validFrom) == null || parseIsoDate(validTo) == null) return null
        val offers = (data["offers"] as? List<*>)
            .orEmpty()
            .mapNotNull { it as? Map<*, *> }
            .mapNotNull(::offerFromMap)
        return FlyerCampaign(
            id = data["id"]?.toString()?.takeIf { it.isNotBlank() } ?: java.util.UUID.randomUUID().toString(),
            name = name,
            sourceType = data["sourceType"]?.toString().orEmpty(),
            sourceLabel = data["sourceLabel"]?.toString().orEmpty(),
            validFrom = validFrom,
            validTo = validTo,
            createdAt = (data["createdAt"] as? Number)?.toLong() ?: 0L,
            enabled = data["enabled"] as? Boolean ?: true,
            offers = offers
        )
    }

    private fun offerFromMap(data: Map<*, *>): FlyerOffer? {
        val type = enumValueOrNull<FlyerOfferType>(data["type"]?.toString()) ?: return null
        return FlyerOffer(
            id = data["id"]?.toString().orEmpty().ifBlank { java.util.UUID.randomUUID().toString() },
            type = type,
            scope = enumValueOrNull<FlyerOfferScope>(data["scope"]?.toString()) ?: FlyerOfferScope.PRODUCT,
            sourceDescription = data["sourceDescription"]?.toString().orEmpty(),
            detail = data["detail"]?.toString().orEmpty(),
            page = (data["page"] as? Number)?.toInt() ?: 1,
            confidence = (data["confidence"] as? Number)?.toDouble() ?: 0.0,
            matchStatus = enumValueOrNull<FlyerMatchStatus>(data["matchStatus"]?.toString()) ?: FlyerMatchStatus.UNRESOLVED,
            productCodes = stringList(data["productCodes"]),
            barcodes = stringList(data["barcodes"]),
            matchedProductName = data["matchedProductName"]?.toString()?.takeIf { it.isNotBlank() },
            matchTerms = stringList(data["matchTerms"]),
            flyerPrice = number(data["flyerPrice"]),
            regularPrice = number(data["regularPrice"]),
            secondUnitDiscountPercent = number(data["secondUnitDiscountPercent"]),
            secondUnitPrice = number(data["secondUnitPrice"]),
            equivalentUnitPrice = number(data["equivalentUnitPrice"]),
            takeQuantity = number(data["takeQuantity"]),
            payQuantity = number(data["payQuantity"]),
            takeUnit = data["takeUnit"]?.toString()?.takeIf { it.isNotBlank() },
            payUnit = data["payUnit"]?.toString()?.takeIf { it.isNotBlank() },
            cashbackPercent = number(data["cashbackPercent"]),
            cashbackValue = number(data["cashbackValue"]),
            sourceText = data["sourceText"]?.toString().orEmpty(),
            reviewed = data["reviewed"] as? Boolean ?: false,
            clubCondition = enumValueOrNull<FlyerClubCondition>(data["clubCondition"]?.toString()) ?: FlyerClubCondition.NOT_INFORMED
        )
    }

    private fun number(value: Any?): Double? = (value as? Number)?.toDouble()
    private fun stringList(value: Any?): List<String> = (value as? List<*>)
        .orEmpty().mapNotNull { it?.toString()?.trim()?.takeIf(String::isNotEmpty) }

    private inline fun <reified T : Enum<T>> enumValueOrNull(value: String?): T? =
        value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() }
}
