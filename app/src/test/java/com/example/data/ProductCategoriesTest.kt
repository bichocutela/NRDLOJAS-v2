package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductCategoriesTest {
    private fun product(category: String = "Mercearia", memberships: String = "") = Product(
        code = "123",
        name = "Produto Teste",
        searchName = "produto teste",
        category = category,
        categoryMemberships = memberships
    )

    @Test fun legacyProductFallsBackToPrimaryCategory() {
        assertEquals(listOf("Mercearia"), product().categoryNames())
    }

    @Test fun multipleCategoriesPreserveOrderAndRemoveDuplicates() {
        val categories = listOf("Mercearia", "Ofertas", "mercearia", " Bebidas ")
        val encoded = encodeProductCategories(categories)
        val item = product(memberships = encoded)
        assertEquals(listOf("Mercearia", "Ofertas", "Bebidas"), item.categoryNames())
    }

    @Test fun withCategoryNamesKeepsFirstAsLegacyPrimary() {
        val updated = product().withCategoryNames(listOf("Ofertas", "Mercearia", "Clube"))
        assertEquals("Ofertas", updated.category)
        assertEquals(listOf("Ofertas", "Mercearia", "Clube"), updated.categoryNames())
        assertTrue(updated.categoryMemberships.isNotBlank())
    }
}
