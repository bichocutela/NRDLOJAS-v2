package com.example.data.flyer

import org.junit.Assert.*
import org.junit.Test

class FlyerReviewTest {
    private fun offer() = FlyerOffer(type = FlyerOfferType.SECOND_UNIT_PERCENT,
        sourceDescription = "Vinho Branco Tiroliro GF 750ml", productCodes = listOf("2012568001"),
        barcodes = listOf("5604885098906"), regularPrice = 45.49, secondUnitDiscountPercent = 50.0)

    @Test fun identitySuggestionNeedsHumanReview() {
        assertFalse(offer().copy(matchStatus = FlyerMatchStatus.CONFIRMED).matchesAcp("2012568001", ""))
        val approved = offer().confirmedForPublication()!!
        assertTrue(approved.matchesAcp("2012568001", ""))
        assertTrue(approved.matchesAcp("", "5604885098906"))
        assertFalse(approved.matchesAcp("", "560488509890"))
        assertFalse(approved.matchesAcp("", ""))
        assertEquals(34.12, approved.equivalentUnitPrice!!, 0.001)
    }
    @Test fun noRuleInferredFromTakeQuantity() {
        assertNull(offer().copy(secondUnitDiscountPercent = null, takeQuantity = 3.0).confirmedForPublication())
        assertNull(offer().copy(productCodes = emptyList(), barcodes = emptyList()).confirmedForPublication())
        assertNull(offer().copy(regularPrice = Double.NaN).confirmedForPublication())
    }
    @Test fun quantityAndMeasureHaveDifferentValidation() {
        val quantity = offer().copy(type = FlyerOfferType.TAKE_PAY_QUANTITY, takeQuantity = 12.0, payQuantity = 11.0)
        assertNotNull(quantity.confirmedForPublication())
        assertNull(quantity.copy(payQuantity = 12.0).confirmedForPublication())
        assertNull(quantity.copy(payQuantity = 1.5).confirmedForPublication())
        val measure = quantity.copy(type = FlyerOfferType.TAKE_PAY_MEASURE, takeQuantity = 1.0,
            payQuantity = 900.0, takeUnit = "L", payUnit = "ml")
        assertNotNull(measure.confirmedForPublication())
        assertNull(measure.copy(payUnit = "g").confirmedForPublication())
    }
    @Test fun cashbackDoesNotBecomeDiscount() {
        val cashback = offer().copy(type = FlyerOfferType.CASHBACK, cashbackPercent = 30.0,
            regularPrice = 36.99, flyerPrice = 36.99).confirmedForPublication()!!
        assertEquals(36.99, cashback.flyerPrice!!, 0.0)
        assertNull(cashback.equivalentUnitPrice)
        assertNull(cashback.secondUnitPrice)
        assertNull(cashback.copy(cashbackPercent = 130.0).confirmedForPublication())
    }
}
