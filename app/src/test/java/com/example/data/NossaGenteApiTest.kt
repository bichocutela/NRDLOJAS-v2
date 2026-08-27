package com.example.data

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
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
