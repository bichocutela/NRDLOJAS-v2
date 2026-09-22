package com.example.data

internal object LocalAppearanceChoiceState {
    @Volatile
    var themeChosen: Boolean = false
}

class AppearanceSettings(
    overrideLocalTheme: Boolean = false,
    val theme: String = "multicolor",
    val appearanceMode: String = "system",
    val defaultThemeBackgrounds: Map<String, ThemeBackground> = emptyMap(),
    val themeBackgrounds: Map<String, List<ThemeBackground>> = emptyMap(),
    val consultationBackgrounds: List<ThemeBackground> = emptyList(),
    val offerBanners: Map<String, List<ThemeBackground>> = emptyMap(),
    val revision: Long = 0L
) {
    /**
     * Valor administrativo persistido. Ele continua disponível para o painel
     * Mestre editar/visualizar, mas não é usado para substituir a preferência
     * local de tema ou modo de aparência do aparelho.
     */
    val overrideLocalTheme: Boolean = overrideLocalTheme

    /** A aparência remota nunca substitui a preferência local do aparelho. */
    val globalOverrideEnabled: Boolean
        get() = false

    /**
     * Retorna o fundo ativo/agendado pertencente ao tema escolhido localmente.
     * Quando nenhum agendamento está vigente, usa o banner padrão publicado
     * para o tema. Se ele ainda não foi configurado, a UI usa a arte embarcada.
     */
    fun activeBackgroundFor(
        themeKey: String,
        date: String = ThemeBackground.todayIsoDate()
    ): ThemeBackground? {
        val normalizedTheme = normalizeThemeKey(themeKey)
        return themeBackgrounds[normalizedTheme]
            ?.filter { it.isAvailableOn(date) }
            ?.maxByOrNull { ThemeBackground.normalizeDate(it.startDate).orEmpty() }
            ?: defaultThemeBackgrounds[normalizedTheme]
    }

    /** Fundo exclusivo da aba Consultar Produtos, independente do tema da Home. */
    fun activeConsultationBackground(
        date: String = ThemeBackground.todayIsoDate()
    ): ThemeBackground? = consultationBackgrounds
        .filter { it.isAvailableOn(date) }
        .maxByOrNull { ThemeBackground.normalizeDate(it.startDate).orEmpty() }

    /** Banner 3:1 escolhido automaticamente pelo tipo de oferta confirmado na ACP. */
    fun activeOfferBanner(
        offerKey: String,
        date: String = ThemeBackground.todayIsoDate()
    ): ThemeBackground? = offerBanners[offerKey]
        .orEmpty()
        .filter { it.isAvailableOn(date) }
        .maxByOrNull { ThemeBackground.normalizeDate(it.startDate).orEmpty() }

    fun copy(
        overrideLocalTheme: Boolean = this.overrideLocalTheme,
        theme: String = this.theme,
        appearanceMode: String = this.appearanceMode,
        defaultThemeBackgrounds: Map<String, ThemeBackground> = this.defaultThemeBackgrounds,
        themeBackgrounds: Map<String, List<ThemeBackground>> = this.themeBackgrounds,
        consultationBackgrounds: List<ThemeBackground> = this.consultationBackgrounds,
        offerBanners: Map<String, List<ThemeBackground>> = this.offerBanners,
        revision: Long = this.revision
    ): AppearanceSettings = AppearanceSettings(
        overrideLocalTheme = overrideLocalTheme,
        theme = theme,
        appearanceMode = appearanceMode,
        defaultThemeBackgrounds = defaultThemeBackgrounds,
        themeBackgrounds = themeBackgrounds,
        consultationBackgrounds = consultationBackgrounds,
        offerBanners = offerBanners,
        revision = revision
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AppearanceSettings) return false
        return overrideLocalTheme == other.overrideLocalTheme &&
            theme == other.theme &&
            appearanceMode == other.appearanceMode &&
            defaultThemeBackgrounds == other.defaultThemeBackgrounds &&
            themeBackgrounds == other.themeBackgrounds &&
            consultationBackgrounds == other.consultationBackgrounds &&
            offerBanners == other.offerBanners &&
            revision == other.revision
    }

    override fun hashCode(): Int {
        var result = overrideLocalTheme.hashCode()
        result = 31 * result + theme.hashCode()
        result = 31 * result + appearanceMode.hashCode()
        result = 31 * result + defaultThemeBackgrounds.hashCode()
        result = 31 * result + themeBackgrounds.hashCode()
        result = 31 * result + consultationBackgrounds.hashCode()
        result = 31 * result + offerBanners.hashCode()
        result = 31 * result + revision.hashCode()
        return result
    }

    override fun toString(): String =
        "AppearanceSettings(overrideLocalTheme=$overrideLocalTheme, theme=$theme, appearanceMode=$appearanceMode, defaultThemeBackgrounds=$defaultThemeBackgrounds, themeBackgrounds=$themeBackgrounds, consultationBackgrounds=$consultationBackgrounds, offerBanners=$offerBanners, revision=$revision)"

    private fun normalizeThemeKey(value: String): String = when (value.trim().lowercase()) {
        "multicolor" -> "multicolor"
        "red" -> "red"
        "gold" -> "gold"
        "green" -> "green"
        "blue" -> "blue"
        "orange" -> "orange"
        "glass" -> "glass"
        else -> "multicolor"
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
