package com.example.data

import com.example.BuildConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class NordestaoStoreOffer(
    val id: String, val description: String, val code: String, val category: String, val image: String,
    val priceCents: Long, val originalPriceCents: Long, val showPrice: Boolean, val discount: Int,
    val cashback: String, val maxAmount: Double, val tag: String, val startsAt: String, val endsAt: String,
    val validity: String, val conditions: String, val activationRequired: Boolean,
    val images: List<String> = emptyList()
) {
    fun imageCandidates(): List<String> = (images + image).map { it.trim() }
        .filter { it.startsWith("https://") && !it.contains("semImagem",ignoreCase=true) }.distinct()
    fun active(now: Long = System.currentTimeMillis()): Boolean = runCatching {
        val parser = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC"); isLenient = false
        }
        parser.parse(startsAt)!!.time <= now && parser.parse(endsAt)!!.time >= now
    }.getOrDefault(false)
    fun matches(query: String): Boolean {
        fun normalized(value: String) = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
        return normalized("$description $code").contains(normalized(query.trim()))
    }
}
data class NordestaoStoreOffersSnapshot(val storeId: Long, val offers: List<NordestaoStoreOffer>, val fetchedAt: String, val stale: Boolean)
object NordestaoStoreOffersCodec {
    fun encode(snapshot: NordestaoStoreOffersSnapshot): String {
        val items = org.json.JSONArray()
        snapshot.offers.forEach { item ->
            val value = JSONObject()
            value.put("id", item.id).put("description", item.description).put("code", item.code).put("category", item.category)
                .put("image", item.image).put("priceCents", item.priceCents).put("originalPriceCents", item.originalPriceCents)
                .put("images", org.json.JSONArray(item.images))
                .put("showPrice", item.showPrice).put("discount", item.discount).put("cashback", item.cashback)
                .put("maxAmount", item.maxAmount).put("tag", item.tag).put("startsAt", item.startsAt).put("endsAt", item.endsAt)
                .put("validity", item.validity).put("conditions", item.conditions).put("activationRequired", item.activationRequired)
            items.put(value)
        }
        return JSONObject().put("storeId", snapshot.storeId).put("offers", items).put("fetchedAt", snapshot.fetchedAt).put("stale", true).toString()
    }
    fun decode(raw: String, requestedStoreId: Long): NordestaoStoreOffersSnapshot {
        val root = JSONObject(raw)
        require(root.getLong("storeId") == requestedStoreId && requestedStoreId > 0) { "Loja incorreta" }
        val items = root.getJSONArray("offers")
        val seen = mutableSetOf<String>()
        val offers = (0 until items.length()).map { index ->
            val item = items.getJSONObject(index)
            val id = item.getString("id")
            require(id.isNotBlank() && seen.add(id))
            val price = item.getLong("priceCents"); val original = item.getLong("originalPriceCents")
            require(price >= 0 && original >= 0)
            NordestaoStoreOffer(id, item.getString("description"), item.optString("code"), item.getString("category"),
                item.optString("image").takeIf { it.startsWith("https://") }.orEmpty(), price, original,
                item.getBoolean("showPrice"), item.optInt("discount"), item.optString("cashback"), item.optDouble("maxAmount", 0.0),
                item.optString("tag"), item.getString("startsAt"), item.getString("endsAt"), item.optString("validity"),
                item.optString("conditions"), item.optBoolean("activationRequired"),
                item.optJSONArray("images")?.let { array -> (0 until array.length()).mapNotNull { array.optString(it).takeIf { url -> url.startsWith("https://") } } }.orEmpty())
        }
        return NordestaoStoreOffersSnapshot(requestedStoreId, offers, root.getString("fetchedAt"), root.optBoolean("stale"))
    }
}
object NordestaoStoreOffersRepository {
    private val client = OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS).readTimeout(40, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS).followRedirects(false).build()
    suspend fun load(storeId: Long): NordestaoStoreOffersSnapshot = suspendCancellableCoroutine { continuation ->
        val request = Request.Builder().url(BuildConfig.SUPABASE_URL.trimEnd('/') + "/functions/v1/nrd-store-offers?storeId=$storeId")
            .header("Authorization", "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
            .header("apikey", BuildConfig.SUPABASE_ANON_KEY).header("Accept", "application/json").build()
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { if(continuation.isActive) continuation.resumeWithException(e) }
            override fun onResponse(call: Call, response: Response) {
                val result = runCatching { response.use {
                    if (!it.isSuccessful) throw IOException("Não foi possível atualizar as ofertas")
                    NordestaoStoreOffersCodec.decode(it.body?.string().orEmpty(), storeId)
                } }
                if(continuation.isActive) result.fold({ continuation.resume(it) }, { continuation.resumeWithException(it) })
            }
        })
    }
}
