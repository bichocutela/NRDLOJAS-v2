package com.example.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeBackgroundTest {
    @Test
    fun activeBackgroundWithoutStartDateIsNotAvailable() {
        assertFalse(background().isAvailableOn("2026-08-27"))
    }

    @Test
    fun backgroundDoesNotAppearBeforeItsStartDate() {
        assertFalse(background(startDate = "2026-08-28").isAvailableOn("2026-08-27"))
    }

    @Test
    fun endDateIsInclusiveAndDefaultReturnsOnTheFollowingDay() {
        val scheduled = background(startDate = "2026-08-20", endDate = "2026-08-27")

        assertTrue(scheduled.isAvailableOn("2026-08-20"))
        assertTrue(scheduled.isAvailableOn("2026-08-27"))
        assertFalse(scheduled.isAvailableOn("2026-08-28"))
    }

    @Test
    fun appearanceFallsBackToThemeDefaultAfterEndDate() {
        val settings = AppearanceSettings(
            themeBackgrounds = mapOf(
                "green" to listOf(background(startDate = "2026-08-20", endDate = "2026-08-27"))
            )
        )

        assertNull(settings.activeBackgroundFor("green", "2026-08-28"))
    }

    @Test
    fun invalidLegacyDateDoesNotActivateBackground() {
        assertFalse(background(startDate = "27/08/2026").isAvailableOn("2026-08-27"))
    }

    @Test
    fun mostRecentlyStartedActiveBackgroundWinsWhenPeriodsOverlap() {
        val settings = AppearanceSettings(
            themeBackgrounds = mapOf(
                "red" to listOf(
                    background(id = "older", startDate = "2026-01-01"),
                    background(id = "newer", startDate = "2026-12-01")
                )
            )
        )

        assertTrue(settings.activeBackgroundFor("red", "2026-12-20")?.id == "newer")
    }

    private fun background(
        id: String = "background-1",
        startDate: String? = null,
        endDate: String? = null
    ) = ThemeBackground(
        id = id,
        label = "Fundo de teste",
        url = "https://example.com/background.jpg",
        isActive = true,
        startDate = startDate,
        endDate = endDate
    )
}
