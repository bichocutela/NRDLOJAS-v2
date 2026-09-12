package com.example.data.flyer

import com.example.data.Product
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class FlyerOfferParserTest {
    private fun block(page: Int, text: String, left: Int, top: Int, right: Int, bottom: Int) =
        FlyerTextBlock(page, text, left, top, right, bottom, 1000, 1400)

    @Test
    fun parsesValidityAndSecondUnitPercentages() {
        val draft = FlyerOfferParser.parse(
            "Encarte Semanal-64231.pdf",
            listOf(
                block(1, "OFERTAS VÁLIDAS DE 09 A 15.09.2026", 250, 40, 750, 90),
                block(1, "Seleção de Vinhos e Espumantes", 200, 520, 480, 570),
                block(1, "50% DE DESCONTO NA SEGUNDA UNIDADE", 250, 580, 470, 640),
                block(1, "Todos os Lava-roupas Ala, Omo ou Brilhante", 580, 510, 900, 570),
                block(1, "40% DE DESCONTO NA 2ª UNIDADE", 630, 580, 850, 640)
            )
        )

        assertEquals("2026-09-09", draft.validFrom)
        assertEquals("2026-09-15", draft.validTo)
        val secondUnit = draft.offers.filter { it.type == FlyerOfferType.SECOND_UNIT_PERCENT }
        assertEquals(2, secondUnit.size)
        assertTrue(secondUnit.any { it.secondUnitDiscountPercent == 50.0 })
        assertTrue(secondUnit.any { it.secondUnitDiscountPercent == 40.0 })
        assertTrue(secondUnit.all { it.scope == FlyerOfferScope.GROUP })
    }

    @Test
    fun separatesTakePayQuantityFromMeasure() {
        val draft = FlyerOfferParser.parse(
            "encarte.pdf",
            listOf(
                block(1, "OFERTAS VÁLIDAS DE 09 A 15.09.2026", 100, 20, 800, 60),
                block(1, "Bebida Láctea Whey 15g Nescau Nestlé tb 250ml", 100, 300, 420, 350),
                block(1, "Leve 4 Pague 3", 160, 365, 360, 405),
                block(1, "Água Sanitária Brilux", 550, 300, 820, 350),
                block(1, "Leve 1 litro Pague 900ml", 590, 365, 850, 410)
            )
        )

        val quantity = draft.offers.single { it.type == FlyerOfferType.TAKE_PAY_QUANTITY }
        assertEquals(4.0, quantity.takeQuantity!!, 0.0)
        assertEquals(3.0, quantity.payQuantity!!, 0.0)

        val measure = draft.offers.single { it.type == FlyerOfferType.TAKE_PAY_MEASURE }
        assertEquals(1.0, measure.takeQuantity!!, 0.0)
        assertEquals("L", measure.takeUnit)
        assertEquals(900.0, measure.payQuantity!!, 0.0)
        assertEquals("ml", measure.payUnit)
    }

    @Test
    fun ignoresClubRulesFromFlyer() {
        val draft = FlyerOfferParser.parse(
            "encarte.pdf",
            listOf(
                block(1, "OFERTAS VÁLIDAS DE 09 A 15.09.2026", 100, 20, 800, 60),
                block(1, "Clube de Vantagens 50% de desconto na segunda unidade", 100, 300, 700, 360)
            )
        )
        assertTrue(draft.offers.isEmpty())
    }

    @Test
    fun secondUnitMathMatchesKnownTiroliroExample() {
        val result = calculateSecondUnit(45.49, 50.0)
        assertNotNull(result)
        assertEquals(22.75, result!!.first, 0.0)
        assertEquals(34.12, result.second, 0.0)
    }

    @Test
    fun takePayAverageUsesPayOverTake() {
        assertEquals(34.12, calculateTakePayAverage(45.49, 4.0, 3.0)!!, 0.0)
    }

    @Test
    fun flyerExpiresAutomaticallyAndOnlyConfirmedOfferMatches() {
        val campaign = FlyerCampaign(
            name = "Semanal",
            sourceType = "gallery",
            sourceLabel = "encarte.pdf",
            validFrom = "2026-09-09",
            validTo = "2026-09-15"
        )
        val active = LocalDate.of(2026, 9, 12).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val expired = LocalDate.of(2026, 9, 16).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals(FlyerCampaignStatus.ACTIVE, campaign.statusAt(active))
        assertEquals(FlyerCampaignStatus.EXPIRED, campaign.statusAt(expired))

        val product = Product(code = "7891000248768", name = "Chocolate KitKat 4 Fingers", searchName = "kitkat", category = "Mercearia")
        val confirmed = FlyerOffer(
            type = FlyerOfferType.TAKE_PAY_QUANTITY,
            sourceDescription = "Chocolate KitKat 4 Fingers",
            confidence = 0.95,
            reviewed = true, takeQuantity = 4.0, payQuantity = 3.0,
            matchStatus = FlyerMatchStatus.CONFIRMED,
            barcodes = listOf("7891000248768")
        )
        val review = confirmed.copy(id = "review", matchStatus = FlyerMatchStatus.REVIEW)
        assertTrue(confirmed.matches(product))
        assertFalse(review.matches(product))
    }
}
