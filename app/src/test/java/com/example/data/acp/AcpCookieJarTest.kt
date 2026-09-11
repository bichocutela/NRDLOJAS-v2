package com.example.data.acp

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AcpCookieJarTest {
    private val origin = AcpApi.ORIGIN.toHttpUrl()

    @Test fun sessionIsScopedToTenantAndHttps() {
        val jar = AcpCookieJar(null) {}
        jar.saveFromResponse(origin, listOf(Cookie.parse(origin, "session=test; Secure; Path=/")!!))
        assertEquals(1, jar.loadForRequest("${AcpApi.ORIGIN}/api/auth/session".toHttpUrl()).size)
        assertTrue(jar.loadForRequest("https://api.acp.app.br/api/v1/Product/all".toHttpUrl()).isEmpty())
        assertTrue(jar.loadForRequest("http://nordestao12.acp.app.br/".toHttpUrl()).isEmpty())
        assertTrue(jar.loadForRequest("https://evil.example/".toHttpUrl()).isEmpty())
    }

    @Test fun deletionAndChunkedCookiesSurviveStorage() {
        var saved = ""
        val jar = AcpCookieJar(null) { saved = it }
        jar.saveFromResponse(origin, listOf(
            Cookie.parse(origin, "session.0=first; Secure; Path=/")!!,
            Cookie.parse(origin, "session.1=second; Secure; Path=/")!!))
        val restored = AcpCookieJar(saved) {}
        assertEquals(2, restored.loadForRequest(origin).size)
        jar.saveFromResponse(origin, listOf(Cookie.parse(origin, "session.0=; Max-Age=0; Secure; Path=/")!!))
        assertEquals(listOf("session.1"), jar.loadForRequest(origin).map { it.name })
        jar.clear()
        assertEquals("[]", saved)
    }
}
