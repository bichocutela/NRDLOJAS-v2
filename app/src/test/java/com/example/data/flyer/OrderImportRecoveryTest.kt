package com.example.data.flyer

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class OrderImportRecoveryTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    @Before fun reset() {
        context.getSharedPreferences("order_import_tasks", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun tasksKeepIndependentProgressAfterReloadAndCancelOnlyOne() {
        val a = OrderImportTask(UUID.randomUUID().toString(), "content://a", "a.pdf",
            state = "running", phase = "Lendo página 2 de 3", progress = 28)
        val b = a.copy(id = UUID.randomUUID().toString(), uri = "content://b", name = "b.pdf", progress = 60)
        OrderImportTasks.save(context, a)
        OrderImportTasks.save(context, b)
        assertEquals(a, OrderImportTasks.get(context, a.id))
        assertEquals(b, OrderImportTasks.get(context, b.id))
        OrderImportTasks.save(context, a.copy(state = "cancelled"))
        OrderImportTasks.update(context, a.id) { it.copy(state = "running", progress = 90) }
        assertEquals("cancelled", OrderImportTasks.get(context, a.id)?.state)
        assertEquals(60, OrderImportTasks.get(context, b.id)?.progress)
    }

    @Test fun checkpointsReloadEmptyAndReadPagesAndResolvedProductWithoutCrossingTasks() {
        val id = UUID.randomUUID().toString()
        val directory = OrderImportTasks.directory(context, id)
        val checkpoint = OrderImportCheckpoint(File(directory, "checkpoint"))
        val block = FlyerTextBlock(1, "7891234567890 Produto", 0, 0, 10, 20, 100, 200)
        checkpoint.savePage(0, listOf(block))
        checkpoint.savePage(1, emptyList())
        val offer = FlyerOffer(type = FlyerOfferType.DE_POR, sourceDescription = "Produto")
            .copy(matchStatus = FlyerMatchStatus.CONFIRMED)
        checkpoint.saveResolved(offer)
        val reloaded = OrderImportCheckpoint(File(directory, "checkpoint"))
        assertEquals(listOf(block), reloaded.page(0))
        assertEquals(emptyList<FlyerTextBlock>(), reloaded.page(1))
        assertNull(reloaded.page(2))
        assertEquals(offer, reloaded.resolved(offer.id))
        val other = OrderImportCheckpoint(File(OrderImportTasks.directory(context, UUID.randomUUID().toString()), "checkpoint"))
        assertNull(other.page(0))
        assertNull(other.resolved(offer.id))
        directory.deleteRecursively()
    }

    @Test fun parsedCheckpointResumesWithoutReopeningSourceOrRerunningOcr() = runBlocking {
        val directory = OrderImportTasks.directory(context, UUID.randomUUID().toString())
        val checkpoint = OrderImportCheckpoint(File(directory, "checkpoint"))
        val analysis = FlyerAnalysisResult("Documento", null, null, emptyList(), emptyList(), "gallery", "Documento")
        checkpoint.saveAnalysis(analysis)
        val phases = mutableListOf<String>()
        val resumed = FlyerImportEngine.analyzeUri(context, Uri.fromFile(File(directory, "missing.pdf")),
            OrderImportCheckpoint(File(directory, "checkpoint"))) { phase, _ -> phases += phase }
        assertEquals(analysis, resumed)
        assertEquals(listOf("Conferindo produtos"), phases)
        directory.deleteRecursively()
    }
}
