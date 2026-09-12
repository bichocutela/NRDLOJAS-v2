from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def write(path: str, text: str) -> None:
    (ROOT / path).write_text(text, encoding="utf-8")


def replace_once(path: str, old: str, new: str, marker: str | None = None) -> None:
    text = read(path)
    if marker and marker in text:
        print(f"SKIP {path}: {marker}")
        return
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected exactly one match, found {count}: {old[:100]!r}")
    write(path, text.replace(old, new, 1))
    print(f"PATCH {path}")


# 1) AppearanceSettings: add an independent, schedulable background collection
# for the Consultar Produtos screen while keeping old manifests backward compatible.
appearance_path = "app/src/main/java/com/example/data/AppearanceSettings.kt"
write(
    appearance_path,
    '''package com.example.data

internal object LocalAppearanceChoiceState {
    @Volatile
    var themeChosen: Boolean = false
}

class AppearanceSettings(
    overrideLocalTheme: Boolean = false,
    val theme: String = "multicolor",
    val appearanceMode: String = "system",
    val themeBackgrounds: Map<String, List<ThemeBackground>> = emptyMap(),
    val consultationBackgrounds: List<ThemeBackground> = emptyList(),
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
     * Retorna somente o fundo ativo/agendado pertencente ao tema escolhido
     * localmente no aparelho.
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

    /** Fundo exclusivo da aba Consultar Produtos, independente do tema da Home. */
    fun activeConsultationBackground(
        date: String = ThemeBackground.todayIsoDate()
    ): ThemeBackground? = consultationBackgrounds
        .filter { it.isAvailableOn(date) }
        .maxByOrNull { ThemeBackground.normalizeDate(it.startDate).orEmpty() }

    fun copy(
        overrideLocalTheme: Boolean = this.overrideLocalTheme,
        theme: String = this.theme,
        appearanceMode: String = this.appearanceMode,
        themeBackgrounds: Map<String, List<ThemeBackground>> = this.themeBackgrounds,
        consultationBackgrounds: List<ThemeBackground> = this.consultationBackgrounds,
        revision: Long = this.revision
    ): AppearanceSettings = AppearanceSettings(
        overrideLocalTheme = overrideLocalTheme,
        theme = theme,
        appearanceMode = appearanceMode,
        themeBackgrounds = themeBackgrounds,
        consultationBackgrounds = consultationBackgrounds,
        revision = revision
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AppearanceSettings) return false
        return overrideLocalTheme == other.overrideLocalTheme &&
            theme == other.theme &&
            appearanceMode == other.appearanceMode &&
            themeBackgrounds == other.themeBackgrounds &&
            consultationBackgrounds == other.consultationBackgrounds &&
            revision == other.revision
    }

    override fun hashCode(): Int {
        var result = overrideLocalTheme.hashCode()
        result = 31 * result + theme.hashCode()
        result = 31 * result + appearanceMode.hashCode()
        result = 31 * result + themeBackgrounds.hashCode()
        result = 31 * result + consultationBackgrounds.hashCode()
        result = 31 * result + revision.hashCode()
        return result
    }

    override fun toString(): String =
        "AppearanceSettings(overrideLocalTheme=$overrideLocalTheme, theme=$theme, appearanceMode=$appearanceMode, themeBackgrounds=$themeBackgrounds, consultationBackgrounds=$consultationBackgrounds, revision=$revision)"

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
'''
)
print(f"WRITE {appearance_path}")

