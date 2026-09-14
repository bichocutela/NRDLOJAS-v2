package com.example.data.acp

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AcpEvidenceScannerTest {
    private class MemoryStore : AcpStorage {
        private val values = mutableMapOf<String, String>()
        override fun read(name: String) = values[name]
        override fun write(name: String, value: String) { values[name] = value }
    }

    private fun observed() = JSONObject(requireNotNull(javaClass.getResource("/acp/history-page-0-observed.json")).readText())

    // Only pagination in orchestration tests below is synthetic; products come from the uploaded capture.
    private fun page(items: JSONArray, index: Int = 0, pages: Int = 1) = JSONObject()
        .put("items", items).put("pageIndex", index).put("totalPages", pages)
        .put("pageSize", 25).put("totalCount", if (pages == 0) 0 else pages * 10)

    @Test fun observedHistoryRetainsClubPriceAndPrintedValidityWithoutInventingPercent() {
        val rows = observed().getJSONArray("items")
        val trident = rows.getJSONObject(2)
        assertEquals(6306, trident.getInt("id"))
        val excerpts = requireNotNull(AcpEvidenceScanner.historyMatches(trident, "7622210564337", 0))
        assertEquals(4, excerpts.size) // Four printed occurrences, not four distinct promotions.
        val product = excerpts.first().getJSONObject("product")
        assertEquals("1.99", product.getString("clubValue"))
        assertEquals("03/02/2026", product.getString("validOffer"))
        assertTrue(product.getBoolean("showOffersExpirationDate"))
        assertTrue(product.isNull("dueDate"))
        assertFalse(product.has("secondUnitDiscount"))
        assertTrue(product.isNull("cashback"))
        assertEquals(0, product.getInt("cashbackValue"))
        assertTrue(requireNotNull(AcpEvidenceScanner.historyMatches(trident, "2012568001", 0)).isEmpty())
        assertTrue(requireNotNull(AcpEvidenceScanner.historyMatches(trident, "2010473", 0)).isEmpty())
    }

    @Test fun objectDataLogAndSerializedDataLogProduceSameMatchesAndMalformedIsNotAbsence() {
        val row = observed().getJSONArray("items").getJSONObject(2)
        val serialized = AcpEvidenceScanner.historyMatches(row, "2010473004", 0)
        row.put("dataLog", JSONObject(row.getString("dataLog")))
        assertEquals(serialized.toString(), AcpEvidenceScanner.historyMatches(row, "2010473004", 0).toString())
        row.put("dataLog", "broken")
        assertNull(AcpEvidenceScanner.historyMatches(row, "2010473004", 0))
    }

    @Test fun pauseAndResumeUsesLastSavedPageAndKeepsDiagnosticsAwayFromPrices() = runBlocking {
        val store = MemoryStore()
        val rows = observed().getJSONArray("items")
        val calls = mutableListOf<Pair<String, Int>>()
        val read: suspend (String, List<Pair<String, String>>) -> JSONObject = { path, parameters ->
            val index = parameters.toMap().getValue("pageIndex").toInt()
            calls += path to index
            if (path == "ProductGroup/all") page(JSONArray(), pages = 0)
            else if (index == 1) page(JSONArray().put(rows.getJSONObject(2)), index = 1, pages = 2)
            else page(JSONArray().put(rows.getJSONObject(0)), pages = 2)
        }
        val first = AcpEvidenceScanner(read, store, pause = { throw CancellationException("pause") })
        try { first.scan("2010473004") {} } catch (_: CancellationException) { /* expected */ }
        assertEquals(listOf("ProductGroup/all" to 0), calls)
        val second = AcpEvidenceScanner(read, store, pause = {})
        second.scan("2010473004") {}
        assertEquals(listOf("ProductGroup/all" to 0, "TemplatePrintLog/all" to 0,
            "TemplatePrintLog/all" to 1, "TemplatePrintLog/all" to 0), calls)
        val report = JSONObject(requireNotNull(second.report("2010473004")))
        val state = report.getJSONObject("response")
        assertEquals("done", state.getString("phase"))
        assertEquals(4, state.getJSONArray("matches").length())
        val before = calls.size
        second.scan("2010473004") {}
        assertEquals(before, calls.size)
        assertNull(second.report("5604885098906"))
    }

    @Test fun unavailableGroupsDoNotPreventHistoryAndRemainExplicitlyIncomplete() = runBlocking {
        val scanner = AcpEvidenceScanner({ path, _ ->
            if (path == "ProductGroup/all") throw AcpFailure("A ACP não autorizou esta consulta.")
            page(observed().getJSONArray("items"))
        }, MemoryStore(), pause = {})
        scanner.scan("2010473004") {}
        val state = JSONObject(requireNotNull(scanner.report("2010473004"))).getJSONObject("response")
        assertFalse(state.getBoolean("groupsAvailable"))
        assertEquals("done", state.getString("phase"))
        assertEquals(4, state.getJSONArray("matches").length())
    }

    @Test fun groupNameAloneDoesNotMatchOrSupplyDiscount() = runBlocking {
        // Synthetic group fixture, consistent with the observed frontend's products array.
        val groups = JSONArray().put(JSONObject().put("id", 1).put("description", "2012568001 50% segunda unidade")
            .put("products", JSONArray().put(JSONObject().put("code", "999").put("value", 45.49))))
        val scanner = AcpEvidenceScanner({ path, _ ->
            if (path == "ProductGroup/all") page(groups) else page(JSONArray(), pages = 0)
        }, MemoryStore(), pause = {})
        scanner.scan("2012568001") {}
        val state = JSONObject(requireNotNull(scanner.report("2012568001"))).getJSONObject("response")
        assertEquals(0, state.getJSONArray("matches").length())
    }

    @Test fun repeatedPageStopsWithoutFalseCompletion() = runBlocking {
        val store = MemoryStore()
        val groups = JSONArray().put(JSONObject().put("id", 1).put("products", JSONArray()))
        val scanner = AcpEvidenceScanner({ _, parameters -> page(groups,
            index = parameters.toMap().getValue("pageIndex").toInt(), pages = 2)
        }, store, pause = {})
        try { scanner.scan("2012568001") {}; fail("must stop") }
        catch (_: AcpFailure) { /* expected */ }
        val p = requireNotNull(scanner.progress("2012568001"))
        assertFalse(p.finished)
        assertEquals(1, p.pages)
    }
}
