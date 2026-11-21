package com.example.data.promotions

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class StructuredEanImageRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val ean = "7891033777358"
    private val raia = "https://product-data.raiadrogasil.io/images/3483834.webp"
    private val market = "https://http2.mlstatic.com/D_NQ_NP_123-MLB.jpg"
    @Before fun clear() { context.getSharedPreferences("structured_ean_images_v1", Context.MODE_PRIVATE).edit().clear().commit() }
    private fun product(code: String = ean, image: String = raia) =
        """<script type="application/ld+json">{"@type":"Product","gtin13":"$code","image":"$image"}</script>"""
    private fun client(handler: (Request) -> Pair<Int, String>) = OkHttpClient.Builder().addInterceptor { chain ->
        val (status, body) = handler(chain.request())
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(status).message("fixture")
            .body(body.toResponseBody("text/html".toMediaType())).build()
    }.build()

    @Test fun acceptsOnlyProductStructuredDataWithExactEanAndProviderImage() {
        assertEquals(raia, StructuredEanImageRepository.exactImage(product(), ean, "raia"))
        assertEquals(market, StructuredEanImageRepository.exactImage(product(image = market), ean, "mercadolivre"))
        assertNull(StructuredEanImageRepository.exactImage(product(code = "7897622317608"), ean, "raia"))
        assertNull(StructuredEanImageRepository.exactImage(product(image = "https://example.com/wrong.jpg"), ean, "raia"))
        assertNull(StructuredEanImageRepository.exactImage("<p>EAN $ean</p><img src=\"$raia\">", ean, "raia"))
        assertNull(StructuredEanImageRepository.exactImage(product().replace("Product", "WebPage"), ean, "raia"))
        assertNull(StructuredEanImageRepository.exactImage(product() + product(image = "$raia?other=1"), ean, "raia"))
    }

    @Test fun raiaSearchCandidateMustBeVerifiedOnProductPageAndIsCached() = runBlocking {
        val requests = mutableListOf<Request>()
        val http = client { request ->
            requests += request
            200 to if (request.url.encodedPath == "/search") {
                assertEquals(ean, request.url.queryParameter("w"))
                """<script id="__NEXT_DATA__">{"props":{"pageProps":{"pageProps":{"results":{"products":[{"url":"/wrong.html"},{"url":"/correct.html"}]}}}}}</script>"""
            } else if (request.url.encodedPath == "/wrong.html") product(code = "7897622317608") else product()
        }
        val repository = StructuredEanImageRepository(context, http)
        assertEquals(raia, repository.lookup("raia", ean).url)
        assertEquals(raia, StructuredEanImageRepository(context, http).lookup("raia", ean).url)
        assertEquals(3, requests.size)
        assertTrue(requests.all { it.url.host == "www.drogaraia.com.br" })
    }

    @Test fun marketListingImagesNeverReplaceVerifiedProductImage() = runBlocking {
        var requests = 0
        val repository = StructuredEanImageRepository(context, client { request ->
            requests++
            200 to if (request.url.host == "lista.mercadolivre.com.br") {
                assertEquals("/$ean", request.url.encodedPath)
                """<img src="$market"><a href="https://produto.mercadolivre.com.br/MLB-1234567890-product-_JM">Item</a>"""
            } else product(image = market)
        })
        assertEquals(market, repository.lookup("mercadolivre", ean).url)
        assertEquals(2, requests)
    }

    @Test fun invalidIdentifiersMakeNoRequestAndBlockedProviderHasGlobalCooldown() = runBlocking {
        var requests = 0
        var clock = 1L
        val repository = StructuredEanImageRepository(context, client { requests++; 403 to "blocked" }, { clock })
        for (code in listOf(null, "", "250090", "Australian Gold", "7891033777359")) assertNull(repository.lookup("raia", code).url)
        assertEquals(0, requests)
        assertNotNull(repository.lookup("mercadolivre", ean).retryAt)
        assertNotNull(repository.lookup("mercadolivre", "7897622317608").retryAt)
        assertEquals(1, requests)
        clock += 300_001
        repository.lookup("mercadolivre", ean)
        assertEquals(2, requests)
    }
    @Test fun cosmosUsesOnlyTheExactEanImageAddressAndCachesDecodedImages() = runBlocking {
        var requests = 0
        val png = java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAIAAAD91JpzAAAAFklEQVR4nGMMqFjAwMDAxMDAwMDAAAAQugFsZnyF3gAAAABJRU5ErkJggg==")
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            requests++
            assertEquals("https://cdn-cosmos.bluesoft.com.br/products/$ean", chain.request().url.toString())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
                .body(png.toResponseBody(null)).build()
        }.build()
        val actual = StructuredEanImageRepository(context, http).lookup("cosmos", ean).url
        assertNotNull(actual)
        val uri = android.net.Uri.parse(actual)
        assertEquals("file", uri.scheme)
        val file = java.io.File(uri.path!!)
        assertEquals("$ean.png", file.name)
        assertArrayEquals(png, file.readBytes())
        assertEquals(actual, StructuredEanImageRepository(context, http).lookup("cosmos", ean).url)
        assertEquals(1, requests)
    }

    @Test fun missingCosmosImageIsCachedAndHtmlIsNeverAcceptedAsAPhoto() = runBlocking {
        var requests = 0
        val missing = StructuredEanImageRepository(context, client { requests++; 404 to "not found" })
        assertNull(missing.lookup("cosmos", ean).url)
        assertNull(missing.lookup("cosmos", ean).retryAt)
        assertEquals(1, requests)
        clear()
        val blocked = StructuredEanImageRepository(context, client { 200 to "<html>Blocked</html>" })
        val result = blocked.lookup("cosmos", ean)
        assertNull(result.url)
        assertNotNull(result.retryAt)
    }

    @Test fun cosmosRejectsNamesAndInternalCodesWithoutContactingTheSource() = runBlocking {
        var requests = 0
        val repository = StructuredEanImageRepository(context, client { requests++; 200 to "invalid" })
        for (code in listOf(null, "", "250090", "Australian Gold", "7891033777359")) {
            assertNull(repository.lookup("cosmos", code).url)
        }
        assertEquals(0, requests)
    }

    @Test fun absentRaiaProductDoesNotBlockTheNextCandidateOrOtherEans() = runBlocking {
        val requests = mutableListOf<Request>()
        val repository = StructuredEanImageRepository(context, client { request ->
            requests += request
            when (request.url.encodedPath) {
                "/search" -> if (request.url.queryParameter("w") == ean) 200 to
                    """<script id="__NEXT_DATA__">{"props":{"pageProps":{"pageProps":{"results":{"products":[{"url":"/removed.html"},{"url":"/correct.html"}]}}}}}</script>"""
                    else 200 to """<script id="__NEXT_DATA__">{"props":{"pageProps":{"pageProps":{"results":{"products":[]}}}}}</script>"""
                "/removed.html" -> 404 to "not found"
                else -> 200 to product()
            }
        })
        assertEquals(raia, repository.lookup("raia", ean).url)
        val missing = repository.lookup("raia", "7891910000197")
        assertNull(missing.url)
        assertNull(missing.retryAt)
        assertEquals(4, requests.size)
    }

}
