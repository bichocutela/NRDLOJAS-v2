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
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object UpdateChecker {
    /**
     * Verifica a última versão lançada no repositório bichocutela/NRDLOJAS
     * Retorna um Pair com o nome da tag (ex: v1.0.1) e a URL de download, ou null em caso de erro.
     */
    suspend fun checkLatestRelease(): Pair<String, String>? {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("https://api.github.com/repos/bichocutela/NRDLOJAS/releases/latest")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
                connection.setRequestProperty("User-Agent", "NRDLOJAS-Update-Checker")
                
                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    val tagName = json.getString("tag_name")
                    val assets = json.optJSONArray("assets")
                    var downloadUrl = ""
                    
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            val name = asset.getString("name")
                            if (name.endsWith(".apk")) {
                                downloadUrl = asset.getString("browser_download_url")
                                break
                            }
                        }
                    }
                    if (downloadUrl.isEmpty()) {
                        downloadUrl = json.getString("html_url") // fallback
                    }
                    return@withContext Pair(tagName, downloadUrl)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            null
        }
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
