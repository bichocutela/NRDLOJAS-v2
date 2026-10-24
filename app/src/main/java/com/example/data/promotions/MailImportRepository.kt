package com.example.data.promotions

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.example.BuildConfig
import com.example.data.flyer.FlyerAnalysisResult
import com.example.data.flyer.FlyerImportEngine
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

internal data class MailImportDocument(val id: String, val filename: String, val storeCode: String)
internal object MailImportRepository {
    private val client = OkHttpClient.Builder().callTimeout(45, TimeUnit.SECONDS).followRedirects(false).build()
    private val endpoint get() = BuildConfig.SUPABASE_URL.trimEnd('/') + "/functions/v1/nrd-mail-import"
    private suspend fun request(body: JSONObject): JSONObject {
        val user = FirebaseAuth.getInstance().currentUser
        require(user?.email?.equals("mestre@nrdlojas.com", true) == true) { "Entre como Mestre." }
        val token = user!!.getIdToken(false).await().token ?: error("Entre novamente como Mestre.")
        return withContext(Dispatchers.IO) {
            client.newCall(Request.Builder().url(endpoint).header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                .header("x-firebase-token", token).post(body.toString().toRequestBody("application/json".toMediaType())).build()).execute().use {
                check(it.isSuccessful) { "Não foi possível acessar os relatórios recebidos (${it.code}). Tente novamente." }
                JSONObject(it.body?.string() ?: error("Resposta vazia"))
            }
        }
    }
    suspend fun setup(context: Context, email: String): String {
        val result = request(JSONObject().put("operation", "setup").put("email", email))
        val config = JSONObject().put("email", result.getString("email")).put("key", result.getString("key")).put("url", endpoint)
        return context.assets.open("mail-import/connector.js").bufferedReader().use { it.readText() }.replace("__NRD_CONFIG__", config.toString())
    }
    suspend fun list(): List<MailImportDocument> {
        val array = request(JSONObject().put("operation", "list")).getJSONArray("documents")
        return (0 until array.length()).map { index -> array.getJSONObject(index).let {
            val code = it.getString("store_code")
            require(code != "0012" && code in com.example.data.StoreCatalog.codes)
            MailImportDocument(it.getString("id"), it.getString("filename"), code)
        } }
    }
    suspend fun analyze(context: Context, document: MailImportDocument): FlyerAnalysisResult {
        require(document.storeCode != "0012")
        val response = request(JSONObject().put("operation", "download").put("id", document.id))
        require(response.getString("store_code") == document.storeCode && response.getString("filename") == document.filename)
        val bytes = Base64.decode(response.getString("pdf_base64"), Base64.DEFAULT)
        require(bytes.size <= 3 * 1024 * 1024)
        return withContext(Dispatchers.IO) {
            val directory = File(context.cacheDir, "mail-import").apply { mkdirs() }
            val file = File(directory, document.id + ".pdf")
            try {
                file.writeBytes(bytes)
                FlyerImportEngine.analyzeVisualMixUri(context, Uri.fromFile(file)).copy(name = document.filename, sourceLabel = document.filename)
            } finally { file.delete() }
        }
    }
    suspend fun finish(document: MailImportDocument, published: Boolean) {
        request(JSONObject().put("operation", if(published) "published" else "reject").put("id", document.id))
    }
}
