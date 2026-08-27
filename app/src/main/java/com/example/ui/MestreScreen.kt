package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Code
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.data.CategoryDefinition
import com.example.data.FirebaseService
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
    val homeSettings by viewModel.homeSettings.collectAsStateWithLifecycle()
    var draftHomeSettings by remember(homeSettings) { mutableStateOf(homeSettings) }
    var isSavingHomeSettings by remember { mutableStateOf(false) }
    val categoryDefinitions by viewModel.categoryDefinitions.collectAsStateWithLifecycle()
    var showCategoryDialog by remember { mutableStateOf(false) }
    val notificationSettings by FirebaseService.observeNotificationSettings()
        .collectAsStateWithLifecycle(initialValue = NotificationSettings())
    var draftNotificationSettings by remember(notificationSettings) { mutableStateOf(notificationSettings) }
    var isSavingNotificationSettings by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<CategoryDefinition?>(null) }
    var categoryName by remember { mutableStateOf("") }
    val suggestions by FirebaseService.observeSuggestions().collectAsStateWithLifecycle(initialValue = emptyList())
    var suggestionFilter by remember { mutableStateOf("all") }
    val coroutineScope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
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
                title = "Fila de sugestões",
                description = "Analise pendências e marque solicitações como corrigidas"
            )
            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { viewModel.syncProductsFromFirebase() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSyncing
            ) {
                if (isSyncing) {
                    androidx.compose.material3.CircularProgressIndicator(
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
            Spacer(modifier = Modifier.height(16.dp))

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
                title = "Ferramentas administrativas",
                description = "Gerencie abas, produtos e conteúdo visual do aplicativo"
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
            var showConfirmDialog by remember { mutableStateOf(false) }
            var bannerUrlInput by remember { mutableStateOf("") }
            var showUrlDialog by remember { mutableStateOf(false) }
            var selectedUri by remember { mutableStateOf<android.net.Uri?>(null) }
            val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
                contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
            ) { uri: android.net.Uri? ->
                uri?.let {
                    selectedUri = it
                    showConfirmDialog = true
                }
            }
            if (showConfirmDialog && selectedUri != null) {
                AlertDialog(
                    onDismissRequest = { showConfirmDialog = false },
                    title = { Text("Alterar Fundo") },
                    text = { Text("Tem certeza que deseja alterar a imagem de fundo de todos os usuários?") },
                    confirmButton = {
                        TextButton(onClick = {
                            showConfirmDialog = false
                            coroutineScope.launch {
                                try {
                                    val url = com.example.data.FirebaseService.uploadBanner(selectedUri!!)
                                    if (url != null) {
                                        com.example.util.NotificationHelper.showToast(context, "Fundo alterado com sucesso para todos!", android.widget.Toast.LENGTH_SHORT)
                                    } else {
                                        val error = com.example.data.FirebaseService.lastError ?: "Firebase offline ou erro desconhecido"
                                        com.example.util.NotificationHelper.showToast(context, "Erro: $error", android.widget.Toast.LENGTH_LONG)
                                    }
                                } catch (e: Exception) {
                                    com.example.util.NotificationHelper.showToast(context, "Erro exception: ${e.message}", android.widget.Toast.LENGTH_LONG)
                                }
                            }
                        }) {
                            Text("Confirmar")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showConfirmDialog = false }) {
                            Text("Cancelar")
                        }
                    }
                )
            }
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { launcher.launch("image/*") }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.ColorLens, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Alterar Fundo do App (Hero Banner)", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        Text("Escolha uma imagem da galeria.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { showUrlDialog = true }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.ColorLens, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Alterar Fundo por URL", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        Text("Forneça um link (ex: Google Drive).", style = MaterialTheme.typography.bodySmall)
                    }
                }
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

            if (showUrlDialog) {
                AlertDialog(
                    onDismissRequest = { showUrlDialog = false },
                    title = { Text("URL da Imagem") },
                    text = {
                        OutlinedTextField(
                            value = bannerUrlInput,
                            onValueChange = { bannerUrlInput = it },
                            label = { Text("Cole o link aqui") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            showUrlDialog = false
                            if (bannerUrlInput.isNotBlank()) {
                                coroutineScope.launch {
                                    try {
                                        val url = com.example.data.FirebaseService.setBannerUrlDirectly(com.example.util.ImageUrlHelper.normalizeUrl(bannerUrlInput))
                                        viewModel.userPreferences.setBannerImageUri(url)
                                        com.example.util.NotificationHelper.showToast(context, "Fundo alterado com sucesso para todos!", android.widget.Toast.LENGTH_SHORT)
                                    } catch (e: Exception) {
                                        com.example.util.NotificationHelper.showToast(context, "Erro: ${e.message}", android.widget.Toast.LENGTH_LONG)
                                    }
                                }
                            }
                        }) {
                            Text("Salvar")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showUrlDialog = false }) {
                            Text("Cancelar")
                        }
                    }
                )
            }
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))
            
            Text("Recursos administrativos", style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.Start))
            Spacer(modifier = Modifier.height(8.dp))
            
            InfoCard(
                icon = Icons.Default.ViewCarousel,
                title = "Adicionar Novas Abas",
                description = "Peça ao assistente no chat: 'Crie uma nova aba chamada X com a função Y'."
            )
            Spacer(modifier = Modifier.height(8.dp))
            InfoCard(
                icon = Icons.Default.ColorLens,
                title = "Fundo e Tema do App",
                description = "Peça ao assistente no chat: 'Altere a cor de fundo para Z e o tema para escuro'."
            )
            Spacer(modifier = Modifier.height(8.dp))
            InfoCard(
                icon = Icons.Default.Code,
                title = "Edição de Código e Textos",
                description = "Peça ao assistente no chat: 'Modifique o texto na tela inicial'."
            )
        }
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
fun InfoCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, description: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(title, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
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
