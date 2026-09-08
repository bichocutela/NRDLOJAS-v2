package com.example.data

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Fonte remota das Páginas e Cursos do NRD.
 *
 * O conteúdo global fica no Supabase. O Firebase continua independente para
 * notificações e demais recursos que já o utilizam. Isso evita que a publicação
 * das abas dependa das regras do Firestore.
 */
object DynamicTabsRemoteService {
    private val client = OkHttpClient()
    private const val POLL_INTERVAL_MS = 5_000L

    @Volatile
    var lastError: String? = null
        private set

    private fun endpoint(): String =
        "${BuildConfig.SUPABASE_URL.trimEnd('/')}/rest/v1/dynamic_tabs"

    private fun configured(): Boolean =
        BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()

    private fun requestBuilder(url: String): Request.Builder = Request.Builder()
        .url(url)
        .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
        .addHeader("Authorization", "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")

    suspend fun publish(tabs: List<DynamicTab>): Boolean = withContext(Dispatchers.IO) {
        lastError = null
        if (!configured()) {
            lastError = "Supabase não configurado."
            return@withContext false
        }
        if (tabs.isEmpty()) return@withContext true

        try {
            val payload = JSONArray().apply {
                tabs.forEach { tab ->
                    put(JSONObject().apply {
                        put("id", tab.id)
                        put("title", tab.title)
                        put("type", tab.type)
                        put("content", tab.content)
                        put("display_order", tab.displayOrder)
                    })
                }
            }
            val request = requestBuilder("${endpoint()}?on_conflict=id")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    lastError = "Falha ao publicar no Supabase (HTTP ${response.code})."
                    Log.e("DynamicTabsRemote", "Publicação falhou: HTTP ${response.code}; $body")
                    return@withContext false
                }
            }
            true
        } catch (e: Exception) {
            lastError = e.message ?: "Falha ao publicar no Supabase."
            Log.e("DynamicTabsRemote", "Erro ao publicar páginas e cursos", e)
            false
        }
    }

    suspend fun delete(tab: DynamicTab): Boolean = withContext(Dispatchers.IO) {
        lastError = null
        if (!configured()) {
            lastError = "Supabase não configurado."
            return@withContext false
        }
        try {
            val request = requestBuilder("${endpoint()}?id=eq.${tab.id}")
                .delete()
                .addHeader("Prefer", "return=minimal")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    lastError = "Falha ao excluir no Supabase (HTTP ${response.code})."
                    Log.e("DynamicTabsRemote", "Exclusão falhou: HTTP ${response.code}; $body")
                    return@withContext false
                }
            }
            true
        } catch (e: Exception) {
            lastError = e.message ?: "Falha ao excluir no Supabase."
            Log.e("DynamicTabsRemote", "Erro ao excluir página/curso", e)
            false
        }
    }

    private suspend fun fetchAll(): List<DynamicTab>? = withContext(Dispatchers.IO) {
        if (!configured()) {
            lastError = "Supabase não configurado."
            return@withContext null
        }
        try {
            val request = requestBuilder(
                "${endpoint()}?select=id,title,type,content,display_order&order=display_order.asc,id.asc"
            ).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    lastError = "Falha ao carregar do Supabase (HTTP ${response.code})."
                    Log.e("DynamicTabsRemote", "Leitura falhou: HTTP ${response.code}; $body")
                    return@withContext null
                }
                val array = JSONArray(response.body?.string().orEmpty().ifBlank { "[]" })
                buildList {
                    for (index in 0 until array.length()) {
                        val item = array.optJSONObject(index) ?: continue
                        if (!item.has("id")) continue
                        add(
                            DynamicTab(
                                id = item.optInt("id"),
                                title = item.optString("title"),
                                type = item.optString("type", "text"),
                                content = item.optString("content"),
                                displayOrder = item.optInt("display_order", 0)
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            lastError = e.message ?: "Falha ao carregar do Supabase."
            Log.e("DynamicTabsRemote", "Erro ao carregar páginas e cursos", e)
            null
        }
    }

    /**
     * Mantém a mesma experiência de sincronização automática que a aba já tinha.
     * A leitura é pública e leve; alterações do Mestre chegam aos aparelhos sem
     * depender de uma nova versão do APK.
     */
    fun observe(): Flow<List<DynamicTab>> = flow {
        var previous: List<DynamicTab>? = null
        while (currentCoroutineContext().isActive) {
            val current = fetchAll()
            if (current != null && current != previous) {
                emit(current)
                previous = current
            }
            delay(POLL_INTERVAL_MS)
        }
    }
}
