package com.example.data.promotions

import com.example.data.acp.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AcpPromotionMapperTest {
    private fun product() = AcpProductParser.page(JSONObject("""{"items":[{"id":"1","code":"2021000","barCode":"7891149840878","description":"Produto 500ml","value":12,"previousValue":15,"clubValue":10,"quantityTake":3,"quantityPay":2,"productCategories":[{"description":"De-Por"}]}]}"""), 0).items.single()

    @Test fun familiesHaveSeparateIdentitiesAndAuthoritativeConditions() {
        val product = product()
        val rows = product.offers().filter { it.family in promotionFamilies }
            .map { acpPromotion("0012", product, it, null) }
        assertEquals(setOf("De/Por", "Leve e pague", "Clube"), rows.map { it.title }.toSet())
        assertEquals(3, rows.map { it.id }.distinct().size)
        assertEquals("0012|2021000", rows.single { it.title == "De/Por" }.id)
        val take = rows.single { it.title == "Leve e pague" }.products.single()
        assertEquals("LEVE 3 • PAGUE 2", take.discount)
        assertTrue(take.offerPrice!!.endsWith("/un"))
        assertEquals("Clube", rows.single { it.title == "Clube" }.products.single().discount)
    }
}
