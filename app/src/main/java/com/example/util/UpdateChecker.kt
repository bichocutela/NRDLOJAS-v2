package com.example.util

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

sealed interface ReleaseCheckResult {
    data class Success(val tagName: String, val downloadUrl: String) : ReleaseCheckResult
    data class HttpError(
        val responseCode: Int,
        val message: String,
        val rateLimitRemaining: String?,
        val rateLimitReset: String?,
        val retryAfter: String?
    ) : ReleaseCheckResult
    data class NetworkError(val message: String) : ReleaseCheckResult
    data class InvalidResponse(val message: String) : ReleaseCheckResult
}

object UpdateChecker {
    private const val TAG = "UpdateChecker"
    private const val LATEST_RELEASE_URL = "https://api.github.com/repos/bichocutela/NRDLOJAS-v2/releases/latest"
    private const val LATEST_RELEASE_PAGE_URL = "https://github.com/bichocutela/NRDLOJAS-v2/releases/latest"
    private const val LATEST_RELEASE_APK_URL = "https://github.com/bichocutela/NRDLOJAS-v2/releases/latest/download/app-release.apk"

    /**
     * Consulta a última release sem autenticação. A API REST é a fonte primária;
     * em 403/429, usa o redirecionamento público da última release, sem token no APK.
     */
    suspend fun checkLatestRelease(): ReleaseCheckResult {
        val apiResult = checkLatestReleaseFromApi()
        if (apiResult is ReleaseCheckResult.HttpError &&
            (apiResult.responseCode == HttpURLConnection.HTTP_FORBIDDEN || apiResult.responseCode == 429)
        ) {
            Log.w(TAG, "API do GitHub limitada; tentando o fallback público da release mais recente")
            val fallbackResult = checkLatestReleaseFromPublicRedirect()
            if (fallbackResult != null) {
                return fallbackResult
            }
        }
        return apiResult
    }

    private suspend fun checkLatestReleaseFromApi(): ReleaseCheckResult {
        return withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15_000
                    readTimeout = 15_000
                    setRequestProperty("Accept", "application/vnd.github.v3+json")
                    setRequestProperty("User-Agent", "NRDLOJAS-Update-Checker")
                }

                val responseCode = connection.responseCode
                val responseBody = readResponseBody(connection, responseCode)

                if (responseCode != HttpURLConnection.HTTP_OK) {
                    val message = extractGitHubMessage(responseBody)
                        ?: connection.responseMessage
                        ?: "Resposta HTTP sem mensagem"
                    val rateLimitRemaining = connection.getHeaderField("X-RateLimit-Remaining")
                    val rateLimitReset = connection.getHeaderField("X-RateLimit-Reset")
                    val retryAfter = connection.getHeaderField("Retry-After")
                    Log.e(
                        TAG,
                        "GitHub releases HTTP $responseCode: $message; " +
                            "X-RateLimit-Remaining=$rateLimitRemaining, " +
                            "X-RateLimit-Reset=$rateLimitReset, Retry-After=$retryAfter"
                    )
                    return@withContext ReleaseCheckResult.HttpError(
                        responseCode,
                        message,
                        rateLimitRemaining,
                        rateLimitReset,
                        retryAfter
                    )
                }

                if (responseBody.isBlank()) {
                    Log.e(TAG, "GitHub releases HTTP 200 retornou corpo vazio")
                    return@withContext ReleaseCheckResult.InvalidResponse("Corpo da resposta vazio")
                }

