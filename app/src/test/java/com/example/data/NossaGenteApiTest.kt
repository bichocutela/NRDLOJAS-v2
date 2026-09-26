package com.example.data

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.LEGACY)
class NossaGenteApiTest {
    @Test
    fun parsesFlatPromotionsByProductAndStore() {
        val api = NossaGenteApi(ApplicationProvider.getApplicationContext())
        val json = """
            [
              {"loja":"0031","codproduto":123,"desc_prod":"PRODUTO TESTE","categoria":"HIGIENE","datainicio":"2026-08-25 00:00:00","datafim":"2026-08-31 00:00:00","preco_normal":10.0,"preco_promo":8.5,"imagem":"https://example.com/product.jpg","linkloja":"https://example.com/product"},
              {"loja":"0038","codproduto":123,"desc_prod":"PRODUTO TESTE","categoria":"HIGIENE","datainicio":"2026-08-25 00:00:00","datafim":"2026-08-31 00:00:00","preco_normal":11.0,"preco_promo":8.5,"imagem":"https://example.com/product.jpg","linkloja":"https://example.com/product"},
              {"loja":"0031","codproduto":123,"desc_prod":"PRODUTO TESTE","categoria":"HIGIENE","datainicio":"2026-08-25 00:00:00","datafim":"2026-08-31 00:00:00","preco_normal":10.0,"preco_promo":8.5,"imagem":"https://example.com/product.jpg","linkloja":"https://example.com/product"}
            ]
        """.trimIndent()

        val promotions = api.parsePromotionsForTest(json)

        assertEquals(1, promotions.size)
        assertEquals("PRODUTO TESTE", promotions.single().title)
        assertEquals(2, promotions.single().products.size)
        assertEquals("0031", promotions.single().products.first().storeCode)
        assertEquals("R$ 8,50", promotions.single().products.first().offerPrice)
        assertEquals("15%", promotions.single().products.first().discount)
        assertFalse(promotions.single().products.first().imageUrl.isNullOrBlank())
        assertFalse(promotions.single().products.first().linkUrl.isNullOrBlank())
    }

    @Test
    fun parsesAuthenticatedEmployeeProfileWithAdmissionAndTenure() {
        val api = NossaGenteApi(ApplicationProvider.getApplicationContext())
        val json = """
            {
              "data": {
                "id": 42,
                "matricula": "001234",
                "nome": "COLABORADOR TESTE",
                "dataAdmissao": "2019-03-14",
                "tempoAnos": 7,
                "foto": "https://app.nordestao.com.br/media/perfis/42.jpg"
              }
            }
        """.trimIndent()

        val profile = api.parseEmployeeProfileForTest(json)

        assertEquals("COLABORADOR TESTE", profile.name)
        assertEquals("14/03/2019", profile.admissionDate)
        org.junit.Assert.assertTrue(profile.tenure?.startsWith("7 anos") == true)
        assertEquals(7, profile.tenureYears)
        assertEquals("42", profile.employeeId)
        assertEquals("001234", profile.registration)
        assertEquals("https://app.nordestao.com.br/media/perfis/42.jpg", profile.photoUrl)
    }

    @Test
    fun parsesNestedProfilePhotoAlias() {
        val api = NossaGenteApi(ApplicationProvider.getApplicationContext())
        val json = """
            {
              "user": {
                "nome": "COLABORADOR FOTO",
                "imagem": {
                  "url": "https://app.nordestao.com.br/media/perfis/avatar.jpg"
                }
              }
            }
        """.trimIndent()

        val profile = api.parseEmployeeProfileForTest(json)

        assertEquals("COLABORADOR FOTO", profile.name)
        assertEquals("https://app.nordestao.com.br/media/perfis/avatar.jpg", profile.photoUrl)
    }

    @Test
    fun parsesDedicatedProfilePhotoPayloadsFromOfficialEndpoint() {
        val api = NossaGenteApi(ApplicationProvider.getApplicationContext())

        assertEquals(
            "https://app.nordestao.com.br/media/perfis/42.jpg",
            api.parseProfilePhotoReferenceForTest(
                """{"data":{"foto":"https://app.nordestao.com.br/media/perfis/42.jpg"}}"""
            )
        )
        assertEquals(
            "/uploads/perfis/42.jpg",
            api.parseProfilePhotoReferenceForTest(
                """{"imagem":{"url":"/uploads/perfis/42.jpg"}}"""
            )
        )
        assertEquals(
            "https://cdn.example.com/perfil.jpg",
            api.parseProfilePhotoReferenceForTest("\"https://cdn.example.com/perfil.jpg\"")
        )
    }

