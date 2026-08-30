from pathlib import Path

u = Path('app/src/main/java/com/example/util/UpdateChecker.kt')
t = u.read_text(encoding='utf-8')

old = '''                if (downloadUrl == null) {
                    downloadUrl = json.optString("html_url").takeIf { it.isNotBlank() }
                    Log.w(TAG, "app-release.apk não encontrado; usando html_url da release como fallback")
                }

                if (downloadUrl == null) {
                    Log.e(TAG, "Resposta válida, mas sem app-release.apk e sem html_url")
                    return@withContext ReleaseCheckResult.InvalidResponse(
                        "Release sem app-release.apk e sem página de fallback"
                    )
                }
'''
new = '''                if (downloadUrl == null) {
                    Log.e(TAG, "Resposta válida, mas sem app-release.apk")
                    return@withContext ReleaseCheckResult.InvalidResponse(
                        "Release sem o arquivo app-release.apk"
                    )
                }
'''
if old not in t:
    raise SystemExit('fallback html_url nao encontrado')
t = t.replace(old, new, 1)

anchor = '''    fun downloadAndInstallApk(context: Context, url: String, versionTag: String) {
'''
insert = '''    data class ApkDownloadHandle(val id: Long, val filePath: String)
    data class ApkDownloadProgress(
        val status: Int,
        val progressPercent: Int,
        val filePath: String,
        val failureReason: Int? = null
    )

    fun startApkDownload(context: Context, url: String, versionTag: String): ApkDownloadHandle? {
        if (!url.substringBefore('?').endsWith(".apk", ignoreCase = true)) {
            Toast.makeText(context, "Link de atualização inválido", Toast.LENGTH_LONG).show()
            return null
        }
        return try {
            val fileName = "update_$versionTag.apk"
            val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            cleanupOldUpdateApks(downloadDir)
            val file = File(downloadDir, fileName)
            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle("Atualização NRDLOJAS")
                .setDescription("Baixando versão $versionTag")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
            ApkDownloadHandle(manager.enqueue(request), file.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao iniciar download interno", e)
            Toast.makeText(context, "Erro ao iniciar download", Toast.LENGTH_SHORT).show()
            null
        }
    }

    fun queryApkDownload(context: Context, handle: ApkDownloadHandle): ApkDownloadProgress? {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val cursor = manager.query(DownloadManager.Query().setFilterById(handle.id)) ?: return null
        cursor.use {
            if (!it.moveToFirst()) return null
            val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            val done = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val reason = if (status == DownloadManager.STATUS_FAILED) {
                it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
            } else null
            val progress = if (total > 0) ((done * 100L) / total).toInt().coerceIn(0, 100) else 0
            return ApkDownloadProgress(status, progress, handle.filePath, reason)
        }
    }

    fun installDownloadedApk(context: Context, filePath: String) {
        installApk(context, File(filePath))
    }

'''
if 'fun startApkDownload(' not in t:
    if anchor not in t:
        raise SystemExit('downloadAndInstall anchor nao encontrado')
    t = t.replace(anchor, insert + anchor, 1)
u.write_text(t, encoding='utf-8')

p = Path('app/src/main/java/com/example/ui/AboutScreen.kt')
a = p.read_text(encoding='utf-8')
if 'import kotlinx.coroutines.delay' not in a:
    a = a.replace('import kotlinx.coroutines.launch\n', 'import kotlinx.coroutines.launch\nimport kotlinx.coroutines.delay\n', 1)

state_anchor = '''            var updateAvailable by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
'''
state_new = state_anchor + '''            var updateDownloadHandle by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<com.example.util.UpdateChecker.ApkDownloadHandle?>(null) }
            var updateDownloadProgress by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
            var downloadedApkPath by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
            var updateDownloadError by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
'''
if 'updateDownloadHandle' not in a:
    if state_anchor not in a:
        raise SystemExit('estado updateAvailable nao encontrado')
    a = a.replace(state_anchor, state_new, 1)

launch_anchor = '''            androidx.compose.runtime.LaunchedEffect(Unit) {
                when (val releaseResult = com.example.util.UpdateChecker.checkLatestRelease()) {
'''
if launch_anchor not in a:
    raise SystemExit('LaunchedEffect release nao encontrado')

poll_block = '''            androidx.compose.runtime.LaunchedEffect(updateDownloadHandle) {
                val handle = updateDownloadHandle ?: return@LaunchedEffect
                while (true) {
                    val state = com.example.util.UpdateChecker.queryApkDownload(context, handle)
                    if (state != null) {
                        updateDownloadProgress = state.progressPercent
                        when (state.status) {
                            android.app.DownloadManager.STATUS_SUCCESSFUL -> {
                                downloadedApkPath = state.filePath
                                updateDownloadProgress = 100
                                updateDownloadHandle = null
                                break
                            }
                            android.app.DownloadManager.STATUS_FAILED -> {
                                updateDownloadError = "Falha no download. Código: ${state.failureReason ?: -1}"
                                updateDownloadHandle = null
                                break
                            }
                        }
                    }
                    delay(500)
                }
            }

'''
if 'LaunchedEffect(updateDownloadHandle)' not in a:
    idx = a.index(launch_anchor)
    a = a[:idx] + poll_block + a[idx:]

old_button = '''                        Button(
                            onClick = {
                                com.example.util.UpdateChecker.downloadAndInstallApk(
                                    context,
                                    updateUrl,
                                    updateTag
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = updateUrl.isNotBlank()
                        ) {
                            Text("Baixar agora")
                        }
'''
new_button = '''                        if (updateDownloadHandle != null) {
                            LinearProgressIndicator(
                                progress = { updateDownloadProgress / 100f },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Baixando atualização... $updateDownloadProgress%",
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        updateDownloadError?.let {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(it, color = MaterialTheme.colorScheme.error)
                        }
                        Button(
                            onClick = {
                                val readyPath = downloadedApkPath
                                if (readyPath != null) {
                                    com.example.util.UpdateChecker.installDownloadedApk(context, readyPath)
                                } else {
                                    updateDownloadError = null
                                    updateDownloadProgress = 0
                                    updateDownloadHandle = com.example.util.UpdateChecker.startApkDownload(
                                        context,
                                        updateUrl,
                                        updateTag
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = updateUrl.isNotBlank() && updateDownloadHandle == null
                        ) {
                            Text(if (downloadedApkPath != null) "Instalar atualização" else "Baixar atualização")
                        }
'''
if old_button not in a:
    raise SystemExit('botao antigo de baixar nao encontrado')
a = a.replace(old_button, new_button, 1)
p.write_text(a, encoding='utf-8')
print('Atualizador interno restaurado')
