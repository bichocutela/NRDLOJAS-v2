package com.example.data.promotions

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import com.example.data.ProductStandards

/** Official storefront lookup. Neither photos nor associations are uploaded to NRD's backend. */
internal class StoreProductImageRepository(
    private val client: OkHttpClient = OkHttpClient.Builder().callTimeout(12, TimeUnit.SECONDS).build(),
    private val now: () -> Long = System::currentTimeMillis
) {
    private data class Entry(val url: String?, val until: Long)
    private data class Identity(val code: String, val description: String)
    private val entries = linkedMapOf<Identity, Entry>()
    private val gate = Mutex()
    private var token = ""
    private var tokenUntil = 0L

    suspend fun find(code: String, description: String = ""): String? = withContext(Dispatchers.IO) {
        val key = Identity(code.trim(), description.trim())
        if (!key.code.matches(Regex("[0-9]{1,30}")) && key.description.isBlank()) return@withContext null
        gate.withLock {
            entries[key]?.takeIf { it.until > now() }?.let { return@withLock it.url }
            var failed = false
            val url = try {
                resolve(key)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failed = true
                null
            }
            entries.remove(key)
            while (entries.size >= 256) entries.remove(entries.keys.first())
            entries[key] = Entry(url, now() + when {
                failed -> 60_000L
                url == null -> 3_600_000L
                else -> 86_400_000L
            })
            url
        }
    }

    private fun authenticate(): String {
        if (token.isNotEmpty() && tokenUntil > now()) return token
        // Same public storefront identity used by the existing Promotions catalog resolver.
        val body = JSONObject().put("domain", DOMAIN).put("username", "loja")
            .put("key", PUBLIC_KEY).toString().toRequestBody("application/json".toMediaType())
        val request = request("$BASE/org/52/auth/loja/login").post(body).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Store unavailable")
            val data = JSONObject(response.body?.string().orEmpty()).opt("data")
            val value = when (data) {
                is String -> data
                is org.json.JSONArray -> (0 until data.length()).joinToString("") { data.optString(it) }
                is JSONObject -> data.optString("token")
                else -> ""
            }
            if (value.length < 40) throw IOException("Invalid store session")
            token = value
            tokenUntil = now() + 20 * 60_000L
            return value
        }
    }

    private fun request(url: String) = Request.Builder().url(url)
        .header("Accept", "application/json").header("DomainKey", DOMAIN).header("OrganizationId", "52")

    private fun resolve(identity: Identity): String? {
        val records = mutableListOf<JSONObject>()
        if (identity.code.matches(Regex("[0-9]{1,30}"))) {
            records += search(identity.code)
            exactMatches(identity.code, records).takeIf { it.isNotEmpty() }?.let { return photo(it) }
        }
        if (identity.description.isBlank()) return null
        records += search(identity.description)
        val unique = records.distinctBy { it.optString("produto_id").ifBlank { it.toString() } }
        exactMatches(identity.code, unique).takeIf { it.isNotEmpty() }?.let { return photo(it) }
        val query = nameTokens(identity.description)
        if (query.isEmpty()) return null
        val numbers = query.filter { it.any(Char::isDigit) }.toSet()
        val ranked = unique.filter { candidate ->
            val barcode = candidate.optString("codigo_barras")
            // A description must never overrule two conflicting, valid GTINs.
            !(Gtin.valid(identity.code) && Gtin.valid(barcode) && !sameBarcode(identity.code, barcode))
        }.mapNotNull { candidate ->
            val tokens = nameTokens(candidate.optString("descricao"))
            if (tokens.isEmpty() || tokens.filter { it.any(Char::isDigit) }.toSet() != numbers) return@mapNotNull null
            val intersection = query.intersect(tokens).size.toDouble()
            val score = 0.65 * intersection / query.union(tokens).size + 0.35 * intersection / query.size
            candidate to score
        }.sortedByDescending { it.second }
        val best = ranked.firstOrNull() ?: return null
        val margin = best.second - (ranked.getOrNull(1)?.second ?: 0.0)
        return if (best.second >= 0.90 && margin >= 0.08) photo(listOf(best.first)) else null
    }

    private fun exactMatches(code: String, records: List<JSONObject>): List<JSONObject> {
        if (code.isBlank()) return emptyList()
        val internal = records.filter { it.optString("codigo_erp").trim() == code }
        return internal.ifEmpty { records.filter { sameBarcode(code, it.optString("codigo_barras").trim()) } }
    }

    private fun sameBarcode(left: String, right: String): Boolean =
        left.isNotBlank() && (left == right ||
            (left.matches(Regex("[0-9]{8,14}")) && right.matches(Regex("[0-9]{8,14}")) &&
                left.trimStart('0') == right.trimStart('0')))

    private fun nameTokens(name: String): Set<String> {
        val text = ProductStandards.searchNameFrom(ProductStandards.normalizeProductName(name))
        val sizes = Regex("(\\d+(?:[.,]\\d+)?)\\s*(kg|mg|ml|cl|g|l)\\b").replace(text) { match ->
            val unit = match.groupValues[2]
            val factor = when (unit) { "kg", "l" -> "1000"; "mg" -> "0.001"; "cl" -> "10"; else -> "1" }
            val amount = match.groupValues[1].replace(',', '.').toBigDecimal()
                .multiply(factor.toBigDecimal()).stripTrailingZeros().toPlainString()
            " ${if (unit in listOf("kg", "mg", "g")) "mass" else "volume"}${amount} "
        }
        return sizes.split(Regex("[^a-z0-9.]+")).map { it.trim('.') }.filter {
            it.isNotBlank() && it !in setOf("de", "da", "do", "das", "dos", "e")
        }.toSet()
    }

    private fun photo(matches: List<JSONObject>): String? {
        if (matches.size != 1) return null
        val filename = matches.single().optString("imagem").trim()
        if (!filename.matches(Regex("[A-Za-z0-9_-]+\\.(?i:jpg|jpeg|png|webp)"))) return null
        return "$IMAGE_BASE/250x250/$filename"
    }

    private fun search(term: String): List<JSONObject> {
        repeat(2) { attempt ->
            val url = "$BASE/org/52/filial/1/centro_distribuicao/2/loja/buscas/produtos/termo".toHttpUrl()
                .newBuilder().addPathSegment(term.trim().replace(Regex("\\s+"), "+"))
                .addPathSegment("rapida").build()
            val request = request(url.toString()).header("Authorization", "Bearer ${authenticate()}").build()
            client.newCall(request).execute().use { response ->
                if (response.code == 401 && attempt == 0) {
                    token = ""
                    return@use
                }
                if (!response.isSuccessful) throw IOException("Store lookup unavailable")
                val products = JSONObject(response.body?.string().orEmpty())
                    .optJSONObject("data")?.optJSONArray("produtos") ?: throw IOException("Invalid catalog")
                return (0 until products.length()).mapNotNull { products.optJSONObject(it) }
            }
        }
        return emptyList()
    }

    companion object {
        private const val BASE = "https://services.vipcommerce.com.br/api-admin/v1"
        private const val DOMAIN = "nordestaomaisvoce.com.br"
        private const val PUBLIC_KEY = "df072f85df9bf7dd71b6811c34bdbaa4f219d98775b56cff9dfa5f8ca1bf8469"
        private const val IMAGE_BASE = "https://produto-assets-vipcommerce-com-br.br-se1.magaluobjects.com"
        val shared by lazy { StoreProductImageRepository() }
    }
}
