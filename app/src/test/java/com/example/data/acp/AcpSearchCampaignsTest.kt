package com.example.data.acp

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
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
class AcpSearchCampaignsTest {
    private class MemoryStore : AcpStorage {
        private val data = mutableMapOf<String, String>()
        override fun read(name: String) = data[name]
        override fun write(name: String, value: String) { data[name] = value }
    }

    private class Server : Interceptor {
        var campaignReads = 0
        var failNextRead = false
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val session = request.url.encodedPath == "/api/auth/session"
            if (!session) {
                check(request.url.encodedPath.endsWith("/Campaign/all"))
                campaignReads++
            }
            val failed = !session && failNextRead
            if (failed) failNextRead = false
            val body = if (session) """{"user":{"accessToken":"synthetic-token","proxyEnable":false}}"""
            else if (failed) """{"error":"synthetic-failure"}"""
            else """{"items":[{"id":"campaign","name":"Ofertas","products":[{
                "code":"123","value":10,"previousValue":12,"clubValue":8,"quantityTake":3,"quantityPay":2
            }]}],"totalPages":1}"""
            return Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(if (failed) 502 else 200).message("Synthetic")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }
    }

    private fun product() = AcpProductParser.page(JSONObject("""{"items":[{
        "id":"p","code":"123","description":"Teste","value":10
    }]}"""), 0).items.single()

    @Test fun firstSearchHasAllCampaignFamiliesWithoutOpeningProductDetails() = runBlocking {
        val server = Server()
        val api = AcpApi(MemoryStore(), OkHttpClient.Builder().addInterceptor(server))
        val product = product()
        val first = api.campaignOffersFor(listOf(product)).getValue(product.id)
        assertEquals(setOf(AcpOfferFamily.DE_POR, AcpOfferFamily.CLUB, AcpOfferFamily.TAKE_PAY), first.map { it.family }.toSet())
        assertEquals(1, server.campaignReads)
        assertEquals(first, api.campaignOffersFor(listOf(product)).getValue(product.id))
        assertEquals("Typing again reuses the recently loaded rules", 1, server.campaignReads)
    }

    @Test fun failedInitialCampaignReadDoesNotCacheAnEmptySuccessfulResult() = runBlocking {
        val server = Server().apply { failNextRead = true }
        val api = AcpApi(MemoryStore(), OkHttpClient.Builder().addInterceptor(server))
        val product = product()
        assertTrue(runCatching { api.campaignOffersFor(listOf(product)) }.isFailure)
        assertEquals(3, api.campaignOffersFor(listOf(product)).getValue(product.id).size)
        assertEquals(2, server.campaignReads)
    }
}
