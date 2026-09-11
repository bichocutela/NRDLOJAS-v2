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
    // Synthetic fixtures exercise the inspected client contract; not captured ACP responses.
    private fun product(fields: String) = AcpProductParser.page(JSONObject("""{"items":[{"description":"Teste",$fields}],"pageIndex":0,"totalPages":1}"""), 0).items.single()

    @Test fun separatesPrincipalClubAndPreviousPricesAndPreservesShortCodes() {
        val item = product(""""code":"00025","barCode":"500","value":14.29,"clubValue":9.99,"previousValue":16.5""")
        assertEquals("00025", item.code)
        assertEquals("500", item.barcode)
        assertEquals("R$ 14,29", item.value?.brl())
        assertEquals("R$ 9,99", item.clubValue?.brl())
        assertEquals(listOf("De/Por", "Clube de Vantagens"), item.offers().map { it.title })
    }

    @Test fun nullZeroNegativeAndInvalidFieldsNeverBecomePromotions() {
        val item = product(""""value":null,"clubValue":0,"previousValue":-5,"cashback":"NaN","quantityTake":3,"quantityPay":0""")
        assertNull(item.value)
        assertNull(item.previousValue)
        assertTrue(item.offers().isEmpty())
    }

    @Test fun multiBuyRequiresWholeQuantitiesAndShowsConditionalEquivalent() {
        val item = product(""""value":10,"quantityTake":3,"quantityPay":2""")
        assertTrue(item.offers().single().detail.contains("R$ 6,67"))
        assertTrue(item.offers().single().detail.contains("completar a quantidade"))
        assertTrue(product(""""value":10,"quantityTake":2.5,"quantityPay":2""").offers().isEmpty())
    }

    @Test fun cashbackDoesNotReplaceMainPriceAndDatesAreNotUsedAsValidity() {
        val item = product(""""value":"10,50","cashback":10,"cashbackValue":2,"dueDate":"2099-12-31","validOffer":"2099-12-31" """)
        assertEquals("R$ 10,50", item.value?.brl())
        assertTrue(item.offers().all { it.detail.contains("Não é desconto imediato") })
        assertFalse(item.offers().any { it.detail.contains("2099") })
    }

    @Test fun posterReferencesKeepClubAndPreviousPricesSeparate() {
        val offers = product(""""value":14.29,"previousValue":16.5,"clubValue":9.99""").offers()
        assertEquals("R$ 16,50", offers.first { it.title == "De/Por" }.referencePrice?.brl())
        assertEquals("R$ 14,29", offers.first { it.title == "Clube de Vantagens" }.referencePrice?.brl())
        assertEquals("R$ 9,99", offers.first { it.title == "Clube de Vantagens" }.price?.brl())
    }

    @Test fun missingNormalPriceIsNotInventedAndSecondUnitDoesNotInventAverage() {
        assertNull(product(""""value":null,"clubValue":9.99""").offers().single().referencePrice)
        val offer = product(""""value":45.49,"secondUnitDiscount":50""").offers().single()
        assertEquals("50% DE DESCONTO", offer.headline)
        assertEquals("R$ 45,49", offer.referencePrice?.brl())
        assertNull(offer.price)
    }

    @Test(expected = AcpFailure::class) fun malformedContractIsNotAnEmptySearch() {
        AcpProductParser.page(JSONObject("""{"unexpected":[]}"""), 0)
    }
}
