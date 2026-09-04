package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class AppearanceSettingsTest {

    @Test
    fun mostRecentAppearanceSettings_prefersNewerFirestoreRevision() {
        val manifest = AppearanceSettings(theme = "red", revision = 10L)
        val firestore = AppearanceSettings(theme = "blue", revision = 11L)

        assertEquals(firestore, mostRecentAppearanceSettings(manifest, firestore))
    }

    @Test
    fun mostRecentAppearanceSettings_prefersManifestWhenRevisionsMatch() {
        val manifest = AppearanceSettings(theme = "red", revision = 10L)
        val firestore = AppearanceSettings(theme = "blue", revision = 10L)

        assertEquals(manifest, mostRecentAppearanceSettings(manifest, firestore))
    }

    @Test
    fun mostRecentAppearanceSettings_usesAvailableFallback() {
        val firestore = AppearanceSettings(theme = "green", revision = 4L)

        assertEquals(firestore, mostRecentAppearanceSettings(null, firestore))
    }

    @Test
    fun globalOverride_neverReplacesLocalThemeChoice() {
        val settings = AppearanceSettings(
            overrideLocalTheme = true,
            theme = "red",
            appearanceMode = "dark"
        )

        assertFalse(settings.globalOverrideEnabled)
    }

    @Test
    fun activeBackgroundFor_returnsOnlyBackgroundFromSelectedTheme() {
        val blue = ThemeBackground(
            id = "blue-1",
            label = "Azul",
            url = "https://example.com/blue.jpg",
            isActive = true,
            startDate = "2026-01-01"
        )
        val red = ThemeBackground(
            id = "red-1",
            label = "Vermelho",
            url = "https://example.com/red.jpg",
            isActive = true,
            startDate = "2026-01-01"
        )
        val settings = AppearanceSettings(
            themeBackgrounds = mapOf(
                "blue" to listOf(blue),
                "red" to listOf(red)
            )
        )

        assertEquals(blue, settings.activeBackgroundFor("blue", "2026-09-04"))
        assertEquals(red, settings.activeBackgroundFor("red", "2026-09-04"))
        assertNull(settings.activeBackgroundFor("green", "2026-09-04"))
    }
}
