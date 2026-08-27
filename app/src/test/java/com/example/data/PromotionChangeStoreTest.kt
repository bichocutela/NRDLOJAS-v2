package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromotionChangeStoreTest {
    @Test
    fun identicalSnapshotsDoNotGenerateEvents() {
        val baseline = listOf(snapshot())

        val changes = calculatePromotionChanges(baseline, baseline)

        assertTrue(changes.isEmpty())
    }

    @Test
    fun emptyBaselineDoesNotGenerateEvents() {
        val changes = calculatePromotionChanges(emptyList(), listOf(snapshot()))

        assertTrue(changes.isEmpty())
    }

    @Test
    fun newLineAgainstExistingBaselineIsRecognizedAsAdded() {
        val changes = calculatePromotionChanges(
            previous = listOf(snapshot()),
            current = listOf(snapshot(), snapshot(productCode = "456", storeCode = "0038"))
        )

        assertEquals(1, changes.size)
        assertEquals(PromotionChangeType.ADDED, changes.single().type)
        assertEquals("0038", changes.single().storeCode)
    }

    @Test
    fun missingLineIsRecognizedAsRemovedAndKeepsPreviousValues() {
        val changes = calculatePromotionChanges(listOf(snapshot()), emptyList())

        assertEquals(1, changes.size)
        assertEquals(PromotionChangeType.REMOVED, changes.single().type)
        assertEquals("R$ 8,50", changes.single().oldOfferPrice)
        assertEquals("2026-08-31", changes.single().oldValidTo)
    }

    @Test
    fun onlyOfferPriceChangeIsMarkedAsPriceChanged() {
        val changed = snapshot().copy(offerPrice = "R$ 7,50", discount = "25%")

        val changes = calculatePromotionChanges(listOf(snapshot()), listOf(changed))

        assertEquals(1, changes.size)
        assertEquals(PromotionChangeType.CHANGED, changes.single().type)
        assertTrue(changes.single().priceChanged)
        assertFalse(changes.single().validityChanged)
        assertEquals("R$ 8,50", changes.single().oldOfferPrice)
        assertEquals("R$ 7,50", changes.single().newOfferPrice)
    }

    @Test
    fun onlyValidityChangeIsMarkedAsValidityChanged() {
        val changed = snapshot().copy(validTo = "2026-09-02")

        val changes = calculatePromotionChanges(listOf(snapshot()), listOf(changed))

        assertEquals(1, changes.size)
        assertEquals(PromotionChangeType.CHANGED, changes.single().type)
        assertFalse(changes.single().priceChanged)
        assertTrue(changes.single().validityChanged)
        assertEquals("2026-08-31", changes.single().oldValidTo)
        assertEquals("2026-09-02", changes.single().newValidTo)
    }

    @Test
    fun productIdentityDoesNotIncludePrice() {
        val changed = snapshot().copy(offerPrice = "R$ 7,50")

        val changes = calculatePromotionChanges(listOf(snapshot()), listOf(changed))

        assertEquals(1, changes.size)
        assertEquals("123", changes.single().productCode)
        assertEquals("0031", changes.single().storeCode)
    }

    private fun snapshot(
        productCode: String = "123",
        storeCode: String = "0031"
    ) = PromotionOfferSnapshot(
        storeCode = storeCode,
        productCode = productCode,
        productName = "PRODUTO TESTE",
        category = "HIGIENE",
        offerPrice = "R$ 8,50",
        regularPrice = "R$ 10,00",
        discount = "15%",
        validFrom = "2026-08-25",
        validTo = "2026-08-31",
        imageUrl = "https://example.com/product.jpg",
        linkUrl = "https://example.com/product"
    )
}
