package com.example.data

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
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
}
