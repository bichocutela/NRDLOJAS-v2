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
     * Para usuário comum, tema e modo de aparência são sempre escolhas locais.
     * O valor remoto é preservado somente para a sessão de gerenciamento, para
     * manter compatibilidade com o painel enquanto o Mestre administra os fundos.
     */
    val overrideLocalTheme: Boolean
        get() = storedOverrideLocalTheme && isManagementSession()

    /**
     * O host usa esta propriedade para decidir se deve considerar a aparência
     * remota. Para usuários comuns ela é sempre falsa; assim a escolha local
     * nunca é substituída pelo tema salvo no painel do Mestre.
     */
    val globalOverrideEnabled: Boolean
        get() = storedOverrideLocalTheme && isManagementSession()

    /**
     * Retorna somente o fundo pertencente ao tema escolhido pelo usuário.
     * A normalização impede que variações de caixa/espaço façam uma cor buscar
     * o conjunto de fundos de outra cor.
     */
    fun activeBackgroundFor(
        themeKey: String,
        date: String = ThemeBackground.todayIsoDate()
    ): ThemeBackground? {
        val normalizedTheme = normalizeThemeKey(themeKey)
        return themeBackgrounds[normalizedTheme]
            ?.filter { it.isAvailableOn(date) }
            ?.maxByOrNull { ThemeBackground.normalizeDate(it.startDate).orEmpty() }
    }

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

    private fun normalizeThemeKey(value: String): String = when (value.trim().lowercase()) {
        "multicolor" -> "multicolor"
        "red" -> "red"
        "gold" -> "gold"
        "green" -> "green"
        "blue" -> "blue"
        "orange" -> "orange"
        else -> "multicolor"
    }

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
