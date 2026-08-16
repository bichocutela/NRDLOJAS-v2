package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Sync
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
import com.example.data.FirebaseService
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
    val mostUsedLimit by viewModel.userPreferences.mostUsedLimit.collectAsStateWithLifecycle(initialValue = 8)
    val carouselIntervalSeconds by viewModel.userPreferences.carouselIntervalSeconds.collectAsStateWithLifecycle(initialValue = 5)
    val suggestions by FirebaseService.observeSuggestions().collectAsStateWithLifecycle(initialValue = emptyList())
    var suggestionFilter by remember { mutableStateOf("all") }
    val coroutineScope = rememberCoroutineScope()
    
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
                description = "Defina a quantidade de produtos e o intervalo do carrossel"
            )
            Spacer(modifier = Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Mais Utilizados", style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Text("Quantidade de produtos: $mostUsedLimit", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = mostUsedLimit.toFloat(),
                        onValueChange = { coroutineScope.launch { viewModel.userPreferences.setMostUsedLimit(it.toInt()) } },
                        valueRange = 1f..50f,
                        steps = 48
                    )
                    Text("Tempo do carrossel: ${carouselIntervalSeconds}s", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = carouselIntervalSeconds.toFloat(),
                        onValueChange = { coroutineScope.launch { viewModel.userPreferences.setCarouselIntervalSeconds(it.toInt()) } },
                        valueRange = 3f..30f,
                        steps = 26
                    )
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
            var showConfirmDialog by remember { mutableStateOf(false) }
            var bannerUrlInput by remember { mutableStateOf("") }
            var showUrlDialog by remember { mutableStateOf(false) }
            var selectedUri by remember { mutableStateOf<android.net.Uri?>(null) }
            val context = androidx.compose.ui.platform.LocalContext.current
            val coroutineScope = rememberCoroutineScope()
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
