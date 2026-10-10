package com.example.data.promotions
import org.junit.Assert.assertEquals
import org.junit.Test
class PromotionCategoryTest {
    @Test fun prefersOfficialDepartmentAndRetainsConfirmedCategory() {
        assertEquals("Bebidas alcoólicas", PromotionCategory.resolve("BEATS G&T 269ML", "Bebidas alcoólicas"))
        assertEquals("Bebidas alcoólicas", PromotionCategory.resolve("BEATS G&T 269ML", null, "Bebidas alcoólicas"))
        assertEquals("Alimentos", PromotionCategory.resolve("ARROZ 1KG", "Mercearia"))
    }
    @Test fun distinguishesProductsWithSameLeadingWord() {
        assertEquals("Higiene e beleza", PromotionCategory.forDescription("ÁGUA MICELAR LOREAL 200ML"))
        assertEquals("Limpeza", PromotionCategory.forDescription("ÁGUA SANITÁRIA YPE 1L"))
        assertEquals("Bebidas", PromotionCategory.forDescription("ÁGUA MINERAL 500ML"))
        assertEquals("Higiene e beleza", PromotionCategory.forDescription("LEITE DE ROSAS 100ML"))
        assertEquals("Frios e laticínios", PromotionCategory.forDescription("LEITE INTEGRAL 1L"))
    }
}

