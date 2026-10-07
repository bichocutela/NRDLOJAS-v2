package com.example.data.acp

import com.example.BuildConfig
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Only Firebase identity travels to the gateway; no ACP password, cookie or access token. */
internal class AcpGatewayClient(
    private val baseUrl: String = BuildConfig.SUPABASE_URL,
    private val apiKey: String = BuildConfig.SUPABASE_ANON_KEY,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false).followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(75, TimeUnit.SECONDS).build(),
    private val tokenProvider: suspend () -> String? = {
        FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token
    },
    private val deviceTokenProvider: suspend () -> String? = { com.example.data.RestrictedAccessRepository.deviceSessionHeader() },
    private val promotionsOnly: Boolean = false
) {
    suspend fun checkAccess() { execute(JSONObject().put("operation", "access")) }

    suspend fun read(path: String, parameters: List<Pair<String, String>>): JSONObject = execute(
        JSONObject().put("path", path).put("parameters", JSONArray().apply {
            parameters.forEach { (key, value) -> put(JSONArray().put(key).put(value)) }
        })
    )

    private suspend fun execute(payload: JSONObject): JSONObject {
        val deviceToken = deviceTokenProvider()
        val token = tokenProvider()
        return withContext(Dispatchers.IO) {
            if (!baseUrl.startsWith("https://")) throw AcpFailure("Serviço de consulta indisponível nesta versão.")
            val request = Request.Builder().url(baseUrl.trimEnd('/') + "/functions/v1/nrd-price-gateway")
                .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .header("Accept", "application/json")
                .apply {
                    if (promotionsOnly) header("x-nrd-scope", "promotions")
                    if (apiKey.isNotBlank()) header("apikey", apiKey)
                    if (!deviceToken.isNullOrBlank()) header("x-device-session", deviceToken)
                    if (!token.isNullOrBlank()) header("x-firebase-token", token)
                }.build()
            client.newCall(request).execute().use { response ->
                when (response.code) {
                    401 -> throw AcpUnauthorized()
                    403 -> throw AcpFailure(if (promotionsOnly) "Peça ao Mestre para liberar seu acesso às promoções." else "Peça ao Mestre para liberar seu acesso à consulta de preços.")
                    429 -> throw AcpFailure("Muitas consultas. Aguarde um momento e tente novamente.")
                    503 -> throw AcpFailure("O serviço de consulta ainda não está configurado. Avise o Mestre.")
                }
                if (!response.isSuccessful) throw AcpFailure("Não foi possível consultar os produtos. Tente novamente.")
                val raw = response.body?.string().orEmpty()
                try { JSONObject(raw) } catch (_: Exception) {
                    throw AcpFailure("O serviço retornou uma resposta incompatível. Tente novamente.")
                }
            }
        }
    }
}