                val json = JSONObject(responseBody)
                val tagName = json.optString("tag_name").takeIf { it.isNotBlank() }
                    ?: return@withContext ReleaseCheckResult.InvalidResponse("tag_name ausente na release")
                val assets = json.optJSONArray("assets")
                var downloadUrl: String? = null

                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.optJSONObject(i) ?: continue
                        if (asset.optString("name") == "app-release.apk") {
                            downloadUrl = asset.optString("browser_download_url")
                                .takeIf { it.isNotBlank() }
                            break
                        }
                    }
                }

                if (downloadUrl == null) {
                    downloadUrl = json.optString("html_url").takeIf { it.isNotBlank() }
                    Log.w(TAG, "app-release.apk não encontrado; usando html_url da release como fallback")
                }

                if (downloadUrl == null) {
                    Log.e(TAG, "Resposta válida, mas sem app-release.apk e sem html_url")
                    return@withContext ReleaseCheckResult.InvalidResponse(
                        "Release sem app-release.apk e sem página de fallback"
                    )
                }

                ReleaseCheckResult.Success(tagName, downloadUrl)
            } catch (exception: java.net.SocketTimeoutException) {
                Log.e(TAG, "Sem resposta do GitHub dentro do timeout", exception)
                ReleaseCheckResult.NetworkError("Tempo limite de conexão com o GitHub excedido")
            } catch (exception: java.net.UnknownHostException) {
                Log.e(TAG, "Sem internet ou host do GitHub indisponível", exception)
                ReleaseCheckResult.NetworkError("Sem internet ou GitHub indisponível")
            } catch (exception: java.io.IOException) {
                Log.e(TAG, "Erro de rede ao consultar o GitHub", exception)
                ReleaseCheckResult.NetworkError("Erro de rede ao consultar o GitHub")
            } catch (exception: Exception) {
                Log.e(TAG, "Resposta inválida do GitHub", exception)
                ReleaseCheckResult.InvalidResponse("Resposta inválida do GitHub: ${exception.message ?: "erro desconhecido"}")
            } finally {
                connection?.disconnect()
            }
        }
    }

    /**
     * O GitHub mantém uma URL pública estável para a última release e para assets
     * anexados. Este caminho não consulta a API REST e só é usado se ela estiver
     * temporariamente limitada.
     */
    private suspend fun checkLatestReleaseFromPublicRedirect(): ReleaseCheckResult? {
        return withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(LATEST_RELEASE_PAGE_URL).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    instanceFollowRedirects = true
                    connectTimeout = 15_000
                    readTimeout = 15_000
                    setRequestProperty("User-Agent", "NRDLOJAS-Update-Checker")
                }

                val responseCode = connection.responseCode
                val finalUrl = connection.url.toString()
                if (responseCode !in 200..299 || !finalUrl.contains("/releases/tag/")) {
                    Log.e(TAG, "Fallback público da release falhou: HTTP $responseCode, URL=$finalUrl")
                    return@withContext null
                }

                connection.inputStream.close()
                val tagName = Uri.parse(finalUrl).lastPathSegment?.takeIf { it.isNotBlank() }
                if (tagName == null) {
                    Log.e(TAG, "Fallback público não retornou uma tag de release: $finalUrl")
                    return@withContext null
                }

                ReleaseCheckResult.Success(tagName, LATEST_RELEASE_APK_URL)
            } catch (exception: Exception) {
                Log.e(TAG, "Fallback público da release indisponível", exception)
                null
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun readResponseBody(connection: HttpURLConnection, responseCode: Int): String {
        val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
        return stream?.bufferedReader()?.use { it.readText() }.orEmpty()
    }

    private fun extractGitHubMessage(responseBody: String): String? {
        if (responseBody.isBlank()) return null
        return runCatching { JSONObject(responseBody).optString("message").takeIf { it.isNotBlank() } }
            .getOrNull()
    }

    fun downloadAndInstallApk(context: Context, url: String, versionTag: String) {
        if (!url.endsWith(".apk")) {
            openReleaseUrl(context, url)
            return
        }
        
        try {
            val fileName = "update_$versionTag.apk"
            val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            val existingFile = File(downloadDir, fileName)
            if (existingFile.exists()) {
                existingFile.delete()
            }

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val uri = Uri.parse(url)
            val request = DownloadManager.Request(uri)
            request.setTitle("Atualização NRDLOJAS")
            request.setDescription("Baixando versão $versionTag")
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            request.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
            
            val downloadId = downloadManager.enqueue(request)
            
            val onComplete = object : BroadcastReceiver() {
                override fun onReceive(ctxt: Context, intent: Intent) {
                    val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    if (downloadId == id) {
                        try {
                            val query = DownloadManager.Query().setFilterById(downloadId)
                            val cursor = downloadManager.query(query)
                            if (cursor != null && cursor.moveToFirst()) {
                                val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                                val reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                                val uriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                                
                                val status = cursor.getInt(statusIndex)
                                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                                    var localUriStr: String? = null
                                    if (uriIndex != -1) {
                                        localUriStr = cursor.getString(uriIndex)
                                    }
                                    
                                    var downloadedFile: File? = null
                                    if (localUriStr != null) {
                                        val localUri = Uri.parse(localUriStr)
                                        if (localUri.scheme == "file") {
                                            downloadedFile = File(localUri.path!!)
                                        }
                                    }
                                    if (downloadedFile == null || !downloadedFile.exists()) {
                                        downloadedFile = File(ctxt.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
                                    }
                                    
                                    installApk(ctxt, downloadedFile)
                                } else if (status == DownloadManager.STATUS_FAILED) {
                                    val reason = if (reasonIndex != -1) cursor.getInt(reasonIndex) else -1
                                    Toast.makeText(ctxt, "Falha no download. Código: $reason", Toast.LENGTH_LONG).show()
                                }
                            }
                            cursor?.close()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        } finally {
                            try {
                                ctxt.unregisterReceiver(this)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
            }
            
            Toast.makeText(context, "Download iniciado...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Erro ao iniciar download", Toast.LENGTH_SHORT).show()
        }
    }

    private fun installApk(context: Context, file: File) {
        try {
            if (!file.exists()) {
                Toast.makeText(context, "Arquivo APK não encontrado", Toast.LENGTH_SHORT).show()
                return
            }
            if (file.length() <= 0) {
                Toast.makeText(context, "Arquivo APK inválido (vazio)", Toast.LENGTH_SHORT).show()
                file.delete()
                return
            }

            val pm = context.packageManager
            val packageInfo = pm.getPackageArchiveInfo(file.absolutePath, 0)
            if (packageInfo == null) {
                Toast.makeText(context, "Pacote corrompido ou inválido", Toast.LENGTH_SHORT).show()
                file.delete()
                return
            }

            if (packageInfo.packageName != "com.aistudio.codigomercado.xzbkql") {
                Toast.makeText(context, "Pacote não pertence ao NRDLOJAS", Toast.LENGTH_SHORT).show()
                file.delete()
                return
            }

            val intent = Intent(Intent.ACTION_VIEW)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            intent.setDataAndType(uri, "application/vnd.android.package-archive")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Erro ao instalar APK", Toast.LENGTH_SHORT).show()
        }
    }

    fun openReleaseUrl(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
