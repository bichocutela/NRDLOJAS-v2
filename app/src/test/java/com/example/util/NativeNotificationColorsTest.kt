package com.example.util

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.pow

class NativeNotificationColorsTest {
    @Test fun xiaomiExpandedCardStaysDarkWhenPhoneIsLight() {
        assertTrue(NativeNotificationColors.expandedIsDark(xiaomi = true, systemDark = false))
        assertTrue(NativeNotificationColors.expandedIsDark(xiaomi = true, systemDark = true))
    }
    @Test fun samsungAndMotorolaFollowNativeSystemSurface() {
        assertFalse(NativeNotificationColors.expandedIsDark(xiaomi = false, systemDark = false))
        assertTrue(NativeNotificationColors.expandedIsDark(xiaomi = false, systemDark = true))
    }
    @Test fun lightAndDarkTextRemainReadableOnNativeBackgrounds() {
        for (dark in listOf(false, true)) {
            val colors = NativeNotificationColors.text(dark)
            for (bg in if (dark) listOf(0xFF000000.toInt(), 0xFF141821.toInt(), 0xFF242B36.toInt())
                       else listOf(0xFFFFFFFF.toInt(), 0xFFFAFBFF.toInt(), 0xFFE9EDF4.toInt())) {
                assertTrue(contrast(colors.title, bg) >= 7)
                assertTrue(contrast(colors.body, bg) >= 4.5)
            }
        }
        assertNotEquals(NativeNotificationColors.text(false).title, NativeNotificationColors.text(true).title)
    }
    @Test fun everyThemeAndStyleKeepsButtonTextReadable() {
        val accents = listOf(0xFFE62325, 0xFF388E3C, 0xFF1976D2, 0xFFF57C00, 0xFFB8860B, 0xFF000000, 0xFFFFFFFF)
        for (accent in accents) for (dark in listOf(false, true)) for (style in listOf("solid", "tonal", "gradient", "glass")) {
            val bg = NativeNotificationColors.button(dark, accent.toInt(), style)
            assertTrue("Unreadable button: $accent $dark $style", contrast(NativeNotificationColors.text(dark).title, bg) >= 7)
        }
    }
    private fun contrast(a: Int, b: Int): Double {
        val x = luminance(a); val y = luminance(b)
        return (maxOf(x, y) + .05) / (minOf(x, y) + .05)
    }
    private fun luminance(color: Int): Double {
        fun channel(shift: Int): Double {
            val c = ((color ushr shift) and 255) / 255.0
            return if (c <= .04045) c / 12.92 else ((c + .055) / 1.055).pow(2.4)
        }
        return .2126 * channel(16) + .7152 * channel(8) + .0722 * channel(0)
    }
}
