package com.example.data

import com.google.firebase.auth.FirebaseAuth

internal object LocalAppearanceChoiceState {
    @Volatile
    var themeChosen: Boolean = false
}

class AppearanceSettings(
    overrideLocalTheme: Boolean = false,
    val theme: String = "multicolor",
    val appearanceMode: String = "system",
    val themeBackgrounds: Map<String, List<ThemeBackground>> = emptyMap(),
    val revision: Long = 0L
) {
    private val storedOverrideLocalTheme: Boolean = overrideLocalTheme

    /**
     * Para usuários comuns, uma escolha local explícita de tema tem prioridade.
     * O Mestre/Admin continua enxergando e administrando o valor remoto real.
     */
    val overrideLocalTheme: Boolean
        get() = storedOverrideLocalTheme && (isManagementSession() || !LocalAppearanceChoiceState.themeChosen)

    /** Valor remoto bruto, usado pelo host para tratar tema e claro/escuro separadamente. */
    val globalOverrideEnabled: Boolean
        get() = storedOverrideLocalTheme

    fun activeBackgroundFor(themeKey: String, date: String = ThemeBackground.todayIsoDate()): ThemeBackground? =
        themeBackgrounds[themeKey]
            ?.filter { it.isAvailableOn(date) }
            ?.maxByOrNull { ThemeBackground.normalizeDate(it.startDate).orEmpty() }

    fun copy(
        overrideLocalTheme: Boolean = storedOverrideLocalTheme,
        theme: String = this.theme,
        appearanceMode: String = this.appearanceMode,
        themeBackgrounds: Map<String, List<ThemeBackground>> = this.themeBackgrounds,
        revision: Long = this.revision
    ): AppearanceSettings = AppearanceSettings(
        overrideLocalTheme = overrideLocalTheme,
        theme = theme,
        appearanceMode = appearanceMode,
        themeBackgrounds = themeBackgrounds,
        revision = revision
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AppearanceSettings) return false
        return storedOverrideLocalTheme == other.storedOverrideLocalTheme &&
            theme == other.theme &&
            appearanceMode == other.appearanceMode &&
            themeBackgrounds == other.themeBackgrounds &&
            revision == other.revision
    }

    override fun hashCode(): Int {
        var result = storedOverrideLocalTheme.hashCode()
        result = 31 * result + theme.hashCode()
        result = 31 * result + appearanceMode.hashCode()
        result = 31 * result + themeBackgrounds.hashCode()
        result = 31 * result + revision.hashCode()
        return result
    }

    override fun toString(): String =
        "AppearanceSettings(overrideLocalTheme=$storedOverrideLocalTheme, theme=$theme, appearanceMode=$appearanceMode, themeBackgrounds=$themeBackgrounds, revision=$revision)"

    private fun isManagementSession(): Boolean = runCatching {
        FirebaseAuth.getInstance().currentUser?.email?.trim()?.lowercase() in MANAGEMENT_EMAILS
    }.getOrDefault(false)

    private companion object {
        val MANAGEMENT_EMAILS = setOf("admin@nrdlojas.com", "mestre@nrdlojas.com")
    }
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
