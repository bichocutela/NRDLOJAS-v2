package com.example.data.promotions

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Official storefront lookup. Neither photos nor associations are uploaded to NRD's backend. */
internal class StoreProductImageRepository(
    private val client: OkHttpClient = OkHttpClient.Builder().callTimeout(12, TimeUnit.SECONDS).build(),
    private val now: () -> Long = System::currentTimeMillis
) {
    private data class Entry(val url: String?, val until: Long)
    private val entries = linkedMapOf<String, Entry>()
    private val gate = Mutex()
    private var token = ""
    private var tokenUntil = 0L

    suspend fun find(code: String): String? = withContext(Dispatchers.IO) {
        val key = code.trim()
        if (!key.matches(Regex("[0-9]{1,30}"))) return@withContext null
        gate.withLock {
            entries[key]?.takeIf { it.until > now() }?.let { return@withLock it.url }
            var failed = false
            val url = try {
                lookup(key)
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

    private fun lookup(code: String): String? {
        repeat(2) { attempt ->
            val request = request("$BASE/org/52/filial/1/centro_distribuicao/2/loja/buscas/produtos/termo/$code/rapida")
                .header("Authorization", "Bearer ${authenticate()}").build()
            client.newCall(request).execute().use { response ->
                if (response.code == 401 && attempt == 0) {
                    token = ""
                    return@use
                }
                if (!response.isSuccessful) throw IOException("Store lookup unavailable")
                val products = JSONObject(response.body?.string().orEmpty())
                    .optJSONObject("data")?.optJSONArray("produtos") ?: throw IOException("Invalid catalog")
                val matches = (0 until products.length()).mapNotNull { products.optJSONObject(it) }
                    .filter { it.optString("codigo_erp").trim() == code }
                // A search result alone is not proof of identity. Never use name similarity or product ID.
                if (matches.size != 1) return null
                val filename = matches.single().optString("imagem").trim()
                if (!filename.matches(Regex("[A-Za-z0-9_-]+\\.(?i:jpg|jpeg|png|webp)"))) return null
                return "$IMAGE_BASE/250x250/$filename"
            }
        }
        return null
    }

    companion object {
        private const val BASE = "https://services.vipcommerce.com.br/api-admin/v1"
        private const val DOMAIN = "nordestaomaisvoce.com.br"
        private const val PUBLIC_KEY = "df072f85df9bf7dd71b6811c34bdbaa4f219d98775b56cff9dfa5f8ca1bf8469"
        private const val IMAGE_BASE = "https://produto-assets-vipcommerce-com-br.br-se1.magaluobjects.com"
        val shared by lazy { StoreProductImageRepository() }
    }
}
