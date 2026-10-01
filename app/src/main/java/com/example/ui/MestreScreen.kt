package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import com.example.data.AppearanceSettings
import com.example.data.BannerMaskSettings
import com.example.data.CategoryDefinition
import com.example.data.CatalogSnapshot
import com.example.data.CatalogHistoryBackend
import com.example.data.DeviceInstallationSummary
import com.example.data.DeviceInstallationSummaryResult
import com.example.data.DeviceInstallationTracker
import com.example.data.UserPreferences
import com.example.data.ThemeBackground
import com.example.data.SupportedOfferBannerKeys
import com.example.data.SupportedThemeKeys
import com.example.data.FirebaseService
import com.example.data.MaintenanceSummary
import com.example.data.ProductImportParser
import com.example.data.NotificationSettings
import com.example.data.ProductImportResult
import com.example.ui.theme.LocalExpressiveStyle
import com.example.ui.theme.LocalNrdDarkMode
import com.example.ui.theme.LocalGlassSoftStyle
import com.example.ui.theme.glassSoftShadow
import com.example.ui.theme.expressiveShadow

private const val NEW_CATEGORY_ACTION_KEY = "__new_category__"
private const val CATEGORY_PAGE_SIZE = 15
private const val BACKGROUND_PAGE_SIZE = 6
private const val CONSULTATION_BACKGROUND_KEY = "__consultar_produtos__"
private const val CARD_APPEARANCE_KEY = "__aparencia_cartao__"
private const val OFFER_BANNER_KEY_PREFIX = "__oferta__"
private val offerBannerLabels = linkedMapOf(
    "standard" to "Banner padrão",
    "club" to "Banner Preço Clube",
    "de_por" to "Banner De/Por",
    "take_pay" to "Banner Leve/Pague",
    "second_unit" to "Banner 2ª unidade / 50%",
    "cashback" to "Banner Cashback",
    "wholesale" to "Banner Atacado"
)

private fun offerEditorKey(offerKey: String) = "$OFFER_BANNER_KEY_PREFIX$offerKey"
private fun offerKeyFromEditorKey(editorKey: String): String? = editorKey
    .takeIf { it.startsWith(OFFER_BANNER_KEY_PREFIX) }
    ?.removePrefix(OFFER_BANNER_KEY_PREFIX)
    ?.takeIf { it in SupportedOfferBannerKeys }

private data class PendingDefaultBannerChange(
    val themeKey: String,
    val background: ThemeBackground,
    val maskSettings: BannerMaskSettings
)

