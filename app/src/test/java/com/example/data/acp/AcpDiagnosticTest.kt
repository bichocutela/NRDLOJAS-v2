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

    @Test fun startingNewDiagnosticSessionClearsPreviousCapture() = runBlocking {
        val server = Server(session(), """{"items":[],"pageIndex":0,"totalPages":0,"totalCount":0}""")
        val api = AcpApi(MemoryStore(), OkHttpClient.Builder().addInterceptor(server))
        api.get("Product/all", listOf("barCode" to "123"))
        assertNotNull(api.diagnosticText())
        api.beginDiagnosticSession()
        assertNull(api.diagnosticText())
    }
}
