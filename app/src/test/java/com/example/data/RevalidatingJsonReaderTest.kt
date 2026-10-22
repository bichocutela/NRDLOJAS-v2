package com.example.data

import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class RevalidatingJsonReaderTest {
    @Test fun coalescesReadsRevalidatesAndAcceptsChangedContent() = runBlocking {
        var clock = 100L
        val requests = mutableListOf<Request>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests += chain.request()
            val index = requests.size
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(if (index == 2) 304 else 200).message("fixture")
                .header("ETag", if (index < 3) "version-1" else "version-2")
                .body((if (index < 3) "first" else "changed").toResponseBody("application/json".toMediaType())).build()
        }.build()
        val reader = RevalidatingJsonReader(client, { it.takeIf(String::isNotBlank) }, { clock })
        val request = Request.Builder().url("https://example.test/appearance.json").build()
        assertEquals("first", reader.read(request, 60_000))
        repeat(10) { assertEquals("first", reader.read(request, 60_000)) }
        assertEquals(1, requests.size)
        clock += 60_000
        assertEquals("first", reader.read(request, 60_000))
        assertEquals("version-1", requests.last().header("If-None-Match"))
        clock += 60_000
        assertEquals("changed", reader.read(request, 60_000))
        assertEquals(3, requests.size)
    }
}
