package com.example.data.promotions

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class StorePublicationWriterTest {
    private fun records(size: Int) = (1..size).map {
        StorePromotionRecord("$it", "", "Produto $it", 14.49, 17.49, "2026-10-14", "2026-10-14")
    }
    @Test fun ninetyEightOffersEnableTheStoreAndReturnConfirmedServerState() = runBlocking {
        val documents = mutableMapOf<String, Map<String, Any>>()
        val order = mutableListOf<String>()
        val writer = StorePublicationWriter(write = { path, data, merge ->
            order += path
            assertEquals(path == "config/promotion_stores", merge)
            documents[path] = data.mapValues { it.value!! }
        }, read = { documents[it].orEmpty() })
        val result = writer.publish("0037", "test", records(98), "Relatório", 1)
        assertEquals(listOf("config/promotion_0037_test_0", "config/promotion_stores"), order)
        val store = result["0037"] as Map<*, *>
        assertEquals(true, store["enabled"])
        assertEquals(98, store["count"])
        assertEquals("test", store["revision"])
    }
    @Test fun failedPageCannotExposePartialOffersOrEnableStore() = runBlocking {
        val writes = mutableListOf<String>()
        val writer = StorePublicationWriter(write = { path, _, _ ->
            writes += path
            if (path.endsWith("_1")) error("Falha de conexão")
        }, read = { error("Must not read after failed write") })
        val failure = runCatching { writer.publish("0037", "test", records(201), "Relatório", 1) }.exceptionOrNull()
        assertNotNull(failure)
        assertFalse(writes.contains("config/promotion_stores"))
    }
    @Test fun staleReadCannotReportFalseSuccess() = runBlocking {
        val writer = StorePublicationWriter(write = { _, _, _ -> },
            read = { mapOf("0037" to mapOf("enabled" to false, "revision" to "old", "pages" to 1, "count" to 98)) })
        val failure = runCatching { writer.publish("0037", "new", records(98), "Relatório", 1) }.exceptionOrNull()
        assertNotNull(failure)
        assertTrue(failure!!.message!!.contains("confirmar"))
    }
    @Test fun serverConflictCannotReportFalseSuccess() = runBlocking {
        val writer = StorePublicationWriter(write = { path, _, _ ->
            if (path == "config/promotion_stores") error("A loja foi alterada em outro aparelho")
        }, read = { error("Must not read after failed pointer write") })
        assertNotNull(runCatching { writer.publish("0037", "test", records(98), "Relatório", 1) }.exceptionOrNull())
    }
}
