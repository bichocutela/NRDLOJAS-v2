package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OfferBannerSettingsTest {
    @Test
    fun `active offer banner respects its offer type and schedule`() {
        val club = ThemeBackground(
            id = "club",
            label = "Clube setembro",
            url = "https://example.com/club.jpg",
            isActive = true,
            startDate = "2026-09-01",
            endDate = "2026-09-30"
        )
        val settings = AppearanceSettings(offerBanners = mapOf(OFFER_BANNER_CLUB to listOf(club)))

        assertEquals(club, settings.activeOfferBanner(OFFER_BANNER_CLUB, "2026-09-13"))
        assertNull(settings.activeOfferBanner(OFFER_BANNER_CLUB, "2026-10-01"))
        assertNull(settings.activeOfferBanner(OFFER_BANNER_CASHBACK, "2026-09-13"))
    }

    @Test
    fun `most recent active banner wins within the same offer type`() {
        val older = ThemeBackground("old", "Antigo", "https://example.com/old.jpg", true, "2026-01-01")
        val newer = ThemeBackground("new", "Novo", "https://example.com/new.jpg", true, "2026-09-01")
        val settings = AppearanceSettings(
            offerBanners = mapOf(OFFER_BANNER_STANDARD to listOf(older, newer))
        )

        assertEquals(newer, settings.activeOfferBanner(OFFER_BANNER_STANDARD, "2026-09-13"))
    }
}
