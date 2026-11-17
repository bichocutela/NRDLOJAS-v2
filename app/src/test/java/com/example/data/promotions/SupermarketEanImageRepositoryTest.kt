package com.example.data.promotions

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class SupermarketEanImageRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val ean = "7891910000197"
    private val wrong = "7891990002197"
    private val carone = "https://tezegw.vteximg.com.br/arquivos/ids/552072/25472simplus1png.png?v=639192883511470000"
    private val mateus = "https://ik.imagekit.io/glm5kjz1tud/products/None/7891910000197.png?tr=w-600&v=20260901130028"
    @Before fun clear() { context.getSharedPreferences("structured_ean_images_v1", Context.MODE_PRIVATE).edit().clear().commit() }
    private fun client(handler: (Request) -> Pair<Int, String>) = OkHttpClient.Builder().addInterceptor { chain ->
        val (status, body) = handler(chain.request())
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(status).message("fixture")
            .body(body.toResponseBody("application/json".toMediaType())).build()
    }.build()
    private fun sku(code: String = ean, image: String = carone) = """{"ean":"$code","images":[{"imageUrl":"$image"}]}"""
    private fun hit(code: String = ean, image: String = mateus) = """{"barcode":"$code","image":"$image"}"""

    @Test fun caroneUsesExactEanFilterAndPersistentCache() = runBlocking {
        var calls = 0
        val http = client { request ->
            calls++
            assertEquals("www.carone.com.br", request.url.host)
            assertEquals("GET", request.method)
            assertEquals("alternateIds_Ean:$ean", request.url.queryParameter("fq"))
            200 to """[{"items":[${sku(wrong)},${sku()}]}]"""
        }
        assertEquals(carone, StructuredEanImageRepository(context, http).lookup("carone", ean).url)
        assertEquals(carone, StructuredEanImageRepository(context, http).lookup("carone", ean).url)
        assertEquals(1, calls)
    }

    @Test fun caroneSupportsEightDigitEanAndRejectsWrongOrAmbiguousSku() = runBlocking {
        val small = "78933873"
        assertEquals(carone, StructuredEanImageRepository(context, client { request ->
            assertEquals("alternateIds_Ean:$small", request.url.queryParameter("fq"))
            200 to """[{"items":[${sku(small)}]}]"""
        }).lookup("carone", small).url)
        for (items in listOf(sku(wrong), sku() + "," + sku(), sku(image = "https://example.com/wrong.png"), sku(image = ""))) {
            clear()
            assertNull(StructuredEanImageRepository(context, client { 200 to """[{"items":[$items]}]""" }).lookup("carone", ean).url)
        }
    }

    @Test fun mateusIgnoresFuzzySearchHitsAndVerifiesRequestBody() = runBlocking {
        val repository = StructuredEanImageRepository(context, client { request ->
            assertEquals("POST", request.method)
            assertEquals("https://app.mateusmais.com.br/api/products/internal/v1/service/", request.url.toString())
            val buffer = okio.Buffer()
            request.body!!.writeTo(buffer)
            val body = JSONObject(buffer.readUtf8())
            assertEquals("query=$ean&hitsPerPage=10&page=0", body.getString("params"))
            assertEquals("meilisearch", body.getString("service"))
            assertEquals("SHOWCASE_catalog_product_api_index_PROD", body.getString("index"))
            200 to """{"hits":[${hit(wrong, "https://ik.imagekit.io/glm5kjz1tud/products/None/$wrong.png")},${hit()}]}"""
        })
        assertEquals(mateus, repository.lookup("mateus", ean).url)
    }

    @Test fun mateusNeverBorrowsPhotoFromAnotherProductOrForeignTenant() = runBlocking {
        for (hits in listOf(hit(wrong), hit(image = "") + "," + hit(wrong),
            hit(image = "https://ik.imagekit.io/other-tenant/products/photo.png"),
            hit() + "," + hit(image = "$mateus&other=1"))) {
            clear()
            assertNull(StructuredEanImageRepository(context, client { 200 to """{"hits":[$hits]}""" }).lookup("mateus", ean).url)
        }
    }

    @Test fun emptyCatalogIsNormalAndCachesIndependentlyForEachSource() = runBlocking {
        val calls = mutableListOf<String>()
        val repository = StructuredEanImageRepository(context, client { request ->
            calls += request.url.host
            if (request.url.host == "www.carone.com.br") 200 to "[]" else 200 to """{"hits":[]}"""
        })
        for (source in listOf("carone", "mateus")) {
            assertNull(repository.lookup(source, ean).url)
            assertNull(repository.lookup(source, ean).retryAt)
        }
        assertEquals(2, calls.size)
    }

    @Test fun namesInternalCodesAndInvalidEansNeverContactNewSources() = runBlocking {
        var calls = 0
        val repository = StructuredEanImageRepository(context, client { calls++; 200 to "{}" })
        for (source in listOf("carone", "mateus")) for (code in listOf(null, "", "250090", "Açúcar União", "7891910000198")) {
            assertNull(repository.lookup(source, code).url)
        }
        assertEquals(0, calls)
    }

    @Test fun temporarySourceFailureDoesNotPoisonOtherSourcesOrBecomePermanentMiss() = runBlocking {
        var clock = 1L
        var calls = 0
        val repository = StructuredEanImageRepository(context, client { request ->
            calls++
            if (request.url.host == "www.carone.com.br") 503 to "unavailable"
            else 200 to """{"hits":[${hit()}]}"""
        }, { clock })
        assertNotNull(repository.lookup("carone", ean).retryAt)
        assertEquals(mateus, repository.lookup("mateus", ean).url)
        repository.lookup("carone", ean)
        assertEquals(2, calls)
        clock += 300_001
        repository.lookup("carone", ean)
        assertEquals(3, calls)
    }
}
