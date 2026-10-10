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
    @Test fun separatesPackagedPotatoesFromFreshAndFrozenVegetables() {
        listOf("BATATA RUFFLES ELMA CHIPS PC 70G HOT DOG", "BATATA PALHA YOKI 105G",
            "BATATA ONDULADA 115G", "BATATA FRITA LAYS 80G", "BANANA CHIPS 100G", "SALGADINHO CHEETOS 90G").forEach {
            assertEquals(it, "Snacks", PromotionCategory.resolve(it, null, "Hortifruti"))
        }
        assertEquals("Hortifruti", PromotionCategory.resolve("BATATA INGLESA KG", null))
        assertEquals("Hortifruti", PromotionCategory.resolve("BATATA DOCE KG", null))
        assertEquals("Congelados", PromotionCategory.resolve("BATATA FRITA CONGELADA 1KG", null, "Hortifruti"))
        assertEquals("Snacks", PromotionCategory.resolve("BATATA PALHA YOKI 105G", "Snacks", "Hortifruti"))
        assertEquals("Alimentos", PromotionCategory.resolve("BATATA PALHA 105G", "Alimentos"))
    }
    @Test fun processedIngredientsAlcoholBabyAndHouseholdHaveDistinctDepartments() {
        assertEquals("Alimentos", PromotionCategory.resolve("TOMATE PELADO LATA 400G", null, "Hortifruti"))
        assertEquals("Hortifruti", PromotionCategory.resolve("TOMATE CEREJA 250G", null))
        assertEquals("Alimentos", PromotionCategory.resolve("LEITE CONDENSADO 395G", null))
        assertEquals("Leites e laticínios", PromotionCategory.resolve("LEITE INTEGRAL 1L", null))
        assertEquals("Bebidas alcoólicas", PromotionCategory.resolve("CERVEJA 350ML", null, "Bebidas"))
        assertEquals("Bebês e crianças", PromotionCategory.resolve("FRALDA INFANTIL 20UN", null))
        assertEquals("Bazar", PromotionCategory.resolve("PAPEL ALUMINIO 7 5M", null))
    }
}

