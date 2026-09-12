package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConsultationAppearanceSettingsTest {
    @Test
    fun activeConsultationBackground_prefersMostRecentActiveStart() {
        val older = ThemeBackground(
            id = "older",
            label = "Antigo",
            url = "https://example.com/older.jpg",
            isActive = true,
            startDate = "2026-09-01"
        )
        val newer = ThemeBackground(
            id = "newer",
            label = "Novo",
            url = "https://example.com/newer.jpg",
            isActive = true,
            startDate = "2026-09-10"
        )
        val settings = AppearanceSettings(consultationBackgrounds = listOf(older, newer))

        assertEquals(newer, settings.activeConsultationBackground("2026-09-12"))
    }

    @Test
    fun activeConsultationBackground_returnsNullOutsideSchedule() {
        val scheduled = ThemeBackground(
            id = "scheduled",
            label = "Campanha",
            url = "https://example.com/campaign.jpg",
            isActive = true,
            startDate = "2026-10-01",
            endDate = "2026-10-31"
        )
        val settings = AppearanceSettings(consultationBackgrounds = listOf(scheduled))

        assertNull(settings.activeConsultationBackground("2026-09-12"))
    }
}
