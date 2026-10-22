package com.example.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
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
    fun configuredDefaultIsUsedWhenNoScheduledBannerIsAvailable() {
        val default = background(id = "default-green").copy(isActive = false, startDate = null)
        val settings = AppearanceSettings(
            defaultThemeBackgrounds = mapOf("green" to default),
            themeBackgrounds = mapOf(
                "green" to listOf(background(startDate = "2026-08-20", endDate = "2026-08-27"))
            )
        )

        assertEquals("default-green", settings.activeBackgroundFor("green", "2026-08-28")?.id)
    }

    @Test
    fun scheduledBannerKeepsPriorityOverConfiguredDefault() {
        val default = background(id = "default-red").copy(isActive = false, startDate = null)
        val scheduled = background(id = "scheduled-red", startDate = "2026-09-01")
        val settings = AppearanceSettings(
            defaultThemeBackgrounds = mapOf("red" to default),
            themeBackgrounds = mapOf("red" to listOf(scheduled))
        )

        assertEquals("scheduled-red", settings.activeBackgroundFor("red", "2026-09-22")?.id)
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
