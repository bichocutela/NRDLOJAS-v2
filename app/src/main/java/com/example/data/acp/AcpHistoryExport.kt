package com.example.data.acp

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File

/** Durable staging: the document picker may recreate the activity and its Compose state. */
internal class AcpHistoryExport(private val context: Context) {
    private val pending = AtomicFile(File(context.noBackupFilesDir, "acp-history-pending.json"))

    fun stage(payload: String) {
        require(payload.isNotBlank()) { "Diagnóstico vazio" }
        val root = JSONObject(payload)
        require(root.optString("endpoint") == "TemplatePrintLog/all" && root.optJSONObject("response") != null)
        context.noBackupFilesDir.mkdirs()
        val stream = pending.startWrite()
        try {
            stream.write(payload.toByteArray(Charsets.UTF_8))
            pending.finishWrite(stream)
        } catch (failure: Exception) {
            pending.failWrite(stream)
            throw failure
        }
    }

    fun readPending(): ByteArray {
        val bytes = pending.readFully()
        require(bytes.isNotEmpty()) { "Diagnóstico vazio" }
        JSONObject(bytes.toString(Charsets.UTF_8))
        return bytes
    }

    fun save(uri: Uri): Int {
        // Read before opening/truncating the destination. Keep staging for retry on failure.
        val bytes = readPending()
        val resolver = context.contentResolver
        val output = resolver.openOutputStream(uri, "wt") ?: error("Arquivo indisponível")
        output.use { it.write(bytes); it.flush() }
        val written = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Não foi possível verificar o arquivo")
        check(bytes.contentEquals(written)) { "O arquivo gravado está incompleto" }
        return written.size
    }
}
