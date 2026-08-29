package com.example.data

data class AppearanceSettings(
    val overrideLocalTheme: Boolean = false,
    val theme: String = "multicolor",
    val appearanceMode: String = "system",
    val themeBackgrounds: Map<String, List<ThemeBackground>> = emptyMap(),
    val revision: Long = 0L
) {
    fun activeBackgroundFor(themeKey: String, date: String = ThemeBackground.todayIsoDate()): ThemeBackground? =
        themeBackgrounds[themeKey]
            ?.filter { it.isAvailableOn(date) }
            ?.maxByOrNull { ThemeBackground.normalizeDate(it.startDate).orEmpty() }
}

internal fun mostRecentAppearanceSettings(
    publicManifest: AppearanceSettings?,
    firestore: AppearanceSettings?
): AppearanceSettings? = when {
    publicManifest == null -> firestore
    firestore == null -> publicManifest
    firestore.revision > publicManifest.revision -> firestore
    else -> publicManifest
}
