package com.example.data.acp

import kotlinx.coroutines.runBlocking
import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Synthetic HTTP responses only. No ACP credentials or network are used. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AcpAuthenticationTest {
    private class MemoryStore : AcpStorage {
        val data = mutableMapOf("access" to """{"login":"test-user","password":"test-only-password"}""")
        override fun read(name: String): String? = data[name]
        override fun write(name: String, value: String) { data[name] = value }
    }
    private data class Reply(val body: String, val code: Int = 200, val location: String? = null)
    private class Server(vararg replies: Reply) : Interceptor {
        val queue = ArrayDeque(replies.toList())
        val requests = mutableListOf<Request>()
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            requests.add(request)
            check(queue.isNotEmpty()) { "Unexpected request" }
            val reply = queue.removeFirst()
            return Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(reply.code).message("Synthetic response")
                .body(reply.body.toResponseBody("application/json".toMediaType()))
                .apply { reply.location?.let { header("Location", it) } }.build()
        }
    }
    private fun api(server: Server, store: MemoryStore = MemoryStore()) =
        AcpApi(store, OkHttpClient.Builder().addInterceptor(server))
    private fun session(proxy: Boolean = false) = Reply("""{"user":{"accessToken":"test-only-token","proxyEnable":$proxy}}""")

    @Test fun loginUsesCsrfAndVerifiesSessionAfterCallback() = runBlocking {
        val server = Server(Reply("{}"), Reply("""{"csrfToken":"test-only-csrf"}"""),
            Reply("""{"url":"/home"}"""), session())
        api(server).confirmAccess()
        assertEquals(listOf("/api/auth/session", "/api/auth/csrf", "/api/auth/callback/credentials", "/api/auth/session"),
            server.requests.map { it.url.encodedPath })
        val login = server.requests[2]
        assertEquals("POST", login.method)
        assertEquals(AcpApi.ORIGIN, login.header("Origin"))
        val body = login.body as FormBody
        val values = (0 until body.size).associate { body.name(it) to body.value(it) }
        assertEquals("test-only-csrf", values["csrfToken"])
        assertEquals("test-user", values["login"])
        assertEquals("test-only-password", values["password"])
        assertEquals("true", values["json"])
        assertTrue(server.requests.all { it.url.host == "nordestao12.acp.app.br" })
    }

    @Test fun existingSessionDoesNotResendPassword() = runBlocking {
        val server = Server(session())
        api(server).confirmAccess()
        assertEquals(1, server.requests.size)
        assertEquals("GET", server.requests.single().method)
        assertNull(server.requests.single().body)
    }

    @Test fun nullCsrfStopsBeforeSendingCredentials() = runBlocking {
        val server = Server(Reply("{}"), Reply("""{"csrfToken":null}"""))
        val failure = runCatching { api(server).confirmAccess() }.exceptionOrNull()
        assertTrue(failure is AcpFailure)
        assertEquals(2, server.requests.size)
        assertTrue(server.requests.all { it.method == "GET" })
    }

    @Test fun relativeCallbackErrorIsNotMistakenForSuccessfulLogin() = runBlocking {
        val server = Server(Reply("{}"), Reply("""{"csrfToken":"test-only-csrf"}"""),
            Reply("""{"url":"/login?error=CredentialsSignin"}"""))
        val failure = runCatching { api(server).confirmAccess() }.exceptionOrNull()
        assertTrue(failure is AcpFailure)
        assertEquals(3, server.requests.size)
    }

    @Test fun rejectedCredentialsAreNotRetriedOrReportedAsSessionExpiry() = runBlocking {
        val server = Server(Reply("{}"), Reply("""{"csrfToken":"test-only-csrf"}"""), Reply("{}", 401))
        val failure = runCatching { api(server).confirmAccess() }.exceptionOrNull()
        assertTrue(failure is AcpFailure)
        assertTrue(failure?.message.orEmpty().contains("não aceitou"))
        assertEquals(1, server.requests.count { it.method == "POST" })
    }

    @Test fun invalidSessionTokenNeverReachesProductsApi() = runBlocking {
        for (token in listOf("null", "123", "{}", "\"\"", "\"null\"")) {
            val server = Server(Reply("""{"user":{"accessToken":$token}}"""))
            val store = MemoryStore()
            val failure = runCatching { api(server, store).get("Product/all", emptyList()) }.exceptionOrNull()
            assertTrue(failure is AcpUnauthorized)
            assertEquals(1, server.requests.size)
            assertEquals("[]", store.data["session"])
        }
    }

    @Test fun productUnauthorizedClearsSessionWithoutAutomaticLogin() = runBlocking {
        val server = Server(session(), Reply("{}", 401))
        val store = MemoryStore()
        val failure = runCatching { api(server, store).get("Product/all", emptyList()) }.exceptionOrNull()
        assertTrue(failure is AcpUnauthorized)
        assertEquals("[]", store.data["session"])
        assertEquals(2, server.requests.size)
        assertTrue(server.requests.all { it.method == "GET" })
    }

    @Test fun proxySelectionAndQueryEncodingMatchClientContract() = runBlocking {
        for (proxy in listOf(false, true)) {
            val server = Server(session(proxy), Reply("""{"items":[]}"""))
            api(server).get("Product/all", listOf("description" to "Açúcar & Café", "productCategoryIds" to "a", "productCategoryIds" to "b"))
            val request = server.requests.last()
            assertEquals(if (proxy) "nordestao12.acp.app.br" else "api.acp.app.br", request.url.host)
            assertEquals(if (proxy) "/api/proxy/api/v1/Product/all" else "/api/v1/Product/all", request.url.encodedPath)
            assertEquals("Açúcar & Café", request.url.queryParameter("description"))
            assertEquals(listOf("a", "b"), request.url.queryParameterValues("productCategoryIds"))
            assertEquals("Bearer test-only-token", request.header("Authorization"))
        }
    }

    @Test fun redirectsAreNotFollowedAndInvalidPayloadIsNotExposed() = runBlocking {
        val redirected = Server(session(), Reply("{}", 302, "https://example.invalid/login"))
        assertTrue(runCatching { api(redirected).get("Product/all", emptyList()) }.exceptionOrNull() is AcpUnauthorized)
        assertEquals(2, redirected.requests.size)
        val invalid = Server(session(), Reply("<html>private-server-detail</html>"))
        val failure = runCatching { api(invalid).get("Product/all", emptyList()) }.exceptionOrNull()
        assertTrue(failure is AcpFailure)
        assertFalse(failure?.message.orEmpty().contains("private-server-detail"))
    }
}
