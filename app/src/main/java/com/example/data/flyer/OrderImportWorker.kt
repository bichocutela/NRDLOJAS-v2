package com.example.data.flyer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.util.AtomicFile
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.example.MainActivity
import com.example.R
import com.example.util.OrderProcessService
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File

/** The reading job belongs to Android, never to a composable or Activity. */
class OrderImportWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    private val documentId get() = inputData.getString("documentId").orEmpty()
    private val notificationId get() = documentId.hashCode()
    private val manager get() = applicationContext.getSystemService(NotificationManager::class.java)

    private fun notification(task: OrderImportTask, finished: Boolean = false): Notification {
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Leitura de documentos", NotificationManager.IMPORTANCE_LOW)
        )
        val target = Intent(applicationContext, MainActivity::class.java)
            .setAction(OrderProcessService.ACTION_RESUME).putExtra("orderSessionId", documentId)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(applicationContext, notificationId, target,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification_default)
            .setContentTitle(if (finished) if (task.error == null) "Leitura concluída" else "Revisar leitura" else task.name)
            .setContentText(if (finished) task.error ?: (task.name + " • Toque para revisar.")
                else (task.phase + " • " + task.progress + "%"))
            .setContentIntent(pending).setOnlyAlertOnce(true)
            .setOngoing(!finished).setAutoCancel(finished)
            .apply { if (!finished) setProgress(100, task.progress, false) }
            .build()
    }

    private fun foreground(task: OrderImportTask): ForegroundInfo {
        val notice = notification(task)
        return if (Build.VERSION.SDK_INT >= 29)
            ForegroundInfo(notificationId, notice, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(notificationId, notice)
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        foreground(OrderImportTasks.get(applicationContext, documentId)
            ?: OrderImportTask(documentId, "", "Documento"))

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val context = applicationContext
        val initial = OrderImportTasks.get(context, documentId) ?: return@withContext Result.success()
        if (!initial.pending) return@withContext Result.success()
        try {
            val master = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                ?.email?.equals("mestre@nrdlojas.com", true) == true
            if (!master) {
                OrderImportTasks.update(context, documentId) {
                    it.copy(state = "failed", error = "Entre como Mestre para retomar a leitura.", phase = "Aguardando acesso")
                }
                return@withContext Result.failure()
            }
            setForeground(foreground(initial))
            update("Aguardando leitura", initial.progress)
            currentCoroutineContext().ensureActive()
            val directory = OrderImportTasks.directory(context, documentId)
            val source = File(directory, "documento.pdf")
            // Atomic copy preserves even share intents with a temporary URI permission.
            val atomic = AtomicFile(source)
            val existing = runCatching { atomic.openRead().use { it.available() > 0 } }.getOrDefault(false)
            if (!existing) {
                update("Salvando documento", 1)
                val out = atomic.startWrite()
                try {
                    val input = context.contentResolver.openInputStream(Uri.parse(initial.uri))
                        ?: throw IllegalArgumentException("Não foi possível abrir o documento. Importe-o novamente.")
                    input.use {
                        val buffer = ByteArray(8192)
                        var total = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = it.read(buffer)
                            if (count < 0) break
                            total += count
                            require(total <= 25L * 1024 * 1024) { "O documento excede 25 MB." }
                            out.write(buffer, 0, count)
                        }
                    }
                    atomic.finishWrite(out)
                } catch (failure: Throwable) { atomic.failWrite(out); throw failure }
            }
            gate.withPermit {
                currentCoroutineContext().ensureActive()
                val result = FlyerImportEngine.analyzeUri(context, Uri.fromFile(source),
                    OrderImportCheckpoint(File(directory, "checkpoint"))) { phase, percent -> update(phase, percent) }
                currentCoroutineContext().ensureActive()
                if (OrderImportTasks.get(context, documentId)?.pending != true) return@withPermit
                VisualMixReviewStore.saveSession(context, documentId, result.copy(name = initial.name, sourceLabel = initial.name), emptySet())
                val done = OrderImportTasks.update(context, documentId) {
                    it.copy(state = "succeeded", phase = "Leitura concluída", progress = 100, error = null)
                }
                if (done != null && done.state == "succeeded") manager.notify(documentId, notificationId, notification(done, true))
            }
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            val task = OrderImportTasks.update(context, documentId) {
                it.copy(state = "failed", phase = "Leitura interrompida",
                    error = failure.message ?: "Não foi possível ler o documento. Tente retomar.")
            }
            if (task != null && task.state != "cancelled") manager.notify(documentId, notificationId, notification(task, true))
            Result.failure()
        }
    }

    private suspend fun update(phase: String, progress: Int) {
        currentCoroutineContext().ensureActive()
        val task = OrderImportTasks.update(applicationContext, documentId) {
            it.copy(state = "running", phase = phase, progress = maxOf(it.progress, progress), error = null)
        } ?: throw CancellationException("Documento removido")
        if (task.state == "cancelled") throw CancellationException("Leitura cancelada")
        setProgress(workDataOf("phase" to phase, "percent" to task.progress))
        setForeground(foreground(task))
    }

    companion object {
        private const val CHANNEL = "order_import_reading"
        private val gate = Semaphore(2)
    }
}