private enum class MestrePanelPage(val title: String) {
    DASHBOARD("Painel Mestre"),
    SUGGESTIONS("Pendências"),
    CONTENT("Conteúdo e catálogo"),
    CATEGORIES("Categorias"),
    SETTINGS("Configuração do aplicativo"),
    NOVELTY_SETTINGS("Inserir Novidade"),
    WORK_SCHEDULE_SETTINGS("Inserir Escala"),
    HOME_SETTINGS("Configurações da Home"),
    NOTIFICATION_SETTINGS("Notificações globais"),
    APPEARANCE_SETTINGS("Fundos por tema"),
    CARD_APPEARANCE_SETTINGS("Aparência Cartão"),
    BUBBLE_SETTINGS("Movimentos das Bolhas"),
    CONSULTATION_APPEARANCE_SETTINGS("Aparência Consultar Produtos"),
    ADVANCED("Ferramentas avançadas")
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MestreScreen(
    viewModel: MainViewModel,
    onNavigateToAdmin: () -> Unit,
    onNavigateToManageTabs: () -> Unit,
    onNavigateToManageProducts: () -> Unit,
    onNavigateBack: () -> Unit,
    quickEditThemeKey: String? = null
) {
    var pageStack by rememberSaveable(quickEditThemeKey) {
        mutableStateOf(
            if (quickEditThemeKey.isNullOrBlank()) arrayListOf(MestrePanelPage.DASHBOARD.name)
            else arrayListOf(MestrePanelPage.APPEARANCE_SETTINGS.name)
        )
    }
    val currentPage = MestrePanelPage.entries.firstOrNull { it.name == pageStack.lastOrNull() }
        ?: MestrePanelPage.DASHBOARD
    val openPage: (MestrePanelPage) -> Unit = { page ->
        if (page != currentPage) pageStack = ArrayList(pageStack + page.name)
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val catalogSnapshots by viewModel.catalogSnapshots.collectAsStateWithLifecycle()
    val isLoadingCatalogHistory by viewModel.isLoadingCatalogHistory.collectAsStateWithLifecycle()
    val allProducts by viewModel.allProducts.collectAsStateWithLifecycle()
    val localCategoryCounts = remember(allProducts) {
        allProducts
            .groupingBy { it.category.ifBlank { "Sem categoria" } }
            .eachCount()
            .map { com.example.data.CategoryCount(it.key, it.value) }
            .sortedByDescending { it.count }
    }
    val homeSettings by viewModel.homeSettings.collectAsStateWithLifecycle()
    var draftHomeSettings by remember(homeSettings) { mutableStateOf(homeSettings) }
    var isSavingHomeSettings by remember { mutableStateOf(false) }
    val categoryDefinitions by viewModel.categoryDefinitions.collectAsStateWithLifecycle()
    var showCategoryDialog by remember { mutableStateOf(false) }
    val notificationSettingsFlow = remember(currentPage) {
        if (currentPage == MestrePanelPage.NOTIFICATION_SETTINGS) {
            FirebaseService.observeNotificationSettings()
        } else {
            kotlinx.coroutines.flow.flowOf(NotificationSettings())
        }
    }
    val notificationSettings by notificationSettingsFlow
        .collectAsStateWithLifecycle(initialValue = NotificationSettings())
    var draftNotificationSettings by remember(notificationSettings) { mutableStateOf(notificationSettings) }
    var isSavingNotificationSettings by remember { mutableStateOf(false) }
    val appearanceSettingsFlow = remember(currentPage) {
        if (currentPage == MestrePanelPage.APPEARANCE_SETTINGS ||
            currentPage == MestrePanelPage.CARD_APPEARANCE_SETTINGS ||
            currentPage == MestrePanelPage.BUBBLE_SETTINGS ||
            currentPage == MestrePanelPage.CONSULTATION_APPEARANCE_SETTINGS
        ) {
            FirebaseService.observeAppearanceSettings()
        } else {
            kotlinx.coroutines.flow.flowOf(AppearanceSettings())
        }
    }
    val appearanceSettings by appearanceSettingsFlow
        .collectAsStateWithLifecycle(initialValue = AppearanceSettings())
    var draftAppearanceSettings by remember(appearanceSettings) { mutableStateOf(appearanceSettings) }
    val pendingBubbleImageDeletes = remember { mutableStateListOf<String>() }
    var draftDefaultThemeBackgrounds by remember(appearanceSettings.defaultThemeBackgrounds) {
        mutableStateOf(appearanceSettings.defaultThemeBackgrounds)
    }
    var draftThemeBackgrounds by remember(appearanceSettings.themeBackgrounds) {
        mutableStateOf(appearanceSettings.themeBackgrounds)
    }
    var cardAppearanceThemeKey by rememberSaveable { mutableStateOf("red") }
    var draftCardBackgrounds by remember(appearanceSettings.cardBackgrounds) {
        mutableStateOf(appearanceSettings.cardBackgrounds)
    }
    var draftCardBackgroundSchedules by remember(appearanceSettings.cardBackgroundSchedules) {
        mutableStateOf(appearanceSettings.cardBackgroundSchedules)
    }
    var draftCardScheduleStarts by remember(appearanceSettings.cardBackgroundSchedules) {
        mutableStateOf(appearanceSettings.cardBackgroundSchedules.mapValues { it.value.startDate.orEmpty() })
    }
    var draftCardScheduleEnds by remember(appearanceSettings.cardBackgroundSchedules) {
        mutableStateOf(appearanceSettings.cardBackgroundSchedules.mapValues { it.value.endDate.orEmpty() })
    }
    var showCardStartDatePicker by remember { mutableStateOf(false) }
    var showCardEndDatePicker by remember { mutableStateOf(false) }
    var cardScheduleError by remember { mutableStateOf<String?>(null) }
    var showDeleteCardBackgroundDialog by remember { mutableStateOf(false) }
    var isSavingCardAppearance by remember { mutableStateOf(false) }
    var draftConsultationBackgrounds by remember(appearanceSettings.consultationBackgrounds) {
        mutableStateOf(appearanceSettings.consultationBackgrounds)
    }
    var draftOfferBanners by remember(appearanceSettings.offerBanners) {
        mutableStateOf(appearanceSettings.offerBanners)
    }
    var consultationBackgroundPage by rememberSaveable { mutableIntStateOf(0) }
    var showDiscardChangesDialog by remember { mutableStateOf(false) }
    var isSavingAppearanceSettings by remember { mutableStateOf(false) }
    var isSavingGlobalAppearance by remember { mutableStateOf(false) }
    var isSavingThemeBackgrounds by remember { mutableStateOf(false) }
    var isSavingConsultationAppearance by remember { mutableStateOf(false) }
    var expandedBackgroundThemes by remember { mutableStateOf<Set<String>>(emptySet()) }
    var backgroundPages by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var showThemeBackgroundDialog by remember { mutableStateOf(false) }
    var editingBackgroundTheme by remember { mutableStateOf<String?>(null) }
    var editingBackground by remember { mutableStateOf<ThemeBackground?>(null) }
    var editingDefaultBackground by remember { mutableStateOf(false) }
    var backgroundLabelInput by remember { mutableStateOf("") }
    var backgroundUrlInput by remember { mutableStateOf("") }
    var backgroundStartDateInput by remember { mutableStateOf("") }
    var backgroundEndDateInput by remember { mutableStateOf("") }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var backgroundInputError by remember { mutableStateOf<String?>(null) }
    var isUploadingThemeBackground by remember { mutableStateOf(false) }
    var backgroundToDelete by remember { mutableStateOf<Pair<String, ThemeBackground>?>(null) }
    var backgroundToPreview by remember { mutableStateOf<Pair<String, ThemeBackground>?>(null) }
    var pendingDefaultBannerChange by remember { mutableStateOf<PendingDefaultBannerChange?>(null) }
    var quickPreviewOpened by remember(quickEditThemeKey) { mutableStateOf(false) }
    var maintenanceSummary by remember { mutableStateOf<MaintenanceSummary?>(null) }
    var isLoadingMaintenance by remember { mutableStateOf(false) }
    var installationSummary by remember { mutableStateOf<DeviceInstallationSummary?>(null) }
    var installationSummaryError by remember { mutableStateOf<String?>(null) }
    var isLoadingInstallationSummary by remember { mutableStateOf(false) }
    var snapshotToRestore by remember { mutableStateOf<CatalogSnapshot?>(null) }
    var showAllCatalogBackups by rememberSaveable { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<CategoryDefinition?>(null) }
    var categoryName by remember { mutableStateOf("") }
    var categoryActionInProgress by remember { mutableStateOf<String?>(null) }
    var categoryPage by rememberSaveable { mutableIntStateOf(0) }
    val suggestions by FirebaseService.observeSuggestions().collectAsStateWithLifecycle(initialValue = emptyList())
    val coroutineScope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val masterPreferences = remember(context) { UserPreferences(context.applicationContext) }
    val installationNotificationsEnabled by masterPreferences.masterInstallationNotificationsEnabled
        .collectAsStateWithLifecycle(initialValue = false)
    val expressive = LocalExpressiveStyle.current.enabled
    val glassStyle = LocalGlassSoftStyle.current
    val screenProfile = rememberNrdScreenProfile()
    val themeBackgroundLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        val themeKey = editingBackgroundTheme
        if (uri == null || themeKey == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            isUploadingThemeBackground = true
            backgroundInputError = null
            try {
                val uploadFolder = when {
                    themeKey == CONSULTATION_BACKGROUND_KEY -> "consultation_backgrounds"
                    offerKeyFromEditorKey(themeKey) != null -> "offer_banners/${offerKeyFromEditorKey(themeKey)}"
                    themeKey == CARD_APPEARANCE_KEY -> "benefit_card_backgrounds/$cardAppearanceThemeKey"
                    else -> "theme_backgrounds/$themeKey"
                }
                val uploadedUrl = FirebaseService.uploadImageToStorage(
                    uri,
                    "$uploadFolder/${UUID.randomUUID()}.jpg"
                )
                if (uploadedUrl.isNullOrBlank()) {
                    backgroundInputError = FirebaseService.lastError ?: "Não foi possível enviar a imagem."
                } else {
                    if (themeKey == CARD_APPEARANCE_KEY) {
                        val themeKeyForCard = cardAppearanceThemeKey
                        draftCardBackgrounds = draftCardBackgrounds + (themeKeyForCard to uploadedUrl)
                        draftCardBackgroundSchedules[themeKeyForCard]?.let { oldSchedule ->
                            draftCardBackgroundSchedules = draftCardBackgroundSchedules +
                                (themeKeyForCard to oldSchedule.copy(url = uploadedUrl))
                        }
                    } else {
                        backgroundUrlInput = uploadedUrl
                    }
                }
            } finally {
                isUploadingThemeBackground = false
            }
        }
    }
    val bubblePngLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val replacedImageUrl = draftAppearanceSettings.bubbleImageUrl
        coroutineScope.launch {
            isUploadingThemeBackground = true
            try {
                val url = FirebaseService.uploadImageToStorage(uri, "glass_particles/${UUID.randomUUID()}.png")
                if (url.isNullOrBlank()) {
                    snackbarHostState.showSnackbar(FirebaseService.lastError ?: "Não foi possível enviar o PNG.")
                } else {
                    if (replacedImageUrl.isNotBlank() && replacedImageUrl != url) {
                        pendingBubbleImageDeletes.add(replacedImageUrl)
                    }
                    draftAppearanceSettings = draftAppearanceSettings.copy(bubbleImageUrl = url)
                    snackbarHostState.showSnackbar("PNG carregado. Salve os movimentos para publicar.")
                }
            } finally {
                isUploadingThemeBackground = false
            }
        }
    }
    var importResult by remember { mutableStateOf<ProductImportResult?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }
    var isParsingImport by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }
    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        coroutineScope.launch {
            isParsingImport = true
            importResult = ProductImportParser.parse(context, uri)
            isParsingImport = false
            showImportDialog = true
        }
    }
    val themeOptions = listOf(
        "multicolor" to "Multicolorido",
        "red" to "Vermelho",
        "gold" to "Dourado",
        "green" to "Verde",
        "blue" to "Azul",
        "orange" to "Laranja",
        "glass" to "Glass Soft",
        "expressive" to "Expressivo"
    )
    val appearanceModeOptions = listOf(
        "system" to "Seguir sistema",
        "light" to "Claro",
        "dark" to "Escuro"
    )
    var expandedRemoteTheme by remember { mutableStateOf(false) }
    var expandedRemoteMode by remember { mutableStateOf(false) }
    var expandedBubbleMotion by remember { mutableStateOf(false) }
    var expandedBubbleShape by remember { mutableStateOf(false) }

    fun defaultBackgroundFor(themeKey: String): ThemeBackground =
        draftDefaultThemeBackgrounds[themeKey] ?: ThemeBackground(
            id = "default-$themeKey",
            label = "Banner padrão do aplicativo",
            url = ""
        )

    fun openBackgroundEditor(
        themeKey: String,
        background: ThemeBackground?,
        isDefault: Boolean = false
    ) {
        editingBackgroundTheme = themeKey
        editingBackground = background
        editingDefaultBackground = isDefault
        backgroundLabelInput = background?.label.orEmpty()
        backgroundUrlInput = background?.url.orEmpty()
        backgroundStartDateInput = background?.startDate.orEmpty()
        backgroundEndDateInput = background?.endDate.orEmpty()
        showStartDatePicker = false
        showEndDatePicker = false
        backgroundInputError = null
        showThemeBackgroundDialog = true
    }

    LaunchedEffect(quickEditThemeKey, appearanceSettings.themeBackgrounds) {
        val themeKey = quickEditThemeKey?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        if (!quickPreviewOpened) {
            appearanceSettings.activeBackgroundFor(themeKey)?.let { activeBackground ->
                backgroundToPreview = themeKey to activeBackground
                quickPreviewOpened = true
            }
        }
    }

    fun pickerDateToIsoDate(millis: Long?): String? = millis?.let {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(it))
    }

    fun dateToPickerMillis(value: String): Long? = ThemeBackground.parseDate(value)?.time

    fun backgroundsForKey(themeKey: String): List<ThemeBackground> =
        when {
            themeKey == CONSULTATION_BACKGROUND_KEY -> draftConsultationBackgrounds
            offerKeyFromEditorKey(themeKey) != null -> draftOfferBanners[offerKeyFromEditorKey(themeKey)].orEmpty()
            else -> draftThemeBackgrounds[themeKey].orEmpty()
        }

    fun updateBackgrounds(themeKey: String, backgrounds: List<ThemeBackground>) {
        when {
            themeKey == CONSULTATION_BACKGROUND_KEY -> draftConsultationBackgrounds = backgrounds
            offerKeyFromEditorKey(themeKey) != null -> {
                val offerKey = requireNotNull(offerKeyFromEditorKey(themeKey))
                draftOfferBanners = draftOfferBanners + (offerKey to backgrounds)
            }
            else -> draftThemeBackgrounds = draftThemeBackgrounds + (themeKey to backgrounds)
        }
    }
    val homeHasChanges = draftHomeSettings != homeSettings
    val notificationsHaveChanges = draftNotificationSettings != notificationSettings
    val appearanceDraft = draftAppearanceSettings.copy(
        defaultThemeBackgrounds = draftDefaultThemeBackgrounds,
        themeBackgrounds = draftThemeBackgrounds,
        consultationBackgrounds = draftConsultationBackgrounds,
        offerBanners = draftOfferBanners
    )
    val globalAppearanceHasChanges =
        draftAppearanceSettings.overrideLocalTheme != appearanceSettings.overrideLocalTheme ||
            draftAppearanceSettings.theme != appearanceSettings.theme ||
            draftAppearanceSettings.appearanceMode != appearanceSettings.appearanceMode ||
            draftAppearanceSettings.bubbleSpeed != appearanceSettings.bubbleSpeed ||
            draftAppearanceSettings.bubbleMotion != appearanceSettings.bubbleMotion ||
            draftAppearanceSettings.bubbleSize != appearanceSettings.bubbleSize ||
            draftAppearanceSettings.bubbleExtraCount != appearanceSettings.bubbleExtraCount ||
            draftAppearanceSettings.bubbleBrightness != appearanceSettings.bubbleBrightness ||
            draftAppearanceSettings.bubbleOutline != appearanceSettings.bubbleOutline ||
            draftAppearanceSettings.bubbleShape != appearanceSettings.bubbleShape ||
            draftAppearanceSettings.bubbleImageUrl != appearanceSettings.bubbleImageUrl ||
            draftAppearanceSettings.bubbleAlphaMin != appearanceSettings.bubbleAlphaMin ||
            draftAppearanceSettings.bubbleAlphaMax != appearanceSettings.bubbleAlphaMax ||
            draftAppearanceSettings.bubbleSway != appearanceSettings.bubbleSway ||
            draftAppearanceSettings.bubbleSpawnRate != appearanceSettings.bubbleSpawnRate ||
            draftAppearanceSettings.bubbleScalePulse != appearanceSettings.bubbleScalePulse ||
            draftAppearanceSettings.bubbleRotation != appearanceSettings.bubbleRotation ||
            draftAppearanceSettings.bubbleFade != appearanceSettings.bubbleFade
    val themeBackgroundsHaveChanges =
        draftDefaultThemeBackgrounds != appearanceSettings.defaultThemeBackgrounds ||
            draftThemeBackgrounds != appearanceSettings.themeBackgrounds
    val cardScheduleDatesHaveChanges = SupportedThemeKeys.any { themeKey ->
        draftCardScheduleStarts[themeKey].orEmpty() != appearanceSettings.cardBackgroundSchedules[themeKey]?.startDate.orEmpty() ||
            draftCardScheduleEnds[themeKey].orEmpty() != appearanceSettings.cardBackgroundSchedules[themeKey]?.endDate.orEmpty()
    }
    val cardAppearanceHasChanges = draftCardBackgrounds != appearanceSettings.cardBackgrounds ||
        draftCardBackgroundSchedules != appearanceSettings.cardBackgroundSchedules || cardScheduleDatesHaveChanges
    val consultationAppearanceHasChanges =
        draftConsultationBackgrounds != appearanceSettings.consultationBackgrounds ||
            draftOfferBanners != appearanceSettings.offerBanners
    val appearancePageHasChanges = globalAppearanceHasChanges || themeBackgroundsHaveChanges
    val consultationDraft = appearanceSettings.copy(
        consultationBackgrounds = draftConsultationBackgrounds,
        offerBanners = draftOfferBanners
    )
    val currentPageHasChanges = when (currentPage) {
        MestrePanelPage.HOME_SETTINGS -> homeHasChanges
        MestrePanelPage.NOTIFICATION_SETTINGS -> notificationsHaveChanges
        MestrePanelPage.APPEARANCE_SETTINGS, MestrePanelPage.BUBBLE_SETTINGS -> appearancePageHasChanges
        MestrePanelPage.CARD_APPEARANCE_SETTINGS -> cardAppearanceHasChanges
        MestrePanelPage.CONSULTATION_APPEARANCE_SETTINGS -> consultationAppearanceHasChanges
        else -> false
    }
    val performPanelBack: () -> Unit = {
        if (pageStack.size > 1) pageStack = ArrayList(pageStack.dropLast(1))
        else onNavigateBack()
    }
    val returnFromPage: () -> Unit = {
        if (currentPageHasChanges) showDiscardChangesDialog = true
        else performPanelBack()
    }
    BackHandler(enabled = currentPage != MestrePanelPage.DASHBOARD) {
        returnFromPage()
    }
    val panelScrollState = rememberScrollState()

    LaunchedEffect(currentPage) {
        panelScrollState.scrollTo(0)
        if (currentPage == MestrePanelPage.ADVANCED) {
            isLoadingInstallationSummary = true
            installationSummaryError = null
            when (val result = DeviceInstallationTracker.fetchSummary()) {
                is DeviceInstallationSummaryResult.Success -> installationSummary = result.summary
                is DeviceInstallationSummaryResult.Error -> installationSummaryError = result.message
            }
            isLoadingInstallationSummary = false
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshCatalogHistory()
        viewModel.syncMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        containerColor = if (expressive || glassStyle.enabled) Color.Transparent else MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        currentPage.title,
                        fontWeight = if (expressive) androidx.compose.ui.text.font.FontWeight.ExtraBold else androidx.compose.ui.text.font.FontWeight.Normal
                    )
                },
                navigationIcon = {
                    IconButton(onClick = returnFromPage) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (expressive || glassStyle.enabled) Color.Transparent else MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(panelScrollState)
                .padding(
                    horizontal = if (screenProfile.compact) 10.dp else 16.dp,
                    vertical = if (screenProfile.compact) 10.dp else 16.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (currentPage == MestrePanelPage.DASHBOARD) {
                NfcInstallTestPanel()
                Spacer(modifier = Modifier.height(20.dp))
                MestreDashboardOverview(
                    pendingSuggestions = suggestions.count { it.status == com.example.data.ProductSuggestion.STATUS_PENDING },
                    productCount = allProducts.size,
                    activeCategoryCount = categoryDefinitions.count { it.isActive },
                    categoryCount = categoryDefinitions.size,
                    latestBackupAt = catalogSnapshots.maxOfOrNull { it.createdAt },
                    importEnabled = !isParsingImport && !isImporting,
                    onOpenCatalog = { openPage(MestrePanelPage.CONTENT) },
                    onOpenCategories = { openPage(MestrePanelPage.CATEGORIES) },
                    onManageTabs = onNavigateToManageTabs,
                    onImportProducts = { importLauncher.launch("text/*") }
                )
                Spacer(modifier = Modifier.height(24.dp))

                MestreSuggestionsPreview(
                    suggestions = suggestions,
                    onViewAll = { openPage(MestrePanelPage.SUGGESTIONS) }
                )
                Spacer(modifier = Modifier.height(20.dp))
                MestrePanelAreaNavigation(
                    onOpenCatalog = { openPage(MestrePanelPage.CONTENT) },
                    onOpenSettings = { openPage(MestrePanelPage.SETTINGS) },
                    onOpenAdvanced = { openPage(MestrePanelPage.ADVANCED) }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (currentPage == MestrePanelPage.SUGGESTIONS) {
                MestreSuggestionsSection(
                    suggestions = suggestions,
                    showHeader = false
                ) { suggestion, status ->
                    val updated = FirebaseService.updateSuggestionStatus(suggestion.id, status)
                    val statusLabel = if (status == "fixed") "corrigida" else "pendente"
                    val message = if (updated) "Sugestão marcada como $statusLabel."
                    else "Não foi possível atualizar a sugestão. Tente novamente."
                    coroutineScope.launch { snackbarHostState.showSnackbar(message) }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (currentPage == MestrePanelPage.CONTENT) {
                MestreContentHub(
                    importEnabled = !isParsingImport && !isImporting,
                    onManageProducts = onNavigateToManageProducts,
                    onAddProduct = onNavigateToAdmin,
                    onOpenCategories = { openPage(MestrePanelPage.CATEGORIES) },
                    onManageTabs = onNavigateToManageTabs,
                    onImportProducts = { importLauncher.launch("text/*") }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (currentPage == MestrePanelPage.SETTINGS) {
                MestreSettingsHub(
                    onOpenHome = { openPage(MestrePanelPage.HOME_SETTINGS) },
                    onOpenAppearance = { openPage(MestrePanelPage.APPEARANCE_SETTINGS) },
                    onOpenCardAppearance = { openPage(MestrePanelPage.CARD_APPEARANCE_SETTINGS) },
                    onOpenBubbles = { openPage(MestrePanelPage.BUBBLE_SETTINGS) },
                    onOpenConsultationAppearance = { openPage(MestrePanelPage.CONSULTATION_APPEARANCE_SETTINGS) },
                    onOpenNotifications = { openPage(MestrePanelPage.NOTIFICATION_SETTINGS) },
                    onOpenNovelties = { openPage(MestrePanelPage.NOVELTY_SETTINGS) },
                    onOpenWorkSchedule = { openPage(MestrePanelPage.WORK_SCHEDULE_SETTINGS) }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (currentPage == MestrePanelPage.NOVELTY_SETTINGS) {
                MestreNoveltySettings()
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (currentPage == MestrePanelPage.WORK_SCHEDULE_SETTINGS) {
                MestreWorkScheduleSettings()
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (currentPage == MestrePanelPage.ADVANCED) {
                MestreAdvancedSection(
                    maintenanceSummary = maintenanceSummary,
                    isLoadingMaintenance = isLoadingMaintenance,
                    isLoadingCatalogHistory = isLoadingCatalogHistory,
                    isSyncing = isSyncing,
                    installationSummary = installationSummary,
                    installationSummaryError = installationSummaryError,
                    isLoadingInstallationSummary = isLoadingInstallationSummary,
                    installationNotificationsEnabled = installationNotificationsEnabled,
                    catalogSnapshots = catalogSnapshots,
                    showAllCatalogBackups = showAllCatalogBackups,
                    onShowAllCatalogBackupsChange = { showAllCatalogBackups = it },
                    onUpdateMaintenance = {
                        coroutineScope.launch {
                            isLoadingMaintenance = true
                            try {
                                val result = CatalogHistoryBackend.getMaintenanceSummary(
                                    localProductCount = allProducts.size,
                                    localCategoryCounts = localCategoryCounts
                                )
                                maintenanceSummary = result
                                if (!result.remoteAvailable) {
                                    snackbarHostState.showSnackbar(
                                        FirebaseService.lastError ?: "Não foi possível consultar a nuvem. Tente novamente."
                                    )
                                }
                            } finally {
                                isLoadingMaintenance = false
                            }
                        }
                    },
                    onRefreshInstallations = {
                        coroutineScope.launch {
                            isLoadingInstallationSummary = true
                            installationSummaryError = null
                            when (val result = DeviceInstallationTracker.fetchSummary()) {
                                is DeviceInstallationSummaryResult.Success -> installationSummary = result.summary
                                is DeviceInstallationSummaryResult.Error -> installationSummaryError = result.message
                            }
                            isLoadingInstallationSummary = false
                        }
                    },
                    onInstallationNotificationsChange = { enabled ->
                        coroutineScope.launch {
                            if (enabled) {
                                val baseline = when (val result = DeviceInstallationTracker.fetchSummary()) {
                                    is DeviceInstallationSummaryResult.Success -> {
                                        installationSummary = result.summary
                                        result.summary.lastInstallationAt ?: System.currentTimeMillis()
                                    }
                                    is DeviceInstallationSummaryResult.Error -> {
                                        installationSummaryError = result.message
                                        System.currentTimeMillis()
                                    }
                                }
                                masterPreferences.setMasterInstallationNotificationBaseline(baseline)
                                masterPreferences.setMasterInstallationNotificationsEnabled(true)
                                com.example.util.InstallationNotificationWorker.schedule(context.applicationContext)
                            } else {
                                masterPreferences.setMasterInstallationNotificationsEnabled(false)
                                com.example.util.InstallationNotificationWorker.cancel(context.applicationContext)
                            }
                        }
                    },
                    onCreateCatalogSnapshot = { viewModel.createCatalogSnapshot() },
                    onRefreshCatalogHistory = { viewModel.refreshCatalogHistory() },
                    onRestoreSnapshot = { snapshotToRestore = it }
                )
            }

            if (currentPage == MestrePanelPage.HOME_SETTINGS) {
            MestrePageIntro(
                description = "Escolha o que aparece para todos os usuários",
                hasUnsavedChanges = homeHasChanges
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedCard(modifier = Modifier.fillMaxWidth().glassSoftShadow(MaterialTheme.shapes.medium)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Seções visíveis",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                    HomeSettingSwitch(
                        label = "Categorias",
                        checked = draftHomeSettings.showCategories,
                        onCheckedChange = { draftHomeSettings = draftHomeSettings.copy(showCategories = it) }
                    )
                    HomeSettingSwitch(
                        label = "Mais utilizados",
                        checked = draftHomeSettings.showMostUsed,
                        onCheckedChange = { draftHomeSettings = draftHomeSettings.copy(showMostUsed = it) }
                    )
                    HomeSettingSwitch(
                        label = "Histórico recente",
                        checked = draftHomeSettings.showHistory,
                        onCheckedChange = { draftHomeSettings = draftHomeSettings.copy(showHistory = it) }
                    )
                    HomeSettingSwitch(
                        label = "Meus favoritos",
                        checked = draftHomeSettings.showFavorites,
                        onCheckedChange = { draftHomeSettings = draftHomeSettings.copy(showFavorites = it) }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Ações do cabeçalho",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                    HomeSettingSwitch(
                        label = "Mostrar ícone do menu",
                        checked = draftHomeSettings.showDrawerIcon,
                        onCheckedChange = { draftHomeSettings = draftHomeSettings.copy(showDrawerIcon = it) }
                    )
                    HomeSettingSwitch(
                        label = "Mostrar sino quando houver notificações",
                        checked = draftHomeSettings.showNotificationIcon,
                        onCheckedChange = { draftHomeSettings = draftHomeSettings.copy(showNotificationIcon = it) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Mais utilizados: ${draftHomeSettings.mostUsedLimit} produtos", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = draftHomeSettings.mostUsedLimit.toFloat(),
                        onValueChange = {
                            draftHomeSettings = draftHomeSettings.copy(mostUsedLimit = it.toInt())
                        },
                        valueRange = 1f..50f,
                        steps = 48
                    )
                    Text("Intervalo do carrossel: ${draftHomeSettings.carouselIntervalSeconds}s", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = draftHomeSettings.carouselIntervalSeconds.toFloat(),
                        onValueChange = {
                            draftHomeSettings = draftHomeSettings.copy(carouselIntervalSeconds = it.toInt())
                        },
                        valueRange = 3f..30f,
                        steps = 26
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isSavingHomeSettings = true
                                val saved = FirebaseService.saveHomeSettings(draftHomeSettings)
                                isSavingHomeSettings = false
                                snackbarHostState.showSnackbar(
                                    if (saved) "Configurações da Home publicadas para todos."
                                    else FirebaseService.lastError ?: "Não foi possível publicar as configurações da Home."
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = homeHasChanges && !isSavingHomeSettings
                    ) {
                        if (isSavingHomeSettings) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Publicando...")
                        } else if (homeHasChanges) {
                            Text("Publicar configurações")
                        } else {
                            Text("Tudo atualizado")
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            }

            if (currentPage == MestrePanelPage.CATEGORIES) {
            MestrePageIntro("Organize os grupos exibidos e usados no catálogo")
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedCard(modifier = Modifier.fillMaxWidth().glassSoftShadow(MaterialTheme.shapes.medium)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Button(
                        onClick = {
                            editingCategory = null
                            categoryName = ""
                            showCategoryDialog = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = categoryActionInProgress == null
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Adicionar categoria")
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    val orderedCategories = categoryDefinitions
                        .sortedWith(compareBy<CategoryDefinition> { it.displayOrder }.thenBy { it.name })
                    val categoryPagination = calculatePaginationWindow(
                        totalItems = orderedCategories.size,
                        requestedPage = categoryPage,
                        pageSize = CATEGORY_PAGE_SIZE
                    )
                    LaunchedEffect(orderedCategories.size) {
                        if (categoryPage != categoryPagination.pageIndex) {
                            categoryPage = categoryPagination.pageIndex
                        }
                    }
                    if (orderedCategories.isNotEmpty()) {
                        Text(
                            "Exibindo ${categoryPagination.fromIndex + 1}–${categoryPagination.toIndex} de ${orderedCategories.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    orderedCategories
                        .subList(categoryPagination.fromIndex, categoryPagination.toIndex)
                        .forEachIndexed { pageIndex, category ->
                            val globalIndex = categoryPagination.fromIndex + pageIndex
                            CategoryManagementRow(
                                category = category,
                                isFirst = globalIndex == 0,
                                isLast = globalIndex == orderedCategories.lastIndex,
                                enabled = categoryActionInProgress == null,
                                isUpdating = categoryActionInProgress == category.id,
                                onMoveUp = {
                                    if (categoryActionInProgress == null) {
                                        categoryActionInProgress = category.id
                                        coroutineScope.launch {
                                            try {
                                                viewModel.moveCategory(category, -1)
                                            } finally {
                                                categoryActionInProgress = null
                                            }
                                        }
                                    }
                                },
                                onMoveDown = {
                                    if (categoryActionInProgress == null) {
                                        categoryActionInProgress = category.id
                                        coroutineScope.launch {
                                            try {
                                                viewModel.moveCategory(category, 1)
                                            } finally {
                                                categoryActionInProgress = null
                                            }
                                        }
                                    }
                                },
                                onEdit = {
                                    editingCategory = category
                                    categoryName = category.name
                                    showCategoryDialog = true
                                },
                                onActiveChange = { isActive ->
                                    if (categoryActionInProgress == null) {
                                        categoryActionInProgress = category.id
                                        coroutineScope.launch {
                                            try {
                                                viewModel.setCategoryActive(category, isActive)
                                            } finally {
                                                categoryActionInProgress = null
                                            }
                                        }
                                    }
                                }
                            )
                            if (pageIndex < categoryPagination.toIndex - categoryPagination.fromIndex - 1) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            }
                        }
                    if (categoryPagination.pageCount > 1) {
                        MestrePaginationControls(
                            pageIndex = categoryPagination.pageIndex,
                            pageCount = categoryPagination.pageCount,
                            onPrevious = { categoryPage = categoryPagination.pageIndex - 1 },
                            onNext = { categoryPage = categoryPagination.pageIndex + 1 }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            }

            if (currentPage == MestrePanelPage.NOTIFICATION_SETTINGS) {
            MestrePageIntro(
                description = "Controle o que pode ser recebido pelos usuários",
                hasUnsavedChanges = notificationsHaveChanges
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedCard(modifier = Modifier.fillMaxWidth().glassSoftShadow(MaterialTheme.shapes.medium)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    NotificationSettingSwitch(
                        label = "Permitir notificações",
                        checked = draftNotificationSettings.enabled,
                        onCheckedChange = { draftNotificationSettings = draftNotificationSettings.copy(enabled = it) }
                    )
                    Text(
                        "As preferências individuais continuam sendo respeitadas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    NotificationSettingSwitch(
                        label = "Produto adicionado",
                        checked = draftNotificationSettings.productAddedEnabled,
                        onCheckedChange = { draftNotificationSettings = draftNotificationSettings.copy(productAddedEnabled = it) },
                        enabled = draftNotificationSettings.enabled
                    )
                    NotificationSettingSwitch(
                        label = "Código alterado",
                        checked = draftNotificationSettings.codeChangedEnabled,
                        onCheckedChange = { draftNotificationSettings = draftNotificationSettings.copy(codeChangedEnabled = it) },
                        enabled = draftNotificationSettings.enabled
                    )
                    NotificationSettingSwitch(
                        label = "Sugestão corrigida",
                        checked = draftNotificationSettings.suggestionFixedEnabled,
                        onCheckedChange = { draftNotificationSettings = draftNotificationSettings.copy(suggestionFixedEnabled = it) },
                        enabled = draftNotificationSettings.enabled
                    )
                    NotificationSettingSwitch(
                        label = "Atualização do app",
                        checked = draftNotificationSettings.appUpdateEnabled,
                        onCheckedChange = { draftNotificationSettings = draftNotificationSettings.copy(appUpdateEnabled = it) },
                        enabled = draftNotificationSettings.enabled
                    )
                    NotificationSettingSwitch(
                        label = "Promoções atualizadas",
                        checked = draftNotificationSettings.promotionUpdatedEnabled,
                        onCheckedChange = { draftNotificationSettings = draftNotificationSettings.copy(promotionUpdatedEnabled = it) },
                        enabled = draftNotificationSettings.enabled
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isSavingNotificationSettings = true
                                val saved = FirebaseService.saveNotificationSettings(draftNotificationSettings)
                                isSavingNotificationSettings = false
                                snackbarHostState.showSnackbar(
                                    if (saved) "Política de notificações publicada para todos."
                                    else FirebaseService.lastError ?: "Não foi possível publicar a política de notificações."
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = notificationsHaveChanges && !isSavingNotificationSettings
                    ) {
                        if (isSavingNotificationSettings) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Publicando...")
                        } else if (notificationsHaveChanges) {
                            Text("Publicar notificações")
                        } else {
                            Text("Tudo atualizado")
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            }

            if (currentPage == MestrePanelPage.BUBBLE_SETTINGS) {
                MestrePageIntro(
                    description = "Personalize o movimento das bolhas do Glass Expressivo para os usuários do aplicativo.",
                    hasUnsavedChanges = globalAppearanceHasChanges
                )
                Spacer(modifier = Modifier.height(12.dp))
                MestreSectionHeader(
                    title = "Movimentos das Bolhas",
                    description = "Ajuste movimento, tamanho, quantidade e aparência das bolhas"
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedCard(modifier = Modifier.fillMaxWidth().glassSoftShadow(MaterialTheme.shapes.medium)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        val motionOptions = listOf(
                            "random" to "Aleatória",
                            "circular" to "Circular",
                            "rise" to "Subida suave",
                            "drift" to "Deriva lateral"
                        )
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = motionOptions.find { it.first == draftAppearanceSettings.bubbleMotion }?.second ?: "Aleatória",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Modelo de movimento") },
                                trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Box(Modifier.matchParentSize().clickable(onClick = { expandedBubbleMotion = !expandedBubbleMotion }))
                            DropdownMenu(

                                expanded = expandedBubbleMotion,
                                onDismissRequest = { expandedBubbleMotion = false }
                            ) {
                                motionOptions.forEach { (key, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            draftAppearanceSettings = draftAppearanceSettings.copy(bubbleMotion = key)
                                            expandedBubbleMotion = false
                                        }
                                    )
                                }
                            }
                        }
                        val particleShapes = listOf(
                            "classic" to "Bolha clássica", "organic" to "Oval orgânica", "drop" to "Gota d’água",
                            "metaball" to "Bolha dupla fundida", "ring" to "Anel translúcido", "crystal" to "Cristal facetado",
                            "cluster" to "Microbolhas agrupadas", "sparkle" to "Estrela suave", "neon" to "Borda neon",
                            "capsule" to "Cápsula suave", "condensation" to "Pingos condensados",
                            "soap" to "Esfera iridescente", "lens" to "Disco / lente"
                        )
                        Box(Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = particleShapes.firstOrNull { it.first == draftAppearanceSettings.bubbleShape }?.second ?: "Bolha clássica",
                                onValueChange = {}, readOnly = true,
                                label = { Text("Forma da partícula") },
                                trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Box(Modifier.matchParentSize().clickable { expandedBubbleShape = true })
                            DropdownMenu(expanded = expandedBubbleShape, onDismissRequest = { expandedBubbleShape = false }) {
                                particleShapes.forEach { (key, label) -> DropdownMenuItem(text = { Text(label) }, onClick = {
                                    draftAppearanceSettings = draftAppearanceSettings.copy(bubbleShape = key)
                                    expandedBubbleShape = false
                                }) }
                            }
                        }
                        com.example.ui.theme.ExpressiveParticlePreview(
                            shapeModel = draftAppearanceSettings.bubbleShape,
                            imageUrl = draftAppearanceSettings.bubbleImageUrl,
                            modifier = Modifier.fillMaxWidth().height(132.dp)
                        )
                        Text(
                            "Prévia da partícula: ${particleShapes.firstOrNull { it.first == draftAppearanceSettings.bubbleShape }?.second ?: "Bolha clássica"}",
                            style = MaterialTheme.typography.labelMedium
                        )
                        if (draftAppearanceSettings.bubbleImageUrl.isNotBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                coil.compose.AsyncImage(
                                    model = draftAppearanceSettings.bubbleImageUrl,
                                    contentDescription = "Prévia do PNG personalizado",
                                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(14.dp))
                                )
                                Column(Modifier.weight(1f)) {
                                    Text("PNG personalizado", style = MaterialTheme.typography.titleSmall)
                                    Text("A imagem aparece na prévia acima.", style = MaterialTheme.typography.bodySmall)
                                }
                                TextButton(
                                    onClick = { bubblePngLauncher.launch("image/png") },
                                    enabled = !isUploadingThemeBackground
                                ) { Text("Editar / substituir") }
                                IconButton(
                                    onClick = {
                                        val imageUrl = draftAppearanceSettings.bubbleImageUrl
                                        if (imageUrl.isNotBlank()) pendingBubbleImageDeletes.add(imageUrl)
                                        draftAppearanceSettings = draftAppearanceSettings.copy(bubbleImageUrl = "")
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("PNG removido da configuração. Salve os movimentos para publicar.")
                                        }
                                    },
                                    enabled = !isUploadingThemeBackground
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Excluir PNG personalizado")
                                }
                            }
                        } else {
                            OutlinedButton(
                                onClick = { bubblePngLauncher.launch("image/png") },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isUploadingThemeBackground
                            ) {
                                Text(if (isUploadingThemeBackground) "Enviando PNG…" else "Usar PNG personalizado")
                            }
                        }
                        Text("Opacidade mínima: ${(draftAppearanceSettings.bubbleAlphaMin * 100).toInt()}%", style = MaterialTheme.typography.titleSmall)
                        Slider(value = draftAppearanceSettings.bubbleAlphaMin, onValueChange = {
                            draftAppearanceSettings = draftAppearanceSettings.copy(bubbleAlphaMin = it.coerceAtMost(draftAppearanceSettings.bubbleAlphaMax))
                        }, valueRange = 0.05f..0.9f)
                        Text("Opacidade máxima: ${(draftAppearanceSettings.bubbleAlphaMax * 100).toInt()}%", style = MaterialTheme.typography.titleSmall)
                        Slider(value = draftAppearanceSettings.bubbleAlphaMax, onValueChange = {
                            draftAppearanceSettings = draftAppearanceSettings.copy(bubbleAlphaMax = it.coerceAtLeast(draftAppearanceSettings.bubbleAlphaMin))
                        }, valueRange = 0.1f..1f)
                        Text("Oscilação lateral: ${(draftAppearanceSettings.bubbleSway * 50).toInt()}%", style = MaterialTheme.typography.titleSmall)
                        Slider(value = draftAppearanceSettings.bubbleSway, onValueChange = { draftAppearanceSettings = draftAppearanceSettings.copy(bubbleSway = it) }, valueRange = 0f..2f)
                        Text("Taxa de surgimento: ${String.format(Locale("pt", "BR"), "%.2f", draftAppearanceSettings.bubbleSpawnRate)}×", style = MaterialTheme.typography.titleSmall)
                        Slider(value = draftAppearanceSettings.bubbleSpawnRate, onValueChange = { draftAppearanceSettings = draftAppearanceSettings.copy(bubbleSpawnRate = it) }, valueRange = 0.25f..2f)
                        Text("Pulso de escala: ${(draftAppearanceSettings.bubbleScalePulse * 100).toInt()}%", style = MaterialTheme.typography.titleSmall)
                        Slider(value = draftAppearanceSettings.bubbleScalePulse, onValueChange = { draftAppearanceSettings = draftAppearanceSettings.copy(bubbleScalePulse = it) }, valueRange = 0f..0.5f)
                        Text("Rotação: ${(draftAppearanceSettings.bubbleRotation * 100).toInt()}%", style = MaterialTheme.typography.titleSmall)
                        Slider(value = draftAppearanceSettings.bubbleRotation, onValueChange = { draftAppearanceSettings = draftAppearanceSettings.copy(bubbleRotation = it) }, valueRange = 0f..1f)
                        Text("Fade do ciclo: ${(draftAppearanceSettings.bubbleFade * 100).toInt()}%", style = MaterialTheme.typography.titleSmall)
                        Slider(value = draftAppearanceSettings.bubbleFade, onValueChange = { draftAppearanceSettings = draftAppearanceSettings.copy(bubbleFade = it) }, valueRange = 0f..1f)
                        Text(
                            "Velocidade: ${String.format(Locale("pt", "BR"), "%.1f", draftAppearanceSettings.bubbleSpeed)}×",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Slider(
                            value = draftAppearanceSettings.bubbleSpeed,
                            onValueChange = { draftAppearanceSettings = draftAppearanceSettings.copy(bubbleSpeed = it) },
                            valueRange = 0.25f..2.5f,
                            steps = 8
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Lenta", style = MaterialTheme.typography.labelSmall)
                            Text("Rápida", style = MaterialTheme.typography.labelSmall)
                        }
                        Text(
                            "Tamanho: ${String.format(Locale("pt", "BR"), "%.0f", draftAppearanceSettings.bubbleSize * 100)}%",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Slider(
                            value = draftAppearanceSettings.bubbleSize,
                            onValueChange = { draftAppearanceSettings = draftAppearanceSettings.copy(bubbleSize = it) },
                            valueRange = 0.65f..1.8f,
                            steps = 4
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Menores", style = MaterialTheme.typography.labelSmall)
                            Text("Maiores", style = MaterialTheme.typography.labelSmall)
                        }
                        Text(
                            "Bolhas extras: +${draftAppearanceSettings.bubbleExtraCount}",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Slider(
                            value = draftAppearanceSettings.bubbleExtraCount.toFloat(),
                            onValueChange = { draftAppearanceSettings = draftAppearanceSettings.copy(bubbleExtraCount = it.toInt()) },
                            valueRange = 0f..12f,
                            steps = 11
                        )
                        Text(
                            "A quantidade é ajustada automaticamente conforme o desempenho do aparelho.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "Brilho: ${String.format(Locale("pt", "BR"), "%.0f", draftAppearanceSettings.bubbleBrightness * 100)}%",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Slider(
                            value = draftAppearanceSettings.bubbleBrightness,
                            onValueChange = { draftAppearanceSettings = draftAppearanceSettings.copy(bubbleBrightness = it) },
                            valueRange = 0.25f..2f,
                            steps = 6
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Contorno das bolhas", style = MaterialTheme.typography.titleSmall)
                                Text("Exibe o aro luminoso ao redor de cada bolha", style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(
                                checked = draftAppearanceSettings.bubbleOutline,
                                onCheckedChange = { draftAppearanceSettings = draftAppearanceSettings.copy(bubbleOutline = it) }
                            )
                        }
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    isSavingGlobalAppearance = true
                                    val settingsToSave = appearanceSettings.copy(
                                        bubbleSpeed = draftAppearanceSettings.bubbleSpeed,
                                        bubbleMotion = draftAppearanceSettings.bubbleMotion,
                                        bubbleSize = draftAppearanceSettings.bubbleSize,
                                        bubbleExtraCount = draftAppearanceSettings.bubbleExtraCount,
                                        bubbleBrightness = draftAppearanceSettings.bubbleBrightness,
                                        bubbleOutline = draftAppearanceSettings.bubbleOutline,
                                        bubbleShape = draftAppearanceSettings.bubbleShape,
                                        bubbleImageUrl = draftAppearanceSettings.bubbleImageUrl,
                                        bubbleAlphaMin = draftAppearanceSettings.bubbleAlphaMin,
                                        bubbleAlphaMax = draftAppearanceSettings.bubbleAlphaMax,
                                        bubbleSway = draftAppearanceSettings.bubbleSway,
                                        bubbleSpawnRate = draftAppearanceSettings.bubbleSpawnRate,
                                        bubbleScalePulse = draftAppearanceSettings.bubbleScalePulse,
                                        bubbleRotation = draftAppearanceSettings.bubbleRotation,
                                        bubbleFade = draftAppearanceSettings.bubbleFade
                                    )
                                    val saved = FirebaseService.saveAppearanceSettings(settingsToSave)
                                    isSavingGlobalAppearance = false
                                    if (saved) draftAppearanceSettings = settingsToSave
                                    if (saved && pendingBubbleImageDeletes.isNotEmpty()) {
                                        val cleanupUrls = pendingBubbleImageDeletes.distinct().filter { it != settingsToSave.bubbleImageUrl }
                                        val removedUrls = cleanupUrls.filter { FirebaseService.deleteGlassParticleImage(it) }
                                        pendingBubbleImageDeletes.removeAll(removedUrls.toSet())
                                    }
                                    snackbarHostState.showSnackbar(
                                        if (saved) "Movimentos das bolhas publicados para todos."
                                        else FirebaseService.lastError ?: "Não foi possível salvar os movimentos."
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = globalAppearanceHasChanges && !isSavingGlobalAppearance
                        ) {
                            Text(if (isSavingGlobalAppearance) "Salvando..." else "Salvar movimentos")
                        }
                    }
                }
            }

            if (currentPage == MestrePanelPage.APPEARANCE_SETTINGS) {
            MestrePageIntro(
                description = "Aparência global e fundos por tema são configurações independentes. Salve cada bloco separadamente.",
                hasUnsavedChanges = appearancePageHasChanges
            )
            Spacer(modifier = Modifier.height(12.dp))
            MestreSectionHeader(
                title = "Aparência global",
                description = "Define o padrão da primeira instalação ou, quando ativado, força tema e modo para todos"
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedCard(modifier = Modifier.fillMaxWidth().glassSoftShadow(MaterialTheme.shapes.medium)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    NotificationSettingSwitch(
                        label = "Aplicar aparência para todos",
                        checked = draftAppearanceSettings.overrideLocalTheme,
                        onCheckedChange = {
                            draftAppearanceSettings = draftAppearanceSettings.copy(overrideLocalTheme = it)
                        }
                    )
                    Text(
                        if (draftAppearanceSettings.overrideLocalTheme) {
                            "Ativado: tema e modo global são aplicados a todos os aparelhos, inclusive quem já personalizou."
                        } else {
                            "Desativado: tema e modo global servem apenas como padrão para novas instalações. Depois, cada usuário pode escolher o seu em Configurações."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = themeOptions.find { it.first == draftAppearanceSettings.theme }?.second ?: "Multicolorido",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Tema global") },
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = true
                        )
                        Box(Modifier.matchParentSize().clickable(onClick = { expandedRemoteTheme = !expandedRemoteTheme }))
                        DropdownMenu(

                            expanded = expandedRemoteTheme,
                            onDismissRequest = { expandedRemoteTheme = false }
                        ) {
                            themeOptions.forEach { (themeKey, themeLabel) ->
                                DropdownMenuItem(
                                    text = { Text(themeLabel) },
                                    onClick = {
                                        draftAppearanceSettings = draftAppearanceSettings.copy(theme = themeKey)
                                        expandedRemoteTheme = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = appearanceModeOptions.find { it.first == draftAppearanceSettings.appearanceMode }?.second ?: "Seguir sistema",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Modo de aparência") },
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = true
                        )
                        Box(Modifier.matchParentSize().clickable(onClick = { expandedRemoteMode = !expandedRemoteMode }))
                        DropdownMenu(

                            expanded = expandedRemoteMode,
                            onDismissRequest = { expandedRemoteMode = false }
                        ) {
                            appearanceModeOptions.forEach { (modeKey, modeLabel) ->
                                DropdownMenuItem(
                                    text = { Text(modeLabel) },
                                    onClick = {
                                        draftAppearanceSettings = draftAppearanceSettings.copy(appearanceMode = modeKey)
                                        expandedRemoteMode = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isSavingGlobalAppearance = true
                                val settingsToSave = appearanceSettings.copy(
                                    overrideLocalTheme = draftAppearanceSettings.overrideLocalTheme,
                                    theme = draftAppearanceSettings.theme,
                                    appearanceMode = draftAppearanceSettings.appearanceMode
                                )
                                val saved = FirebaseService.saveAppearanceSettings(settingsToSave)
                                isSavingGlobalAppearance = false
                                if (saved) draftAppearanceSettings = settingsToSave
                                snackbarHostState.showSnackbar(
                                    if (saved) "Aparência global salva."
                                    else FirebaseService.lastError ?: "Não foi possível salvar a aparência global."
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = globalAppearanceHasChanges && !isSavingGlobalAppearance
                    ) {
                        if (isSavingGlobalAppearance) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Salvando...")
                        } else if (globalAppearanceHasChanges) {
                            Text("Salvar aparência global")
                        } else {
                            Text("Aparência global atualizada")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            MestreSectionHeader(
                title = "Fundos por tema",
                description = "Gerencie banners, períodos e fundos padrão sem alterar a aparência global"
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedCard(modifier = Modifier.fillMaxWidth().glassSoftShadow(MaterialTheme.shapes.medium)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "O fundo padrão permanece disponível. Você pode ativar vários fundos por tema quando cada um tiver data de início; o período define qual aparece ao longo do ano.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    themeOptions.forEach { (themeKey, themeLabel) ->
                        val backgrounds = draftThemeBackgrounds[themeKey].orEmpty()
                        val expanded = themeKey in expandedBackgroundThemes
                        val backgroundPagination = calculatePaginationWindow(
                            totalItems = backgrounds.size,
                            requestedPage = backgroundPages[themeKey] ?: 0,
                            pageSize = BACKGROUND_PAGE_SIZE
                        )
                        LaunchedEffect(themeKey, backgrounds.size) {
                            if (backgroundPages[themeKey] != backgroundPagination.pageIndex) {
                                backgroundPages = backgroundPages + (themeKey to backgroundPagination.pageIndex)
                            }
                        }
                        OutlinedCard(
                            modifier = Modifier.fillMaxWidth().glassSoftShadow(MaterialTheme.shapes.medium),
                            enabled = true,
                            onClick = {
                                expandedBackgroundThemes = if (expanded) {
                                    expandedBackgroundThemes - themeKey
                                } else {
                                    expandedBackgroundThemes + themeKey
                                }
                            }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(themeLabel, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                                    Text(
                                        when {
                                            themeKey == "expressive" && backgrounds.any { it.isAvailableOn() } ->
                                                "Expressivo • Sólido padrão / Glass opcional • fundo personalizado ativo"
                                            themeKey == "expressive" ->
                                                "Expressivo • Sólido padrão / Glass opcional • fundo padrão ativo"
                                            backgrounds.any { it.isAvailableOn() } -> "Fundo personalizado ativo"
                                            else -> "Fundo padrão ativo"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (expanded) "Recolher $themeLabel" else "Expandir $themeLabel"
                                )
                            }
                        }
                        if (expanded) {
                            var horizontalDragTotal by remember(themeKey) { mutableFloatStateOf(0f) }
                            Column(
                                modifier = Modifier
                                    .padding(start = 8.dp, top = 6.dp)
                                    .pointerInput(themeKey, backgroundPagination.pageIndex, backgroundPagination.pageCount) {
                                        detectHorizontalDragGestures(
                                            onDragStart = { horizontalDragTotal = 0f },
                                            onHorizontalDrag = { change, dragAmount ->
                                                change.consume()
                                                horizontalDragTotal += dragAmount
                                            },
                                            onDragEnd = {
                                                when {
                                                    horizontalDragTotal <= -80f && backgroundPagination.pageIndex < backgroundPagination.pageCount - 1 ->
                                                        backgroundPages = backgroundPages + (themeKey to (backgroundPagination.pageIndex + 1))
                                                    horizontalDragTotal >= 80f && backgroundPagination.pageIndex > 0 ->
                                                        backgroundPages = backgroundPages + (themeKey to (backgroundPagination.pageIndex - 1))
                                                }
                                                horizontalDragTotal = 0f
                                            },
                                            onDragCancel = { horizontalDragTotal = 0f }
                                        )
                                    }
                            ) {
                                val defaultBackground = defaultBackgroundFor(themeKey)
                                Text(
                                    "Banner padrão",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                DefaultThemeBackgroundItem(
                                    themeKey = themeKey,
                                    background = defaultBackground,
                                    onPreview = { backgroundToPreview = themeKey to defaultBackground },
                                    onEdit = { openBackgroundEditor(themeKey, defaultBackground, isDefault = true) }
                                )
                                if (backgrounds.any { it.isActive }) {
                                    TextButton(
                                        onClick = {
                                            updateBackgrounds(themeKey, backgrounds.map { it.copy(isActive = false) })
                                        },
                                        modifier = Modifier.align(Alignment.End)
                                    ) {
                                        Text("Usar padrão agora")
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    "Banners programados",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                                )
                                if (backgrounds.isNotEmpty()) {
                                    Text(
                                        "Exibindo ${backgroundPagination.fromIndex + 1}–${backgroundPagination.toIndex} de ${backgrounds.size}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                backgrounds
                                    .subList(backgroundPagination.fromIndex, backgroundPagination.toIndex)
                                    .forEach { background ->
                                    Spacer(modifier = Modifier.height(6.dp))
                                    ThemeBackgroundItem(
                                        background = background,
                                        enabled = true,
                                        onActiveChange = { isActive ->
                                            updateBackgrounds(
                                                themeKey,
                                                backgrounds.map {
                                                    if (it.id == background.id) it.copy(isActive = isActive)
                                                    else it
                                                }
                                            )
                                        },
                                        onPreview = { backgroundToPreview = themeKey to background },
                                        onEdit = { openBackgroundEditor(themeKey, background) },
                                        onDelete = { backgroundToDelete = themeKey to background }
                                    )
                                }
                                if (backgroundPagination.pageCount > 1) {
                                    MestrePaginationControls(
                                        pageIndex = backgroundPagination.pageIndex,
                                        pageCount = backgroundPagination.pageCount,
                                        onPrevious = {
                                            backgroundPages = backgroundPages +
                                                (themeKey to (backgroundPagination.pageIndex - 1))
                                        },
                                        onNext = {
                                            backgroundPages = backgroundPages +
                                                (themeKey to (backgroundPagination.pageIndex + 1))
                                        }
                                    )
                                    Text(
                                        "Deslize para a esquerda ou direita para trocar de página",
                                        modifier = Modifier.fillMaxWidth(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedButton(
                                    onClick = { openBackgroundEditor(themeKey, null) },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = true
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Adicionar fundo a $themeLabel")
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isSavingThemeBackgrounds = true
                                val settingsToSave = appearanceSettings.copy(
                                    defaultThemeBackgrounds = draftDefaultThemeBackgrounds,
                                    themeBackgrounds = draftThemeBackgrounds
                                )
                                val saved = FirebaseService.saveAppearanceSettings(settingsToSave)
                                isSavingThemeBackgrounds = false
                                snackbarHostState.showSnackbar(
                                    if (saved) "Fundos por tema publicados."
                                    else FirebaseService.lastError ?: "Não foi possível publicar os fundos por tema."
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = themeBackgroundsHaveChanges && !isSavingThemeBackgrounds
                    ) {
                        if (isSavingThemeBackgrounds) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Salvando fundos...")
                        } else if (themeBackgroundsHaveChanges) {
                            Text("Salvar fundos por tema")
                        } else {
                            Text("Fundos atualizados")
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            }


            if (currentPage == MestrePanelPage.CARD_APPEARANCE_SETTINGS) {
                val cardThemeLabels = mapOf(
                    "multicolor" to "Multicolorido", "red" to "Vermelho", "gold" to "Dourado",
                    "green" to "Verde", "blue" to "Azul", "orange" to "Laranja",
                    "glass" to "Glass Soft", "expressive" to "Glass Expressivo"
                )
                val selectedCardUrl = draftCardBackgrounds[cardAppearanceThemeKey].orEmpty()
                    .ifBlank { draftCardBackgroundSchedules[cardAppearanceThemeKey]?.url.orEmpty() }
                val selectedStartDate = draftCardScheduleStarts[cardAppearanceThemeKey].orEmpty()
                val selectedEndDate = draftCardScheduleEnds[cardAppearanceThemeKey].orEmpty()
                MestrePageIntro(
                    description = "Escolha o fundo do cartão de convênio para cada tema. Você pode remover a imagem ou programar quando ela será exibida.",
                    hasUnsavedChanges = cardAppearanceHasChanges
                )
                Spacer(modifier = Modifier.height(12.dp))
                MestreSectionHeader(
                    title = "Aparência Cartão",
                    description = "Imagem recomendada: 1.586:1 — ideal 1.586 × 1.000 px (mínimo 1.080 × 681 px). Inclua marcas e mascotes no fundo; deixe livre o centro e a parte inferior esquerda para os dados."
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.example.data.SupportedThemeKeys.forEach { key ->
                        FilterChip(
                            selected = cardAppearanceThemeKey == key,
                            onClick = {
                                cardAppearanceThemeKey = key
                                cardScheduleError = null
                            },
                            label = { Text(cardThemeLabels[key] ?: key) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                BenefitCardSurface(
                    backgroundUrl = selectedCardUrl,
                    themeKey = cardAppearanceThemeKey,
                    name = "Alessandro Paulo da Silva",
                    limit = "R$ 491,40",
                    spent = "R$ 490,74",
                    balance = "R$ 0,66",
                    period = "10/09/2026 a 07/10/2026",
                    onClick = {}
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Tema selecionado: ${cardThemeLabels[cardAppearanceThemeKey] ?: cardAppearanceThemeKey}", style = MaterialTheme.typography.titleSmall)
                Text(
                    when {
                        selectedCardUrl.isBlank() -> "Usando o fundo padrão deste tema."
                        selectedStartDate.isBlank() -> "Imagem personalizada ativa imediatamente."
                        selectedEndDate.isBlank() -> "Agendada a partir de ${formatThemeBackgroundDate(selectedStartDate)}."
                        else -> "Agendada de ${formatThemeBackgroundDate(selectedStartDate)} a ${formatThemeBackgroundDate(selectedEndDate)}."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("Período da imagem (opcional)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "Sem data de início, a imagem fica ativa imediatamente. Com início definido, ela aparece apenas no período escolhido; depois o cartão volta ao fundo padrão.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showCardStartDatePicker = true },
                        enabled = selectedCardUrl.isNotBlank() && !isSavingCardAppearance,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (selectedStartDate.isBlank()) "Agendar início" else "Início: ${formatThemeBackgroundDate(selectedStartDate)}")
                    }
                    OutlinedButton(
                        onClick = { showCardEndDatePicker = true },
                        enabled = selectedCardUrl.isNotBlank() && selectedStartDate.isNotBlank() && !isSavingCardAppearance,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (selectedEndDate.isBlank()) "Sem data final" else "Fim: ${formatThemeBackgroundDate(selectedEndDate)}")
                    }
                }
                if (selectedStartDate.isNotBlank() || selectedEndDate.isNotBlank()) {
                    TextButton(
                        onClick = {
                            draftCardScheduleStarts = draftCardScheduleStarts - cardAppearanceThemeKey
                            draftCardScheduleEnds = draftCardScheduleEnds - cardAppearanceThemeKey
                            cardScheduleError = null
                        },
                        enabled = !isSavingCardAppearance
                    ) { Text("Remover agendamento") }
                }
                cardScheduleError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        enabled = !isUploadingThemeBackground && !isSavingCardAppearance,
                        onClick = {
                            editingBackgroundTheme = CARD_APPEARANCE_KEY
                            themeBackgroundLauncher.launch("image/*")
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            when {
                                isUploadingThemeBackground -> "Enviando imagem…"
                                selectedCardUrl.isBlank() -> "Escolher imagem"
                                else -> "Trocar imagem"
                            }
                        )
                    }
                    if (selectedCardUrl.isNotBlank()) {
                        IconButton(
                            onClick = { showDeleteCardBackgroundDialog = true },
                            enabled = !isUploadingThemeBackground && !isSavingCardAppearance
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Excluir imagem do cartão")
                        }
                    }
                }
                Button(
                    enabled = cardAppearanceHasChanges && !isSavingCardAppearance && !isUploadingThemeBackground,
                    onClick = {
                        val selectedUrl = draftCardBackgrounds[cardAppearanceThemeKey].orEmpty()
                            .ifBlank { draftCardBackgroundSchedules[cardAppearanceThemeKey]?.url.orEmpty() }
                        val rawStart = draftCardScheduleStarts[cardAppearanceThemeKey].orEmpty()
                        val rawEnd = draftCardScheduleEnds[cardAppearanceThemeKey].orEmpty()
                        val startDate = ThemeBackground.normalizeDate(rawStart)
                        val endDate = ThemeBackground.normalizeDate(rawEnd)
                        when {
                            selectedUrl.isNotBlank() && rawStart.isNotBlank() && startDate == null ->
                                cardScheduleError = "Selecione uma data inicial válida."
                            selectedUrl.isNotBlank() && rawEnd.isNotBlank() && endDate == null ->
                                cardScheduleError = "Selecione uma data final válida."
                            selectedUrl.isNotBlank() && startDate == null && endDate != null ->
                                cardScheduleError = "Defina uma data inicial antes da data final."
                            selectedUrl.isNotBlank() && startDate != null && endDate != null && endDate < startDate ->
                                cardScheduleError = "A data final não pode ser anterior à data inicial."
                            else -> {
                                cardScheduleError = null
                                val immediateImages = draftCardBackgrounds.toMutableMap()
                                val schedules = draftCardBackgroundSchedules.toMutableMap()
                                if (selectedUrl.isBlank()) {
                                    immediateImages.remove(cardAppearanceThemeKey)
                                    schedules.remove(cardAppearanceThemeKey)
                                } else if (startDate != null) {
                                    immediateImages.remove(cardAppearanceThemeKey)
                                    schedules[cardAppearanceThemeKey] = ThemeBackground(
                                        id = "card-$cardAppearanceThemeKey",
                                        label = "Fundo do cartão",
                                        url = selectedUrl,
                                        isActive = true,
                                        startDate = startDate,
                                        endDate = endDate
                                    )
                                } else {
                                    schedules.remove(cardAppearanceThemeKey)
                                    immediateImages[cardAppearanceThemeKey] = selectedUrl
                                }
                                val imagesToSave = immediateImages.toMap()
                                val schedulesToSave = schedules.toMap()
                                val settingsToSave = appearanceSettings.copy(
                                    cardBackgrounds = imagesToSave,
                                    cardBackgroundSchedules = schedulesToSave
                                )
                                coroutineScope.launch {
                                    isSavingCardAppearance = true
                                    val saved = FirebaseService.saveAppearanceSettings(settingsToSave)
                                    isSavingCardAppearance = false
                                    if (saved) {
                                        draftCardBackgrounds = imagesToSave
                                        draftCardBackgroundSchedules = schedulesToSave
                                        draftCardScheduleStarts = schedulesToSave.mapValues { it.value.startDate.orEmpty() }
                                        draftCardScheduleEnds = schedulesToSave.mapValues { it.value.endDate.orEmpty() }
                                    }
                                    snackbarHostState.showSnackbar(
                                        if (saved) "Fundo e agendamento do cartão salvos."
                                        else FirebaseService.lastError ?: "Não foi possível salvar a aparência do cartão."
                                    )
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isSavingCardAppearance) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text("Salvar imagem e período")
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (currentPage == MestrePanelPage.CONSULTATION_APPEARANCE_SETTINGS) {
                MestrePageIntro(
                    description = "Escolha, agende e ajuste o fundo exclusivo da aba Consultar Produtos.",
                    hasUnsavedChanges = consultationAppearanceHasChanges
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedCard(modifier = Modifier.fillMaxWidth().glassSoftShadow(MaterialTheme.shapes.medium)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        val backgrounds = draftConsultationBackgrounds
                        val activeBackground = consultationDraft.activeConsultationBackground()
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

                        Spacer(modifier = Modifier.height(20.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Banner de Ofertas",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        Text(
                            "Cadastre imagens 3:1. O app seleciona automaticamente o banner conforme a oferta confirmada na ACP; sem cadastro, mantém o cartaz atual.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        offerBannerLabels.forEach { (offerKey, title) ->
                            val editorKey = offerEditorKey(offerKey)
                            val banners = draftOfferBanners[offerKey].orEmpty()
                            val active = consultationDraft.activeOfferBanner(offerKey)
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(title, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                    Text(
                                        active?.let { "Ativo agora: ${it.label}" } ?: "Banner personalizado não definido",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    banners.forEach { banner ->
                                        Spacer(modifier = Modifier.height(6.dp))
                                        ThemeBackgroundItem(
                                            background = banner,
                                            enabled = true,
                                            onActiveChange = { isActive ->
                                                updateBackgrounds(
                                                    editorKey,
                                                    banners.map {
                                                        if (it.id == banner.id) it.copy(isActive = isActive) else it
                                                    }
                                                )
                                            },
                                            onPreview = { backgroundToPreview = editorKey to banner },
                                            onEdit = { openBackgroundEditor(editorKey, banner) },
                                            onDelete = { backgroundToDelete = editorKey to banner }
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedButton(
                                        onClick = { openBackgroundEditor(editorKey, null) },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Adicionar $title")
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    isSavingConsultationAppearance = true
                                    val saved = FirebaseService.saveAppearanceSettings(consultationDraft)
                                    isSavingConsultationAppearance = false
                                    snackbarHostState.showSnackbar(
                                        if (saved) "Aparência de Consultar Produtos publicada para todos."
                                        else FirebaseService.lastError
                                            ?: "Não foi possível publicar a aparência de Consultar Produtos."
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = consultationAppearanceHasChanges && !isSavingConsultationAppearance
                        ) {
                            if (isSavingConsultationAppearance) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Publicando...")
                            } else if (consultationAppearanceHasChanges) {
                                Text("Salvar configuração")
                            } else {
                                Text("Tudo atualizado")
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (showDiscardChangesDialog) {
                AlertDialog(
                    onDismissRequest = { showDiscardChangesDialog = false },
                    title = { Text("Descartar alterações?") },
                    text = { Text("As mudanças desta página ainda não foram publicadas e serão perdidas.") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                when (currentPage) {
                                    MestrePanelPage.HOME_SETTINGS -> draftHomeSettings = homeSettings
                                    MestrePanelPage.NOTIFICATION_SETTINGS -> draftNotificationSettings = notificationSettings
                                    MestrePanelPage.APPEARANCE_SETTINGS -> {
                                        draftAppearanceSettings = appearanceSettings
                                        draftDefaultThemeBackgrounds = appearanceSettings.defaultThemeBackgrounds
                                        draftThemeBackgrounds = appearanceSettings.themeBackgrounds
                                    }
                                    MestrePanelPage.CONSULTATION_APPEARANCE_SETTINGS -> {
                                        draftConsultationBackgrounds = appearanceSettings.consultationBackgrounds
                                        draftOfferBanners = appearanceSettings.offerBanners
                                    }
                                    else -> Unit
                                }
                                showDiscardChangesDialog = false
                                performPanelBack()
                            }
                        ) {
                            Text("Descartar", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDiscardChangesDialog = false }) {
                            Text("Continuar editando")
                        }
                    }
                )
            }

            if (showImportDialog && importResult != null) {
                ImportPreviewDialog(
                    result = importResult!!,
                    isImporting = isImporting,
                    onDismiss = { if (!isImporting) showImportDialog = false },
                    onConfirm = {
                        coroutineScope.launch {
                            isImporting = true
                            val commitResult = viewModel.importProducts(importResult!!.rows)
                            isImporting = false
                            val summary = buildString {
                                append("${commitResult.importedCount} produto(s) importado(s).")
                                if (commitResult.skippedRows > 0) {
                                    append(" ${commitResult.skippedRows} linha(s) ignorada(s).")
                                }
                            }
                            snackbarHostState.showSnackbar(summary)
                            if (commitResult.importedCount > 0 || commitResult.errors.isNotEmpty()) {
                                showImportDialog = false
                                importResult = null
                            }
                        }
                    }
                )
            }

            if (snapshotToRestore != null) {
                val selectedSnapshot = snapshotToRestore!!
                AlertDialog(
                    onDismissRequest = { snapshotToRestore = null },
                    title = { Text("Restaurar catálogo?") },
                    text = {
                        Text(
                            "A versão de ${formatCatalogHistoryDate(selectedSnapshot.createdAt)} contém ${selectedSnapshot.productCount} produto(s). O catálogo remoto atual será salvo em um backup automático antes da substituição. Essa operação pode alterar o catálogo de todos os aparelhos."
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                snapshotToRestore = null
                                viewModel.restoreCatalogSnapshot(selectedSnapshot.id)
                            }
                        ) {
                            Text("Restaurar")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { snapshotToRestore = null }) {
                            Text("Cancelar")
                        }
                    }
                )
            }

            if (showCategoryDialog) {
                AlertDialog(
                    onDismissRequest = {
                        if (categoryActionInProgress == null) {
                            showCategoryDialog = false
                        }
                    },
                    title = { Text(if (editingCategory == null) "Nova categoria" else "Renomear categoria") },
                    text = {
                        OutlinedTextField(
                            value = categoryName,
                            onValueChange = { categoryName = it },
                            label = { Text("Nome da categoria") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = categoryActionInProgress == null
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                if (categoryActionInProgress == null) {
                                    val actionKey = editingCategory?.id ?: NEW_CATEGORY_ACTION_KEY
                                    categoryActionInProgress = actionKey
                                    coroutineScope.launch {
                                        try {
                                            val saved = editingCategory?.let {
                                                viewModel.renameCategory(it, categoryName)
                                            } ?: viewModel.addCategory(categoryName)
                                            if (saved) {
                                                showCategoryDialog = false
                                            }
                                        } finally {
                                            categoryActionInProgress = null
                                        }
                                    }
                                }
                            },
                            enabled = categoryName.isNotBlank() && categoryActionInProgress == null
                        ) {
                            if (categoryActionInProgress != null) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Salvando...")
                            } else {
                                Text("Salvar")
                            }
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showCategoryDialog = false },
                            enabled = categoryActionInProgress == null
                        ) {
                            Text("Cancelar")
                        }
                    }
                )
            }

            if (showStartDatePicker) {
                val datePickerState = rememberDatePickerState(
                    initialSelectedDateMillis = dateToPickerMillis(backgroundStartDateInput)
                )
                DatePickerDialog(
                    onDismissRequest = { showStartDatePicker = false },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                backgroundStartDateInput = pickerDateToIsoDate(datePickerState.selectedDateMillis).orEmpty()
                                backgroundInputError = null
                                showStartDatePicker = false
                            }
                        ) {
                            Text("Usar data")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showStartDatePicker = false }) {
                            Text("Cancelar")
                        }
                    }
                ) {
                    DatePicker(state = datePickerState)
                }
            }

            if (showEndDatePicker) {
                val datePickerState = rememberDatePickerState(
                    initialSelectedDateMillis = dateToPickerMillis(backgroundEndDateInput)
                )
                DatePickerDialog(
                    onDismissRequest = { showEndDatePicker = false },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                backgroundEndDateInput = pickerDateToIsoDate(datePickerState.selectedDateMillis).orEmpty()
                                backgroundInputError = null
                                showEndDatePicker = false
                            }
                        ) {
                            Text("Usar data")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showEndDatePicker = false }) {
                            Text("Cancelar")
                        }
                    }
                ) {
                    DatePicker(state = datePickerState)
                }
            }

            if (showCardStartDatePicker) {
                val datePickerState = rememberDatePickerState(
                    initialSelectedDateMillis = dateToPickerMillis(draftCardScheduleStarts[cardAppearanceThemeKey].orEmpty())
                )
                DatePickerDialog(
                    onDismissRequest = { showCardStartDatePicker = false },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                pickerDateToIsoDate(datePickerState.selectedDateMillis)?.let { selectedDate ->
                                    draftCardScheduleStarts = draftCardScheduleStarts + (cardAppearanceThemeKey to selectedDate)
                                }
                                cardScheduleError = null
                                showCardStartDatePicker = false
                            }
                        ) { Text("Usar data") }
                    },
                    dismissButton = { TextButton(onClick = { showCardStartDatePicker = false }) { Text("Cancelar") } }
                ) { DatePicker(state = datePickerState) }
            }

            if (showCardEndDatePicker) {
                val datePickerState = rememberDatePickerState(
                    initialSelectedDateMillis = dateToPickerMillis(draftCardScheduleEnds[cardAppearanceThemeKey].orEmpty())
                )
                DatePickerDialog(
                    onDismissRequest = { showCardEndDatePicker = false },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                pickerDateToIsoDate(datePickerState.selectedDateMillis)?.let { selectedDate ->
                                    draftCardScheduleEnds = draftCardScheduleEnds + (cardAppearanceThemeKey to selectedDate)
                                }
                                cardScheduleError = null
                                showCardEndDatePicker = false
                            }
                        ) { Text("Usar data") }
                    },
                    dismissButton = { TextButton(onClick = { showCardEndDatePicker = false }) { Text("Cancelar") } }
                ) { DatePicker(state = datePickerState) }
            }

            if (showThemeBackgroundDialog) {
                AlertDialog(
                    onDismissRequest = { showThemeBackgroundDialog = false },
                    title = {
                        val consultation = editingBackgroundTheme == CONSULTATION_BACKGROUND_KEY
                        val offerBanner = editingBackgroundTheme?.let(::offerKeyFromEditorKey) != null
                        Text(
                            if (editingBackground == null) {
                                when {
                                    consultation -> "Adicionar fundo da consulta"
                                    offerBanner -> "Adicionar banner de oferta"
                                    else -> "Adicionar fundo ao tema"
                                }
                            } else {
                                when {
                                    editingDefaultBackground -> "Editar banner padrão"
                                    consultation -> "Editar fundo da consulta"
                                    offerBanner -> "Editar banner de oferta"
                                    else -> "Editar fundo do tema"
                                }
                            }
                        )
                    },
                    text = {
                        Column {
                            OutlinedTextField(
                                value = backgroundLabelInput,
                                onValueChange = { backgroundLabelInput = it },
                                label = { Text(if (editingBackgroundTheme?.let(::offerKeyFromEditorKey) != null) "Nome do banner" else "Nome do fundo") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = backgroundUrlInput,
                                onValueChange = {
                                    backgroundUrlInput = it
                                    backgroundInputError = null
                                },
                                label = { Text("URL da imagem") },
                                placeholder = { Text("https://...") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                if (editingDefaultBackground) {
                                    "Troque a imagem por URL ou pelo seletor abaixo. A alteração só será publicada ao salvar a aparência."
                                } else if (editingBackgroundTheme?.let(::offerKeyFromEditorKey) != null) {
                                    "Use uma imagem 3:1 acessível por link HTTP/HTTPS. O cartaz atual continuará disponível como padrão."
                                } else {
                                    "Use uma imagem acessível por link HTTP/HTTPS. O fundo padrão continuará disponível."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (!editingDefaultBackground) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    "Período de ativação (opcional)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                                )
                                Text(
                                    "A data de início é obrigatória para ativar. Sem data de fim, permanece ativo até ser desativado. Após o fim, o fundo padrão volta automaticamente.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedButton(
                                    onClick = { showStartDatePicker = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        if (backgroundStartDateInput.isBlank()) "Definir data de início"
                                        else "Início: ${formatThemeBackgroundDate(backgroundStartDateInput)}"
                                    )
                                }
                                if (backgroundStartDateInput.isNotBlank()) {
                                    TextButton(onClick = { backgroundStartDateInput = "" }) {
                                        Text("Limpar data de início")
                                    }
                                }
                                OutlinedButton(
                                    onClick = { showEndDatePicker = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        if (backgroundEndDateInput.isBlank()) "Definir data de fim"
                                        else "Fim: ${formatThemeBackgroundDate(backgroundEndDateInput)}"
                                    )
                                }
                                if (backgroundEndDateInput.isNotBlank()) {
                                    TextButton(onClick = { backgroundEndDateInput = "" }) {
                                        Text("Limpar data de fim")
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { themeBackgroundLauncher.launch("image/*") },
                                enabled = !isUploadingThemeBackground,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (isUploadingThemeBackground) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Enviando imagem...")
                                } else {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Escolher imagem do aparelho")
                                }
                            }
                            backgroundInputError?.let { error ->
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                    val normalizedUrl = com.example.util.ImageUrlHelper.normalizeUrl(backgroundUrlInput)
                                    val normalizedStartDate = ThemeBackground.normalizeDate(backgroundStartDateInput)
                                    val normalizedEndDate = ThemeBackground.normalizeDate(backgroundEndDateInput)
                                    val themeKey = editingBackgroundTheme
                                    val current = themeKey?.let(::backgroundsForKey).orEmpty()
                                    when {
                                        themeKey == null -> backgroundInputError = "Tema inválido."
                                        !editingDefaultBackground && normalizedUrl.isBlank() ->
                                            backgroundInputError = "Informe uma URL HTTP/HTTPS válida."
                                        normalizedUrl.isNotBlank() && !(normalizedUrl.startsWith("https://") || normalizedUrl.startsWith("http://")) ->
                                            backgroundInputError = "Informe uma URL HTTP/HTTPS válida."
                                        !editingDefaultBackground && backgroundStartDateInput.isNotBlank() && normalizedStartDate == null ->
                                            backgroundInputError = "Informe uma data de início válida."
                                        !editingDefaultBackground && backgroundEndDateInput.isNotBlank() && normalizedEndDate == null ->
                                            backgroundInputError = "Informe uma data de fim válida."
                                        !editingDefaultBackground && normalizedStartDate != null && normalizedEndDate != null && normalizedEndDate < normalizedStartDate ->
                                            backgroundInputError = "A data de fim não pode ser anterior à data de início."
                                        else -> {
                                        if (editingDefaultBackground) {
                                            val previous = editingBackground ?: defaultBackgroundFor(themeKey!!)
                                            draftDefaultThemeBackgrounds = draftDefaultThemeBackgrounds + (
                                                themeKey!! to previous.copy(
                                                    id = "default-$themeKey",
                                                    label = backgroundLabelInput.trim().ifBlank { "Banner padrão" },
                                                    url = normalizedUrl,
                                                    isActive = false,
                                                    startDate = null,
                                                    endDate = null
                                                )
                                            )
                                        } else {
                                        val updated = if (editingBackground == null) {
                                            current + ThemeBackground(
                                                id = UUID.randomUUID().toString(),
                                                label = backgroundLabelInput.trim().ifBlank { "Fundo personalizado" },
                                                url = normalizedUrl,
                                                isActive = false,
                                                startDate = normalizedStartDate,
                                                endDate = normalizedEndDate
                                            )
                                        } else {
                                            current.map { background ->
                                                if (background.id == editingBackground!!.id) {
                                                    background.copy(
                                                        label = backgroundLabelInput.trim().ifBlank { "Fundo personalizado" },
                                                        url = normalizedUrl,
                                                        isActive = background.isActive && normalizedStartDate != null,
                                                        startDate = normalizedStartDate,
                                                        endDate = normalizedEndDate
                                                    )
                                                } else {
                                                    background
                                                }
                                            }
                                        }
                                        updateBackgrounds(themeKey!!, updated)
                                        }
                                        showThemeBackgroundDialog = false
                                    }
                                }
                            }
                        ) {
                            Text("Salvar")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showThemeBackgroundDialog = false }) {
                            Text("Cancelar")
                        }
                    }
                )
            }

  backgroundToPreview?.let { (themeKey, background) ->
      val previewOfferKey = offerKeyFromEditorKey(themeKey)
      val previewIsDefault = previewOfferKey == null &&
          themeKey != CONSULTATION_BACKGROUND_KEY &&
          background.id == "default-$themeKey"
      BannerPreviewEditor(
          themeKey = if (themeKey == CONSULTATION_BACKGROUND_KEY || previewOfferKey != null) "multicolor" else themeKey,
          themeLabel = when {
              themeKey == CONSULTATION_BACKGROUND_KEY -> "Consultar Produtos"
              previewOfferKey != null -> offerBannerLabels[previewOfferKey] ?: "Banner de oferta"
              else -> themeOptions.firstOrNull { it.first == themeKey }?.second ?: themeKey
          },
          background = background,
          isSaving = isSavingAppearanceSettings,
          onDismiss = {
              if (!isSavingAppearanceSettings) backgroundToPreview = null
          },
          onEditBackground = if (themeKey != CONSULTATION_BACKGROUND_KEY && previewOfferKey == null) {
              {
                  backgroundToPreview = null
                  openBackgroundEditor(themeKey, background, isDefault = previewIsDefault)
              }
          } else null,
          onMakeDefault = if (
              themeKey != CONSULTATION_BACKGROUND_KEY &&
              previewOfferKey == null &&
              !previewIsDefault
          ) {
              { candidate, maskSettings ->
                  pendingDefaultBannerChange = PendingDefaultBannerChange(
                      themeKey = themeKey,
                      background = candidate,
                      maskSettings = maskSettings
                  )
              }
          } else null,
          onSave = { updatedBackground, maskSettings ->
              val updatedList = backgroundsForKey(themeKey).map { item ->
                  if (item.id == updatedBackground.id) updatedBackground else item
              }
              val updatedDefaultThemeBackgrounds = if (previewIsDefault) {
                  draftDefaultThemeBackgrounds + (themeKey to updatedBackground)
              } else {
                  draftDefaultThemeBackgrounds
              }
              val updatedThemeBackgrounds = if (themeKey == CONSULTATION_BACKGROUND_KEY || previewOfferKey != null) draftThemeBackgrounds
              else if (previewIsDefault) draftThemeBackgrounds
              else draftThemeBackgrounds + (themeKey to updatedList)
              val updatedConsultationBackgrounds = if (themeKey == CONSULTATION_BACKGROUND_KEY) {
                  updatedList
              } else {
                  draftConsultationBackgrounds
              }
              val updatedOfferBanners = if (previewOfferKey != null) {
                  draftOfferBanners + (previewOfferKey to updatedList)
              } else draftOfferBanners
              draftDefaultThemeBackgrounds = updatedDefaultThemeBackgrounds
              draftThemeBackgrounds = updatedThemeBackgrounds
              draftConsultationBackgrounds = updatedConsultationBackgrounds
              draftOfferBanners = updatedOfferBanners
              val settingsToSave = if (themeKey == CONSULTATION_BACKGROUND_KEY || previewOfferKey != null) {
                  appearanceSettings.copy(
                      consultationBackgrounds = updatedConsultationBackgrounds,
                      offerBanners = updatedOfferBanners
                  )
              } else {
                  appearanceSettings.copy(
                      defaultThemeBackgrounds = updatedDefaultThemeBackgrounds,
                      themeBackgrounds = updatedThemeBackgrounds
                  )
              }

              coroutineScope.launch {
                  isSavingAppearanceSettings = true
                  val appearanceSaved = FirebaseService.saveAppearanceSettings(settingsToSave)
                  if (!appearanceSaved) {
                      isSavingAppearanceSettings = false
                      snackbarHostState.showSnackbar(
                          FirebaseService.lastError
                              ?: "Não foi possível salvar o enquadramento da prévia."
                      )
                      return@launch
                  }

                  val maskSaved = if (themeKey == CONSULTATION_BACKGROUND_KEY || previewOfferKey != null) {
                      true
                  } else {
                      com.example.data.BannerMaskStore.save(
                          themeKey = themeKey,
                          backgroundUrl = updatedBackground.url,
                          settings = maskSettings
                      )
                  }
                  isSavingAppearanceSettings = false

                  if (maskSaved) {
                      backgroundToPreview = null
                      snackbarHostState.showSnackbar(
                          if (themeKey == CONSULTATION_BACKGROUND_KEY || previewOfferKey != null) {
                              "Enquadramento salvo para ${if (previewOfferKey != null) offerBannerLabels[previewOfferKey] else "Consultar Produtos"}."
                          } else {
                              "Prévia salva. Enquadramento e máscara já serão usados na Home."
                          }
                      )
                  } else {
                      backgroundToPreview = themeKey to updatedBackground
                      snackbarHostState.showSnackbar(
                          FirebaseService.lastError
                              ?: "O enquadramento foi salvo, mas não foi possível salvar a máscara."
                      )
                  }
              }
          }
      )
  }

  pendingDefaultBannerChange?.let { pending ->
      AlertDialog(
          onDismissRequest = {
              if (!isSavingAppearanceSettings) pendingDefaultBannerChange = null
          },
          title = { Text("Tornar este o banner padrão?") },
          text = {
              Text(
                  "O padrão atual de ${themeOptions.firstOrNull { it.first == pending.themeKey }?.second ?: pending.themeKey} será substituído para todos. Os banners com período ativo continuarão tendo prioridade."
              )
          },
          confirmButton = {
              TextButton(
                  enabled = !isSavingAppearanceSettings,
                  onClick = {
                      val newDefault = pending.background.copy(
                          id = "default-${pending.themeKey}",
                          label = pending.background.label.ifBlank { "Banner padrão" },
                          isActive = false,
                          startDate = null,
                          endDate = null
                      )
                      val updatedDefaults = draftDefaultThemeBackgrounds + (pending.themeKey to newDefault)
                      val settingsToSave = appearanceSettings.copy(
                          defaultThemeBackgrounds = updatedDefaults,
                          themeBackgrounds = draftThemeBackgrounds
                      )
                      coroutineScope.launch {
                          isSavingAppearanceSettings = true
                          val maskSaved = com.example.data.BannerMaskStore.save(
                              themeKey = pending.themeKey,
                              backgroundUrl = newDefault.url,
                              settings = pending.maskSettings
                          )
                          val appearanceSaved = maskSaved &&
                              FirebaseService.saveAppearanceSettings(settingsToSave)
                          isSavingAppearanceSettings = false
                          if (appearanceSaved && maskSaved) {
                              draftDefaultThemeBackgrounds = updatedDefaults
                              pendingDefaultBannerChange = null
                              backgroundToPreview = pending.themeKey to newDefault
                              snackbarHostState.showSnackbar("Novo banner padrão publicado para todos.")
                          } else {
                              snackbarHostState.showSnackbar(
                                  FirebaseService.lastError ?: "Não foi possível trocar o banner padrão."
                              )
                          }
                      }
                  }
              ) {
                  if (isSavingAppearanceSettings) {
                      CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                  } else {
                      Text("Confirmar troca")
                  }
              }
          },
          dismissButton = {
              TextButton(
                  enabled = !isSavingAppearanceSettings,
                  onClick = { pendingDefaultBannerChange = null }
              ) {
                  Text("Cancelar")
              }
          }
      )
  }

            if (showDeleteCardBackgroundDialog) {
                AlertDialog(
                    onDismissRequest = { showDeleteCardBackgroundDialog = false },
                    title = { Text("Excluir imagem do cartão?") },
                    text = {
                        Text("A imagem será removida do tema selecionado quando você salvar. Os cartões voltarão a usar o fundo padrão.")
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                draftCardBackgrounds = draftCardBackgrounds - cardAppearanceThemeKey
                                draftCardBackgroundSchedules = draftCardBackgroundSchedules - cardAppearanceThemeKey
                                draftCardScheduleStarts = draftCardScheduleStarts - cardAppearanceThemeKey
                                draftCardScheduleEnds = draftCardScheduleEnds - cardAppearanceThemeKey
                                cardScheduleError = null
                                showDeleteCardBackgroundDialog = false
                            }
                        ) { Text("Excluir") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteCardBackgroundDialog = false }) { Text("Cancelar") }
                    }
                )
            }

  backgroundToDelete?.let { (themeKey, background) ->
                AlertDialog(
                    onDismissRequest = { backgroundToDelete = null },
                    title = { Text("Excluir fundo?") },
                    text = { Text("O fundo \"${background.label}\" será removido do rascunho deste tema. Para refletir a exclusão nos usuários, ainda será necessário salvar a aparência.") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                updateBackgrounds(themeKey, backgroundsForKey(themeKey).filterNot { it.id == background.id })
                                backgroundToDelete = null
                            }
                        ) {
                            Text("Excluir")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { backgroundToDelete = null }) {
                            Text("Cancelar")
                        }
                    }
                )
            }

        }
    }
}

private fun formatThemeBackgroundDate(value: String): String =
    ThemeBackground.formatDisplayDate(value) ?: value

private fun backgroundScheduleStatus(background: ThemeBackground): String {
    if (ThemeBackground.normalizeDate(background.startDate) == null) {
        return "Defina a data de início para liberar"
    }
    if (!background.isActive) return "Desativado manualmente"

    val today = ThemeBackground.todayIsoDate()
    val start = ThemeBackground.normalizeDate(background.startDate)
    val end = ThemeBackground.normalizeDate(background.endDate)
    return when {
        start != null && today < start ->
            "Agendado para ${formatThemeBackgroundDate(background.startDate.orEmpty())}"
        end != null && today > end ->
            "Período encerrado — fundo padrão ativo"
        start != null && end != null ->
            "Ativo de ${formatThemeBackgroundDate(background.startDate.orEmpty())} a ${formatThemeBackgroundDate(background.endDate.orEmpty())} (inclusive)"
        else -> "Ativo desde ${formatThemeBackgroundDate(background.startDate.orEmpty())} — sem data de fim"
    }
}

@Composable
private fun DefaultThemeBackgroundItem(
    themeKey: String,
    background: ThemeBackground,
    onPreview: () -> Unit,
    onEdit: () -> Unit
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth().glassSoftShadow(MaterialTheme.shapes.medium)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MaskedThemeBanner(
                appTheme = themeKey,
                backgroundUrl = background.url,
                imageScale = background.imageScale,
                imageOffsetX = background.imageOffsetX,
                imageOffsetY = background.imageOffsetY,
                imageStretchX = background.imageStretchX,
                imageStretchY = background.imageStretchY,
                modifier = Modifier
                    .size(width = 72.dp, height = 44.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onPreview)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(background.label, style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (background.url.isBlank()) "Arte original do aplicativo" else "Padrão personalizado",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = onPreview,
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
                ) {
                    Text("Visualizar e ajustar")
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Editar banner padrão")
            }
        }
    }
}

@Composable
private fun ThemeBackgroundItem(
    background: ThemeBackground,
    enabled: Boolean,
    onActiveChange: (Boolean) -> Unit,
    onPreview: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    OutlinedCard(modifier = Modifier.fillMaxWidth().glassSoftShadow(MaterialTheme.shapes.medium)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            coil.compose.AsyncImage(
                model = background.url,
                contentDescription = "Prévia de ${background.label}",
                modifier = Modifier
                    .size(width = 72.dp, height = 44.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(enabled = enabled, onClick = onPreview),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(background.label, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(3.dp))
                BackgroundStatusBadge(background)
                Spacer(modifier = Modifier.height(4.dp))
                BackgroundScheduleDates(background)
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    backgroundScheduleStatus(background),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = background.isActive,
                onCheckedChange = onActiveChange,
                enabled = enabled && ThemeBackground.normalizeDate(background.startDate) != null,
                colors = SwitchDefaults.colors(
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                    uncheckedBorderColor = MaterialTheme.colorScheme.outline,
                    disabledUncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledUncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledUncheckedBorderColor = MaterialTheme.colorScheme.outline,
                    disabledCheckedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    disabledCheckedTrackColor = MaterialTheme.colorScheme.primary
                )
            )
            Box {
                IconButton(onClick = { menuExpanded = true }, enabled = enabled) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Ações de ${background.label}")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Editar") },
                        onClick = {
                            menuExpanded = false
                            onEdit()
                        },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Excluir", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun BackgroundScheduleDates(background: ThemeBackground) {
    val start = ThemeBackground.normalizeDate(background.startDate)
    val end = ThemeBackground.normalizeDate(background.endDate)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ScheduleDatePill("Início", start?.let { formatThemeBackgroundDate(it) } ?: "Sem data", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        ScheduleDatePill("Fim", end?.let { formatThemeBackgroundDate(it) } ?: "Sem fim", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
    }
}
@Composable
private fun ScheduleDatePill(label:String,value:String,containerColor:Color,contentColor:Color) {
    Surface(color=containerColor,contentColor=contentColor,shape=RoundedCornerShape(50)) {
        Text("$label: $value",style=MaterialTheme.typography.labelSmall,fontWeight=androidx.compose.ui.text.font.FontWeight.SemiBold,modifier=Modifier.padding(horizontal=7.dp,vertical=2.dp))
    }
}
@Composable
private fun BackgroundStatusBadge(background: ThemeBackground) {
    val today = ThemeBackground.todayIsoDate()
    val start = ThemeBackground.normalizeDate(background.startDate)
    val end = ThemeBackground.normalizeDate(background.endDate)
    val isDark = LocalNrdDarkMode.current
    val (label, colors) = when {
        start == null -> "Sem data" to (MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer)
        !background.isActive -> "Desativado" to (MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer)
        today < start -> "Agendado" to (MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer)
        end != null && today > end -> "Encerrado" to (MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer)
        else -> "Ativo" to (
            (if (isDark) Color(0xFF1B5E20) else Color(0xFFE8F5E9)) to
                (if (isDark) Color(0xFFC8E6C9) else Color(0xFF1B5E20))
            )
    }
    Surface(
        color = colors.first,
        contentColor = colors.second,
        shape = RoundedCornerShape(50)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun CatalogSnapshotItem(
    snapshot: CatalogSnapshot,
    enabled: Boolean,
    onRestore: (CatalogSnapshot) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().glassSoftShadow(MaterialTheme.shapes.medium),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    formatCatalogHistoryDate(snapshot.createdAt),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                )
                Text(
                    "${snapshot.productCount} produto(s) · ${catalogHistoryReason(snapshot.reason)}${if (snapshot.restoredAt != null) " · restaurado" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (snapshot.createdBy.isNotBlank()) {
                    Text(
                        "Por: ${snapshot.createdBy}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            TextButton(onClick = { onRestore(snapshot) }, enabled = enabled) {
                Text("Restaurar")
            }
        }
    }
}

private fun formatCatalogHistoryDate(timestamp: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(Date(timestamp))

private fun catalogHistoryReason(reason: String): String = when (reason) {
    "pre_restoration" -> "backup automático"
    else -> "manual"
}

@Composable
private fun MaintenanceMetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
    }
}

@Composable
private fun NotificationSettingSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun ImportPreviewDialog(
    result: ProductImportResult,
    isImporting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Prévia da importação") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("${result.rows.size} linha(s) pronta(s) para análise de duplicidade.")
                Text(
                    "Formato detectado: ${if (result.delimiter == '\t') "TSV" else "CSV"}.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(12.dp))
                result.rows.take(8).forEach { row ->
                    Text(
                        "Linha ${row.lineNumber}: ${row.name} • ${row.code} • ${row.category}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                if (result.rows.size > 8) {
                    Text("... e mais ${result.rows.size - 8} linha(s).", style = MaterialTheme.typography.bodySmall)
                }
                if (result.errors.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Alertas da leitura", style = MaterialTheme.typography.titleSmall)
                    result.errors.take(6).forEach { error ->
                        Text(
                            "• $error",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (result.errors.size > 6) {
                        Text("... e mais ${result.errors.size - 6} alerta(s).", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !isImporting && result.rows.isNotEmpty()
            ) {
                Text(if (isImporting) "Publicando..." else "Publicar válidos")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isImporting) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
private fun CategoryManagementRow(
    category: CategoryDefinition,
    isFirst: Boolean,
    isLast: Boolean,
    enabled: Boolean,
    isUpdating: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEdit: () -> Unit,
    onActiveChange: (Boolean) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(category.name, style = MaterialTheme.typography.titleSmall)
            Text(
                if (category.isActive) "Visível no app" else "Oculta no app",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (isUpdating) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        }
        Switch(
            checked = category.isActive,
            onCheckedChange = onActiveChange,
            enabled = enabled
        )
        Box {
            IconButton(onClick = { menuExpanded = true }, enabled = enabled) {
                Icon(Icons.Default.MoreVert, contentDescription = "Ações de ${category.name}")
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Mover para cima") },
                    onClick = {
                        menuExpanded = false
                        onMoveUp()
                    },
                    enabled = !isFirst,
                    leadingIcon = { Icon(Icons.Default.ArrowUpward, contentDescription = null) }
                )
                DropdownMenuItem(
                    text = { Text("Mover para baixo") },
                    onClick = {
                        menuExpanded = false
                        onMoveDown()
                    },
                    enabled = !isLast,
                    leadingIcon = { Icon(Icons.Default.ArrowDownward, contentDescription = null) }
                )
                DropdownMenuItem(
                    text = { Text("Renomear") },
                    onClick = {
                        menuExpanded = false
                        onEdit()
                    },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                )
            }
        }
    }
}

@Composable
private fun HomeSettingSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
internal fun MestreSectionHeader(title: String, description: String) {
    val expressive = LocalExpressiveStyle.current.enabled
    val profile = rememberNrdScreenProfile()
    if (expressive) {
        val shape = RoundedCornerShape(if (profile.compact) 20.dp else 24.dp)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .expressiveShadow(shape, 4.dp),
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = if (profile.compact) 12.dp else 15.dp,
                    vertical = if (profile.compact) 10.dp else 12.dp
                )
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MestrePageIntro(
    description: String,
    hasUnsavedChanges: Boolean = false
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (hasUnsavedChanges) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = RoundedCornerShape(50)
            ) {
                Text(
                    "Alterações não salvas",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun MestrePaginationControls(
    pageIndex: Int,
    pageCount: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onPrevious, enabled = pageIndex > 0) {
            Text("Anterior")
        }
        Text(
            "Página ${pageIndex + 1} de $pageCount",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = onNext, enabled = pageIndex < pageCount - 1) {
            Text("Próxima")
        }
    }
}

@Composable
private fun mestreSuccessColor(): Color = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
    Color(0xFF81C784)
} else {
    Color(0xFF2E7D32)
}
