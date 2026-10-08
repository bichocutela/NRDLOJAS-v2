package com.example.data.promotions

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URI
import java.util.concurrent.TimeUnit

internal data class BarcodeImage(val url: String, val source: String, val credit: String)
internal interface BarcodeImageSource { suspend fun find(ean: String, family: FactsFamily): BarcodeImage? }

internal enum class FactsFamily(val domain: String, val credit: String) {
    FOOD("openfoodfacts.org", "Open Food Facts"), BEAUTY("openbeautyfacts.org", "Open Beauty Facts"),
    PET("openpetfoodfacts.org", "Open Pet Food Facts"), PRODUCTS("openproductsfacts.org", "Open Products Facts");
    companion object {
        fun forCategory(category: String): FactsFamily = when (category) {
            "Higiene e beleza" -> BEAUTY
            "Pet" -> PET
            "Limpeza", "Outras ofertas" -> PRODUCTS
            else -> FOOD
        }
    }
}

internal object Gtin {
    fun valid(value: String): Boolean {
        if (value.length !in listOf(8, 12, 13, 14) || value.any { it !in '0'..'9' }) return false
        val sum = value.dropLast(1).reversed().mapIndexed { index, char ->
            (char - '0') * if (index % 2 == 0) 3 else 1
        }.sum()
        return (10 - sum % 10) % 10 == value.last() - '0'
    }
}

internal class OpenFactsImageSource : BarcodeImageSource {
    private val client = OkHttpClient.Builder().callTimeout(12, TimeUnit.SECONDS).build()
    override suspend fun find(ean: String, family: FactsFamily): BarcodeImage? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://world.${family.domain}/api/v3/product/$ean?fields=code,image_front_url,image_url")
            .header("User-Agent", "NRDLOJAS/1.0 (https://github.com/bichocutela/NRDLOJAS-v2)")
            .get().build()
        client.newCall(request).execute().use { response ->
            if (response.code == 404) return@withContext null
            check(response.isSuccessful) { "Catálogo de imagens temporariamente indisponível." }
            val product = JSONObject(response.body?.string().orEmpty()).optJSONObject("product") ?: return@withContext null
            if (product.optString("code").trimStart('0') != ean.trimStart('0')) return@withContext null
            val url = product.optString("image_front_url").ifBlank { product.optString("image_url") }
            val uri = runCatching { URI(url) }.getOrNull() ?: return@withContext null
            if (uri.scheme != "https" || uri.host?.let { it == family.domain || it.endsWith(".${family.domain}") } != true) return@withContext null
            BarcodeImage(url, "https://world.${family.domain}/product/$ean", family.credit)
        }
    }
}

/** Visible cards only. A global gate caps requests below the provider's 15/minute limit. */
internal class ProductImageRepository private constructor(context: Context) {
    private val cache = context.applicationContext.getSharedPreferences("ean_images_v1", Context.MODE_PRIVATE)
    private val source: BarcodeImageSource = OpenFactsImageSource()
    private val gate = Mutex()
    private var lastRequest = -4_100L

    private fun cachedEntry(key: String): JSONObject? =
        cache.getString(key, null)?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?.takeIf { it.optLong("expires") > System.currentTimeMillis() }

    private fun JSONObject.toImage(): BarcodeImage? =
        optString("url").takeIf { it.isNotBlank() }?.let {
            BarcodeImage(it, optString("source"), optString("credit"))
        }

    // Cached images must not wait behind unrelated network requests.
    fun cachedImage(ean: String?, category: String): BarcodeImage? {
        val code = ean?.trim().orEmpty()
        if (!Gtin.valid(code)) return null
        return cachedEntry("${FactsFamily.forCategory(category).name}:$code")?.toImage()
    }

    suspend fun find(ean: String?, category: String): BarcodeImage? = withContext(Dispatchers.IO) {
        val code = ean?.trim().orEmpty()
        if (!Gtin.valid(code)) return@withContext null
        val family = FactsFamily.forCategory(category)
        val key = "${family.name}:$code"
        cachedEntry(key)?.let { return@withContext it.toImage() }
        gate.withLock {
            // A concurrent request may have filled the cache while this one waited.
            cachedEntry(key)?.let { return@withLock it.toImage() }
            delay((4_100L - (SystemClock.elapsedRealtime() - lastRequest)).coerceAtLeast(0))
            lastRequest = SystemClock.elapsedRealtime()
            var transient = false
            val result = try { source.find(code, family) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { transient = true; null }
            val ttl = when { transient -> 5 * 60_000L; result == null -> 7 * 86_400_000L; else -> 365 * 86_400_000L }
            val json = JSONObject().put("url", result?.url.orEmpty()).put("source", result?.source.orEmpty())
                .put("credit", result?.credit.orEmpty()).put("expires", System.currentTimeMillis() + ttl)
            cache.edit().putString(key, json.toString()).apply()
            result
        }
    }
    companion object {
        @Volatile private var instance: ProductImageRepository? = null
        fun get(context: Context): ProductImageRepository = instance ?: synchronized(this) {
            instance ?: ProductImageRepository(context).also { instance = it }
        }
    }
}