# 2) Persist the dedicated background list in Firestore and the public appearance manifest.
firebase = "app/src/main/java/com/example/data/FirebaseService.kt"
replace_once(
    firebase,
    '''    private fun normalizePersistedThemeBackgroundDate(value: String?): String? {
''',
    '''    private fun parseConsultationBackgrounds(raw: Any?): List<ThemeBackground> {
        return (raw as? List<*>)
            ?.mapNotNull { item ->
                val map = item as? Map<*, *> ?: return@mapNotNull null
                val id = map["id"] as? String ?: return@mapNotNull null
                val url = (map["url"] as? String)
                    ?.trim()
                    ?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
                    ?: return@mapNotNull null
                ThemeBackground(
                    id = id,
                    label = (map["label"] as? String)?.trim().orEmpty().ifBlank { "Fundo personalizado" },
                    url = url,
                    isActive = map["isActive"] as? Boolean ?: false,
                    startDate = normalizePersistedThemeBackgroundDate(map["startDate"] as? String),
                    endDate = normalizePersistedThemeBackgroundDate(map["endDate"] as? String),
                    imageScale = ((map["imageScale"] as? Number)?.toFloat() ?: 1f).coerceIn(0.5f, 3f),
                    imageOffsetX = ((map["imageOffsetX"] as? Number)?.toFloat() ?: 0f).coerceIn(-1f, 1f),
                    imageOffsetY = ((map["imageOffsetY"] as? Number)?.toFloat() ?: 0f).coerceIn(-1f, 1f),
                    imageStretchX = ((map["imageStretchX"] as? Number)?.toFloat() ?: 1f).coerceIn(0.5f, 2.5f),
                    imageStretchY = ((map["imageStretchY"] as? Number)?.toFloat() ?: 1f).coerceIn(0.5f, 2.5f)
                )
            }
            .orEmpty()
    }

    private fun normalizePersistedThemeBackgroundDate(value: String?): String? {
''',
    marker="private fun parseConsultationBackgrounds(raw: Any?)"
)
replace_once(
    firebase,
    '''                        themeBackgrounds = parseThemeBackgrounds(snapshot?.get("appearanceThemeBackgrounds")),
                        revision = snapshot?.getLong("appearanceRevision") ?: 0L
''',
    '''                        themeBackgrounds = parseThemeBackgrounds(snapshot?.get("appearanceThemeBackgrounds")),
                        consultationBackgrounds = parseConsultationBackgrounds(snapshot?.get("appearanceConsultationBackgrounds")),
                        revision = snapshot?.getLong("appearanceRevision") ?: 0L
''',
    marker='consultationBackgrounds = parseConsultationBackgrounds(snapshot?.get("appearanceConsultationBackgrounds"))'
)
replace_once(
    firebase,
    '''        val hasInvalidDateWindow = settings.themeBackgrounds.values.flatten().any { background ->
''',
    '''        val hasInvalidDateWindow = (settings.themeBackgrounds.values.flatten() + settings.consultationBackgrounds).any { background ->
''',
    marker="settings.themeBackgrounds.values.flatten() + settings.consultationBackgrounds"
)
replace_once(
    firebase,
    '''        val manifest = buildAppearanceManifest(
''',
    '''        val safeConsultationBackgrounds = settings.consultationBackgrounds
            .filter { background ->
                val url = background.url.trim()
                url.startsWith("https://") || url.startsWith("http://")
            }
            .map { background ->
                val startDate = ThemeBackground.normalizeDate(background.startDate)
                val endDate = ThemeBackground.normalizeDate(background.endDate)
                linkedMapOf<String, Any>(
                    "id" to background.id.ifBlank { UUID.randomUUID().toString() },
                    "label" to background.label.trim().take(80).ifBlank { "Fundo personalizado" },
                    "url" to background.url.trim(),
                    "isActive" to (background.isActive && startDate != null),
                    "imageScale" to background.imageScale.coerceIn(0.5f, 3f),
                    "imageOffsetX" to background.imageOffsetX.coerceIn(-1f, 1f),
                    "imageOffsetY" to background.imageOffsetY.coerceIn(-1f, 1f),
                    "imageStretchX" to background.imageStretchX.coerceIn(0.5f, 2.5f),
                    "imageStretchY" to background.imageStretchY.coerceIn(0.5f, 2.5f)
                ).apply {
                    if (startDate != null) put("startDate", startDate)
                    if (endDate != null) put("endDate", endDate)
                }
            }

        val manifest = buildAppearanceManifest(
''',
    marker="val safeConsultationBackgrounds = settings.consultationBackgrounds"
)
replace_once(
    firebase,
    '''            themeBackgrounds = safeBackgrounds,
            revision = revision
''',
    '''            themeBackgrounds = safeBackgrounds,
            consultationBackgrounds = safeConsultationBackgrounds,
            revision = revision
''',
    marker="consultationBackgrounds = safeConsultationBackgrounds"
)
replace_once(
    firebase,
    '''                        "appearanceThemeBackgrounds" to safeBackgrounds,
                        "appearanceRevision" to revision
''',
    '''                        "appearanceThemeBackgrounds" to safeBackgrounds,
                        "appearanceConsultationBackgrounds" to safeConsultationBackgrounds,
                        "appearanceRevision" to revision
''',
    marker='"appearanceConsultationBackgrounds" to safeConsultationBackgrounds'
)
replace_once(
    firebase,
    '''        themeBackgrounds: Map<String, List<Map<String, Any>>>,
        revision: Long
''',
    '''        themeBackgrounds: Map<String, List<Map<String, Any>>>,
        consultationBackgrounds: List<Map<String, Any>>,
        revision: Long
''',
    marker="consultationBackgrounds: List<Map<String, Any>>"
)
replace_once(
    firebase,
    '''        return org.json.JSONObject()
            .put("appearanceOverrideLocalTheme", overrideLocalTheme)
''',
    '''        val consultationJson = org.json.JSONArray()
        consultationBackgrounds.forEach { background ->
            consultationJson.put(org.json.JSONObject(background))
        }
        return org.json.JSONObject()
            .put("appearanceOverrideLocalTheme", overrideLocalTheme)
''',
    marker="val consultationJson = org.json.JSONArray()"
)
replace_once(
    firebase,
    '''            .put("appearanceThemeBackgrounds", backgroundsJson)
            .put("appearanceRevision", revision)
''',
    '''            .put("appearanceThemeBackgrounds", backgroundsJson)
            .put("appearanceConsultationBackgrounds", consultationJson)
            .put("appearanceRevision", revision)
''',
    marker='.put("appearanceConsultationBackgrounds", consultationJson)'
)
replace_once(
    firebase,
    '''            themeBackgrounds = parseThemeBackgroundsJson(root.optJSONObject("appearanceThemeBackgrounds")),
            revision = root.optLong("appearanceRevision", 0L)
''',
    '''            themeBackgrounds = parseThemeBackgroundsJson(root.optJSONObject("appearanceThemeBackgrounds")),
            consultationBackgrounds = parseConsultationBackgroundsJson(root.optJSONArray("appearanceConsultationBackgrounds")),
            revision = root.optLong("appearanceRevision", 0L)
''',
    marker='consultationBackgrounds = parseConsultationBackgroundsJson(root.optJSONArray("appearanceConsultationBackgrounds"))'
)
replace_once(
    firebase,
    '''    private fun parseThemeBackgroundsJson(raw: org.json.JSONObject?): Map<String, List<ThemeBackground>> {
''',
    '''    private fun parseConsultationBackgroundsJson(raw: org.json.JSONArray?): List<ThemeBackground> {
        if (raw == null) return emptyList()
        return (0 until raw.length()).mapNotNull { index ->
            val item = raw.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optString("id").trim().takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val url = item.optString("url").trim().takeIf {
                it.startsWith("https://") || it.startsWith("http://")
            } ?: return@mapNotNull null
            ThemeBackground(
                id = id,
                label = item.optString("label").trim().ifBlank { "Fundo personalizado" },
                url = url,
                isActive = item.optBoolean("isActive", false),
                startDate = normalizePersistedThemeBackgroundDate(item.optString("startDate")),
                endDate = normalizePersistedThemeBackgroundDate(item.optString("endDate")),
                imageScale = item.optDouble("imageScale", 1.0).toFloat().coerceIn(0.5f, 3f),
                imageOffsetX = item.optDouble("imageOffsetX", 0.0).toFloat().coerceIn(-1f, 1f),
                imageOffsetY = item.optDouble("imageOffsetY", 0.0).toFloat().coerceIn(-1f, 1f),
                imageStretchX = item.optDouble("imageStretchX", 1.0).toFloat().coerceIn(0.5f, 2.5f),
                imageStretchY = item.optDouble("imageStretchY", 1.0).toFloat().coerceIn(0.5f, 2.5f)
            )
        }
    }

    private fun parseThemeBackgroundsJson(raw: org.json.JSONObject?): Map<String, List<ThemeBackground>> {
''',
    marker="private fun parseConsultationBackgroundsJson(raw: org.json.JSONArray?)"
)