    @Test
    fun decodesBase64PhotoReturnedByOfficialProfileEndpoint() {
        val api = NossaGenteApi(ApplicationProvider.getApplicationContext())
        val jpeg = ByteArray(96) { 1 }.also { bytes -> bytes[0] = 0xFF.toByte(); bytes[1] = 0xD8.toByte(); bytes[2] = 0xFF.toByte(); bytes[3] = 0xE0.toByte() }
        val encoded = android.util.Base64.encodeToString(jpeg, android.util.Base64.NO_WRAP)

        assertEquals(jpeg.toList(), api.parseProfilePhotoBytesForTest("""{"data":{"foto":"$encoded"}}""")?.toList())
        assertEquals(jpeg.toList(), api.parseProfilePhotoBytesForTest("data:image/jpeg;base64,$encoded")?.toList())
    }

    @Test
    fun recognizesCommonImageSignaturesForAuthenticatedProfilePhoto() {
        val api = NossaGenteApi(ApplicationProvider.getApplicationContext())

        assertEquals(
            true,
            api.looksLikeImageBytesForTest(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x00))
        )
        assertEquals(
            true,
            api.looksLikeImageBytesForTest(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))
        )
        assertEquals(false, api.looksLikeImageBytesForTest("sem-foto".toByteArray()))
    }

    @Test
    fun formatsExactTenureInYearsAndMonths() {
        val api = NossaGenteApi(ApplicationProvider.getApplicationContext())
        val now = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.SEPTEMBER, 25, 12, 0, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }

        assertEquals("8 meses", api.formatExactTenureForTest("22/01/2026", now.timeInMillis))
        assertEquals("1 ano e 9 meses", api.formatExactTenureForTest("25/12/2024", now.timeInMillis))
        assertEquals("15 anos e 3 meses", api.formatExactTenureForTest("25/06/2011", now.timeInMillis))
    }

    @Test
    fun fingerprintIgnoresOrderOfEquivalentProducts() {
        val first = Promotion(
            id = "higiene-123",
            title = "PRODUTO TESTE",
            description = "HIGIENE",
            imageUrl = "https://example.com/product.jpg",
            validFrom = "2026-08-25",
            validTo = "2026-08-31",
            products = listOf(
                PromotionProduct("123", "PRODUTO TESTE", "R$ 8,50", "R$ 10,00", "15%", "0031", "https://example.com/product.jpg", "https://example.com/product"),
                PromotionProduct("456", "OUTRO PRODUTO", "R$ 5,00", "R$ 6,00", "17%", "0038", "https://example.com/other.jpg", "https://example.com/other")
            )
        )
        val reordered = first.copy(products = first.products.reversed())

        assertEquals(fingerprintPromotionsForTest(first.let(::listOf)), fingerprintPromotionsForTest(reordered.let(::listOf)))
    }

    @Test
    fun fingerprintChangesWhenOfferPriceChanges() {
        val base = listOf(
            Promotion(
                id = "higiene-123",
                title = "PRODUTO TESTE",
                description = "HIGIENE",
                imageUrl = "https://example.com/product.jpg",
                validFrom = "2026-08-25",
                validTo = "2026-08-31",
                products = listOf(
                    PromotionProduct(
                        code = "123",
                        name = "PRODUTO TESTE",
                        offerPrice = "R$ 8,50",
                        regularPrice = "R$ 10,00",
                        discount = "15%",
                        storeCode = "0031",
                        imageUrl = "https://example.com/product.jpg",
                        linkUrl = "https://example.com/product"
                    )
                )
            )
        )
        val changed = base.map { promotion ->
            promotion.copy(products = promotion.products.map { it.copy(offerPrice = "R$ 7,50") })
        }

        assertEquals(fingerprintPromotionsForTest(base), fingerprintPromotionsForTest(base))
        assertNotEquals(fingerprintPromotionsForTest(base), fingerprintPromotionsForTest(changed))
    }
}
