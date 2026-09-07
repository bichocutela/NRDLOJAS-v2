package com.example.data

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import com.example.BuildConfig
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

object DynamicMediaUploader {
    private const val MAX_UPLOAD_BYTES = 80L * 1024L * 1024L
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    suspend fun upload(context: Context, uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val mime = resolver.getType(uri) ?: "application/octet-stream"
            val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
                ?: uri.lastPathSegment?.substringAfterLast('.', "")?.takeIf { it.length in 1..8 }
                ?: "bin"
            val temp = File.createTempFile("nrd_dynamic_", ".$extension", context.cacheDir)
            try {
                resolver.openInputStream(uri)?.use { input ->
                    temp.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var total = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            total += read
                            if (total > MAX_UPLOAD_BYTES) {
                                error("Arquivo maior que 80 MB. Use uma URL para arquivos maiores.")
                            }
                            output.write(buffer, 0, read)
                        }
                    }
                } ?: error("Não foi possível ler o arquivo selecionado.")

                val token = FirebaseAuth.getInstance().currentUser
                    ?.getIdToken(false)?.await()?.token
                    ?: error("Sessão do Mestre expirada. Entre novamente.")
                val supabaseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
                val supabaseKey = BuildConfig.SUPABASE_ANON_KEY
                if (supabaseUrl.isBlank() || supabaseKey.isBlank()) {
                    error("Upload remoto não configurado.")
                }

                val remotePath = "dynamic-pages/${System.currentTimeMillis()}_${UUID.randomUUID()}.$extension"
                val body = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("path", remotePath)
                    .addFormDataPart(
                        "file",
                        temp.name,
                        temp.asRequestBody(mime.toMediaTypeOrNull())
                    )
                    .build()
                val request = Request.Builder()
                    .url("$supabaseUrl/functions/v1/upload-image")
                    .post(body)
                    .addHeader("Authorization", "Bearer $supabaseKey")
                    .addHeader("apikey", supabaseKey)
                    .addHeader("x-firebase-token", token)
                    .build()

                client.newCall(request).execute().use { response ->
                    val payload = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val message = runCatching { JSONObject(payload).optString("error") }.getOrNull()
                        error(message?.takeIf { it.isNotBlank() } ?: "Falha no upload (${response.code}).")
                    }
                    JSONObject(payload).optString("url").takeIf { it.isNotBlank() }
                        ?: error("O servidor não retornou a URL do arquivo.")
                }
            } finally {
                temp.delete()
            }
        }
    }
}
