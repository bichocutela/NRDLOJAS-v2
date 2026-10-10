package com.example.data.flyer

import android.content.Context
import android.net.Uri
import androidx.work.*
import org.json.JSONObject
import java.io.File
import java.util.UUID

internal data class OrderImportTask(
    val id: String, val uri: String, val name: String,
    val state: String = "queued", val phase: String = "Preparando documento",
    val progress: Int = 0, val error: String? = null
) {
    val pending get() = state == "queued" || state == "running"
}

internal object OrderImportTasks {
    private fun prefs(context: Context) = context.getSharedPreferences("order_import_tasks", Context.MODE_PRIVATE)
    fun workName(id: String) = "order-import-$id"
    fun directory(context: Context, id: String): File {
        require(UUID.fromString(id).toString() == id)
        return File(context.filesDir, "order_import/$id").also { it.mkdirs() }
    }

    @Synchronized fun all(context: Context): List<OrderImportTask> =
        prefs(context).all.values.filterIsInstance<String>().mapNotNull { raw ->
            runCatching {
                val o = JSONObject(raw)
                OrderImportTask(o.getString("id"), o.getString("uri"), o.getString("name"),
                    o.getString("state"), o.getString("phase"), o.getInt("progress"),
                    o.optString("error").takeIf { it.isNotBlank() && it != "null" })
            }.getOrNull()
        }

    fun get(context: Context, id: String) = all(context).firstOrNull { it.id == id }

    @Synchronized fun save(context: Context, task: OrderImportTask) {
        check(prefs(context).edit().putString(task.id, JSONObject().apply {
            put("id", task.id); put("uri", task.uri); put("name", task.name)
            put("state", task.state); put("phase", task.phase)
            put("progress", task.progress.coerceIn(0, 100)); put("error", task.error)
        }.toString()).commit()) { "Não foi possível salvar o progresso." }
    }

    @Synchronized fun update(context: Context, id: String, change: (OrderImportTask) -> OrderImportTask): OrderImportTask? {
        val task = get(context, id) ?: return null
        if (task.state == "cancelled") return task
        return change(task).also { save(context, it) }
    }

    fun enqueue(context: Context, task: OrderImportTask) {
        val request = OneTimeWorkRequestBuilder<OrderImportWorker>()
            .setInputData(workDataOf("documentId" to task.id))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, java.util.concurrent.TimeUnit.SECONDS)
            .addTag("order-import")
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(workName(task.id), ExistingWorkPolicy.KEEP, request)
    }

    fun start(context: Context, id: String, uri: Uri, name: String) {
        runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val task = OrderImportTask(id, uri.toString(), name)
        save(context, task)
        enqueue(context, task)
    }

    fun retry(context: Context, id: String) {
        val task = get(context, id) ?: return
        val updated = task.copy(state = "queued", phase = "Retomando leitura", error = null)
        save(context, updated)
        enqueue(context, updated)
    }

    fun cancel(context: Context, id: String) {
        get(context, id)?.let { save(context, it.copy(state = "cancelled", phase = "Leitura cancelada", error = "Leitura cancelada.")) }
        WorkManager.getInstance(context).cancelUniqueWork(workName(id))
    }

    fun remove(context: Context, id: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(id))
        prefs(context).edit().remove(id).commit()
        directory(context, id).deleteRecursively()
    }
}
