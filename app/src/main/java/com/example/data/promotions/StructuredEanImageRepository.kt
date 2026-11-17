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
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Public product pages only. A search hit never proves the EAN of its photo. */
internal class StructuredEanImageRepository(
    context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build(),
    private val now: () -> Long = System::currentTimeMillis
) {
    private val cache = context.applicationContext.getSharedPreferences("structured_ean_images_v1", Context.MODE_PRIVATE)
    private val locks = Array(32) { Mutex() }
    private val slots = Semaphore(2)
    private val unavailableUntil = java.util.concurrent.ConcurrentHashMap<String, Long>()

    suspend fun lookup(source: String, ean: String?): PromofarmaImageRepository.Lookup = withContext(Dispatchers.IO) {
        val code = ean?.trim().orEmpty()
        if (source !in listOf("raia", "mercadolivre", "cosmos", "carone", "mateus") || !Gtin.valid(code)) return@withContext PromofarmaImageRepository.Lookup(null)
        val key = "$source:$code"
        locks[(key.hashCode() and Int.MAX_VALUE) % locks.size].withLock {
            val entry = cache.getString(key, null)?.let { runCatching { JSONObject(it) }.getOrNull() }
            if (entry != null && entry.optLong("expires") > now()) return@withLock PromofarmaImageRepository.Lookup(
                entry.optString("url").takeIf { it.isNotBlank() }, entry.optLong("retryAt").takeIf { it > 0 })
            unavailableUntil[source]?.takeIf { it > now() }?.let { return@withLock PromofarmaImageRepository.Lookup(null, it) }
            var transient = false
            val image = try { slots.withPermit { fetch(source, code) } }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { transient = true; unavailableUntil[source] = now() + 300_000; null }
            val until = now() + when { transient -> 300_000L; image == null -> 7 * 86_400_000L; else -> 30 * 86_400_000L }
            cache.edit().putString(key, JSONObject().put("url", image.orEmpty()).put("expires", until)
                .put("retryAt", if (transient) until else 0).toString()).apply()
            PromofarmaImageRepository.Lookup(image, until.takeIf { transient })
        }
    }

    private fun page(url: String, source: String): String? {
        client.newCall(Request.Builder().url(url).header("Accept", "text/html")
            .header("User-Agent", "Mozilla/5.0 (compatible; NRDProductImages/1.0)").build()).execute().use { response ->
            if (response.code == 404) return null
            if (!response.isSuccessful) throw IOException("Product source unavailable")
            if (!allowedPage(source, response.request.url.host)) throw IOException("Unexpected redirect")
            val body = response.body ?: throw IOException("Empty page")
            // Bound page memory; blocked or truncated pages are retried, not cached as misses.
            val bytes = boundedBytes(body)
            return bytes.toString(Charsets.UTF_8)
        }
    }

    private fun catalogResponse(request: Request): String? {
        client.newCall(request).execute().use { response ->
            if (response.code == 404) return null
            if (!response.isSuccessful) throw IOException("Product catalog unavailable")
            val target = response.request.url
            if (target.scheme != "https" || target.host != request.url.host) throw IOException("Unexpected catalog redirect")
            return boundedBytes(response.body ?: throw IOException("Empty catalog")).toString(Charsets.UTF_8)
        }
    }

    private fun catalogImage(value: String, source: String): String? {
        val url = value.toHttpUrlOrNull() ?: return null
        if (url.scheme != "https" || url.username.isNotEmpty() || url.password.isNotEmpty()) return null
        val valid = when (source) {
            "carone" -> url.host in setOf("tezegw.vteximg.com.br", "tezegw.vtexassets.com") && url.encodedPath.startsWith("/arquivos/ids/")
            "mateus" -> url.host == "ik.imagekit.io" && url.encodedPath.startsWith("/glm5kjz1tud/products/")
            else -> false
        }
        return url.toString().takeIf { valid }
    }

    private fun caroneImage(ean: String): String? {
        val url = "https://www.carone.com.br/api/catalog_system/pub/products/search".toHttpUrl().newBuilder()
            .addQueryParameter("fq", "alternateIds_Ean:$ean").build()
        val raw = catalogResponse(Request.Builder().url(url).header("Accept", "application/json").build()) ?: return null
        val products = JSONArray(raw)
        val matching = mutableListOf<JSONObject>()
        for (p in 0 until products.length()) {
            val items = products.optJSONObject(p)?.optJSONArray("items") ?: continue
            for (i in 0 until items.length()) {
                items.optJSONObject(i)?.takeIf { it.optString("ean").trim() == ean }?.let(matching::add)
            }
        }
        val images = matching.singleOrNull()?.optJSONArray("images") ?: return null
        return (0 until images.length()).firstNotNullOfOrNull { index ->
            catalogImage(images.optJSONObject(index)?.optString("imageUrl").orEmpty(), "carone")
        }
    }

    private fun mateusImage(ean: String): String? {
        val body = JSONObject().put("facets", JSONArray()).put("params", "query=$ean&hitsPerPage=10&page=0")
            .put("index", "SHOWCASE_catalog_product_api_index_PROD").put("service", "meilisearch")
            .toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url("https://app.mateusmais.com.br/api/products/internal/v1/service/")
            .header("Accept", "application/json").post(body).build()
        val raw = catalogResponse(request) ?: return null
        val hits = JSONObject(raw).optJSONArray("hits") ?: throw IOException("Invalid product catalog")
        val images = mutableSetOf<String>()
        for (i in 0 until hits.length()) {
            val product = hits.optJSONObject(i) ?: continue
            // Search is fuzzy: even a numeric query can return different barcodes.
            if (product.optString("barcode").trim() != ean) continue
            catalogImage(product.optString("image"), "mateus")?.let(images::add)
        }
        return images.singleOrNull()
    }

    private fun cosmosImage(ean: String): String? {
        val url = "https://cdn-cosmos.bluesoft.com.br/products/$ean"
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (response.code == 404) return null
            if (!response.isSuccessful || response.request.url.toString() != url) throw IOException("Image source unavailable")
            val body = response.body ?: throw IOException("Empty image")
            val bytes = boundedBytes(body)
            if (!rasterSignature(bytes)) throw IOException("Response is not a product image")
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth !in 1..10000 || bounds.outHeight !in 1..10000) throw IOException("Invalid image")
            return url
        }
    }

    private fun rasterSignature(bytes: ByteArray): Boolean {
        if (bytes.size < 12) return false
        val jpeg = bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() && bytes[2] == 0xff.toByte()
        val png = bytes.copyOfRange(0, 8).contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a))
        val gif = String(bytes, 0, 6, Charsets.US_ASCII) in setOf("GIF87a", "GIF89a")
        val webp = String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" && String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP"
        return jpeg || png || gif || webp
    }

    private fun boundedBytes(body: okhttp3.ResponseBody): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        val stream = body.byteStream()
        while (output.size() <= 2_000_000) {
            val read = stream.read(buffer, 0, minOf(buffer.size, 2_000_001 - output.size()))
            if (read < 0) break
            output.write(buffer, 0, read)
        }
        if (output.size() > 2_000_000) throw IOException("Product response too large")
        return output.toByteArray()
    }

    private fun fetch(source: String, ean: String): String? {
        if (source == "cosmos") return cosmosImage(ean)
        if (source == "carone") return caroneImage(ean)
        if (source == "mateus") return mateusImage(ean)
        val search = if (source == "raia") "https://www.drogaraia.com.br/search?w=$ean"
            else "https://lista.mercadolivre.com.br/$ean"
        val html = page(search, source) ?: return null
        exactImage(html, ean, source)?.let { return it }
        val links = if (source == "raia") raiaLinks(html) else marketLinks(html)
        if (links.isEmpty() && (source == "mercadolivre" || !html.contains("__NEXT_DATA__")))
            throw IOException("No verifiable search response")
        for (link in links.take(4)) {
            val detail = page(link, source) ?: continue
            val found = exactImage(detail, ean, source)
            if (found != null) return found
        }
        return null
    }

    companion object {
        @Volatile private var instance: StructuredEanImageRepository? = null
        fun get(context: Context): StructuredEanImageRepository = instance ?: synchronized(this) {
            instance ?: StructuredEanImageRepository(context).also { instance = it }
        }
        private fun allowedPage(source: String, host: String) = when (source) {
            "raia" -> host == "www.drogaraia.com.br"
            "mercadolivre" -> host in setOf("www.mercadolivre.com.br", "produto.mercadolivre.com.br", "lista.mercadolivre.com.br")
            else -> false
        }
        private fun safeImage(value: String, source: String): String? {
            val url = value.toHttpUrlOrNull() ?: return null
            val hosts = if (source == "raia") setOf("product-data.raiadrogasil.io", "img.drogaraia.com.br", "img.drogasil.com.br")
                else setOf("http2.mlstatic.com")
            return url.takeIf { it.scheme == "https" && it.host in hosts && it.username.isEmpty() && it.password.isEmpty() }?.toString()
        }
        internal fun exactImage(html: String, ean: String, source: String): String? {
            if (!Gtin.valid(ean)) return null
            val results = mutableSetOf<String>()
            fun visit(node: Any?) {
                when (node) {
                    is JSONArray -> for (i in 0 until node.length()) visit(node.opt(i))
                    is JSONObject -> {
                        val type = node.opt("@type")
                        val product = type == "Product" || (type is JSONArray && (0 until type.length()).any { type.optString(it) == "Product" })
                        if (product && listOf("gtin", "gtin8", "gtin12", "gtin13", "gtin14", "ean").any { node.optString(it).trim() == ean }) {
                            fun image(value: Any?) {
                                when (value) {
                                    is String -> safeImage(value, source)?.let(results::add)
                                    is JSONObject -> image(value.opt("url") ?: value.opt("contentUrl"))
                                    is JSONArray -> if (value.length() > 0) image(value.opt(0))
                                }
                            }
                            image(node.opt("image"))
                        }
                        visit(node.opt("@graph"))
                        if (node.optString("@type") == "ItemList") visit(node.opt("itemListElement"))
                        if (node.optString("@type") == "ListItem") visit(node.opt("item"))
                    }
                }
            }
            Regex("<script\\b[^>]*type\\s*=\\s*[\"']application/ld\\+json[\"'][^>]*>(.*?)</script>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .findAll(html).forEach { match -> runCatching { visit(JSONTokener(match.groupValues[1]).nextValue()) } }
            return results.singleOrNull()
        }
        private fun raiaLinks(html: String): List<String> {
            val raw = Regex("<script\\b[^>]*id=[\"']__NEXT_DATA__[\"'][^>]*>(.*?)</script>", RegexOption.DOT_MATCHES_ALL)
                .find(html)?.groupValues?.get(1) ?: return emptyList()
            val products = runCatching { JSONObject(raw).getJSONObject("props").getJSONObject("pageProps")
                .getJSONObject("pageProps").getJSONObject("results").getJSONArray("products") }.getOrNull() ?: return emptyList()
            return (0 until products.length()).mapNotNull { i ->
                "https://www.drogaraia.com.br".toHttpUrl().resolve(products.optJSONObject(i)?.optString("url").orEmpty())
                    ?.takeIf { it.host == "www.drogaraia.com.br" && it.encodedPath.endsWith(".html") }?.toString()
            }.distinct()
        }
        private fun marketLinks(html: String): List<String> = Regex("href=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
            .findAll(html).mapNotNull { it.groupValues[1].replace("&amp;", "&").toHttpUrlOrNull() }
            .filter { allowedPage("mercadolivre", it.host) && Regex("MLB-?\\d+", RegexOption.IGNORE_CASE).containsMatchIn(it.encodedPath) }
            .map { it.toString() }.distinct().take(4).toList()
    }
}
