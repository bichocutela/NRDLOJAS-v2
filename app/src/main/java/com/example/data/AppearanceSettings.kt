package com.example.data

data class AppearanceSettings(
    val overrideLocalTheme: Boolean = false,
    val theme: String = "multicolor",
    val appearanceMode: String = "system",
    val themeBackgrounds: Map<String, List<ThemeBackground>> = emptyMap()
) {
    fun activeBackgroundFor(themeKey: String): ThemeBackground? =
        themeBackgrounds[themeKey]?.firstOrNull { it.isActive && it.url.isNotBlank() }
}