# 3) Add the requested entry immediately below Aparência global.
dashboard = "app/src/main/java/com/example/ui/MestreDashboardOverview.kt"
replace_once(
    dashboard,
    '''internal fun MestreSettingsHub(
    onOpenHome: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenNotifications: () -> Unit
) {
''',
    '''internal fun MestreSettingsHub(
    onOpenHome: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenConsultationAppearance: () -> Unit,
    onOpenNotifications: () -> Unit
) {
''',
    marker="onOpenConsultationAppearance: () -> Unit"
)
replace_once(
    dashboard,
    '''    PanelAreaCard(
        title = "Aparência global",
        description = "Tema, modo visual e fundos programados",
        icon = Icons.Default.Palette,
        onClick = onOpenAppearance
    )
    Spacer(modifier = Modifier.height(6.dp))
    PanelAreaCard(
        title = "Notificações globais",
''',
    '''    PanelAreaCard(
        title = "Aparência global",
        description = "Tema, modo visual e fundos programados",
        icon = Icons.Default.Palette,
        onClick = onOpenAppearance
    )
    Spacer(modifier = Modifier.height(6.dp))
    PanelAreaCard(
        title = "Aparência Consultar Produtos",
        description = "Fundos e enquadramento exclusivos da consulta",
        icon = Icons.Default.Palette,
        onClick = onOpenConsultationAppearance
    )
    Spacer(modifier = Modifier.height(6.dp))
    PanelAreaCard(
        title = "Notificações globais",
''',
    marker='title = "Aparência Consultar Produtos"'
)

