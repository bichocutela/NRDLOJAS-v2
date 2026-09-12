package com.example.data.acp

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AcpProductsTest {
    private fun product(fields: String) = AcpProductParser.page(JSONObject("""{"items":[{"description":"Teste",$fields}],"pageIndex":0,"totalPages":1}"""), 0).items.single()

    @Test fun separatesPrincipalClubAndPreviousPricesAndPreservesShortCodes() {
        val item = product(""""code":"00025","barCode":"500","value":14.29,"clubValue":9.99,"previousValue":16.5,"productCategories":[{"id":3,"description":"De-Por"},{"id":5,"description":"Clube de Vantagens"}]""")
        assertEquals("00025", item.code)
        assertEquals("500", item.barcode)
        assertEquals("R$ 14,29", item.value?.brl())
        assertEquals("R$ 9,99", item.clubValue?.brl())
        assertEquals(listOf("De/Por", "Clube de Vantagens"), item.offers().map { it.title })
    }

    @Test fun realKitKatPayloadKeepsDePorClubAndStockTogether() {
        val item = product(""""code":"2013995003","barCode":"7891000248768","value":3.49,"previousValue":6.69,"clubValue":3.49,"stockQuantity":624,"packageQuantity":3,"packageType":{"id":7,"description":"SUB"},"unit":{"id":2,"description":"cada"},"productCategories":[{"id":1,"description":"Varejo"},{"id":3,"description":"De-Por"},{"id":5,"description":"Clube de Vantagens"}]""")
        assertEquals("R$ 3,49", item.value?.brl())
        assertEquals("R$ 6,69", item.previousValue?.brl())
        assertEquals("R$ 3,49", item.clubValue?.brl())
        assertEquals("624", item.stockQuantity?.quantity())
        assertEquals("3", item.packageQuantity?.quantity())
        assertEquals("SUB", item.packageType)
        assertEquals("cada", item.unit)
        assertEquals(listOf("De/Por", "Clube de Vantagens"), item.offers().map { it.title })
        assertEquals("R$ 6,69", item.offers().first { it.title == "Clube de Vantagens" }.referencePrice?.brl())
    }

    @Test fun realClubOnlyKitKatDoesNotInventDePorFromPreviousValue() {
        val item = product(""""code":"2013995014","barCode":"7891000469569","value":3.49,"previousValue":6.69,"clubValue":3.49,"stockQuantity":0,"productCategories":[{"id":1,"description":"Varejo"},{"id":5,"description":"Clube de Vantagens"}]""")
        val offers = item.offers()
        assertEquals(listOf("Clube de Vantagens"), offers.map { it.title })
        assertEquals("R$ 3,49", offers.single().price?.brl())
        assertEquals("R$ 6,69", offers.single().referencePrice?.brl())
        assertFalse(offers.any { it.title == "De/Por" })
    }

    @Test fun previousValueAloneIsReferenceDataNotProofOfDePor() {
        val item = product(""""value":8.99,"previousValue":12.99,"productCategories":[{"id":1,"description":"Varejo"}]""")
        assertTrue(item.offers().isEmpty())
    }

    @Test fun realRavanalPayloadIsLevePagueAndUsesProductStock() {
        val item = product(""""code":"2012718001","barCode":"7804374000405","value":47.99,"quantityTake":3,"quantityPay":2,"stockQuantity":26,"packageQuantity":12,"packageType":{"id":1,"description":"Caixa"},"productCategories":[{"id":1,"description":"Varejo"},{"id":6,"description":"Leve-Pague"}]""")
        assertEquals("26", item.stockQuantity?.quantity())
        assertEquals("12", item.packageQuantity?.quantity())
        assertEquals("Caixa", item.packageType)
        val offer = item.offers().single()
        assertEquals("Leve/Pague", offer.title)
        assertEquals("LEVE 3 • PAGUE 2", offer.headline)
        assertEquals("R$ 47,99", offer.referencePrice?.brl())
        assertEquals("R$ 31,99", offer.price?.brl())
        assertTrue(offer.detail.contains("Leve 3, pague 2"))
    }

    @Test fun realTiroliroPayloadDoesNotInventSecondUnitDiscount() {
        val item = product(""""code":"2012568001","barCode":"5604885098906","value":45.49,"quantityTake":3,"quantityPay":null,"stockQuantity":23,"packageQuantity":6,"packageType":{"id":1,"description":"Caixa"}""")
        assertEquals("R$ 45,49", item.value?.brl())
        assertEquals("23", item.stockQuantity?.quantity())
        assertTrue(item.offers().isEmpty())
    }

    @Test fun nullZeroNegativeAndInvalidFieldsNeverBecomePromotions() {
        val item = product(""""value":null,"clubValue":0,"previousValue":-5,"cashback":"NaN","quantityTake":3,"quantityPay":0""")
        assertNull(item.value)
        assertNull(item.previousValue)
        assertTrue(item.offers().isEmpty())
    }

    @Test fun multiBuyRequiresWholeQuantitiesAndShowsConditionalEquivalent() {
        val item = product(""""value":10,"quantityTake":3,"quantityPay":2""")
        val offer = item.offers().single()
        assertEquals("R$ 6,67", offer.price?.brl())
        assertEquals("R$ 10,00", offer.referencePrice?.brl())
        assertEquals("LEVE 3 • PAGUE 2", offer.headline)
        assertTrue(offer.detail.contains("Média equivalente"))
        assertTrue(offer.detail.contains("completar a quantidade"))
        assertTrue(product(""""value":10,"quantityTake":2.5,"quantityPay":2""").offers().isEmpty())
    }

    @Test fun wholesaleKeepsRetailReferenceAndMinimumQuantity() {
        val offer = product(""""value":12.99,"wholesaleValue":9.49,"wholesaleQuantity":10""").offers().single()
        assertEquals("Atacado", offer.title)
        assertEquals("R$ 12,99", offer.referencePrice?.brl())
        assertEquals("R$ 9,49", offer.price?.brl())
        assertEquals("A PARTIR DE 10 UN.", offer.headline)
        assertTrue(offer.detail.contains("A partir de 10 unidades"))
    }

    @Test fun cashbackDoesNotReplaceMainPriceAndDatesAreNotUsedAsValidity() {
        val item = product(""""value":"10,50","cashback":10,"cashbackValue":2,"dueDate":"2099-12-31","validOffer":"2099-12-31" """)
        assertEquals("R$ 10,50", item.value?.brl())
        assertEquals("2099-12-31", item.dueDate)
        val offers = item.offers()
        assertTrue(offers.all { it.detail.contains("Não é desconto imediato") })
        assertTrue(offers.all { it.referencePrice?.brl() == "R$ 10,50" })
        assertTrue(offers.all { it.price == null })
        assertEquals("10% DE VOLTA", offers.first { it.title == "Cashback" }.headline)
        assertEquals("R$ 2,00 DE VOLTA", offers.first { it.title == "Cashback em valor" }.headline)
        assertFalse(offers.any { it.detail.contains("2099") })
    }

    @Test fun posterReferencesKeepCurrentPreviousAndClubPricesSeparate() {
        val offers = product(""""value":14.29,"previousValue":16.5,"clubValue":9.99,"productCategories":[{"id":3,"description":"De-Por"},{"id":5,"description":"Clube de Vantagens"}]""").offers()
        assertEquals("R$ 16,50", offers.first { it.title == "De/Por" }.referencePrice?.brl())
        assertEquals("R$ 14,29", offers.first { it.title == "Clube de Vantagens" }.referencePrice?.brl())
        assertEquals("R$ 9,99", offers.first { it.title == "Clube de Vantagens" }.price?.brl())
    }

    @Test fun clubFallsBackToPreviousReferenceOnlyWhenCurrentEqualsClub() {
        val offer = product(""""value":3.49,"previousValue":6.69,"clubValue":3.49,"productCategories":[{"id":5,"description":"Clube de Vantagens"}]""").offers().single()
        assertEquals("Clube de Vantagens", offer.title)
        assertEquals("R$ 6,69", offer.referencePrice?.brl())
        assertEquals("R$ 3,49", offer.price?.brl())
    }

    @Test fun missingNormalPriceIsNotInventedAndSecondUnitDoesNotInventAverage() {
        assertNull(product(""""value":null,"clubValue":9.99""").offers().single().referencePrice)
        val offer = product(""""value":45.49,"secondUnitDiscount":50""").offers().single()
        assertEquals("50% DE DESCONTO", offer.headline)
        assertEquals("R$ 45,49", offer.referencePrice?.brl())
        assertNull(offer.price)
    }

    @Test fun pagePreservesTotalCountFromProductAll() {
        val page = AcpProductParser.page(JSONObject("""
            {"items":[{"id":"1","description":"KitKat"}],"pageIndex":1,"totalPages":3,"totalCount":43}
        """), 1)
        assertEquals(1, page.pageIndex)
        assertEquals(3, page.totalPages)
        assertEquals(43, page.totalCount)
    }

    @Test fun acpDatesAreFormattedWithoutChangingUnknownValues() {
        assertEquals("12/09/2026", acpDateLabel("2026-09-12"))
        assertEquals("12/09/2026", acpDateLabel("2026-09-12T02:25:08"))
        assertEquals("12/09/2026 02:25", acpDateLabel("2026-09-12T02:25:08", includeTime = true))
        assertEquals("texto da ACP", acpDateLabel("texto da ACP", includeTime = true))
        assertNull(acpDateLabel(null))
    }

    @Test fun exactBarcodeAndCodeArePrioritizedWithoutChangingCount() {
        val page = AcpProductParser.page(JSONObject("""
            {"items":[
              {"id":"1","description":"Outro","code":"111","barCode":"123"},
              {"id":"2","description":"Alvo","code":"222","barCode":"7891000248768"}
            ],"pageIndex":0,"totalPages":1,"totalCount":2}
        """), 0)
        val byBarcode = page.prioritizeExact(AcpSearchField.BARCODE, "7891000248768")
        assertEquals("2", byBarcode.items.first().id)
        assertEquals(2, byBarcode.totalCount)
        val byCode = page.prioritizeExact(AcpSearchField.CODE, "111")
        assertEquals("1", byCode.items.first().id)
        assertEquals(listOf("1", "2"), page.prioritizeExact(AcpSearchField.DESCRIPTION, "Alvo").items.map { it.id })
    }

    @Test(expected = AcpFailure::class) fun malformedContractIsNotAnEmptySearch() {
        AcpProductParser.page(JSONObject("""{"unexpected":[]}"""), 0)
    }
    @Test fun reportedTiroliroPartialPayloadDoesNotInventSecondUnitOrMultiBuy() {
        // Fields reported by the owner from Product/all; not a fresh captured HTTP response.
        // The fixture intentionally omits unobserved fields and uses a synthetic page envelope.
        val observed = JSONObject("""{
            "description":"Vinho Branco Tiroliro GF 750ml",
            "code":"2012568001","barCode":"5604885098906",
            "value":45.49,"quantityTake":3,"quantityPay":null
        }""")
        val item = AcpProductParser.page(JSONObject().put("items", org.json.JSONArray().put(observed)), 0).items.single()
        assertEquals("R$ 45,49", item.value?.brl())
        assertEquals("3", item.quantityTake?.quantity())
        assertNull(item.quantityPay)
        assertNull(item.secondUnitDiscount)
        assertTrue(item.offers().isEmpty())
    }

}
