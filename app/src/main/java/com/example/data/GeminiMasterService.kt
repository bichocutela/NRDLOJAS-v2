package com.example.data

import com.example.BuildConfig
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Cliente mínimo da Inteligência NRD.
 *
 * A chave do Gemini nunca é enviada ao aplicativo. O APK fala apenas com a
 * Edge Function gemini-master, que valida o Firebase ID token do perfil Mestre
 * e acessa GEMINI_API_KEY no ambiente protegido do Supabase.
 */
object GeminiMasterService {
    data class Reply(
        val text: String,
        val model: String? = null,
        val timestamp: String? = null
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .callTimeout(130, TimeUnit.SECONDS)
        .build()

    suspend fun ping(): Result<Reply> = requestReply(
        JSONObject().put("action", "ping")
    )

    suspend fun ask(message: String): Result<Reply> {
        val clean = message.trim()
        if (clean.isEmpty()) return Result.failure(IllegalArgumentException("Digite uma pergunta."))
        return requestReply(
            JSONObject()
                .put("action", "ask")
                .put("message", clean.take(4000))
        )
    }

    /**
     * Envia somente o texto OCR do encarte. O arquivo original continua no aparelho.
     * O retorno é o objeto `analysis` já saneado pela Edge Function.
     */
    suspend fun analyzeFlyer(sourceName: String, ocrText: String): Result<JSONObject> {
        val cleanText = ocrText.trim()
        if (cleanText.isEmpty()) {
            return Result.failure(IllegalArgumentException("O texto do encarte está vazio."))
        }
        return requestJson(
            JSONObject()
                .put("action", "analyze_flyer")
                .put("sourceName", sourceName.trim().take(120))
                .put("ocrText", cleanText.take(60000))
        ).mapCatching { root ->
            root.optJSONObject("analysis") ?: error("O Gemini não retornou a análise do encarte.")
        }
    }

    private suspend fun requestReply(payload: JSONObject): Result<Reply> = requestJson(payload).mapCatching { json ->
        val text = json.optString("text").trim()
        if (text.isBlank()) error("O Gemini respondeu sem conteúdo.")

        Reply(
            text = text,
            model = json.optString("model").takeIf { it.isNotBlank() },
            timestamp = json.optString("checkedAt").takeIf { it.isNotBlank() }
                ?: json.optString("answeredAt").takeIf { it.isNotBlank() }
        )
    }

    private suspend fun requestJson(payload: JSONObject): Result<JSONObject> = withContext(Dispatchers.IO) {
        runCatching {
            val token = FirebaseAuth.getInstance().currentUser
                ?.getIdToken(false)
                ?.await()
                ?.token
                ?.takeIf { it.isNotBlank() }
                ?: error("Sessão do Mestre expirada. Entre novamente.")

            val supabaseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
            val supabaseKey = BuildConfig.SUPABASE_ANON_KEY.trim()
            if (supabaseUrl.isBlank() || supabaseKey.isBlank()) {
                error("Inteligência NRD não configurada neste build.")
            }

            val request = Request.Builder()
                .url("$supabaseUrl/functions/v1/gemini-master")
                .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .addHeader("Authorization", "Bearer $supabaseKey")
                .addHeader("apikey", supabaseKey)
                .addHeader("x-firebase-token", token)
                .addHeader("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                val json = runCatching { JSONObject(raw) }.getOrNull()
                if (!response.isSuccessful) {
                    val message = json?.optString("error")?.takeIf { it.isNotBlank() }
                    error(message ?: "Falha ao acessar a Inteligência NRD (${response.code}).")
                }
                json ?: error("A Inteligência NRD retornou uma resposta inválida.")
            }
        }
    }
}
