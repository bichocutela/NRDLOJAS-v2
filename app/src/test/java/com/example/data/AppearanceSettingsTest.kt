package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AppearanceSettingsTest {

    @Test
    fun resolveEffectiveAppearance_usesGlobalForFirstInstallation() {
        assertEquals(
            "expressive",
            resolveEffectiveAppearanceValue(
                remoteValue = "expressive",
                localValue = "multicolor",
                hasLocalChoice = false,
                forceGlobal = false
            )
        )
    }

    @Test
    fun resolveEffectiveAppearance_keepsUserChoiceAfterCustomization() {
        assertEquals(
            "blue",
            resolveEffectiveAppearanceValue(
                remoteValue = "expressive",
                localValue = "blue",
                hasLocalChoice = true,
                forceGlobal = false
            )
        )
    }

    @Test
    fun resolveEffectiveAppearance_forceGlobalOverridesExistingChoice() {
        assertEquals(
            "expressive",
            resolveEffectiveAppearanceValue(
                remoteValue = "expressive",
                localValue = "blue",
                hasLocalChoice = true,
                forceGlobal = true
            )
        )
    }

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
}
