package com.example.data.flyer

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

object FlyerRepository {
    private const val COLLECTION = "flyers"
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
        registration = firestore.collection(COLLECTION)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    lastError = error.message
                    Log.e("FlyerRepository", "Erro ao observar encartes", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val campaigns = snapshot?.documents.orEmpty()
                    .mapNotNull(::campaignFromDocument)
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
        return try {
            FirebaseFirestore.getInstance().collection(COLLECTION).document(campaign.id)
                .set(campaignToMap(campaign))
                .await()
            true
        } catch (error: Exception) {
            lastError = error.message
            Log.e("FlyerRepository", "Erro ao salvar encarte ${campaign.id}", error)
            false
        }
    }

    suspend fun setEnabled(id: String, enabled: Boolean): Boolean {
        if (!hasManagementAccess() || id.isBlank()) return false
        return try {
            FirebaseFirestore.getInstance().collection(COLLECTION).document(id)
                .update(mapOf("enabled" to enabled, "updatedAt" to System.currentTimeMillis()))
                .await()
            true
        } catch (error: Exception) {
            lastError = error.message
            Log.e("FlyerRepository", "Erro ao alterar encarte $id", error)
            false
        }
    }

    suspend fun deleteCampaign(id: String): Boolean {
        if (!hasManagementAccess() || id.isBlank()) return false
        return try {
            FirebaseFirestore.getInstance().collection(COLLECTION).document(id).delete().await()
            true
        } catch (error: Exception) {
            lastError = error.message
            Log.e("FlyerRepository", "Erro ao excluir encarte $id", error)
            false
        }
    }

    /**
     * Mestre/Admin can materialize expiry in Firestore. Search clients do not depend on this:
     * they always apply FlyerCampaign.isActiveAt(), so an expired flyer stops appearing even
     * before this maintenance pass runs.
     */
    suspend fun disableExpiredCampaigns(nowMillis: Long = System.currentTimeMillis()): Int {
        if (!hasManagementAccess()) return 0
        return try {
            val firestore = FirebaseFirestore.getInstance()
            val expired = firestore.collection(COLLECTION).get().await().documents
                .mapNotNull(::campaignFromDocument)
                .filter { it.enabled && it.statusAt(nowMillis) == FlyerCampaignStatus.EXPIRED }
            expired.chunked(400).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { campaign ->
                    batch.update(
                        firestore.collection(COLLECTION).document(campaign.id),
                        mapOf("enabled" to false, "autoDisabledAt" to nowMillis)
                    )
                }
                batch.commit().await()
            }
            expired.size
        } catch (error: Exception) {
            lastError = error.message
            Log.w("FlyerRepository", "Não foi possível materializar vencimentos", error)
            0
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
        "offers" to campaign.offers.map(::offerToMap),
        "updatedAt" to System.currentTimeMillis()
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
        "cashbackValue" to offer.cashbackValue
    )

    private fun campaignFromDocument(document: DocumentSnapshot): FlyerCampaign? {
        val data = document.data ?: return null
        val name = data["name"]?.toString()?.trim().orEmpty()
        val validFrom = data["validFrom"]?.toString()?.trim().orEmpty()
        val validTo = data["validTo"]?.toString()?.trim().orEmpty()
        if (name.isBlank() || parseIsoDate(validFrom) == null || parseIsoDate(validTo) == null) return null
        val offers = (data["offers"] as? List<*>)
            .orEmpty()
            .mapNotNull { it as? Map<*, *> }
            .mapNotNull(::offerFromMap)
        return FlyerCampaign(
            id = data["id"]?.toString()?.takeIf { it.isNotBlank() } ?: document.id,
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
            cashbackValue = number(data["cashbackValue"])
        )
    }

    private fun number(value: Any?): Double? = (value as? Number)?.toDouble()
    private fun stringList(value: Any?): List<String> = (value as? List<*>)
        .orEmpty().mapNotNull { it?.toString()?.trim()?.takeIf(String::isNotEmpty) }

    private inline fun <reified T : Enum<T>> enumValueOrNull(value: String?): T? =
        value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() }
}
