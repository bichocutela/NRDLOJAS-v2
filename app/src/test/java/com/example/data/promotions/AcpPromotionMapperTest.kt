package com.example.data.promotions

import com.example.data.acp.*
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class AcpPromotionMapperTest {
    private fun product() = AcpProduct(id = "1", code = "2021000", barcode = "7891149840878",
        description = "Produto 500ml", value = BigDecimal(12), previousValue = BigDecimal(15),
        clubValue = BigDecimal(10), wholesaleValue = null, wholesaleQuantity = null,
        quantityTake = BigDecimal(3), quantityPay = BigDecimal(2), cashback = null, cashbackValue = null,
        secondUnitDiscount = null, unitLimitPerCPF = null, unit = null, categories = listOf("De-Por"))

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
    @Test fun allFamiliesUseOfficialDepartmentAndRepairOldFallbackCategories() {
        val official = product().copy(description = "BATATA RUFFLES 68G",
            detailsJson = "{\"nrdCategory\":\"Snacks\",\"catalog_category_source\":\"official\"}")
        official.offers().filter { it.family in promotionFamilies }.forEach { offer ->
            assertEquals("Snacks", acpPromotion("0012", official, offer, null, "Hortifruti").description)
        }
        val pending = official.copy(detailsJson = null)
        pending.offers().filter { it.family in promotionFamilies }.forEach { offer ->
            assertEquals("Snacks", acpPromotion("0012", pending, offer, null, "Hortifruti").description)
        }
    }
}
