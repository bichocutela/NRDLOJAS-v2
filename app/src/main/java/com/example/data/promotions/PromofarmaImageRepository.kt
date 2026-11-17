package com.example.data.promotions

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Secondary source only. No name, ERP code, SKU ID or approximate product matching. */
internal class PromofarmaImageRepository(
    context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder().callTimeout(12, TimeUnit.SECONDS).build(),
    private val now: () -> Long = System::currentTimeMillis
) {
    data class Lookup(val url: String?, val retryAt: Long? = null)
    private val cache = context.applicationContext.getSharedPreferences("promofarma_ean_images_v1", Context.MODE_PRIVATE)
    private val locks = Array(32) { Mutex() }
    private val slots = Semaphore(2)

    suspend fun lookup(ean: String?): Lookup = withContext(Dispatchers.IO) {
        val code = ean?.trim().orEmpty()
        if (!Gtin.valid(code)) return@withContext Lookup(null)
        locks[(code.hashCode() and Int.MAX_VALUE) % locks.size].withLock {
            val cached = cache.getString(code, null)?.let { runCatching { JSONObject(it) }.getOrNull() }
            if (cached != null && cached.optLong("expires") > now()) {
                return@withLock Lookup(cached.optString("url").takeIf { it.isNotBlank() },
                    cached.optLong("retryAt").takeIf { it > 0 })
            }
            var transient = false
            val photo = try { slots.withPermit { fetch(code) } }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { transient = true; null }
            val expires = now() + when { transient -> 5 * 60_000L; photo == null -> 7 * 86_400_000L; else -> 30 * 86_400_000L }
            cache.edit().putString(code, JSONObject().put("url", photo.orEmpty())
                .put("expires", expires).put("retryAt", if (transient) expires else 0).toString()).apply()
            Lookup(photo, expires.takeIf { transient })
        }
    }

    private fun fetch(ean: String): String? {
        val url = "https://www.promofarma.com.br/api/catalog_system/pub/products/search".toHttpUrl()
            .newBuilder().addQueryParameter("fq", "alternateIds_Ean:$ean").build()
        client.newCall(Request.Builder().url(url).header("Accept", "application/json").build()).execute().use { response ->
            if (response.code == 404) return null
            if (!response.isSuccessful) throw IOException("Image catalog unavailable")
            val products = JSONArray(response.body?.string().orEmpty())
            val matched = mutableListOf<JSONObject>()
            for (p in 0 until products.length()) {
                val items = products.optJSONObject(p)?.optJSONArray("items") ?: continue
                for (i in 0 until items.length()) {
                    val sku = items.optJSONObject(i) ?: continue
                    if (sku.optString("ean").trim() == ean) matched += sku
                }
            }
            // Require a unique exact SKU, including when a response contains other variations.
            if (matched.size != 1) return null
            val images = matched.single().optJSONArray("images") ?: return null
            for (i in 0 until images.length()) {
                val image = images.optJSONObject(i)?.optString("imageUrl")?.toHttpUrlOrNullSafe() ?: continue
                if (image.scheme == "https" && image.host in setOf("promofarma.vteximg.com.br", "promofarma.vtexassets.com") &&
                    image.username.isEmpty() && image.password.isEmpty() && image.encodedPath.startsWith("/arquivos/ids/")) return image.toString()
            }
            return null
        }
    }

    private fun String.toHttpUrlOrNullSafe() = runCatching { toHttpUrl() }.getOrNull()

    companion object {
        @Volatile private var instance: PromofarmaImageRepository? = null
        fun get(context: Context): PromofarmaImageRepository = instance ?: synchronized(this) {
            instance ?: PromofarmaImageRepository(context).also { instance = it }
        }
    }
}
