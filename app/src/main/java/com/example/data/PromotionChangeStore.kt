package com.example.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

private const val MAX_SNAPSHOT_ENTRIES = 15_000
private const val MAX_DAILY_CHANGES = 5_000
private const val HISTORY_FILE_NAME = "nossa_gente_offer_history.json.gz"

enum class PromotionChangeType {
    ADDED,
    CHANGED,
    REMOVED
}

data class PromotionOfferSnapshot(
    val storeCode: String,
    val productCode: String,
    val productName: String,
    val category: String,
    val offerPrice: String?,
    val regularPrice: String?,
    val discount: String?,
    val validFrom: String?,
    val validTo: String?,
    val imageUrl: String?,
    val linkUrl: String?
) {
    val identityKey: String
        get() = listOf(storeCode, productCode).joinToString("|")

    val key: String
        get() = listOf(identityKey, validFrom.orEmpty(), validTo.orEmpty()).joinToString("|")
}

data class PromotionChange(
    val stableKey: String,
    val type: PromotionChangeType,
    val storeCode: String,
    val productCode: String,
    val productName: String,
    val category: String,
    val oldOfferPrice: String?,
    val newOfferPrice: String?,
    val oldRegularPrice: String?,
    val newRegularPrice: String?,
    val oldDiscount: String?,
    val newDiscount: String?,
    val oldValidFrom: String?,
    val newValidFrom: String?,
    val oldValidTo: String?,
    val newValidTo: String?,
    val imageUrl: String?,
    val linkUrl: String?,
    val priceChanged: Boolean,
    val validityChanged: Boolean
)

data class PromotionChangeState(
    val dayKey: String,
    val changes: List<PromotionChange>,
    val baselineReady: Boolean,
    val limitedBySafetyCap: Boolean
)

private data class PromotionHistoryState(
    val dayKey: String,
    val snapshot: List<PromotionOfferSnapshot>,
    val changes: List<PromotionChange>
)

class PromotionChangeStore(context: Context) {
    private val historyFile = File(context.applicationContext.filesDir, HISTORY_FILE_NAME)

    suspend fun clear() = withContext(Dispatchers.IO) {
        runCatching { historyFile.delete() }
    }

    suspend fun compareAndSave(promotions: List<Promotion>): PromotionChangeState = withContext(Dispatchers.IO) {
        val dayKey = currentDayKey()
        val currentSnapshot = snapshotEntries(promotions)
        if (currentSnapshot.size > MAX_SNAPSHOT_ENTRIES) {
            val existing = readState()
            return@withContext PromotionChangeState(
                dayKey = dayKey,
                changes = existing?.changes.orEmpty(),
                baselineReady = false,
                limitedBySafetyCap = true
            )
        }

        val previous = readState()
        if (previous == null || previous.snapshot.isEmpty()) {
            writeState(PromotionHistoryState(dayKey, currentSnapshot, emptyList()))
            return@withContext PromotionChangeState(
                dayKey = dayKey,
                changes = emptyList(),
                baselineReady = true,
                limitedBySafetyCap = false
            )
        }

        val delta = calculatePromotionChanges(previous.snapshot, currentSnapshot)
        val mergedChanges = if (previous.dayKey == dayKey) {
            mergeDailyChanges(previous.changes, delta)
        } else {
            delta.take(MAX_DAILY_CHANGES)
        }
        writeState(PromotionHistoryState(dayKey, currentSnapshot, mergedChanges))
        PromotionChangeState(
            dayKey = dayKey,
            changes = mergedChanges,
            baselineReady = true,
            limitedBySafetyCap = false
        )
    }

    private fun snapshotEntries(promotions: List<Promotion>): List<PromotionOfferSnapshot> {
        val byKey = linkedMapOf<String, PromotionOfferSnapshot>()
        promotions.forEach { promotion ->
            val category = promotion.description.trim().ifBlank { "Outras ofertas" }
            promotion.products.forEach { product ->
                val storeCode = product.storeCode?.trim().orEmpty()
                val productCode = product.code.trim()
                val productName = product.name.trim().ifBlank { productCode.ifBlank { "Produto em oferta" } }
                val snapshot = PromotionOfferSnapshot(
                    storeCode = storeCode,
                    productCode = productCode,
                    productName = productName,
                    category = category,
                    offerPrice = product.offerPrice,
                    regularPrice = product.regularPrice,
                    discount = product.discount,
                    validFrom = promotion.validFrom,
                    validTo = promotion.validTo,
                    imageUrl = product.imageUrl ?: promotion.imageUrl,
                    linkUrl = product.linkUrl
                )
                byKey[snapshot.key] = snapshot
            }
        }
        return byKey.values.sortedWith(compareBy({ it.identityKey }, { it.key }))
    }

