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
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

object DynamicMediaUploader {
    private const val MAX_UPLOAD_BYTES = 80L * 1024L * 1024L
    private val allowedExtensions = setOf(
        "jpg", "jpeg", "png", "webp",
        "mp4", "webm", "mov",
        "mp3", "m4a", "aac", "ogg", "wav",
        "pdf"
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    suspend fun upload(context: Context, uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val detectedMime = resolver.getType(uri)?.trim()?.lowercase(Locale.ROOT)
            val fallbackExtension = uri.lastPathSegment
                ?.substringAfterLast('.', "")
                ?.lowercase(Locale.ROOT)
                ?.takeIf { it.length in 1..8 }
            val extension = detectedMime
                ?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
                ?.lowercase(Locale.ROOT)
                ?: fallbackExtension
                ?: "bin"

            val mimeAllowed = detectedMime?.let {
                it.startsWith("image/") ||
                    it.startsWith("video/") ||
                    it.startsWith("audio/") ||
                    it == "application/pdf" ||
                    it == "application/octet-stream"
            } ?: true

            if (!mimeAllowed || extension !in allowedExtensions) {
                error("Formato não suportado. Use imagem, vídeo, áudio ou PDF.")
            }

            val mime = when {
                detectedMime != null && detectedMime != "application/octet-stream" -> detectedMime
                extension == "pdf" -> "application/pdf"
                extension in setOf("jpg", "jpeg") -> "image/jpeg"
                extension == "png" -> "image/png"
                extension == "webp" -> "image/webp"
                extension == "mp4" -> "video/mp4"
                extension == "webm" -> "video/webm"
                extension == "mov" -> "video/quicktime"
                extension == "mp3" -> "audio/mpeg"
                extension == "m4a" -> "audio/mp4"
                extension == "aac" -> "audio/aac"
                extension == "ogg" -> "audio/ogg"
                extension == "wav" -> "audio/wav"
                else -> "application/octet-stream"
            }

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

                if (!temp.exists() || temp.length() <= 0L) {
                    error("O arquivo selecionado está vazio ou não pôde ser lido.")
                }

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
