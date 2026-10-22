package com.example.data.promotions

import android.app.Application
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class StoreProductImageRepositoryTest {
    private val session = "public-store-session-".repeat(4)
    private val photo = "8fde000e-da0b-48a2-bd13-4b5eb95f2be5.jpg"
    private fun client(handler: (Request) -> Pair<Int, String>) = OkHttpClient.Builder().addInterceptor { chain ->
        val request = chain.request()
        val (status, body) = handler(request)
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(status).message("fixture")
            .body(body.toResponseBody("application/json".toMediaType())).build()
    }.build()
    private fun catalog(rows: String) = """{"data":{"produtos":[$rows]}}"""
    private fun row(code: String, image: String = photo) =
        """{"codigo_erp":"$code","produto_id":11599,"descricao":"Pão Francês 380g","imagem":"$image"}"""

    @Test fun exactCodeUsesOfficialPhotoAndConcurrentReadsShareLookup() = runBlocking {
        val requests = mutableListOf<Request>()
        val repository = StoreProductImageRepository(client { request ->
            requests += request
            200 to if (request.method == "POST") """{"data":"$session"}""" else catalog(row("250090"))
        })
        val urls = List(8) { async { repository.find("250090") } }.awaitAll()
        assertTrue(urls.all { it == "https://produto-assets-vipcommerce-com-br.br-se1.magaluobjects.com/250x250/$photo" })
        assertEquals(2, requests.size)
        assertTrue(requests.all { it.url.host == "services.vipcommerce.com.br" })
        assertEquals("Bearer $session", requests.last().header("Authorization"))
    }

    @Test fun missingWrongCodeAmbiguousAndUnsafeImageStayHidden() = runBlocking {
        for (rows in listOf("", row("250702"), row("250090") + "," + row("250090"),
            row("250090", ""), row("250090", "../other.jpg"), row("250090", "null"))) {
            val repository = StoreProductImageRepository(client { request ->
                200 to if (request.method == "POST") """{"data":"$session"}""" else catalog(rows)
            })
            assertNull(repository.find("250090"))
        }
    }

    @Test fun leadingZeroesAreNotDiscardedAndInvalidCodesNeverRequest() = runBlocking {
        val paths = mutableListOf<String>()
        val repository = StoreProductImageRepository(client { request ->
            paths += request.url.encodedPath
            200 to if (request.method == "POST") """{"data":"$session"}""" else catalog(row("250090"))
        })
        assertNull(repository.find(""))
        assertNull(repository.find("../250090"))
        assertTrue(paths.isEmpty())
        assertNull(repository.find("0250090"))
        assertTrue(paths.last().contains("/0250090/"))
    }

    @Test fun missesExpireAndTemporaryFailuresDoNotBecomePermanentMisses() = runBlocking {
        var clock = 0L
        var searches = 0
        val repository = StoreProductImageRepository(client { request ->
            if (request.method == "POST") 200 to """{"data":"$session"}"""
            else {
                searches++
                when (searches) {
                    1 -> 503 to "{}"
                    2 -> 200 to catalog("")
                    else -> 200 to catalog(row("250090"))
                }
            }
        }, { clock })
        assertNull(repository.find("250090"))
        assertNull(repository.find("250090"))
        assertEquals(1, searches)
        clock += 60_000
        assertNull(repository.find("250090"))
        clock += 3_600_000
        assertNotNull(repository.find("250090"))
        assertEquals(3, searches)
    }

    @Test fun expiredStoreSessionIsRefreshedOnce() = runBlocking {
        var logins = 0
        var searches = 0
        val repository = StoreProductImageRepository(client { request ->
            if (request.method == "POST") { logins++; 200 to """{"data":"$session"}""" }
            else if (++searches == 1) 401 to "{}" else 200 to catalog(row("250090"))
        })
        assertNotNull(repository.find("250090"))
        assertEquals(2, logins)
        assertEquals(2, searches)
    }
    @Test fun barcodeCanDifferFromInternalCodeAndAcceptGtinPadding() = runBlocking {
        val repository = StoreProductImageRepository(client { request ->
            200 to if (request.method == "POST") """{"data":"$session"}"""
            else catalog("""{"produto_id":3653,"codigo_erp":2021000,"codigo_barras":"7891149840878","imagem":"$photo"}""")
        })
        assertNotNull(repository.find("7891149840878"))
        assertNotNull(repository.find("07891149840878"))
    }

    @Test fun descriptionFallbackNormalizesAccentsUnitsAndUpdatesCacheAfterRename() = runBlocking {
        val paths = mutableListOf<String>()
        val repository = StoreProductImageRepository(client { request ->
            paths += request.url.encodedPath
            200 to if (request.method == "POST") """{"data":"$session"}"""
            else if (request.url.encodedPath.contains("/999999/")) catalog("")
            else catalog("""{"produto_id":9,"codigo_erp":123456,"descricao":"Pão de Queijo Marca 0,5kg","imagem":"$photo"}""")
        })
        assertNull(repository.find("999999", "Produto inexistente"))
        assertNotNull(repository.find("999999", "Pao Queijo Marca 500g."))
        assertTrue(paths.any { it.contains("Pao+Queijo+Marca+500g.") })
    }

    @Test fun descriptionRejectsOtherSizesFlavoursAndAmbiguity() = runBlocking {
        for (rows in listOf(
            """{"produto_id":1,"descricao":"Iogurte Marca Morango 200g","imagem":"$photo"}""",
            """{"produto_id":1,"descricao":"Iogurte Marca Coco 100g","imagem":"$photo"}""",
            """{"produto_id":1,"descricao":"Iogurte Marca Morango 100g","imagem":"$photo"},{"produto_id":2,"descricao":"Iogurte Marca Morango 100g","imagem":"$photo"}"""
        )) {
            val repository = StoreProductImageRepository(client { request ->
                200 to if (request.method == "POST") """{"data":"$session"}""" else catalog(rows)
            })
            assertNull(repository.find("999999", "Iogurte Marca Morango 100g"))
        }
    }

    @Test fun descriptionNeverOverrulesAConflictingValidBarcode() = runBlocking {
        val repository = StoreProductImageRepository(client { request ->
            200 to if (request.method == "POST") """{"data":"$session"}"""
            else catalog("""{"produto_id":1,"codigo_barras":"3017620422003","descricao":"Produto Marca 100g","imagem":"$photo"}""")
        })
        assertNull(repository.find("7891149840878", "Produto Marca 100g"))
    }

}
