package com.example.data.promotions

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
class PromofarmaImageRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val ean = "7897622317608"
    private val image = "https://promofarma.vteximg.com.br/arquivos/ids/185236/Mockup-Lenco.png?v=638899037094530000"
    @Before fun clear() { context.getSharedPreferences("promofarma_ean_images_v1", Context.MODE_PRIVATE).edit().clear().commit() }
    private fun client(handler: (Request) -> Pair<Int, String>) = OkHttpClient.Builder().addInterceptor { chain ->
        val (status, body) = handler(chain.request())
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(status).message("fixture")
            .body(body.toResponseBody("application/json".toMediaType())).build()
    }.build()
    private fun sku(code: String = ean, photo: String = image) = """{"ean":"$code","images":[{"imageUrl":"$photo"}]}"""
    private fun catalog(items: String) = """[{"items":[$items]}]"""

    @Test fun exactEanFindsPhotoAndRecreationUsesPersistentCache() = runBlocking {
        var calls = 0
        val http = client { request ->
            calls++
            assertEquals("www.promofarma.com.br", request.url.host)
            assertEquals("alternateIds_Ean:$ean", request.url.queryParameter("fq"))
            assertEquals(setOf("fq"), request.url.queryParameterNames)
            200 to catalog(sku())
        }
        val repository = PromofarmaImageRepository(context, http)
        assertTrue(List(8) { async { repository.lookup(ean).url } }.awaitAll().all { it == image })
        assertEquals(image, PromofarmaImageRepository(context, http).lookup(ean).url)
        assertEquals(1, calls)
    }

    @Test fun internalCodesNamesAndInvalidEansNeverSendRequests() = runBlocking {
        var calls = 0
        val repository = PromofarmaImageRepository(context, client { calls++; 200 to "[]" })
        for (value in listOf(null, "", "250090", "Australian Gold", "7897622317609", "../7897622317608")) {
            assertNull(repository.lookup(value).url)
        }
        assertEquals(0, calls)
    }

    @Test fun onlyTheMatchingSkuSuppliesItsImage() = runBlocking {
        val body = catalog(sku("7898106008883", "https://promofarma.vteximg.com.br/arquivos/ids/1/wrong.jpg") + "," + sku())
        assertEquals(image, PromofarmaImageRepository(context, client { 200 to body }).lookup(ean).url)
    }

    @Test fun wrongEanAmbiguousSkuAndForeignImageAreRejected() = runBlocking {
        for (items in listOf(sku("7898106008883"), sku() + "," + sku(), sku(photo = "https://example.com/wrong.jpg"))) {
            clear()
            assertNull(PromofarmaImageRepository(context, client { 200 to catalog(items) }).lookup(ean).url)
        }
    }

    @Test fun confirmedMissIsCachedAndTransientFailureRetries() = runBlocking {
        var clock = 0L
        var calls = 0
        val repository = PromofarmaImageRepository(context, client {
            calls++
            when (calls) { 1 -> 503 to "{}"; 2 -> 200 to "[]"; else -> 200 to catalog(sku()) }
        }, { clock })
        assertEquals(300_000L, repository.lookup(ean).retryAt)
        assertNull(repository.lookup(ean).url)
        assertEquals(1, calls)
        clock += 300_001
        assertNull(repository.lookup(ean).retryAt)
        assertNull(repository.lookup(ean).url)
        assertEquals(2, calls)
        clock += 7 * 86_400_000L + 1
        assertEquals(image, repository.lookup(ean).url)
        assertEquals(3, calls)
    }
}
