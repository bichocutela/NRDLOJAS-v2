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
    val cardBackgrounds: Map<String, String> = emptyMap(),
    val bubbleSpeed: Float = 1f,
    val bubbleMotion: String = "random",
    val bubbleSize: Float = 1f,
    val bubbleExtraCount: Int = 0,
    val bubbleBrightness: Float = 1f,
    val bubbleOutline: Boolean = true,
    val bubbleShape: String = "classic",
    val bubbleImageUrl: String = "",
    val bubbleAlphaMin: Float = 0.12f,
    val bubbleAlphaMax: Float = 0.52f,
    val bubbleSway: Float = 0.55f,
    val bubbleSpawnRate: Float = 1f,
    val bubbleScalePulse: Float = 0.08f,
    val bubbleRotation: Float = 0.12f,
    val bubbleFade: Float = 0.45f,
    val revision: Long = 0L
) {
    /**
     * Valor administrativo persistido. Ele continua disponível para o painel
     * Mestre editar/visualizar, mas não é usado para substituir a preferência
     * local de tema ou modo de aparência do aparelho.
     */
    val overrideLocalTheme: Boolean = overrideLocalTheme

    /**
     * Quando ativado pelo Mestre, a aparência global substitui também aparelhos
     * que já fizeram uma escolha local. Quando desativado, o global continua sendo
     * apenas o padrão de primeira instalação.
     */
    val globalOverrideEnabled: Boolean
        get() = overrideLocalTheme

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
        cardBackgrounds: Map<String, String> = this.cardBackgrounds,
        bubbleSpeed: Float = this.bubbleSpeed,
        bubbleMotion: String = this.bubbleMotion,
        bubbleSize: Float = this.bubbleSize,
        bubbleExtraCount: Int = this.bubbleExtraCount,
        bubbleBrightness: Float = this.bubbleBrightness,
        bubbleOutline: Boolean = this.bubbleOutline,
        bubbleShape: String = this.bubbleShape,
        bubbleImageUrl: String = this.bubbleImageUrl,
        bubbleAlphaMin: Float = this.bubbleAlphaMin,
        bubbleAlphaMax: Float = this.bubbleAlphaMax,
        bubbleSway: Float = this.bubbleSway,
        bubbleSpawnRate: Float = this.bubbleSpawnRate,
        bubbleScalePulse: Float = this.bubbleScalePulse,
        bubbleRotation: Float = this.bubbleRotation,
        bubbleFade: Float = this.bubbleFade,
        revision: Long = this.revision
    ): AppearanceSettings = AppearanceSettings(
        overrideLocalTheme = overrideLocalTheme,
        theme = theme,
        appearanceMode = appearanceMode,
        defaultThemeBackgrounds = defaultThemeBackgrounds,
        themeBackgrounds = themeBackgrounds,
        consultationBackgrounds = consultationBackgrounds,
        offerBanners = offerBanners,
        cardBackgrounds = cardBackgrounds,
        bubbleSpeed = bubbleSpeed,
        bubbleMotion = bubbleMotion,
        bubbleSize = bubbleSize,
        bubbleExtraCount = bubbleExtraCount,
        bubbleBrightness = bubbleBrightness,
        bubbleOutline = bubbleOutline,
        bubbleShape = bubbleShape,
        bubbleImageUrl = bubbleImageUrl,
        bubbleAlphaMin = bubbleAlphaMin,
        bubbleAlphaMax = bubbleAlphaMax,
        bubbleSway = bubbleSway,
        bubbleSpawnRate = bubbleSpawnRate,
        bubbleScalePulse = bubbleScalePulse,
        bubbleRotation = bubbleRotation,
        bubbleFade = bubbleFade,
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
            cardBackgrounds == other.cardBackgrounds &&
            bubbleSpeed == other.bubbleSpeed &&
            bubbleMotion == other.bubbleMotion &&
            bubbleSize == other.bubbleSize &&
            bubbleExtraCount == other.bubbleExtraCount &&
            bubbleBrightness == other.bubbleBrightness &&
            bubbleOutline == other.bubbleOutline &&
            bubbleShape == other.bubbleShape &&
            bubbleImageUrl == other.bubbleImageUrl &&
            bubbleAlphaMin == other.bubbleAlphaMin &&
            bubbleAlphaMax == other.bubbleAlphaMax &&
            bubbleSway == other.bubbleSway &&
            bubbleSpawnRate == other.bubbleSpawnRate &&
            bubbleScalePulse == other.bubbleScalePulse &&
            bubbleRotation == other.bubbleRotation &&
            bubbleFade == other.bubbleFade &&
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
        result = 31 * result + cardBackgrounds.hashCode()
        result = 31 * result + bubbleSpeed.hashCode()
        result = 31 * result + bubbleMotion.hashCode()
        result = 31 * result + bubbleSize.hashCode()
        result = 31 * result + bubbleExtraCount.hashCode()
        result = 31 * result + bubbleBrightness.hashCode()
        result = 31 * result + bubbleOutline.hashCode()
        result = 31 * result + bubbleShape.hashCode()
        result = 31 * result + bubbleImageUrl.hashCode()
        result = 31 * result + bubbleAlphaMin.hashCode()
        result = 31 * result + bubbleAlphaMax.hashCode()
        result = 31 * result + bubbleSway.hashCode()
        result = 31 * result + bubbleSpawnRate.hashCode()
        result = 31 * result + bubbleScalePulse.hashCode()
        result = 31 * result + bubbleRotation.hashCode()
        result = 31 * result + bubbleFade.hashCode()
        result = 31 * result + revision.hashCode()
        return result
    }

    override fun toString(): String =
        "AppearanceSettings(overrideLocalTheme=$overrideLocalTheme, theme=$theme, appearanceMode=$appearanceMode, defaultThemeBackgrounds=$defaultThemeBackgrounds, themeBackgrounds=$themeBackgrounds, consultationBackgrounds=$consultationBackgrounds, offerBanners=$offerBanners, cardBackgrounds=$cardBackgrounds, revision=$revision)"

    private fun normalizeThemeKey(value: String): String = when (value.trim().lowercase()) {
        "multicolor" -> "multicolor"
        "red" -> "red"
        "gold" -> "gold"
        "green" -> "green"
        "blue" -> "blue"
        "orange" -> "orange"
        "glass" -> "glass"
        "expressive" -> "expressive"
        else -> "multicolor"
    }
}

internal fun resolveEffectiveAppearanceValue(
    remoteValue: String,
    localValue: String,
    hasLocalChoice: Boolean,
    forceGlobal: Boolean
): String = if (forceGlobal || !hasLocalChoice) remoteValue else localValue

internal fun mostRecentAppearanceSettings(
    publicManifest: AppearanceSettings?,
    firestore: AppearanceSettings?
): AppearanceSettings? = when {
    publicManifest == null -> firestore
    firestore == null -> publicManifest
    firestore.revision > publicManifest.revision -> firestore
    else -> publicManifest
}