# 4) Mestre screen: add a dedicated page while reusing the existing background editor/preview machinery.
mestre = "app/src/main/java/com/example/ui/MestreScreen.kt"
replace_once(
    mestre,
    'private const val BACKGROUND_PAGE_SIZE = 6\n',
    'private const val BACKGROUND_PAGE_SIZE = 6\nprivate const val CONSULTATION_BACKGROUND_KEY = "__consultar_produtos__"\n',
    marker="CONSULTATION_BACKGROUND_KEY"
)
replace_once(
    mestre,
    '''    APPEARANCE_SETTINGS("Fundos por tema"),
    ADVANCED("Ferramentas avançadas")
''',
    '''    APPEARANCE_SETTINGS("Fundos por tema"),
    CONSULTATION_APPEARANCE_SETTINGS("Aparência Consultar Produtos"),
    ADVANCED("Ferramentas avançadas")
''',
    marker='CONSULTATION_APPEARANCE_SETTINGS("Aparência Consultar Produtos")'
)
replace_once(
    mestre,
    '''    val appearanceSettingsFlow = remember(currentPage) {
        if (currentPage == MestrePanelPage.APPEARANCE_SETTINGS) {
''',
    '''    val appearanceSettingsFlow = remember(currentPage) {
        if (currentPage == MestrePanelPage.APPEARANCE_SETTINGS ||
            currentPage == MestrePanelPage.CONSULTATION_APPEARANCE_SETTINGS
        ) {
''',
    marker="currentPage == MestrePanelPage.CONSULTATION_APPEARANCE_SETTINGS"
)
replace_once(
    mestre,
    '''    var draftThemeBackgrounds by remember(appearanceSettings.themeBackgrounds) {
        mutableStateOf(appearanceSettings.themeBackgrounds)
    }
''',
    '''    var draftThemeBackgrounds by remember(appearanceSettings.themeBackgrounds) {
        mutableStateOf(appearanceSettings.themeBackgrounds)
    }
    var draftConsultationBackgrounds by remember(appearanceSettings.consultationBackgrounds) {
        mutableStateOf(appearanceSettings.consultationBackgrounds)
    }
    var consultationBackgroundPage by rememberSaveable { mutableIntStateOf(0) }
''',
    marker="var draftConsultationBackgrounds"
)
replace_once(
    mestre,
    '''                val uploadedUrl = FirebaseService.uploadImageToStorage(
                    uri,
                    "theme_backgrounds/$themeKey/${UUID.randomUUID()}.jpg"
                )
''',
    '''                val uploadFolder = if (themeKey == CONSULTATION_BACKGROUND_KEY) {
                    "consultation_backgrounds"
                } else {
                    "theme_backgrounds/$themeKey"
                }
                val uploadedUrl = FirebaseService.uploadImageToStorage(
                    uri,
                    "$uploadFolder/${UUID.randomUUID()}.jpg"
                )
''',
    marker='"consultation_backgrounds"'
)
replace_once(
    mestre,
    '''    fun updateBackgrounds(themeKey: String, backgrounds: List<ThemeBackground>) {
        draftThemeBackgrounds = draftThemeBackgrounds + (themeKey to backgrounds)
    }
''',
    '''    fun backgroundsForKey(themeKey: String): List<ThemeBackground> =
        if (themeKey == CONSULTATION_BACKGROUND_KEY) {
            draftConsultationBackgrounds
        } else {
            draftThemeBackgrounds[themeKey].orEmpty()
        }

    fun updateBackgrounds(themeKey: String, backgrounds: List<ThemeBackground>) {
        if (themeKey == CONSULTATION_BACKGROUND_KEY) {
            draftConsultationBackgrounds = backgrounds
        } else {
            draftThemeBackgrounds = draftThemeBackgrounds + (themeKey to backgrounds)
        }
    }
''',
    marker="fun backgroundsForKey(themeKey: String)"
)
replace_once(
    mestre,
    '''    val appearanceDraft = draftAppearanceSettings.copy(themeBackgrounds = draftThemeBackgrounds)
''',
    '''    val appearanceDraft = draftAppearanceSettings.copy(
        themeBackgrounds = draftThemeBackgrounds,
        consultationBackgrounds = draftConsultationBackgrounds
    )
''',
    marker="consultationBackgrounds = draftConsultationBackgrounds"
)
replace_once(
    mestre,
    '''        MestrePanelPage.APPEARANCE_SETTINGS -> appearanceHasChanges
        else -> false
''',
    '''        MestrePanelPage.APPEARANCE_SETTINGS,
        MestrePanelPage.CONSULTATION_APPEARANCE_SETTINGS -> appearanceHasChanges
        else -> false
''',
    marker="MestrePanelPage.CONSULTATION_APPEARANCE_SETTINGS -> appearanceHasChanges"
)
replace_once(
    mestre,
    '''                    onOpenAppearance = { openPage(MestrePanelPage.APPEARANCE_SETTINGS) },
                    onOpenNotifications = { openPage(MestrePanelPage.NOTIFICATION_SETTINGS) }
''',
    '''                    onOpenAppearance = { openPage(MestrePanelPage.APPEARANCE_SETTINGS) },
                    onOpenConsultationAppearance = { openPage(MestrePanelPage.CONSULTATION_APPEARANCE_SETTINGS) },
                    onOpenNotifications = { openPage(MestrePanelPage.NOTIFICATION_SETTINGS) }
''',
    marker="onOpenConsultationAppearance ="
)
consultation_page = '''
            if (currentPage == MestrePanelPage.CONSULTATION_APPEARANCE_SETTINGS) {
                MestrePageIntro(
                    description = "Escolha, agende e ajuste o fundo exclusivo da aba Consultar Produtos.",
                    hasUnsavedChanges = appearanceHasChanges
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedCard(modifier = Modifier.fillMaxWidth().glassSoftShadow(MaterialTheme.shapes.medium)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        val backgrounds = draftConsultationBackgrounds
                        val activeBackground = appearanceDraft.activeConsultationBackground()
                        val pagination = calculatePaginationWindow(
                            totalItems = backgrounds.size,
                            requestedPage = consultationBackgroundPage,
                            pageSize = BACKGROUND_PAGE_SIZE
                        )
                        LaunchedEffect(backgrounds.size) {
                            if (consultationBackgroundPage != pagination.pageIndex) {
                                consultationBackgroundPage = pagination.pageIndex
                            }
                        }

                        Text(
                            "Fundo da aba Consultar Produtos",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        Text(
                            activeBackground?.let { "Ativo agora: ${it.label}" }
                                ?: "Fundo padrão da aba ativo",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Você pode manter vários fundos programados por data. A prévia permite ajustar zoom, posição e proporção sem alterar os fundos da Home.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = {
                                updateBackgrounds(
                                    CONSULTATION_BACKGROUND_KEY,
                                    backgrounds.map { it.copy(isActive = false) }
                                )
                            },
                            enabled = backgrounds.any { it.isActive }
                        ) {
                            Text("Usar fundo padrão")
                        }

                        if (backgrounds.isNotEmpty()) {
                            Text(
                                "Exibindo ${pagination.fromIndex + 1}–${pagination.toIndex} de ${backgrounds.size}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        backgrounds
                            .subList(pagination.fromIndex, pagination.toIndex)
                            .forEach { background ->
                                Spacer(modifier = Modifier.height(6.dp))
                                ThemeBackgroundItem(
                                    background = background,
                                    enabled = true,
                                    onActiveChange = { isActive ->
                                        updateBackgrounds(
                                            CONSULTATION_BACKGROUND_KEY,
                                            backgrounds.map {
                                                if (it.id == background.id) it.copy(isActive = isActive) else it
                                            }
                                        )
                                    },
                                    onPreview = {
                                        backgroundToPreview = CONSULTATION_BACKGROUND_KEY to background
                                    },
                                    onEdit = {
                                        openBackgroundEditor(CONSULTATION_BACKGROUND_KEY, background)
                                    },
                                    onDelete = {
                                        backgroundToDelete = CONSULTATION_BACKGROUND_KEY to background
                                    }
                                )
                            }
                        if (pagination.pageCount > 1) {
                            MestrePaginationControls(
                                pageIndex = pagination.pageIndex,
                                pageCount = pagination.pageCount,
                                onPrevious = { consultationBackgroundPage = pagination.pageIndex - 1 },
                                onNext = { consultationBackgroundPage = pagination.pageIndex + 1 }
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { openBackgroundEditor(CONSULTATION_BACKGROUND_KEY, null) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Adicionar fundo para Consultar Produtos")
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    isSavingAppearanceSettings = true
                                    val saved = FirebaseService.saveAppearanceSettings(appearanceDraft)
                                    isSavingAppearanceSettings = false
                                    snackbarHostState.showSnackbar(
                                        if (saved) "Aparência de Consultar Produtos publicada para todos."
                                        else FirebaseService.lastError
                                            ?: "Não foi possível publicar a aparência de Consultar Produtos."
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = appearanceHasChanges && !isSavingAppearanceSettings
                        ) {
                            if (isSavingAppearanceSettings) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Publicando...")
                            } else if (appearanceHasChanges) {
                                Text("Salvar fundo da consulta")
                            } else {
                                Text("Tudo atualizado")
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

'''
replace_once(
    mestre,
    '''            if (showDiscardChangesDialog) {
''',
    consultation_page + '''            if (showDiscardChangesDialog) {
''',
    marker='description = "Escolha, agende e ajuste o fundo exclusivo da aba Consultar Produtos."'
)
replace_once(
    mestre,
    '''                                    MestrePanelPage.APPEARANCE_SETTINGS -> {
                                        draftAppearanceSettings = appearanceSettings
                                        draftThemeBackgrounds = appearanceSettings.themeBackgrounds
                                    }
''',
    '''                                    MestrePanelPage.APPEARANCE_SETTINGS,
                                    MestrePanelPage.CONSULTATION_APPEARANCE_SETTINGS -> {
                                        draftAppearanceSettings = appearanceSettings
                                        draftThemeBackgrounds = appearanceSettings.themeBackgrounds
                                        draftConsultationBackgrounds = appearanceSettings.consultationBackgrounds
                                    }
''',
    marker="draftConsultationBackgrounds = appearanceSettings.consultationBackgrounds"
)
replace_once(
    mestre,
    '''                        Text(if (editingBackground == null) "Adicionar fundo ao tema" else "Editar fundo do tema")
''',
    '''                        val consultation = editingBackgroundTheme == CONSULTATION_BACKGROUND_KEY
                        Text(
                            if (editingBackground == null) {
                                if (consultation) "Adicionar fundo da consulta" else "Adicionar fundo ao tema"
                            } else {
                                if (consultation) "Editar fundo da consulta" else "Editar fundo do tema"
                            }
                        )
''',
    marker='"Adicionar fundo da consulta"'
)
replace_once(
    mestre,
    '''                                    val current = themeKey?.let { draftThemeBackgrounds[it].orEmpty() }.orEmpty()
''',
    '''                                    val current = themeKey?.let(::backgroundsForKey).orEmpty()
''',
    marker="themeKey?.let(::backgroundsForKey)"
)
replace_once(
    mestre,
    '''      BannerPreviewEditor(
          themeKey = themeKey,
          themeLabel = themeOptions.firstOrNull { it.first == themeKey }?.second ?: themeKey,
''',
    '''      BannerPreviewEditor(
          themeKey = if (themeKey == CONSULTATION_BACKGROUND_KEY) "multicolor" else themeKey,
          themeLabel = if (themeKey == CONSULTATION_BACKGROUND_KEY) {
              "Consultar Produtos"
          } else {
              themeOptions.firstOrNull { it.first == themeKey }?.second ?: themeKey
          },
''',
    marker='themeLabel = if (themeKey == CONSULTATION_BACKGROUND_KEY)'
)
replace_once(
    mestre,
    '''              val updatedBackgrounds = draftThemeBackgrounds + (
                  themeKey to draftThemeBackgrounds[themeKey].orEmpty().map { item ->
                      if (item.id == updatedBackground.id) updatedBackground else item
                  }
              )
              draftThemeBackgrounds = updatedBackgrounds
              val settingsToSave = draftAppearanceSettings.copy(
                  themeBackgrounds = updatedBackgrounds
              )
''',
    '''              val updatedList = backgroundsForKey(themeKey).map { item ->
                  if (item.id == updatedBackground.id) updatedBackground else item
              }
              val updatedThemeBackgrounds = if (themeKey == CONSULTATION_BACKGROUND_KEY) {
                  draftThemeBackgrounds
              } else {
                  draftThemeBackgrounds + (themeKey to updatedList)
              }
              val updatedConsultationBackgrounds = if (themeKey == CONSULTATION_BACKGROUND_KEY) {
                  updatedList
              } else {
                  draftConsultationBackgrounds
              }
              draftThemeBackgrounds = updatedThemeBackgrounds
              draftConsultationBackgrounds = updatedConsultationBackgrounds
              val settingsToSave = draftAppearanceSettings.copy(
                  themeBackgrounds = updatedThemeBackgrounds,
                  consultationBackgrounds = updatedConsultationBackgrounds
              )
''',
    marker="val updatedConsultationBackgrounds = if (themeKey == CONSULTATION_BACKGROUND_KEY)"
)
replace_once(
    mestre,
    '''                  val maskSaved = com.example.data.BannerMaskStore.save(
                      themeKey = themeKey,
                      backgroundUrl = updatedBackground.url,
                      settings = maskSettings
                  )
''',
    '''                  val maskSaved = if (themeKey == CONSULTATION_BACKGROUND_KEY) {
                      true
                  } else {
                      com.example.data.BannerMaskStore.save(
                          themeKey = themeKey,
                          backgroundUrl = updatedBackground.url,
                          settings = maskSettings
                      )
                  }
''',
    marker="if (themeKey == CONSULTATION_BACKGROUND_KEY) {\n                      true\n                  } else {\n                      com.example.data.BannerMaskStore.save"
)
replace_once(
    mestre,
    '''                      snackbarHostState.showSnackbar(
                          "Prévia salva. Enquadramento e máscara já serão usados na Home."
                      )
''',
    '''                      snackbarHostState.showSnackbar(
                          if (themeKey == CONSULTATION_BACKGROUND_KEY) {
                              "Enquadramento salvo para Consultar Produtos."
                          } else {
                              "Prévia salva. Enquadramento e máscara já serão usados na Home."
                          }
                      )
''',
    marker='"Enquadramento salvo para Consultar Produtos."'
)
replace_once(
    mestre,
    '''                                updateBackgrounds(themeKey, draftThemeBackgrounds[themeKey].orEmpty().filterNot { it.id == background.id })
''',
    '''                                updateBackgrounds(themeKey, backgroundsForKey(themeKey).filterNot { it.id == background.id })
''',
    marker="backgroundsForKey(themeKey).filterNot"
)

