package com.example.data.acp

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AcpHistoryExportTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val payload = JSONObject().put("endpoint", "TemplatePrintLog/all")
        .put("response", JSONObject().put("dataLog", "{\"descrição\":\"Promoção\"}"))
        .toString(2)

    @Test fun recreatedExporterSavesFullJsonAfterOriginalStateIsLost() {
        AcpHistoryExport(context).stage(payload)
        val destination = File.createTempFile("history", ".json", context.cacheDir)
        // Simulate the picker having created an empty document and a recreated screen.
        assertEquals(0L, destination.length())
        val recreated = AcpHistoryExport(context)
        val count = recreated.save(Uri.fromFile(destination))
        assertEquals(payload.toByteArray(Charsets.UTF_8).size, count)
        assertEquals(payload, destination.readText())
    }

    @Test fun overwriteTruncatesOldDataAndRetainsStagingForRetry() {
        val exporter = AcpHistoryExport(context)
        exporter.stage(payload)
        val destination = File.createTempFile("history", ".json", context.cacheDir)
        destination.writeText("x".repeat(10000))
        exporter.save(Uri.fromFile(destination))
        assertEquals(payload, destination.readText())
        assertEquals(payload, AcpHistoryExport(context).readPending().toString(Charsets.UTF_8))
    }

    @Test fun rejectsEmptyPayloadBeforeOpeningPicker() {
        assertThrows(IllegalArgumentException::class.java) { AcpHistoryExport(context).stage("") }
    }
}
