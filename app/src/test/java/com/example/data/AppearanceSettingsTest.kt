package com.example.data

import org.junit.Assert.assertEquals
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
}
