package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.example.data.AppearanceSettings
import com.example.data.SupportedThemeKeys
import com.example.data.ThemeBackground
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassSoftThemeTest {

    @Test
    fun `glass exposes seven colors and three glass types`() {
        assertEquals(7, GlassSoftAccentNames.distinct().size)
        assertEquals(3, GlassSoftTypes.distinct().size)
        GlassSoftAccentNames.forEach { accent ->
            val light = glassSoftBackgroundColors(accent, isDark = false)
            val dark = glassSoftBackgroundColors(accent, isDark = true)
            assertTrue(light.isNotEmpty())
            assertTrue(dark.isNotEmpty())
            assertTrue(light.map { it.luminance() }.average() > dark.map { it.luminance() }.average())
        }
    }

    @Test
    fun `all glass combinations keep readable contrast and safe opacity`() {
        val transparencies = (20..90 step 5).map { it / 100f }

        GlassSoftAccentNames.forEach { accent ->
            GlassSoftTypes.forEach { type ->
                listOf(false, true).forEach { isDark ->
                    transparencies.forEach { transparency ->
                        val style = resolveGlassSoftStyle(
                            enabled = true,
                            type = type,
                            transparency = transparency,
                            accentName = accent,
                            isDark = isDark
                        )
                        val scheme = glassSoftColorScheme(style)

                        assertTrue(style.surfaceAlpha in 0.52f..0.92f)
                        assertTrue(style.strongSurfaceAlpha in style.surfaceAlpha..0.96f)
                        assertTrue(style.borderAlpha in 0.54f..0.90f)
                        assertContrastAtLeast(scheme.primary, scheme.onPrimary, 4.5f)
                        assertContrastAtLeast(scheme.secondary, scheme.onSecondary, 4.5f)
                        assertContrastAtLeast(scheme.tertiary, scheme.onTertiary, 4.5f)

                        glassSoftBackgroundColors(accent, isDark).forEach { background ->
                            assertContrastAtLeast(background, scheme.onBackground, 4.5f)
                            assertContrastAtLeast(background, scheme.primary, 4.5f)
                            assertContrastAtLeast(background, scheme.secondary, 4.5f)
                            assertContrastAtLeast(background, scheme.tertiary, 4.5f)
                            assertContrastAtLeast(
                                scheme.surface.compositeOver(background),
                                scheme.onSurface,
                                4.5f
                            )
                            assertContrastAtLeast(
                                scheme.surfaceVariant.compositeOver(background),
                                scheme.onSurfaceVariant,
                                4.5f
                            )
                            assertContrastAtLeast(
                                scheme.surfaceContainerHigh.compositeOver(background),
                                scheme.onSurface,
                                4.5f
                            )
                            assertContrastAtLeast(
                                scheme.primaryContainer.compositeOver(background),
                                scheme.onPrimaryContainer,
                                4.5f
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `glass types and transparency have predictable visual hierarchy`() {
        listOf(false, true).forEach { isDark ->
            GlassSoftAccentNames.forEach { accent ->
                (20..90 step 5).map { it / 100f }.forEach { transparency ->
                    val frosted = resolveGlassSoftStyle(true, "frosted", transparency, accent, isDark)
                    val soft = resolveGlassSoftStyle(true, "soft", transparency, accent, isDark)
                    val crystal = resolveGlassSoftStyle(true, "crystal", transparency, accent, isDark)
                    assertTrue(frosted.surfaceAlpha > soft.surfaceAlpha)
                    assertTrue(soft.surfaceAlpha > crystal.surfaceAlpha)
                }

                GlassSoftTypes.forEach { type ->
                    val alphaByTransparency = (20..90 step 5).map {
                        resolveGlassSoftStyle(true, type, it / 100f, accent, isDark).surfaceAlpha
                    }
                    alphaByTransparency.zipWithNext().forEach { (moreSolid, moreTransparent) ->
                        assertTrue(moreSolid >= moreTransparent)
                    }
                }
            }
        }
    }

    @Test
    fun `legacy glass accent names stay compatible`() {
        assertEquals(glassSoftAccent("pink"), glassSoftAccent("red"))
        assertEquals(glassSoftAccent("orange"), glassSoftAccent("gold"))
    }

    @Test
    fun `glass backgrounds are supported and scheduled independently`() {
        assertTrue("glass" in SupportedThemeKeys)
        val scheduled = ThemeBackground(
            id = "glass-scheduled",
            label = "Glass anual",
            url = "https://example.com/glass.jpg",
            isActive = true,
            startDate = "2026-08-01",
            endDate = "2026-09-30"
        )
        val settings = AppearanceSettings(themeBackgrounds = mapOf("glass" to listOf(scheduled)))

        assertEquals(scheduled, settings.activeBackgroundFor("glass", "2026-08-31"))
        assertEquals(null, settings.activeBackgroundFor("multicolor", "2026-08-31"))
    }

    private fun assertContrastAtLeast(background: Color, foreground: Color, expected: Float) {
        val contrast = contrastRatio(background, foreground)
        assertTrue(
            "Expected contrast >= $expected, but was $contrast for $foreground over $background",
            contrast >= expected
        )
    }

    private fun contrastRatio(first: Color, second: Color): Float {
        val firstLuminance = first.luminance()
        val secondLuminance = second.luminance()
        val lighter = maxOf(firstLuminance, secondLuminance)
        val darker = minOf(firstLuminance, secondLuminance)
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    private fun Color.compositeOver(background: Color): Color {
        val outputAlpha = alpha + background.alpha * (1f - alpha)
        if (outputAlpha <= 0f) return Color.Transparent
        val backgroundFactor = background.alpha * (1f - alpha)
        return Color(
            red = (red * alpha + background.red * backgroundFactor) / outputAlpha,
            green = (green * alpha + background.green * backgroundFactor) / outputAlpha,
            blue = (blue * alpha + background.blue * backgroundFactor) / outputAlpha,
            alpha = outputAlpha
        )
    }
}
