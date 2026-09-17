package com.example.data.flyer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

internal object VisualMixReviewStore {
    private const val PREFS = "visual_mix_review"
    private const val KEY_CONFIRMED = "confirmed_keys"
    private const val KEY_DRAFT = "draft_json"

    fun stableKey(offer: FlyerOffer): String {
        val raw = listOf(
            offer.type.name,
            offer.clubCondition.name,
            offer.productCodes.firstOrNull().orEmpty().trim(),
            offer.barcodes.firstOrNull().orEmpty().filter(Char::isDigit),
            normalizeText(offer.sourceDescription),
            offer.regularPrice?.toString().orEmpty(),
            offer.flyerPrice?.toString().orEmpty(),
            offer.secondUnitDiscountPercent?.toString().orEmpty(),
            offer.takeQuantity?.toString().orEmpty(),
            offer.payQuantity?.toString().orEmpty(),
            offer.cashbackPercent?.toString().orEmpty(),
            offer.cashbackValue?.toString().orEmpty(),
            offer.detail
        ).joinToString("|")
        return MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    fun confirmedKeys(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_CONFIRMED, emptySet())
            ?.toSet()
            .orEmpty()

    fun markConfirmed(context: Context, offer: FlyerOffer) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val updated = prefs.getStringSet(KEY_CONFIRMED, emptySet()).orEmpty().toMutableSet()
        updated += stableKey(offer)
        prefs.edit().putStringSet(KEY_CONFIRMED, updated).apply()
    }

    fun markConfirmed(context: Context, offers: Collection<FlyerOffer>) {
        if (offers.isEmpty()) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val updated = prefs.getStringSet(KEY_CONFIRMED, emptySet()).orEmpty().toMutableSet()
        offers.forEach { updated += stableKey(it) }
        prefs.edit().putStringSet(KEY_CONFIRMED, updated).apply()
    }

    fun isConfirmed(context: Context, offer: FlyerOffer): Boolean = stableKey(offer) in confirmedKeys(context)

    fun saveDraft(context: Context, result: FlyerAnalysisResult) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DRAFT, result.toJson().toString())
            .apply()
    }

    fun loadDraft(context: Context): FlyerAnalysisResult? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_DRAFT, null) ?: return null
        return runCatching { JSONObject(raw).toAnalysisResult() }.getOrNull()
    }

    fun hasDraft(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(KEY_DRAFT)

    fun clearDraft(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_DRAFT).apply()
    }

    private fun FlyerAnalysisResult.toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("validFrom", validFrom)
        put("validTo", validTo)
        put("warnings", JSONArray(warnings))
        put("sourceType", sourceType)
        put("sourceLabel", sourceLabel)
        put("offers", JSONArray().apply { offers.forEach { put(it.toJson()) } })
    }

    private fun FlyerOffer.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("type", type.name)
        put("scope", scope.name)
        put("sourceDescription", sourceDescription)
        put("detail", detail)
        put("page", page)
        put("confidence", confidence)
        put("matchStatus", matchStatus.name)
        put("productCodes", JSONArray(productCodes))
        put("barcodes", JSONArray(barcodes))
        put("matchedProductName", matchedProductName)
        put("matchTerms", JSONArray(matchTerms))
        put("flyerPrice", flyerPrice)
        put("regularPrice", regularPrice)
        put("secondUnitDiscountPercent", secondUnitDiscountPercent)
        put("secondUnitPrice", secondUnitPrice)
        put("equivalentUnitPrice", equivalentUnitPrice)
        put("takeQuantity", takeQuantity)
        put("payQuantity", payQuantity)
        put("takeUnit", takeUnit)
        put("payUnit", payUnit)
        put("cashbackPercent", cashbackPercent)
        put("cashbackValue", cashbackValue)
        put("sourceText", sourceText)
        put("reviewed", reviewed)
        put("clubCondition", clubCondition.name)
    }

    private fun JSONObject.toAnalysisResult(): FlyerAnalysisResult {
        val offersArray = optJSONArray("offers") ?: JSONArray()
        val offers = buildList {
            for (i in 0 until offersArray.length()) {
                offersArray.optJSONObject(i)?.toOffer()?.let(::add)
            }
        }
        return FlyerAnalysisResult(
            name = optString("name"),
            validFrom = optString("validFrom").takeIf { it.isNotBlank() && it != "null" },
            validTo = optString("validTo").takeIf { it.isNotBlank() && it != "null" },
            offers = offers,
            warnings = optStringList("warnings"),
            sourceType = optString("sourceType"),
            sourceLabel = optString("sourceLabel")
        )
    }

    private fun JSONObject.toOffer(): FlyerOffer? = runCatching {
        FlyerOffer(
            id = optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
            type = FlyerOfferType.valueOf(getString("type")),
            scope = FlyerOfferScope.valueOf(optString("scope", FlyerOfferScope.PRODUCT.name)),
            sourceDescription = getString("sourceDescription"),
            detail = optString("detail"),
            page = optInt("page", 1),
            confidence = optDouble("confidence", 0.0),
            matchStatus = FlyerMatchStatus.valueOf(optString("matchStatus", FlyerMatchStatus.UNRESOLVED.name)),
            productCodes = optStringList("productCodes"),
            barcodes = optStringList("barcodes"),
            matchedProductName = optString("matchedProductName").takeIf { it.isNotBlank() && it != "null" },
            matchTerms = optStringList("matchTerms"),
            flyerPrice = optNullableDouble("flyerPrice"),
            regularPrice = optNullableDouble("regularPrice"),
            secondUnitDiscountPercent = optNullableDouble("secondUnitDiscountPercent"),
            secondUnitPrice = optNullableDouble("secondUnitPrice"),
            equivalentUnitPrice = optNullableDouble("equivalentUnitPrice"),
            takeQuantity = optNullableDouble("takeQuantity"),
            payQuantity = optNullableDouble("payQuantity"),
            takeUnit = optString("takeUnit").takeIf { it.isNotBlank() && it != "null" },
            payUnit = optString("payUnit").takeIf { it.isNotBlank() && it != "null" },
            cashbackPercent = optNullableDouble("cashbackPercent"),
            cashbackValue = optNullableDouble("cashbackValue"),
            sourceText = optString("sourceText"),
            reviewed = optBoolean("reviewed", false),
            clubCondition = FlyerClubCondition.valueOf(optString("clubCondition", FlyerClubCondition.NOT_INFORMED.name))
        )
    }.getOrNull()

    private fun JSONObject.optStringList(key: String): List<String> {
        val array = optJSONArray(key) ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                array.optString(i).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }

    private fun JSONObject.optNullableDouble(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        return optDouble(key).takeIf { it.isFinite() }
    }
}