# 5) Render the dedicated active background behind the native ACP consultation UI.
acp = "app/src/main/java/com/example/ui/AcpConsultationScreen.kt"
replace_once(
    acp,
    '''import androidx.compose.foundation.Image
''',
    '''import androidx.compose.foundation.Image
import androidx.compose.foundation.background
''',
    marker="import androidx.compose.foundation.background"
)
replace_once(
    acp,
    '''import androidx.compose.ui.graphics.asImageBitmap
''',
    '''import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
''',
    marker="import androidx.compose.ui.graphics.graphicsLayer"
)
replace_once(
    acp,
    '''import androidx.compose.ui.unit.dp
''',
    '''import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.AppearanceSettings
import com.example.data.FirebaseService
''',
    marker="import com.example.data.AppearanceSettings"
)
replace_once(
    acp,
    '''    val scope = rememberCoroutineScope()
    val bannerBitmap = remember(context) {
''',
    '''    val scope = rememberCoroutineScope()
    val appearanceSettings by FirebaseService.observeAppearanceSettings()
        .collectAsStateWithLifecycle(initialValue = AppearanceSettings())
    val activeConsultationBackground = appearanceSettings.activeConsultationBackground()
    val bannerBitmap = remember(context) {
''',
    marker="val activeConsultationBackground = appearanceSettings.activeConsultationBackground()"
)
replace_once(
    acp,
    '''    Scaffold(topBar = {
''',
    '''    Box(modifier = Modifier.fillMaxSize()) {
        activeConsultationBackground?.let { background ->
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(background.url)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = background.imageScale * background.imageStretchX
                        scaleY = background.imageScale * background.imageStretchY
                        translationX = size.width * background.imageOffsetX
                        translationY = size.height * background.imageOffsetY
                    }
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.12f))
            )
        }

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
''',
    marker="activeConsultationBackground?.let { background ->"
)
replace_once(
    acp,
    '''    }

    if (configure && canConfigure) {
''',
    '''        }
    }

    if (configure && canConfigure) {
''',
    marker="        }\n    }\n\n    if (configure && canConfigure)"
)