    private fun mergeDailyChanges(
        previous: List<PromotionChange>,
        delta: List<PromotionChange>
    ): List<PromotionChange> {
        val merged = linkedMapOf<String, PromotionChange>()
        previous.forEach { merged[it.stableKey] = it }
        delta.forEach { merged[it.stableKey] = it }
        return merged.values.toList().takeLast(MAX_DAILY_CHANGES)
    }

    private fun readState(): PromotionHistoryState? = runCatching {
        if (!historyFile.exists() || historyFile.length() <= 0L) return@runCatching null
        val json = GZIPInputStream(BufferedInputStream(FileInputStream(historyFile))).bufferedReader(Charsets.UTF_8).use { it.readText() }
        val root = JSONObject(json)
        PromotionHistoryState(
            dayKey = root.optString("dayKey"),
            snapshot = root.optJSONArray("snapshot")?.toSnapshotEntries().orEmpty(),
            changes = root.optJSONArray("changes")?.toPromotionChanges().orEmpty()
        )
    }.getOrNull()

    private fun writeState(state: PromotionHistoryState) {
        runCatching {
            val temporary = File(historyFile.parentFile, "$HISTORY_FILE_NAME.tmp")
            val root = JSONObject()
                .put("dayKey", state.dayKey)
                .put("snapshot", JSONArray().apply { state.snapshot.forEach { put(it.toJson()) } })
                .put("changes", JSONArray().apply { state.changes.forEach { put(it.toJson()) } })
            GZIPOutputStream(BufferedOutputStream(FileOutputStream(temporary))).bufferedWriter(Charsets.UTF_8).use {
                it.write(root.toString())
            }
            if (!temporary.renameTo(historyFile)) temporary.delete()
        }
    }

