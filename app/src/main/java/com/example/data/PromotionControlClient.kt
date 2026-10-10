package com.example.data

import com.example.BuildConfig
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONArray
import java.util.concurrent.TimeUnit

/** Public configuration and grants use Supabase; Firebase remains the identity provider. */
internal object PromotionControlClient {
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS).build()
    suspend fun request(body: JSONObject, session: Boolean = true): JSONObject {
        val device = if (session) RestrictedAccessRepository.deviceSessionHeader() else null
        val token = FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token
        return withContext(Dispatchers.IO) {
            val request = Request.Builder().url(BuildConfig.SUPABASE_URL.trimEnd('/') + "/functions/v1/nrd-promotion-control")
                .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                .apply { if (token != null) header("x-firebase-token", token); if (device != null) header("x-device-session", device) }
                .post(body.toString().toRequestBody("application/json".toMediaType())).build()
            client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "Não foi possível sincronizar. Tente novamente." }
                JSONObject(response.body?.string().orEmpty())
            }
        }
    }
    suspend fun read(path: String): Map<String, Any> = map(request(JSONObject().put("operation", "read").put("path", path)).getJSONObject("data"))
    suspend fun write(path: String, data: Map<String, Any?>, merge: Boolean = false) {
        request(JSONObject().put("operation", "write").put("path", path).put("data", JSONObject(data)).put("merge", merge))
    }
    fun observe(path: String) = flow {
        while (true) {
            try { emit(read(path)) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* Retain display configuration. */ }
            delay(60_000)
        }
    }
    fun map(value: JSONObject): Map<String, Any> = value.keys().asSequence().mapNotNull { key ->
        val raw = convert(value.get(key)) ?: return@mapNotNull null
        key to raw
    }.toMap()
    private fun convert(value: Any): Any? = when (value) {
        JSONObject.NULL -> null
        is JSONObject -> map(value)
        is JSONArray -> (0 until value.length()).map { convert(value.get(it)) }
        else -> value
    }
}
