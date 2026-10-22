package com.example.ui

import com.example.data.promotions.promotionNumericPrice
import com.example.data.promotions.promotionDiscountPercent
import org.junit.Assert.*
import org.junit.Test

class PromotionSortingTest {
    private fun offer(id: String, price: String?, regular: String? = null, condition: String? = null,
        store: String = "0012", addedAt: Long = 0L, validTo: String? = null) =
        OfferGroup(id, "Alimentos", id, id, null, null, validTo,
            listOf(StoreOffer(store, price, regular, condition, null, null, promotionNumericPrice(price))), addedAt)

    @Test fun parsesDisplayedPricesAcrossFamiliesWithoutAcceptingUnknownValues() {
        assertEquals(2.54, promotionNumericPrice("R$ 2,54/un")!!, 0.00001)
        assertEquals(1234.56, promotionNumericPrice("R$ 1.234,56")!!, 0.00001)
        assertEquals(9.19, promotionNumericPrice("R$ 9,19/kg")!!, 0.00001)
        listOf(null, "", "Preço não informado", "NaN", "-1", "R$ 2,54 lixo").forEach {
            assertNull(promotionNumericPrice(it))
        }
    }

    @Test fun priceSortUsesEffectiveTakePayUnitPriceAndMissingPricesLast() {
        val rows = listOf(offer("a", "R$ 2,54/un"), offer("b", "R$ 1,79/un"), offer("c", "R$ 2,17/un"), offer("d", null))
        assertEquals(listOf("b", "c", "a", "d"), sortOfferGroups(rows, OfferSortOption.PRICE_ASC).map { it.id })
        assertEquals(listOf("a", "c", "b", "d"), sortOfferGroups(rows, OfferSortOption.PRICE_DESC).map { it.id })
    }

    @Test fun discountsUsePricesForDePorClubAndTakePayConditions() {
        val rows = listOf(offer("club", "R$ 8,00", "R$ 10,00", "Clube"),
            offer("take", "R$ 6,67/un", "R$ 10,00", "LEVE 3 • PAGUE 2"),
            offer("depor", "R$ 5,00", "R$ 10,00", "50%"), offer("unknown", null))
        assertEquals(listOf("depor", "take", "club", "unknown"), sortOfferGroups(rows, OfferSortOption.DISCOUNT_DESC).map { it.id })
        assertEquals(listOf("club", "take", "depor", "unknown"), sortOfferGroups(rows, OfferSortOption.DISCOUNT_ASC).map { it.id })
        assertNull(promotionDiscountPercent(null, null, "LEVE 3 • PAGUE 2"))
    }

    @Test fun selectedStoreAndEnabledStoresControlPricesDiscountsAndCards() {
        val a = offer("a", "R$ 1,00", "R$ 10,00", store = "0001")
            .copy(stores = offer("a", "R$ 1,00", store = "0001").stores + offer("a", "R$ 9,00").stores)
        val b = offer("b", "R$ 5,00")
        val scoped = scopeOfferGroups(listOf(a, b), "0012", setOf("0012", "0001"))
        assertEquals(listOf("b", "a"), sortOfferGroups(scoped, OfferSortOption.PRICE_ASC).map { it.id })
        assertEquals("R$ 9,00", scoped.first().bestOffer?.offerPrice)
        val all = scopeOfferGroups(listOf(a, b), "Todas", setOf("0012"))
        assertTrue(all.all { it.stores.all { store -> store.storeCode == "0012" } })
        assertTrue(scopeOfferGroups(listOf(a, b), "0002", setOf("0012")).isEmpty())
    }

    @Test fun nameExpiryAndAdditionOrderAreIndependent() {
        val rows = listOf(offer("b", "R$ 1,00", addedAt = 20, validTo = "2026-11-01"),
            offer("a", "R$ 1,00", addedAt = 10, validTo = "2026-10-20"), offer("c", "R$ 1,00"))
        assertEquals(listOf("a", "b", "c"), sortOfferGroups(rows, OfferSortOption.NAME).map { it.id })
        assertEquals(listOf("a", "b", "c"), sortOfferGroups(rows, OfferSortOption.VALID_UNTIL).map { it.id })
        assertEquals(listOf("b", "a", "c"), sortOfferGroups(rows, OfferSortOption.ADDED).map { it.id })
    }
}
