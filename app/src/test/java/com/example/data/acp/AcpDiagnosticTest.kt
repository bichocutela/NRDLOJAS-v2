package com.example.data.acp

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AcpDiagnosticTest {
    private class MemoryStore : AcpStorage {
        private val data = mutableMapOf<String, String>()
        override fun read(name: String): String? = data[name]
        override fun write(name: String, value: String) { data[name] = value }
    }

    private class Server(private vararg val bodies: String) : Interceptor {
        private var index = 0
        val requests = mutableListOf<Request>()
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            requests += request
            check(index < bodies.size) { "Unexpected request" }
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("Synthetic response")
                .body(bodies[index++].toResponseBody("application/json".toMediaType()))
                .build()
        }
    }

    private fun session() = """{"user":{"accessToken":"test-only-token","proxyEnable":false}}"""

    @Test fun diagnosticKeepsRequestSequenceAndParametersWithoutAuthorization() = runBlocking {
        val server = Server(
            session(), """{"items":[],"pageIndex":0,"totalPages":0,"totalCount":0}""",
            session(), """{"items":[{"id":"p1","description":"Produto"}],"pageIndex":0,"totalPages":1,"totalCount":1}"""
        )
        val api = AcpApi(MemoryStore(), OkHttpClient.Builder().addInterceptor(server))
        api.beginDiagnosticSession()

        api.get("Product/all", listOf("pageSize" to "20", "pageIndex" to "0", "barCode" to "7891000248768"))
        api.get("Product/all", listOf("pageSize" to "20", "pageIndex" to "0", "code" to "2013995003"))

        val text = api.diagnosticText() ?: error("diagnostic missing")
        assertFalse(text.contains("test-only-token"))
        assertFalse(text.contains("Authorization", ignoreCase = true))

        val root = JSONObject(text)
        val requests = root.getJSONArray("requests")
        assertEquals(2, requests.length())
        assertEquals("Product/all", requests.getJSONObject(0).getString("endpoint"))
        assertEquals("barCode", requests.getJSONObject(0).getJSONArray("parameters").getJSONObject(2).getString("name"))
        assertEquals("7891000248768", requests.getJSONObject(0).getJSONArray("parameters").getJSONObject(2).getString("value"))
        assertEquals("code", requests.getJSONObject(1).getJSONArray("parameters").getJSONObject(2).getString("name"))
        assertEquals(0, requests.getJSONObject(0).getJSONObject("response").getInt("totalCount"))
        assertEquals(1, requests.getJSONObject(1).getJSONObject("response").getInt("totalCount"))
        assertEquals(1, root.getJSONObject("endpoints").getJSONObject("Product/all").getInt("totalCount"))
    }

    @Test fun historyCapturePreservesNestedPayloadAndRedactsSecretsWithoutCatalogCache() = runBlocking {
        // Synthetic transport fixture; this does not assert a real promotion exists.
        val log = JSONObject().put("composedTemplates", org.json.JSONArray().put(
            JSONObject().put("items", org.json.JSONArray().put(JSONObject()
                .put("code", "example").put("secondUnitDiscount", 50)
                .put("cashback", JSONObject.NULL).put("token", "nested-secret")))))
        val response = JSONObject().put("items", org.json.JSONArray().put(
            JSONObject().put("dataLog", log.toString())))
            .put("totalPages", 2).put("pageIndex", 1)
        val server = Server(session(), response.toString(), session(), response.toString())
        val api = AcpApi(MemoryStore(), OkHttpClient.Builder().addInterceptor(server))
        repeat(2) {
            val text = api.captureHistory(1)
            assertFalse(text.contains("nested-secret"))
            assertFalse(text.contains("test-only-token"))
            val root = JSONObject(text)
            assertEquals("TemplatePrintLog/all", root.getString("endpoint"))
            val raw = root.getJSONObject("response").getJSONArray("items").getJSONObject(0).getString("dataLog")
            val product = JSONObject(raw).getJSONArray("composedTemplates").getJSONObject(0)
                .getJSONArray("items").getJSONObject(0)
            assertEquals(50, product.getInt("secondUnitDiscount"))
            assertTrue(product.isNull("cashback"))
        }
        assertEquals(4, server.requests.size)
        val request = server.requests[1]
        assertEquals("GET", request.method)
        assertEquals("/api/v1/TemplatePrintLog/all", request.url.encodedPath)
        assertEquals("10", request.url.queryParameter("pageSize"))
        assertEquals("1", request.url.queryParameter("pageIndex"))
        assertNull(api.diagnosticText()) // Large history does not pollute clipboard diagnostics.
    }

    @Test fun evidenceGroupReadUsesGetAndRemovesSecretsWithoutChangingClipboardDiagnostic() = runBlocking {
        val server = Server(session(), """{"items":[{"id":1,"products":[],"token":"private-test-value"}],"pageIndex":0,"totalPages":1,"totalCount":1}""")
        val api = AcpApi(MemoryStore(), OkHttpClient.Builder().addInterceptor(server))
        val response = api.readEvidencePage("ProductGroup/all", listOf("pageSize" to "10", "pageIndex" to "0"))
        assertFalse(response.toString().contains("private-test-value"))
        assertEquals("GET", server.requests.last().method)
        assertEquals("/api/v1/ProductGroup/all", server.requests.last().url.encodedPath)
        assertNull(api.diagnosticText())
        try {
            api.readEvidencePage("Product/bulk", emptyList())
            fail("Write endpoints must not be allowed")
        } catch (_: IllegalArgumentException) { /* expected before any request */ }
        assertEquals(2, server.requests.size)
    }

    @Test fun startingNewDiagnosticSessionClearsPreviousCapture() = runBlocking {
        val server = Server(session(), """{"items":[],"pageIndex":0,"totalPages":0,"totalCount":0}""")
        val api = AcpApi(MemoryStore(), OkHttpClient.Builder().addInterceptor(server))
        api.get("Product/all", listOf("barCode" to "123"))
        assertNotNull(api.diagnosticText())
        api.beginDiagnosticSession()
        assertNull(api.diagnosticText())
    }
}
