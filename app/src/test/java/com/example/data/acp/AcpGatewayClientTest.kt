package com.example.data.acp

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
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
class AcpGatewayClientTest {
    private class Server(var status: Int = 200) : Interceptor {
        val requests = mutableListOf<Request>()
        override fun intercept(chain: Interceptor.Chain): Response {
            requests += chain.request()
            return Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(status).message("Synthetic").body("""{"items":[],"ok":true}""".toResponseBody()).build()
        }
    }
    private class Store : AcpStorage {
        val entries = mutableMapOf<String, String>()
        override fun read(name: String) = entries[name]
        override fun write(name: String, value: String) { entries[name] = value }
    }
    private fun gateway(server: Server, token: String? = "firebase-test-token") = AcpGatewayClient(
        "https://gateway.invalid", "public-test-key", OkHttpClient.Builder().addInterceptor(server).build(), { token })

    @Test fun readsUseGatewayWithFirebaseIdentityAndPreserveRepeatedFilters() = runBlocking {
        val server = Server()
        gateway(server).read("Product/all", listOf("productCategoryIds" to "1", "productCategoryIds" to "2"))
        val request = server.requests.single()
        assertEquals("gateway.invalid", request.url.host)
        assertEquals("/functions/v1/nrd-price-gateway", request.url.encodedPath)
        assertEquals("firebase-test-token", request.header("x-firebase-token"))
        assertEquals("public-test-key", request.header("apikey"))
        assertNull(request.header("Cookie")); assertNull(request.header("Authorization"))
        val buffer = okio.Buffer(); request.body!!.writeTo(buffer)
        val body = JSONObject(buffer.readUtf8())
        assertEquals(2, body.getJSONArray("parameters").length())
        assertFalse(body.has("password")); assertFalse(body.has("login"))
    }
    @Test fun productionApiUsesGatewayForConfirmationAndReads() = runBlocking {
        val server = Server(); val api = AcpApi(Store(), gateway = gateway(server))
        assertTrue(api.hasCredentials()); api.confirmAccess(); api.get("Product/all", emptyList())
        assertEquals(2, server.requests.size)
        assertTrue(server.requests.all { it.url.host == "gateway.invalid" })
    }
    @Test fun deniedServerAccessCannotFallBackToLocalCredentialsOrCachedPrice() = runBlocking {
        val server = Server(); val api = AcpApi(Store(), bundledLogin = "obsolete", bundledPassword = "obsolete", gateway = gateway(server))
        api.get("Product/all", emptyList())
        server.status = 403
        assertTrue(runCatching { api.get("Product/all", emptyList()) }.exceptionOrNull() is AcpFailure)
        assertEquals(2, server.requests.size)
        assertTrue(server.requests.all { it.url.host == "gateway.invalid" })
    }
    @Test fun publicModeCanBeCheckedWithoutLoggingInAndFailuresDoNotExposeBody() = runBlocking {
        val server = Server(); gateway(server, null).checkAccess()
        assertNull(server.requests.single().header("x-firebase-token"))
        server.status = 401
        assertTrue(runCatching { gateway(server).checkAccess() }.exceptionOrNull() is AcpUnauthorized)
        server.status = 502
        assertTrue(runCatching { gateway(server).checkAccess() }.exceptionOrNull() is AcpFailure)
    }
}