    private fun currentDayKey(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
}

internal fun calculatePromotionChanges(
    previous: List<PromotionOfferSnapshot>,
    current: List<PromotionOfferSnapshot>
): List<PromotionChange> {
    if (previous.isEmpty()) return emptyList()
    val oldByKey = previous.associateBy { it.key }
    val currentByKey = current.associateBy { it.key }
    val matchedOldKeys = mutableSetOf<String>()
    val matchedCurrentKeys = mutableSetOf<String>()
    val changes = mutableListOf<PromotionChange>()

    currentByKey.keys.intersect(oldByKey.keys).forEach { key ->
        val old = oldByKey.getValue(key)
        val now = currentByKey.getValue(key)
        matchedOldKeys += key
        matchedCurrentKeys += key
        if (old != now) changes += toPromotionChange(old, now, PromotionChangeType.CHANGED)
    }

    val unmatchedOld = previous.filter { it.key !in matchedOldKeys }
    val unmatchedCurrent = current.filter { it.key !in matchedCurrentKeys }
    val oldByIdentity = unmatchedOld.groupBy { it.identityKey }
    val currentByIdentity = unmatchedCurrent.groupBy { it.identityKey }

    (oldByIdentity.keys + currentByIdentity.keys).distinct().forEach { identity ->
        val oldItems = oldByIdentity[identity].orEmpty().sortedBy { it.key }
        val currentItems = currentByIdentity[identity].orEmpty().sortedBy { it.key }
        val pairCount = minOf(oldItems.size, currentItems.size)
        repeat(pairCount) { index ->
            changes += toPromotionChange(oldItems[index], currentItems[index], PromotionChangeType.CHANGED)
        }
        oldItems.drop(pairCount).forEach { changes += toPromotionChange(it, null, PromotionChangeType.REMOVED) }
        currentItems.drop(pairCount).forEach { changes += toPromotionChange(null, it, PromotionChangeType.ADDED) }
    }

    return changes.sortedWith(compareBy({ it.storeCode }, { it.productName }, { it.type.name }))
}

private fun toPromotionChange(
    old: PromotionOfferSnapshot?,
    now: PromotionOfferSnapshot?,
    type: PromotionChangeType
): PromotionChange {
    val source = now ?: old ?: error("change_without_entry")
    val priceChanged = old?.let {
        it.offerPrice != now?.offerPrice || it.regularPrice != now?.regularPrice || it.discount != now?.discount
    } ?: false
    val validityChanged = old?.let {
        it.validFrom != now?.validFrom || it.validTo != now?.validTo
    } ?: false
    return PromotionChange(
        stableKey = "${type.name}|${source.key}",
        type = type,
        storeCode = source.storeCode,
        productCode = source.productCode,
        productName = source.productName,
        category = source.category,
        oldOfferPrice = old?.offerPrice,
        newOfferPrice = now?.offerPrice,
        oldRegularPrice = old?.regularPrice,
        newRegularPrice = now?.regularPrice,
        oldDiscount = old?.discount,
        newDiscount = now?.discount,
        oldValidFrom = old?.validFrom,
        newValidFrom = now?.validFrom,
        oldValidTo = old?.validTo,
        newValidTo = now?.validTo,
        imageUrl = now?.imageUrl ?: old?.imageUrl,
        linkUrl = now?.linkUrl ?: old?.linkUrl,
        priceChanged = priceChanged,
        validityChanged = validityChanged
    )
}

private fun PromotionOfferSnapshot.toJson(): JSONObject = JSONObject().apply {
    put("key", key)
    put("identityKey", identityKey)
    put("storeCode", storeCode)
    put("productCode", productCode)
    put("productName", productName)
    put("category", category)
    putNullable("offerPrice", offerPrice)
    putNullable("regularPrice", regularPrice)
    putNullable("discount", discount)
    putNullable("validFrom", validFrom)
    putNullable("validTo", validTo)
    putNullable("imageUrl", imageUrl)
    putNullable("linkUrl", linkUrl)
}

private fun PromotionChange.toJson(): JSONObject = JSONObject().apply {
    put("stableKey", stableKey)
    put("type", type.name)
    put("storeCode", storeCode)
    put("productCode", productCode)
    put("productName", productName)
    put("category", category)
    putNullable("oldOfferPrice", oldOfferPrice)
    putNullable("newOfferPrice", newOfferPrice)
    putNullable("oldRegularPrice", oldRegularPrice)
    putNullable("newRegularPrice", newRegularPrice)
    putNullable("oldDiscount", oldDiscount)
    putNullable("newDiscount", newDiscount)
    putNullable("oldValidFrom", oldValidFrom)
    putNullable("newValidFrom", newValidFrom)
    putNullable("oldValidTo", oldValidTo)
    putNullable("newValidTo", newValidTo)
    putNullable("imageUrl", imageUrl)
    putNullable("linkUrl", linkUrl)
    put("priceChanged", priceChanged)
    put("validityChanged", validityChanged)
}

private fun JSONArray.toSnapshotEntries(): List<PromotionOfferSnapshot> = (0 until length()).mapNotNull { index ->
    optJSONObject(index)?.let { item ->
        PromotionOfferSnapshot(
            storeCode = item.optString("storeCode"),
            productCode = item.optString("productCode"),
            productName = item.optString("productName"),
            category = item.optString("category"),
            offerPrice = item.optNullableString("offerPrice"),
            regularPrice = item.optNullableString("regularPrice"),
            discount = item.optNullableString("discount"),
            validFrom = item.optNullableString("validFrom"),
            validTo = item.optNullableString("validTo"),
            imageUrl = item.optNullableString("imageUrl"),
            linkUrl = item.optNullableString("linkUrl")
        )
    }
}

private fun JSONArray.toPromotionChanges(): List<PromotionChange> = (0 until length()).mapNotNull { index ->
    optJSONObject(index)?.let { item ->
        val type = runCatching { PromotionChangeType.valueOf(item.optString("type")) }.getOrNull() ?: return@let null
        PromotionChange(
            stableKey = item.optString("stableKey"),
            type = type,
            storeCode = item.optString("storeCode"),
            productCode = item.optString("productCode"),
            productName = item.optString("productName"),
            category = item.optString("category"),
            oldOfferPrice = item.optNullableString("oldOfferPrice"),
            newOfferPrice = item.optNullableString("newOfferPrice"),
            oldRegularPrice = item.optNullableString("oldRegularPrice"),
            newRegularPrice = item.optNullableString("newRegularPrice"),
            oldDiscount = item.optNullableString("oldDiscount"),
            newDiscount = item.optNullableString("newDiscount"),
            oldValidFrom = item.optNullableString("oldValidFrom"),
            newValidFrom = item.optNullableString("newValidFrom"),
            oldValidTo = item.optNullableString("oldValidTo"),
            newValidTo = item.optNullableString("newValidTo"),
            imageUrl = item.optNullableString("imageUrl"),
            linkUrl = item.optNullableString("linkUrl"),
            priceChanged = item.optBoolean("priceChanged"),
            validityChanged = item.optBoolean("validityChanged")
        )
    }
}.take(MAX_DAILY_CHANGES)

private fun JSONObject.putNullable(key: String, value: String?) {
    put(key, value ?: JSONObject.NULL)
}

private fun JSONObject.optNullableString(key: String): String? {
    if (!has(key) || isNull(key)) return null
    return optString(key).takeIf { it.isNotBlank() }
}