# 6) Focused regression test for scheduling/selection of the dedicated background.
test_path = ROOT / "app/src/test/java/com/example/data/ConsultationAppearanceSettingsTest.kt"
test_path.write_text(
    '''package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConsultationAppearanceSettingsTest {
    @Test
    fun activeConsultationBackground_prefersMostRecentActiveStart() {
        val older = ThemeBackground(
            id = "older",
            label = "Antigo",
            url = "https://example.com/older.jpg",
            isActive = true,
            startDate = "2026-09-01"
        )
        val newer = ThemeBackground(
            id = "newer",
            label = "Novo",
            url = "https://example.com/newer.jpg",
            isActive = true,
            startDate = "2026-09-10"
        )
        val settings = AppearanceSettings(consultationBackgrounds = listOf(older, newer))

        assertEquals(newer, settings.activeConsultationBackground("2026-09-12"))
    }

    @Test
    fun activeConsultationBackground_returnsNullOutsideSchedule() {
        val scheduled = ThemeBackground(
            id = "scheduled",
            label = "Campanha",
            url = "https://example.com/campaign.jpg",
            isActive = true,
            startDate = "2026-10-01",
            endDate = "2026-10-31"
        )
        val settings = AppearanceSettings(consultationBackgrounds = listOf(scheduled))

        assertNull(settings.activeConsultationBackground("2026-09-12"))
    }
}
''',
    encoding="utf-8"
)
print(f"WRITE {test_path.relative_to(ROOT)}")

print("All consultation appearance patches applied.")
