package com.example.data.acp

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AcpConsultationSearchTest {
    private class Memory : AcpStorage {
        private val values = mutableMapOf("access" to """{"login":"synthetic","password":"synthetic"}""")
        override fun read(name: String) = values[name]
        override fun write(name: String, value: String) { values[name] = value }
    }
    private class Server(val rows: List<JSONObject>, val providerChanges: Boolean = false) : Interceptor {
        val queries = mutableListOf<Request>()
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val body = if (request.url.encodedPath.endsWith("/api/auth/session")) {
                JSONObject("""{"user":{"accessToken":"synthetic","proxyEnable":false}}""")
            } else {
                check(request.url.encodedPath.endsWith("/Product/all"))
                queries += request
                val page = request.url.queryParameter("pageIndex")!!.toInt()
                val size = request.url.queryParameter("pageSize")!!.toInt()
                JSONObject().put("items", JSONArray(rows.drop(page * size).take(size)))
                    .put("totalCount", rows.size).put("pageIndex", page).put("totalPages", (rows.size + size - 1) / size)
                    .put("nrdProvider", if (providerChanges && page > 0) "acptech" else "legacy")
            }
            return Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("synthetic")
                .body(body.toString().toResponseBody("application/json".toMediaType())).build()
        }
        fun api() = AcpApi(Memory(), OkHttpClient.Builder().addInterceptor(this))
    }
    private fun row(id: Int, description: String) = JSONObject().put("id", id.toString()).put("code", id.toString())
        .put("description", description).put("value", 10)

    @Test fun sortsAllFilteredPagesBeforeSplittingAndReusesTheSameResultForNextPage() = runBlocking {
        val server = Server((0..300).reversed().map { row(it, "Água %04d 1L".format(it)) })
        val api = server.api()
        val first = api.searchProducts(AcpSearchField.DESCRIPTION, "agua", null, 0)
        val second = api.searchProducts(AcpSearchField.DESCRIPTION, "agua", null, 1)
        assertEquals("Água 0000 1L", first.items.first().description)
        assertEquals("Água 0020 1L", second.items.first().description)
        assertEquals(301, first.totalCount)
        assertEquals(16, first.totalPages)
        assertEquals(2, server.queries.size)
        assertTrue(server.queries.all { it.url.queryParameter("description") == "agua" && it.url.queryParameter("pageSize") == "250" })
        assertTrue((first.items + second.items).all { it.provider == "legacy" })
    }

    @Test fun hashFilterFindsMatchesInLaterUpstreamPagesWithoutAnEmptyFirstPage() = runBlocking {
        val server = Server((0..249).map { row(it, "Amaciante Downy 500ml") } +
            listOf(row(250, "Amaciante Downy 1L"), row(251, "Amaciante Downy 1000ml")))
        val result = server.api().searchProducts(AcpSearchField.DESCRIPTION, "downy#1l#", null, 0)
        assertEquals(2, result.totalCount)
        assertEquals(1, result.totalPages)
        assertEquals(setOf("250", "251"), result.items.map { it.id }.toSet())
    }

    @Test fun rejectsProviderChangesInsteadOfMixingTheTwoCatalogues() = runBlocking {
        val api = Server((0..250).map { row(it, "Água $it") }, providerChanges = true).api()
        try {
            api.searchProducts(AcpSearchField.DESCRIPTION, "agua", null, 0)
            fail("Mixed providers must not be published")
        } catch (_: AcpFailure) {
            assertNull(api.descriptionSearchSnapshot)
        }
    }

    private fun now(value: String): Long = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.ROOT).parse(value)!!.time
    private fun product(): AcpProduct = AcpProductParser.page(JSONObject().put("items", JSONArray().put(
        row(1, "Água").put("previousValue", 12).put("productCategories", JSONArray("""[{"description":"De-Por"}]"""))
    )), 0).items.single()

    @Test fun expiredOrFutureOffersShowTheNormalPriceAndActiveCampaignReplacesOldConditions() {
        val time = now("2026-10-21T15:00:00Z")
        val item = product().copy(validFrom = "2026-10-19", validTo = "2026-10-20")
        assertTrue(item.visibleOffers(nowMillis = time).isEmpty())
        assertEquals("R$ 12,00", item.normalPrice(time)?.brl())
        val future = item.copy(validFrom = "2026-10-22", validTo = "2026-10-23")
        assertTrue(future.visibleOffers(nowMillis = time).isEmpty())
        val active = AcpOffer("Clube de Vantagens", "Atual", java.math.BigDecimal("8"), validFrom = "2026-10-21T15:00:00Z", validTo = "2026-10-21T16:00:00Z")
        assertTrue(item.visibleOffers(listOf(active), nowMillis = time - 1).isEmpty())
        assertEquals("R$ 8,00", item.visibleOffers(listOf(active), nowMillis = time).single().price?.brl())
        assertTrue(item.visibleOffers(listOf(active), nowMillis = now("2026-10-21T16:00:01Z")).isEmpty())
        assertTrue(item.visibleOffers(listOf(active.copy(enabled = false)), nowMillis = time).isEmpty())
        assertEquals(AcpOfferValidity("21/10/2026", "21/10/2026"), active.validityFor(item, null))
    }
}
