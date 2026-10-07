package com.example.data.promotions
import org.junit.Assert.*
import org.junit.Test
class GtinTest {
    @Test fun checksDigitAndRejectsInternalCodes() {
        assertTrue(Gtin.valid("7891000315507"))
        assertTrue(Gtin.valid("3017620422003"))
        assertFalse(Gtin.valid("3017620422004"))
        assertFalse(Gtin.valid("254931"))
        assertFalse(Gtin.valid("301762042200x"))
    }
    @Test fun selectsRelevantCatalogWithoutFoodLookupsForCosmetics() {
        assertEquals(FactsFamily.BEAUTY, FactsFamily.forCategory("Higiene e beleza"))
        assertEquals(FactsFamily.PET, FactsFamily.forCategory("Pet"))
        assertEquals(FactsFamily.PRODUCTS, FactsFamily.forCategory("Limpeza"))
    }
}
