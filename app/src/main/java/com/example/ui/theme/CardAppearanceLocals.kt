package com.example.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import com.example.data.AppearanceSettings

val LocalCardAppearanceSettings = staticCompositionLocalOf { AppearanceSettings() }
val LocalCardThemeKey = staticCompositionLocalOf { "multicolor" }
