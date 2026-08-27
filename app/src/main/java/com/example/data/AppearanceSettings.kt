package com.example.data

data class AppearanceSettings(
    val overrideLocalTheme: Boolean = false,
    val theme: String = "multicolor",
    val appearanceMode: String = "system"
)
