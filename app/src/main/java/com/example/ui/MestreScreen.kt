package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import com.example.data.AppearanceSettings
import com.example.data.AssistantSettings
import com.example.data.CategoryDefinition
import com.example.data.CatalogSnapshot
import com.example.data.ThemeBackground
import com.example.data.FirebaseService
import com.example.data.MaintenanceSummary
import com.example.data.ProductImportParser
import com.example.data.NotificationSettings
import com.example.data.ProductImportResult
import com.example.data.ProductSuggestion

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MestreScreen(
    viewModel: MainViewModel, 
    onNavigateToAdmin: () -> Unit,
    onNavigateToManageTabs: () -> Unit,
    onNavigateToManageProducts: () -> Unit,
    onNavigateBack: () -> Unit
) {
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
    val notificationSettings by FirebaseService.observeNotificationSettings()
        .collectAsStateWithLifecycle(initialValue = NotificationSettings())
    var draftNotificationSettings by remember(notificationSettings) { mutableStateOf(notificationSettings) }
    var isSavingNotificationSettings by remember { mutableStateOf(false) }
    val assistantSettings by FirebaseService.observeAssistantSettings()
        .collectAsStateWithLifecycle(initialValue = AssistantSettings())
    var draftAssistantSettings by remember(assistantSettings) { mutableStateOf(assistantSettings) }
    var isSavingAssistantSettings by remember { mutableStateOf(false) }
    val appearanceSettings by FirebaseService.observeAppearanceSettings()
        .collectAsStateWithLifecycle(initialValue = AppearanceSettings())
    var draftAppearanceSettings by remember(appearanceSettings) { mutableStateOf(appearanceSettings) }
    var draftThemeBackgrounds by remember(appearanceSettings.themeBackgrounds) {
        mutableStateOf(appearanceSettings.themeBackgrounds)
    }
    var isSavingAppearanceSettings by remember { mutableStateOf(false) }
    var expandedBackgroundThemes by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showThemeBackgroundDialog by remember { mutableStateOf(false) }
    var editingBackgroundTheme by remember { mutableStateOf<String?>(null) }
    var editingBackground by remember { mutableStateOf<ThemeBackground?>(null) }
    var backgroundLabelInput by remember { mutableStateOf("") }
    var backgroundUrlInput by remember { mutableStateOf("") }
    var backgroundStartDateInput by remember { mutableStateOf("") }
    var backgroundEndDateInput by remember { mutableStateOf("") }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var backgroundInputError by remember { mutableStateOf<String?>(null) }
    var isUploadingThemeBackground by remember { mutableStateOf(false) }
    var backgroundToDelete by remember { mutableStateOf<Pair<String, ThemeBackground>?>(null) }
    var maintenanceSummary by remember { mutableStateOf<MaintenanceSummary?>(null) }
    var isLoadingMaintenance by remember { mutableStateOf(false) }
    var showSyncConfirmation by remember { mutableStateOf(false) }
    var snapshotToRestore by remember { mutableStateOf<CatalogSnapshot?>(null) }
    var editingCategory by remember { mutableStateOf<CategoryDefinition?>(null) }
    var categoryName by remember { mutableStateOf("") }
    val suggestions by FirebaseService.observeSuggestions().collectAsStateWithLifecycle(initialValue = emptyList())
    var suggestionFilter by remember { mutableStateOf("all") }
    val coroutineScope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val themeBackgroundLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        val themeKey = editingBackgroundTheme
        if (uri == null || themeKey == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            isUploadingThemeBackground = true
            backgroundInputError = null
            try {
                val uploadedUrl = FirebaseService.uploadImageToStorage(
                    uri,
                    "theme_backgrounds/$themeKey/${UUID.randomUUID()}.jpg"
                )
                if (uploadedUrl.isNullOrBlank()) {
                    backgroundInputError = FirebaseService.lastError ?: "Não foi possível enviar a imagem."
                } else {
                    backgroundUrlInput = uploadedUrl
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
    
    LaunchedEffect(Unit) {
        viewModel.refreshCatalogHistory()
        viewModel.syncMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Painel Mestre", color = MaterialTheme.colorScheme.primary) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(12.dp))
            Text("Painel de trabalho do Mestre", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Acompanhe pendências e administre o conteúdo do aplicativo.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))

            MestreSectionHeader(
                title = "Pendências",
                description = "Analise sugestões dos usuários e marque solicitações como corrigidas"
            )
            Spacer(modifier = Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Sugestões dos usuários", style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        Text(
                            suggestions.count { it.status == ProductSuggestion.STATUS_PENDING }.toString(),
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.FilterList, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        listOf("all" to "Todas", "pending" to "Pendentes", "fixed" to "Corrigidas").forEach { (filterKey, filterLabel) ->
                            FilterChip(
                                selected = suggestionFilter == filterKey,
                                onClick = { suggestionFilter = filterKey },
                                label = { Text(filterLabel, maxLines = 1) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    val filteredSuggestions = suggestions
                        .filter { suggestion ->
                            suggestionFilter == "all" ||
                                (suggestionFilter == "pending" && suggestion.status == ProductSuggestion.STATUS_PENDING) ||
                                (suggestionFilter == "fixed" && suggestion.status == ProductSuggestion.STATUS_FIXED)
                        }
                        .sortedBy { if (it.status == ProductSuggestion.STATUS_PENDING) 0 else 1 }
                    if (filteredSuggestions.isEmpty()) {
                        Text(
                            if (suggestionFilter == "pending") "Nenhuma pendência no momento."
                            else if (suggestionFilter == "fixed") "Nenhuma sugestão corrigida."
                            else "Nenhuma sugestão recebida.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        filteredSuggestions.forEach { suggestion ->
                            SuggestionManagementItem(suggestion) { status ->
                                coroutineScope.launch {
                                    val updated = FirebaseService.updateSuggestionStatus(suggestion.id, status)
                                    snackbarHostState.showSnackbar(
                                        if (updated) "Sugestão marcada como $status." else "Não foi possível atualizar a sugestão."
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            MestreSectionHeader(
                title = "Manutenção e sincronização",
                description = "Confira o estado do catálogo remoto antes de sincronizar"
            )
            Spacer(modifier = Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val summary = maintenanceSummary
                    if (summary == null) {
                        Text(
                            "Nenhum diagnóstico realizado nesta sessão.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            if (summary.remoteAvailable) "Conexão remota disponível" else "Não foi possível consultar a nuvem",
                            style = MaterialTheme.typography.titleSmall,
                            color = if (summary.remoteAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        MaintenanceMetricRow("Produtos locais", summary.localProductCount.toString())
                        MaintenanceMetricRow("Produtos na nuvem", summary.remoteProductCount.toString())
                        val productDifference = summary.remoteProductCount - summary.localProductCount
                        val differenceLabel = when {
                            productDifference == 0 -> "Nenhuma diferença"
                            productDifference > 0 -> "+$productDifference na nuvem"
                            else -> "$productDifference na nuvem"
                        }
                        MaintenanceMetricRow("Diferença de produtos", differenceLabel)
                        MaintenanceMetricRow("Abas dinâmicas", summary.dynamicTabCount.toString())
                        MaintenanceMetricRow("Sugestões pendentes", summary.pendingSuggestionCount.toString())
                        val lastUpdate = summary.lastRemoteProductUpdate?.let {
                            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(Date(it))
                        } ?: "Não informado"
                        MaintenanceMetricRow("Última atualização de produto", lastUpdate)
                        MaintenanceMetricRow(
                            "Diagnóstico verificado em",
                            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(Date(summary.checkedAt))
                        )
                        if (summary.localCategoryCounts.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Categorias locais", style = MaterialTheme.typography.titleSmall)
                            summary.localCategoryCounts.take(4).forEach { count ->
                                MaintenanceMetricRow(count.category, count.count.toString())
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                isLoadingMaintenance = true
                                try {
                                    maintenanceSummary = FirebaseService.getMaintenanceSummary(
                                        localProductCount = allProducts.size,
                                        localCategoryCounts = localCategoryCounts
                                    )
                                } finally {
                                    isLoadingMaintenance = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoadingMaintenance && !isLoadingCatalogHistory && !isSyncing
                    ) {
                        if (isLoadingMaintenance) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Consultando...")
                        } else {
                            Icon(Icons.Default.Sync, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Atualizar diagnóstico")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { showSyncConfirmation = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSyncing && !isLoadingMaintenance && !isLoadingCatalogHistory
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sincronizando...")
                        } else {
                            Icon(Icons.Default.Sync, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sincronizar Banco de Dados")
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            MestreSectionHeader(
                title = "Segurança operacional",
                description = "Crie pontos de retorno do catálogo antes de mudanças importantes"
            )
            Spacer(modifier = Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "O histórico mantém até 20 snapshots remotos. Restaurar uma versão cria primeiro um backup automático do catálogo atual.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.createCatalogSnapshot() },
                            enabled = !isLoadingCatalogHistory && !isSyncing,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Inventory, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Criar snapshot")
                        }
                        OutlinedButton(
                            onClick = { viewModel.refreshCatalogHistory() },
                            enabled = !isLoadingCatalogHistory && !isSyncing
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = "Atualizar histórico")
                        }
                    }
                    if (isLoadingCatalogHistory) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Consultando histórico...", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (!isLoadingCatalogHistory && catalogSnapshots.isEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            "Nenhum snapshot disponível ou a nuvem não está acessível.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    catalogSnapshots.forEach { snapshot ->
                        Spacer(modifier = Modifier.height(10.dp))
                        CatalogSnapshotItem(
                            snapshot = snapshot,
                            enabled = !isLoadingCatalogHistory,
                            onRestore = { snapshotToRestore = it }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            MestreSectionHeader(
                title = "Configurações globais",
                description = "Defina o comportamento geral exibido para todos os usuários"
            )
            Spacer(modifier = Modifier.height(8.dp))

            MestreSectionHeader(
                title = "Configurações da Home",
                description = "Escolha o que aparece para todos os usuários"
            )
            Spacer(modifier = Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
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
                                    else "Não foi possível publicar as configurações da Home."
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSavingHomeSettings
                    ) {
                        if (isSavingHomeSettings) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Publicando...")
                        } else {
                            Text("Publicar configurações")
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            MestreSectionHeader(
                title = "Categorias",
                description = "Organize os grupos exibidos e usados no catálogo"
            )
            Spacer(modifier = Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Button(
                        onClick = {
                            editingCategory = null
                            categoryName = ""
                            showCategoryDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Adicionar categoria")
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    categoryDefinitions
                        .sortedWith(compareBy<CategoryDefinition> { it.displayOrder }.thenBy { it.name })
                        .forEachIndexed { index, category ->
                            CategoryManagementRow(
                                category = category,
                                isFirst = index == 0,
                                isLast = index == categoryDefinitions.lastIndex,
                                onMoveUp = {
                                    coroutineScope.launch { viewModel.moveCategory(category, -1) }
                                },
                                onMoveDown = {
                                    coroutineScope.launch { viewModel.moveCategory(category, 1) }
                                },
                                onEdit = {
                                    editingCategory = category
                                    categoryName = category.name
                                    showCategoryDialog = true
                                },
                                onActiveChange = { isActive ->
                                    coroutineScope.launch { viewModel.setCategoryActive(category, isActive) }
                                }
                            )
                            if (index < categoryDefinitions.lastIndex) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            }
                        }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            MestreSectionHeader(
                title = "Notificações globais",
                description = "Controle o que pode ser recebido pelos usuários"
            )
            Spacer(modifier = Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
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
                        onCheckedChange = { draftNotificationSettings = draftNotificationSettings.copy(productAddedEnabled = it) }
                    )
                    NotificationSettingSwitch(
                        label = "Código alterado",
                        checked = draftNotificationSettings.codeChangedEnabled,
                        onCheckedChange = { draftNotificationSettings = draftNotificationSettings.copy(codeChangedEnabled = it) }
                    )
                    NotificationSettingSwitch(
                        label = "Sugestão corrigida",
                        checked = draftNotificationSettings.suggestionFixedEnabled,
                        onCheckedChange = { draftNotificationSettings = draftNotificationSettings.copy(suggestionFixedEnabled = it) }
                    )
                    NotificationSettingSwitch(
                        label = "Atualização do app",
                        checked = draftNotificationSettings.appUpdateEnabled,
                        onCheckedChange = { draftNotificationSettings = draftNotificationSettings.copy(appUpdateEnabled = it) }
                    )
                    NotificationSettingSwitch(
                        label = "Promoções atualizadas",
                        checked = draftNotificationSettings.promotionUpdatedEnabled,
                        onCheckedChange = { draftNotificationSettings = draftNotificationSettings.copy(promotionUpdatedEnabled = it) }
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
                                    else "Não foi possível publicar a política de notificações."
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSavingNotificationSettings
                    ) {
                        if (isSavingNotificationSettings) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Publicando...")
                        } else {
                            Text("Publicar notificações")
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            MestreSectionHeader(
                title = "Assistente IA",
                description = "Defina os limites e a mensagem inicial do assistente"
            )
            Spacer(modifier = Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    AssistantSettingSwitch(
                        label = "Permitir uso do Assistente IA",
                        checked = draftAssistantSettings.enabled,
                        onCheckedChange = { draftAssistantSettings = draftAssistantSettings.copy(enabled = it) }
                    )
                    AssistantSettingSwitch(
                        label = "Restringir respostas ao catálogo",
                        checked = draftAssistantSettings.catalogOnly,
                        onCheckedChange = { draftAssistantSettings = draftAssistantSettings.copy(catalogOnly = it) }
                    )
                    Text(
                        "Quando ativo, a IA recebe apenas os produtos mais relacionados à pergunta.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = draftAssistantSettings.welcomeMessage,
                        onValueChange = {
                            draftAssistantSettings = draftAssistantSettings.copy(welcomeMessage = it.take(160))
                        },
                        label = { Text("Mensagem inicial") },
                        supportingText = { Text("Até 160 caracteres") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 3
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Produtos enviados como contexto: ${draftAssistantSettings.maxContextProducts}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = draftAssistantSettings.maxContextProducts.toFloat(),
                        onValueChange = {
                            draftAssistantSettings = draftAssistantSettings.copy(maxContextProducts = it.toInt())
                        },
                        valueRange = 5f..50f,
                        steps = 44
                    )
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isSavingAssistantSettings = true
                                val saved = FirebaseService.saveAssistantSettings(draftAssistantSettings)
                                isSavingAssistantSettings = false
                                snackbarHostState.showSnackbar(
                                    if (saved) "Configurações do Assistente publicadas para todos."
                                    else "Não foi possível publicar as configurações do Assistente."
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSavingAssistantSettings
                    ) {
                        if (isSavingAssistantSettings) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Publicando...")
                        } else {
                            Text("Publicar Assistente")
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            val themeOptions = listOf(
                "multicolor" to "Multicolorido",
                "red" to "Vermelho",
                "gold" to "Dourado",
                "green" to "Verde",
                "blue" to "Azul",
                "orange" to "Laranja"
            )
            val appearanceModeOptions = listOf(
                "system" to "Seguir sistema",
                "light" to "Claro",
                "dark" to "Escuro"
            )
            var expandedRemoteTheme by remember { mutableStateOf(false) }
            var expandedRemoteMode by remember { mutableStateOf(false) }

            fun openBackgroundEditor(themeKey: String, background: ThemeBackground?) {
                editingBackgroundTheme = themeKey
                editingBackground = background
                backgroundLabelInput = background?.label.orEmpty()
                backgroundUrlInput = background?.url.orEmpty()
                backgroundStartDateInput = background?.startDate.orEmpty()
                backgroundEndDateInput = background?.endDate.orEmpty()
                showStartDatePicker = false
                showEndDatePicker = false
                backgroundInputError = null
                showThemeBackgroundDialog = true
            }

            fun pickerDateToIsoDate(millis: Long?): String? = millis?.let {
                SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.format(Date(it))
            }

            fun dateToPickerMillis(value: String): Long? = ThemeBackground.parseDate(value)?.time

            fun updateBackgrounds(themeKey: String, backgrounds: List<ThemeBackground>) {
                draftThemeBackgrounds = draftThemeBackgrounds + (themeKey to backgrounds)
            }

            MestreSectionHeader(
                title = "Aparência global",
                description = "Personalize o visual para todos os usuários"
            )
            Spacer(modifier = Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    NotificationSettingSwitch(
                        label = "Aplicar aparência para todos",
                        checked = draftAppearanceSettings.overrideLocalTheme,
                        onCheckedChange = {
                            draftAppearanceSettings = draftAppearanceSettings.copy(overrideLocalTheme = it)
                        }
                    )
                    Text(
                        "Desativado, cada usuário mantém sua própria escolha em Configurações.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    ExposedDropdownMenuBox(
                        expanded = expandedRemoteTheme,
                        onExpandedChange = { expandedRemoteTheme = !expandedRemoteTheme }
                    ) {
                        OutlinedTextField(
                            value = themeOptions.find { it.first == draftAppearanceSettings.theme }?.second ?: "Multicolorido",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Tema global") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedRemoteTheme) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
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
                    ExposedDropdownMenuBox(
                        expanded = expandedRemoteMode,
                        onExpandedChange = { expandedRemoteMode = !expandedRemoteMode }
                    ) {
                        OutlinedTextField(
                            value = appearanceModeOptions.find { it.first == draftAppearanceSettings.appearanceMode }?.second ?: "Seguir sistema",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Modo de aparência") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedRemoteMode) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
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
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Fundos por tema",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                    Text(
                        "O fundo padrão permanece disponível. Ative no máximo um fundo personalizado por tema; se todos estiverem desativados, o padrão volta a aparecer.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    themeOptions.forEach { (themeKey, themeLabel) ->
                        val backgrounds = draftThemeBackgrounds[themeKey].orEmpty()
                        val expanded = themeKey in expandedBackgroundThemes
                        OutlinedCard(
                            modifier = Modifier.fillMaxWidth(),
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
                                        if (backgrounds.any { it.isAvailableOn() }) "Fundo personalizado ativo"
                                        else "Fundo padrão ativo",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(if (expanded) "−" else "+", style = MaterialTheme.typography.titleLarge)
                            }
                        }
                        if (expanded) {
                            Column(modifier = Modifier.padding(start = 8.dp, top = 6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Padrão do aplicativo",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    TextButton(
                                        onClick = {
                                            updateBackgrounds(themeKey, backgrounds.map { it.copy(isActive = false) })
                                        },
                                        enabled = backgrounds.any { it.isActive }
                                    ) {
                                        Text("Usar fundo padrão")
                                    }
                                }
                                backgrounds.forEach { background ->
                                    Spacer(modifier = Modifier.height(6.dp))
                                    ThemeBackgroundItem(
                                        background = background,
                                        onActiveChange = { isActive ->
                                            updateBackgrounds(
                                                themeKey,
                                                backgrounds.map {
                                                    if (it.id == background.id) it.copy(isActive = isActive)
                                                    else if (isActive) it.copy(isActive = false) else it
                                                }
                                            )
                                        },
                                        onEdit = { openBackgroundEditor(themeKey, background) },
                                        onDelete = { backgroundToDelete = themeKey to background }
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedButton(
                                    onClick = { openBackgroundEditor(themeKey, null) },
                                    modifier = Modifier.fillMaxWidth()
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
                                isSavingAppearanceSettings = true
                                val saved = FirebaseService.saveAppearanceSettings(
                                    draftAppearanceSettings.copy(themeBackgrounds = draftThemeBackgrounds)
                                )
                                isSavingAppearanceSettings = false
                                snackbarHostState.showSnackbar(
                                    if (saved) "Aparência global publicada para todos."
                                    else FirebaseService.lastError ?: "Não foi possível publicar a aparência global."
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSavingAppearanceSettings
                    ) {
                        if (isSavingAppearanceSettings) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Publicando...")
                        } else {
                            Text("Salvar aparência e fundos")
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            MestreSectionHeader(
                title = "Catálogo e conteúdo",
                description = "Gerencie abas, produtos e importações do catálogo"
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = onNavigateToManageTabs
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.ViewCarousel, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Gerenciar Abas (Painel Mestre)", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        Text("Criar, editar, excluir ou reordenar abas.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = onNavigateToManageProducts
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Inventory, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Editar Produtos Existentes", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        Text("Modificar código, foto e nome de produtos da base.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = onNavigateToAdmin
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Build, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Adicionar Produtos", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        Text("Cadastrar produtos manualmente.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    if (!isParsingImport && !isImporting) importLauncher.launch("text/*")
                }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Inventory, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Importar produtos por planilha", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        Text("Use CSV ou TSV e confira a prévia antes de publicar.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            
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

            if (showSyncConfirmation) {
                AlertDialog(
                    onDismissRequest = { showSyncConfirmation = false },
                    title = { Text("Sincronizar catálogo?") },
                    text = {
                        val difference = maintenanceSummary?.let {
                            it.remoteProductCount - it.localProductCount
                        }
                        Text(
                            when {
                                maintenanceSummary == null ->
                                    "A rotina existente consultará o catálogo remoto e atualizará os dados locais. Continue somente se a conexão estiver disponível."
                                difference == 0 ->
                                    "O diagnóstico não encontrou diferença na quantidade de produtos. A sincronização ainda poderá atualizar nomes, categorias e imagens."
                                difference != null ->
                                    "O diagnóstico encontrou uma diferença de ${kotlin.math.abs(difference)} produto(s) entre a nuvem e este aparelho. A sincronização atualizará o catálogo local usando os dados remotos."
                                else ->
                                    "A sincronização atualizará o catálogo local usando os dados remotos."
                            }
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showSyncConfirmation = false
                                viewModel.syncProductsFromFirebase()
                            }
                        ) {
                            Text("Continuar")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showSyncConfirmation = false }) {
                            Text("Cancelar")
                        }
                    }
                )
            }

            if (showCategoryDialog) {
                AlertDialog(
                    onDismissRequest = { showCategoryDialog = false },
                    title = { Text(if (editingCategory == null) "Nova categoria" else "Renomear categoria") },
                    text = {
                        OutlinedTextField(
                            value = categoryName,
                            onValueChange = { categoryName = it },
                            label = { Text("Nome da categoria") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                coroutineScope.launch {
                                    val saved = editingCategory?.let {
                                        viewModel.renameCategory(it, categoryName)
                                    } ?: viewModel.addCategory(categoryName)
                                    if (saved) {
                                        showCategoryDialog = false
                                    }
                                }
                            },
                            enabled = categoryName.isNotBlank()
                        ) {
                            Text("Salvar")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showCategoryDialog = false }) {
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

            if (showThemeBackgroundDialog) {
                AlertDialog(
                    onDismissRequest = { showThemeBackgroundDialog = false },
                    title = {
                        Text(if (editingBackground == null) "Adicionar fundo ao tema" else "Editar fundo do tema")
                    },
                    text = {
                        Column {
                            OutlinedTextField(
                                value = backgroundLabelInput,
                                onValueChange = { backgroundLabelInput = it },
                                label = { Text("Nome do fundo") },
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
                                "Use uma imagem acessível por link HTTP/HTTPS. O fundo padrão continuará disponível.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "Período de ativação (opcional)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                            )
                            Text(
                                "Sem início, começa agora. Sem fim, permanece até ser desativado. Após o fim, o fundo padrão volta automaticamente.",
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
                                    val current = themeKey?.let { draftThemeBackgrounds[it].orEmpty() }.orEmpty()
                                    when {
                                        themeKey == null -> backgroundInputError = "Tema inválido."
                                        normalizedUrl.isBlank() || !(normalizedUrl.startsWith("https://") || normalizedUrl.startsWith("http://")) ->
                                            backgroundInputError = "Informe uma URL HTTP/HTTPS válida."
                                        backgroundStartDateInput.isNotBlank() && normalizedStartDate == null ->
                                            backgroundInputError = "Informe uma data de início válida."
                                        backgroundEndDateInput.isNotBlank() && normalizedEndDate == null ->
                                            backgroundInputError = "Informe uma data de fim válida."
                                        normalizedStartDate != null && normalizedEndDate != null && normalizedEndDate < normalizedStartDate ->
                                            backgroundInputError = "A data de fim não pode ser anterior à data de início."
                                        else -> {
                                        val updated = if (editingBackground == null) {
                                            current + ThemeBackground(
                                                id = UUID.randomUUID().toString(),
                                                label = backgroundLabelInput.trim().ifBlank { "Fundo personalizado" },
                                                url = normalizedUrl,
                                                isActive = current.none { it.isActive },
                                                startDate = normalizedStartDate,
                                                endDate = normalizedEndDate
                                            )
                                        } else {
                                            current.map { background ->
                                                if (background.id == editingBackground!!.id) {
                                                    background.copy(
                                                        label = backgroundLabelInput.trim().ifBlank { "Fundo personalizado" },
                                                        url = normalizedUrl,
                                                        startDate = normalizedStartDate,
                                                        endDate = normalizedEndDate
                                                    )
                                                } else {
                                                    background
                                                }
                                            }
                                        }
                                        updateBackgrounds(themeKey!!, updated)
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

            backgroundToDelete?.let { (themeKey, background) ->
                AlertDialog(
                    onDismissRequest = { backgroundToDelete = null },
                    title = { Text("Excluir fundo?") },
                    text = { Text("O fundo \"${background.label}\" será removido do rascunho deste tema. Para refletir a exclusão nos usuários, ainda será necessário salvar a aparência.") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                updateBackgrounds(themeKey, draftThemeBackgrounds[themeKey].orEmpty().filterNot { it.id == background.id })
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
        start != null ->
            "Ativo desde ${formatThemeBackgroundDate(background.startDate.orEmpty())} — sem data de fim"
        end != null ->
            "Ativo até ${formatThemeBackgroundDate(background.endDate.orEmpty())} (inclusive)"
        else -> "Sem datas — permanece ativo até desativar"
    }
}

@Composable
private fun ThemeBackgroundItem(
    background: ThemeBackground,
    onActiveChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            coil.compose.AsyncImage(
                model = background.url,
                contentDescription = "Prévia de ${background.label}",
                modifier = Modifier
                    .size(width = 72.dp, height = 44.dp)
                    .clip(RoundedCornerShape(6.dp)),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(background.label, style = MaterialTheme.typography.bodyMedium)
                Text(
                    backgroundScheduleStatus(background),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    background.url,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            Switch(checked = background.isActive, onCheckedChange = onActiveChange)
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Editar ${background.label}")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Excluir ${background.label}")
            }
        }
    }
}

@Composable
private fun CatalogSnapshotItem(
    snapshot: CatalogSnapshot,
    enabled: Boolean,
    onRestore: (CatalogSnapshot) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
private fun AssistantSettingSwitch(
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
private fun NotificationSettingSwitch(
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
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEdit: () -> Unit,
    onActiveChange: (Boolean) -> Unit
) {
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
        IconButton(onClick = onMoveUp, enabled = !isFirst) {
            Icon(Icons.Default.ArrowUpward, contentDescription = "Mover ${category.name} para cima")
        }
        IconButton(onClick = onMoveDown, enabled = !isLast) {
            Icon(Icons.Default.ArrowDownward, contentDescription = "Mover ${category.name} para baixo")
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Default.Edit, contentDescription = "Renomear ${category.name}")
        }
        Switch(
            checked = category.isActive,
            onCheckedChange = onActiveChange
        )
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
private fun MestreSectionHeader(title: String, description: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SuggestionManagementItem(
    suggestion: ProductSuggestion,
    onStatusChange: (String) -> Unit
) {
    val dateText = if (suggestion.createdAt > 0L) {
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(Date(suggestion.createdAt))
    } else {
        "Data não informada"
    }
    val isFixed = suggestion.status == ProductSuggestion.STATUS_FIXED
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(suggestion.text, style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Enviada por: ${suggestion.submittedBy}", style = MaterialTheme.typography.bodySmall)
            Text(dateText, style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { onStatusChange(ProductSuggestion.STATUS_PENDING) },
                    enabled = isFixed,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Icon(Icons.Default.Pending, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Pendente", maxLines = 1)
                }
                Button(
                    onClick = { onStatusChange(ProductSuggestion.STATUS_FIXED) },
                    enabled = !isFixed,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Corrigido", maxLines = 1)
                }
            }
        }
    }
}
