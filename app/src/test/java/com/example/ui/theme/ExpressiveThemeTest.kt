package com.example.ui.theme

import com.example.data.AppearanceSettings
import com.example.data.SupportedThemeKeys
import com.example.data.ThemeBackground
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpressiveThemeTest {

    @Test
    fun `expressive is the eighth supported theme`() {
        assertEquals(8, SupportedThemeKeys.distinct().size)
        assertTrue("expressive" in SupportedThemeKeys)
    }

    @Test
    fun `expressive background is scheduled independently`() {
        val scheduled = ThemeBackground(
            id = "expressive-scheduled",
            label = "Expressivo",
            url = "https://example.com/expressive.jpg",
            isActive = true,
            startDate = "2026-09-01",
            endDate = "2026-09-30"
        )
        val settings = AppearanceSettings(
            themeBackgrounds = mapOf("expressive" to listOf(scheduled))
        )

        assertEquals(scheduled, settings.activeBackgroundFor("expressive", "2026-09-24"))
        assertEquals(null, settings.activeBackgroundFor("multicolor", "2026-09-24"))
    }

    @Test
    fun `solid is not glass and glass variation is glass`() {
        assertFalse(ExpressiveStyle(enabled = true, variant = "solid").isGlass)
        assertTrue(ExpressiveStyle(enabled = true, variant = "glass").isGlass)
        assertFalse(ExpressiveStyle(enabled = false, variant = "glass").isGlass)
    }
}
