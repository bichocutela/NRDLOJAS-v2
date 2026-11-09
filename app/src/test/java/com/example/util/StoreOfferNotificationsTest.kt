package com.example.util

import com.example.data.NordestaoStoreOffer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreOfferNotificationsTest {
    private fun offer(discount: Int, cashback: String = "") = NordestaoStoreOffer(
        id = "campaign:product", description = "Produto de teste", code = "123",
        category = "Mercearia", image = "", barcode = "", priceCents = 100L,
        originalPriceCents = 300L, showPrice = true, discount = discount,
        cashback = cashback, maxAmount = 1.0, tag = "Pra você",
        startsAt = "2026-11-09T00:00:00.000Z", endsAt = "2026-11-10T00:00:00.000Z",
        validity = "10/11", conditions = "", activationRequired = false
    )

    @Test fun aboveThresholdIsStrict() {
        assertFalse(StoreOfferNotifications.matches(offer(50), StoreOfferNotifications.Mode.ABOVE_50))
        assertTrue(StoreOfferNotifications.matches(offer(51), StoreOfferNotifications.Mode.ABOVE_50))
        assertFalse(StoreOfferNotifications.matches(offer(60), StoreOfferNotifications.Mode.ABOVE_60))
        assertTrue(StoreOfferNotifications.matches(offer(61), StoreOfferNotifications.Mode.ABOVE_60))
    }

    @Test fun belowThresholdIsStrictAndExcludesZero() {
        assertFalse(StoreOfferNotifications.matches(offer(0), StoreOfferNotifications.Mode.BELOW_50))
        assertTrue(StoreOfferNotifications.matches(offer(49), StoreOfferNotifications.Mode.BELOW_50))
        assertFalse(StoreOfferNotifications.matches(offer(50), StoreOfferNotifications.Mode.BELOW_50))
        assertTrue(StoreOfferNotifications.matches(offer(50), StoreOfferNotifications.Mode.BELOW_60))
        assertFalse(StoreOfferNotifications.matches(offer(60), StoreOfferNotifications.Mode.BELOW_60))
    }

    @Test fun cashbackIsSeparateFromPriceDiscount() {
        assertTrue(StoreOfferNotifications.matches(offer(0, "12% de cashback"), StoreOfferNotifications.Mode.CASHBACK))
        assertFalse(StoreOfferNotifications.matches(offer(70), StoreOfferNotifications.Mode.CASHBACK))
        assertFalse(StoreOfferNotifications.matches(offer(80, "10% de cashback"), StoreOfferNotifications.Mode.OFF))
    }
}
