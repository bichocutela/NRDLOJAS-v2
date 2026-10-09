package com.example.ui

import android.app.Application
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class VisualMixOrderSessionTest {
    @After fun clear() {
        VisualMixOrderProcesses.sessions.clear()
        VisualMixOrderProcesses.selected.value = null
        VisualMixOrderProcesses.minimized.value = false
    }
    @Test fun switchingAndMinimizingPreserveEachDocumentSelectionAndProgress() {
        val first = VisualMixOrderSession("a")
        val second = VisualMixOrderSession("b")
        VisualMixOrderProcesses.sessions.addAll(listOf(first, second))
        first.selectedKeys.value = setOf("product-a")
        first.batchBusy.value = true
        second.busy.value = true
        VisualMixOrderProcesses.select(first)
        first.minimize()
        assertTrue(VisualMixOrderProcesses.minimized.value)
        VisualMixOrderProcesses.select(second)
        assertEquals("b", VisualMixOrderProcesses.selected.value)
        assertTrue(first.batchBusy.value)
        assertTrue(second.busy.value)
        assertEquals(setOf("product-a"), first.selectedKeys.value)
        assertTrue(second.selectedKeys.value.isEmpty())
        VisualMixOrderProcesses.resume("a")
        assertEquals("a", VisualMixOrderProcesses.selected.value)
        assertFalse(VisualMixOrderProcesses.minimized.value)
    }
    @Test fun cancellingOneDocumentNeverCancelsAnother() = runBlocking {
        val first = VisualMixOrderSession("a")
        val second = VisualMixOrderSession("b")
        first.processingJob = launch { delay(60_000) }
        second.processingJob = launch { delay(60_000) }
        first.cancel()
        first.processingJob!!.join()
        assertTrue(first.processingJob!!.isCancelled)
        assertTrue(second.processingJob!!.isActive)
        assertNotNull(first.error.value)
        second.processingJob!!.cancelAndJoin()
    }
}
