package com.example.data.acp

import com.example.data.promotions.AcpPromotionsRepository
import com.example.data.promotions.PromotionCategory
import com.example.data.promotions.PromotionStores
import com.example.data.flyer.*
import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class PromotionSourceTest {
    @Test fun categoriesUseProductNounInsteadOfIngredients() {
        assertEquals("Hortifruti", PromotionCategory.forDescription("MAÇÃ FUJI KG"))
        assertEquals("Mercearia", PromotionCategory.forDescription("Molho de tomate tradicional"))
        assertEquals("Mercearia", PromotionCategory.forDescription("Arroz com frango"))
        assertEquals("Frios e laticínios", PromotionCategory.forDescription("Leite integral"))
        assertEquals("Limpeza", PromotionCategory.forDescription("Detergente maçã"))
        assertEquals("Outras ofertas", PromotionCategory.forDescription("Produto sem categoria conhecida"))
    }

    @Test fun onlyCityGardenStartsEnabled() {
        assertEquals(listOf("0012"), PromotionStores.defaults.filter { it.enabled }.map { it.code })
    }

    @Test fun validityIsInclusiveAndUsesFortalezaDate() {
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("America/Fortaleza")
        }.parse("2026-10-07 23:59")!!.time
        assertTrue(AcpPromotionsRepository.active("07/10/2026", "2026-10-07", time))
        assertFalse(AcpPromotionsRepository.active("2026-10-08", null, time))
        assertFalse(AcpPromotionsRepository.active(null, "2026-10-06", time))
        assertNull(AcpPromotionsRepository.isoDate("31/02/2026"))
    }

    @Test fun importedOffersKeepIndividualDatesAndOnlyRealDePorPrices() {
        val offer = FlyerOffer(type = FlyerOfferType.DE_POR, sourceDescription = "Arroz branco",
            productCodes = listOf("1234"), flyerPrice = 5.0, regularPrice = 8.0,
            validFrom = "2026-10-01", validTo = "2026-10-08")
        val analysis = FlyerAnalysisResult("Relatório", "2026-10-01", "2026-10-31",
            listOf(offer, offer.copy(type = FlyerOfferType.FLYER_PRICE), offer.copy(flyerPrice = 9.0)),
            emptyList(), "visual_mix", "relatorio.pdf")
        val records = PromotionStores.records(analysis)
        assertEquals(1, records.size)
        assertEquals("2026-10-08", records.single().to)
        assertEquals(5.0, records.single().price, 0.0)
    }
}
