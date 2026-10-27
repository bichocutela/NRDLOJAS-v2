package com.example.data.promotions

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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
    internal data class Lookup(val url: String?, val retryAt: Long? = null, val ean: String? = null)
    private class Entry {
        val gate = Mutex()
        var result = Lookup(null)
        var until = 0L
    }
    private data class Identity(val code: String, val description: String, val barcode: String)
    private val entries = linkedMapOf<Identity, Entry>()
    private val indexGate = Mutex()
    private val authenticationGate = Mutex()
    private val slots = Semaphore(2)
    private var token = ""
    private var tokenUntil = 0L
    @Volatile private var unavailableUntil = 0L

    suspend fun find(code: String, description: String = "", barcode: String = ""): String? =
        lookup(code, description, barcode).url

    /** No category filter: every visible category shares this cache and bounded request pool. */
    suspend fun lookup(code: String, description: String = "", barcode: String = ""): Lookup = withContext(Dispatchers.IO) {
        val key = Identity(code.trim(), description.trim(), barcode.trim())
        if (listOf(key.code, key.barcode).none { it.matches(Regex("[0-9]{1,30}")) } &&
            key.description.isBlank()) return@withContext Lookup(null)
        val entry = indexGate.withLock {
            entries[key] ?: Entry().also {
                while (entries.size >= 256) entries.remove(entries.keys.first())
                entries[key] = it
            }
        }
        // Only duplicate requests for the same product wait for one another.
        entry.gate.withLock {
            if (entry.until > now()) return@withLock entry.result
            val result = slots.withPermit {
                if (unavailableUntil > now()) Lookup(null, unavailableUntil)
                else try {
                    resolve(key)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    unavailableUntil = now() + 60_000L
                    Lookup(null, unavailableUntil)
                }
            }
            entry.result = result
            entry.until = result.retryAt ?: (now() + if (result.url == null && result.ean == null) 3_600_000L else 86_400_000L)
            result
        }
    }

    private suspend fun authenticate(): String = authenticationGate.withLock {
        if (token.isNotEmpty() && tokenUntil > now()) return@withLock token
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
            return@withLock value
        }
    }

    private fun request(url: String) = Request.Builder().url(url)
        .header("Accept", "application/json").header("DomainKey", DOMAIN).header("OrganizationId", "52")

    private suspend fun resolve(identity: Identity): Lookup {
        val records = mutableListOf<JSONObject>()
        var verifiedEan: String? = null
        fun verifiedBarcode(matches: List<JSONObject>): String? = matches.singleOrNull()
            ?.takeIf { identity.code.isNotBlank() && it.optString("codigo_erp").trim() == identity.code }
            ?.optString("codigo_barras")?.trim()?.takeIf(Gtin::valid)
        // Use both identifiers independently: the ACP internal code and the packaging EAN
        // need not be equal. Never assume a substring search is an exact identifier match.
        val identifiers = listOf(identity.code, identity.barcode)
            .filter { it.matches(Regex("[0-9]{1,30}")) }.distinct()
        for (identifier in identifiers) {
            val found = search(identifier)
            records += found
            val exact = exactMatches(identifier, found)
            if (exact.isNotEmpty()) {
                verifiedEan = verifiedBarcode(exact) ?: verifiedEan
                photo(exact)?.let { return Lookup(it, ean = verifiedEan) }
            }
        }
        if (identity.description.isBlank()) return Lookup(null, ean = verifiedEan)
        records += search(identity.description)
        val unique = records.distinctBy { it.optString("produto_id").ifBlank { it.toString() } }
        exactMatches(identity.code, unique).takeIf { it.isNotEmpty() }?.let {
            return Lookup(photo(it), ean = verifiedBarcode(it) ?: verifiedEan)
        }
        val query = nameTokens(identity.description)
        if (query.isEmpty()) return Lookup(null, ean = verifiedEan)
        val numbers = query.filter { it.any(Char::isDigit) }.toSet()
        val expectedGtin = listOf(identity.barcode, identity.code).firstOrNull(Gtin::valid)
        val ranked = unique.filter { candidate ->
            val barcode = candidate.optString("codigo_barras")
            // A description must never overrule a conflicting, valid GTIN.
            !(expectedGtin != null && Gtin.valid(barcode) && !sameBarcode(expectedGtin, barcode))
        }.mapNotNull { candidate ->
            val tokens = nameTokens(candidate.optString("descricao"))
            if (tokens.isEmpty() || tokens.filter { it.any(Char::isDigit) }.toSet() != numbers) return@mapNotNull null
            val intersection = query.intersect(tokens).size.toDouble()
            val score = 0.65 * intersection / query.union(tokens).size + 0.35 * intersection / query.size
            candidate to score
        }.sortedByDescending { it.second }
        val best = ranked.firstOrNull() ?: return Lookup(null, ean = verifiedEan)
        val margin = best.second - (ranked.getOrNull(1)?.second ?: 0.0)
        return Lookup(if (best.second >= 0.78 && margin >= 0.10) photo(listOf(best.first)) else null,
            ean = verifiedEan)
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
            it.isNotBlank() && it !in setOf("de", "da", "do", "das", "dos", "e", "un", "und", "unid", "unidade", "cada")
        }.toSet()
    }

    private fun photo(matches: List<JSONObject>): String? {
        if (matches.size != 1) return null
        val filename = matches.single().optString("imagem").trim()
        if (!filename.matches(Regex("[A-Za-z0-9_-]+\\.(?i:jpg|jpeg|png|webp)"))) return null
        return "$IMAGE_BASE/250x250/$filename"
    }

    private suspend fun search(term: String): List<JSONObject> {
        repeat(2) { attempt ->
            val url = "$BASE/org/52/filial/1/centro_distribuicao/2/loja/buscas/produtos/termo".toHttpUrl()
                .newBuilder().addPathSegment(term.trim().replace(Regex("\\s+"), "+"))
                .addPathSegment("rapida").build()
            val access = authenticate()
            val request = request(url.toString()).header("Authorization", "Bearer $access").build()
            client.newCall(request).execute().use { response ->
                if (response.code == 401 && attempt == 0) {
                    authenticationGate.withLock { if (token == access) token = "" }
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
