package com.example.data

import com.example.BuildConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class StoreEncarte(val id: Long, val name: String, val startDate: String, val endDate: String,
    val pdf: String, val image: String) {
    fun isActive(now: Long = System.currentTimeMillis()): Boolean {
        fun parse(value: String): Long? = runCatching {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT).apply {
                timeZone = TimeZone.getTimeZone("America/Fortaleza"); isLenient = false
            }.parse(value)?.time
        }.getOrNull()
        val start = parse(startDate) ?: return false
        val end = parse(endDate) ?: return false
        return now in start..end
    }
    val validity: String get() = "${startDate.substring(8, 10)}/${startDate.substring(5, 7)} a ${endDate.substring(8, 10)}/${endDate.substring(5, 7)}"
}

object EncartesRepository {
    private val client = OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS).callTimeout(45, TimeUnit.SECONDS)
        .followRedirects(false).build()

    suspend fun load(): List<StoreEncarte> = suspendCancellableCoroutine { continuation ->
        val request = Request.Builder().url(BuildConfig.SUPABASE_URL.trimEnd('/') + "/functions/v1/nrd-encartes")
            .header("Authorization", "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
            .header("apikey", BuildConfig.SUPABASE_ANON_KEY).header("Accept", "application/json").build()
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
            override fun onResponse(call: Call, response: Response) {
                val result = runCatching {
                    response.use {
                        if (!it.isSuccessful) throw IOException("Encartes indisponíveis")
                        val array = JSONObject(it.body?.string().orEmpty()).getJSONArray("tabloids")
                        (0 until array.length()).mapNotNull { index ->
                            val item = array.getJSONObject(index)
                            val encarte = StoreEncarte(item.getLong("id"), item.getString("name"),
                                item.getString("startDate"), item.getString("endDate"), item.getString("uri"), item.getString("image"))
                            encarte.takeIf { e -> e.isActive() && safeAsset(e.pdf, ".pdf") && safeAsset(e.image, ".png") }
                        }.distinctBy { it.id }.sortedWith(compareByDescending<StoreEncarte> { it.startDate }.thenBy { it.id })
                    }
                }
                if (continuation.isActive) result.fold({ continuation.resume(it) }, { continuation.resumeWithException(it) })
            }
        })
    }
    private fun safeAsset(raw: String, suffix: String): Boolean = runCatching {
        val uri = java.net.URI(raw)
        uri.scheme == "https" && uri.host == "datalakeidever.blob.core.windows.net" &&
            uri.userInfo == null && uri.port == -1 && uri.path.lowercase(Locale.ROOT).endsWith(suffix)
    }.getOrDefault(false)
}
