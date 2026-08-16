package com.example.ui

import com.example.util.FcmTopicSubscription
import com.example.util.NotificationHelper
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.ui.theme.getDynamicThemeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel, onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    val vibrateOnClick by viewModel.userPreferences.vibrateOnClick.collectAsState(initial = true)
    val vibrateOnFound by viewModel.userPreferences.vibrateOnFound.collectAsState(initial = true)
    val largeText by viewModel.userPreferences.largeText.collectAsState(initial = false)
    val fontScale by viewModel.userPreferences.fontScale.collectAsState(initial = 1.0f)
    val barcodeNumberScale by viewModel.userPreferences.barcodeNumberScale.collectAsState(initial = 1.0f)
    val barcodeTitleScale by viewModel.userPreferences.barcodeTitleScale.collectAsState(initial = 1.0f)
    val boldOutline by viewModel.userPreferences.boldOutline.collectAsState(initial = false)
    val uppercaseBold by viewModel.userPreferences.uppercaseBold.collectAsState(initial = false)
    val appTheme by viewModel.userPreferences.appTheme.collectAsState(initial = "multicolor")
    val appearanceMode by viewModel.userPreferences.appearanceMode.collectAsState(initial = "system")
    
    val notificationsEnabled by viewModel.userPreferences.notificationsEnabled.collectAsState(initial = true)
    val notificationsProductAddedEnabled by viewModel.userPreferences.notificationsProductAddedEnabled.collectAsState(initial = true)
    val notificationsCodeChangedEnabled by viewModel.userPreferences.notificationsCodeChangedEnabled.collectAsState(initial = true)


    var showSuggestionDialog by remember { mutableStateOf(false) }
    var suggestionText by remember { mutableStateOf("") }
    var suggestionSending by remember { mutableStateOf(false) }
    var selectedCorrectedSuggestion by remember { mutableStateOf<com.example.data.ProductSuggestion?>(null) }
    var suggestionPendingDeletion by remember { mutableStateOf<com.example.data.ProductSuggestion?>(null) }
    var deletingSuggestion by remember { mutableStateOf(false) }
    var appearanceExpanded by remember { mutableStateOf(true) }
    var notificationsExpanded by remember { mutableStateOf(false) }
    var feedbackExpanded by remember { mutableStateOf(false) }
    val installationId by produceState(initialValue = "") {
        value = viewModel.userPreferences.getOrCreateInstallationId()
    }
    val userSuggestions by remember(installationId) {
        com.example.data.FirebaseService.observeUserSuggestions(installationId)
    }.collectAsState(initial = emptyList())
    val publicSuggestions by com.example.data.FirebaseService.observePublicSuggestions().collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configurações") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = getDynamicThemeColor(0, appTheme, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary).first,
                    titleContentColor = getDynamicThemeColor(0, appTheme, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary).second,
                    navigationIconContentColor = getDynamicThemeColor(0, appTheme, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary).second
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            
            SettingsSectionHeader(
                title = "Aparência",
                summary = "Fonte, temas, modo de aparência e vibração",
                expanded = appearanceExpanded,
                onToggle = { appearanceExpanded = !appearanceExpanded }
            )
            if (appearanceExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Tamanho da Fonte", modifier = Modifier.weight(1f))
                Slider(
                    value = fontScale,
                    onValueChange = { coroutineScope.launch { viewModel.userPreferences.setFontScale(it) } },
                    valueRange = 0.8f..2.0f,
                    steps = 11,
                    modifier = Modifier.weight(2f).padding(horizontal = 16.dp)
                )
                Text(String.format("%.1fx", fontScale))
            }
            Button(onClick = { coroutineScope.launch { viewModel.userPreferences.setFontScale(1.0f) } }, modifier = Modifier.align(Alignment.End)) {
                Text("Restaurar Padrão")
            }

            Text("Tamanho do número do código", style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Slider(
                    value = barcodeNumberScale,
                    onValueChange = { coroutineScope.launch { viewModel.userPreferences.setBarcodeNumberScale(it) } },
                    valueRange = 0.8f..1.6f,
                    steps = 7,
                    modifier = Modifier.weight(1f)
                )
                Text(String.format("%.1fx", barcodeNumberScale), style = MaterialTheme.typography.labelLarge)
            }
            Text("Controla o número exibido acima do código de barras.", style = MaterialTheme.typography.bodySmall)

            Text("Tamanho do título do produto", style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Slider(
                    value = barcodeTitleScale,
                    onValueChange = { coroutineScope.launch { viewModel.userPreferences.setBarcodeTitleScale(it) } },
                    valueRange = 0.8f..1.5f,
                    steps = 6,
                    modifier = Modifier.weight(1f)
                )
                Text(String.format("%.1fx", barcodeTitleScale), style = MaterialTheme.typography.labelLarge)
            }
            Text("Controla somente o título no diálogo do código de barras.", style = MaterialTheme.typography.bodySmall)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Aumentar letras da tela inicial")
                Switch(checked = largeText, onCheckedChange = { coroutineScope.launch { viewModel.userPreferences.setLargeText(it) } })
            }
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Letras em contorno negrito")
                Switch(checked = boldOutline, onCheckedChange = { coroutineScope.launch { viewModel.userPreferences.setBoldOutline(it) } })
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Todas letras maiúsculas em negrito")
                Switch(checked = uppercaseBold, onCheckedChange = { coroutineScope.launch { viewModel.userPreferences.setUppercaseBold(it) } })
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            Text("Tema do Aplicativo", style = MaterialTheme.typography.titleMedium, color = getDynamicThemeColor(1, appTheme, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary).first)
            
            var expandedThemeMenu by remember { mutableStateOf(false) }
            val themeOptions = listOf(
                "multicolor" to "Multicolorido",
                "red" to "Vermelho",
                "gold" to "Dourado",
                "green" to "Verde",
                "blue" to "Azul",
                "orange" to "Laranja"
            )
            
            ExposedDropdownMenuBox(
                expanded = expandedThemeMenu,
                onExpandedChange = { expandedThemeMenu = !expandedThemeMenu }
            ) {
                OutlinedTextField(
                    value = themeOptions.find { it.first == appTheme }?.second ?: "Multicolorido",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Selecione o Tema") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedThemeMenu) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                )
                ExposedDropdownMenu(
                    expanded = expandedThemeMenu,
                    onDismissRequest = { expandedThemeMenu = false }
                ) {
                    themeOptions.forEach { (themeKey, themeLabel) ->
                        DropdownMenuItem(
                            text = { Text(themeLabel) },
                            onClick = {
                                coroutineScope.launch { viewModel.userPreferences.setAppTheme(themeKey) }
                                expandedThemeMenu = false
                            }
                        )
                    }
                }
            }
            
                        Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Modo de aparência",
                style = MaterialTheme.typography.titleMedium,
                color = getDynamicThemeColor(2, appTheme, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary).first
            )
            Text(
                "Escolha como as cores do aplicativo serão exibidas.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(8.dp))

            val normalizedAppearanceMode = appearanceMode.takeIf { it in setOf("light", "dark", "system") } ?: "system"
            val appearanceOptions = listOf(
                "light" to "Claro",
                "dark" to "Escuro",
                "system" to "Padrão"
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                appearanceOptions.forEach { (modeKey, modeLabel) ->
                    val isSelected = normalizedAppearanceMode == modeKey
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                coroutineScope.launch {
                                    viewModel.userPreferences.setAppearanceMode(modeKey)
                                }
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            contentColor = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        ),
                        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(modeLabel, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
            Text(
                if (normalizedAppearanceMode == "dark") {
                    "Modo escuro ativo: os destaques seguem a cor predominante do tema selecionado."
                } else {
                    "Padrão segue a configuração de aparência do telefone."
                },
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider(modifier = Modifier.padding(top = 16.dp))
            Text("Vibração", style = MaterialTheme.typography.titleMedium, color = getDynamicThemeColor(3, appTheme, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary).first)

            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Vibrar ao clicar no balão")
                Switch(checked = vibrateOnClick, onCheckedChange = { coroutineScope.launch { viewModel.userPreferences.setVibrateOnClick(it) } })
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Vibrar ao achar produto")
                Switch(checked = vibrateOnFound, onCheckedChange = { coroutineScope.launch { viewModel.userPreferences.setVibrateOnFound(it) } })
            }
            
                            }
            }

            SettingsSectionHeader(
                title = "Notificações",
                summary = if (notificationsEnabled) "Ativadas" else "Desativadas",
                expanded = notificationsExpanded,
                onToggle = { notificationsExpanded = !notificationsExpanded }
            )
            if (notificationsExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Notificações Gerais")
                Switch(
                    checked = notificationsEnabled,
                    onCheckedChange = { enabled ->
                        coroutineScope.launch {
                            viewModel.userPreferences.setNotificationsEnabled(enabled)
                            FcmTopicSubscription.reconcile(enabled)
                            val installationId = viewModel.userPreferences.getOrCreateInstallationId()
                            FcmTopicSubscription.reconcileSuggestionTopic(enabled, installationId)
                        }
                    }
                )
            }
            if (notificationsEnabled) {
                Text("Preferências de notificações", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary)
                Row(modifier = Modifier.fillMaxWidth().padding(start = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Código alterado")
                    Switch(checked = notificationsCodeChangedEnabled, onCheckedChange = { coroutineScope.launch { viewModel.userPreferences.setNotificationsCodeChangedEnabled(it) } })
                }
                Row(modifier = Modifier.fillMaxWidth().padding(start = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Produto adicionado")
                    Switch(checked = notificationsProductAddedEnabled, onCheckedChange = { coroutineScope.launch { viewModel.userPreferences.setNotificationsProductAddedEnabled(it) } })
                }
                        }
                }
            }

            val feedbackColors = getDynamicThemeColor(4, appTheme, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
            SettingsSectionHeader(
                title = "Ajuda e feedback",
                summary = "Enviar sugestões e consultar o histórico",
                expanded = feedbackExpanded,
                onToggle = { feedbackExpanded = !feedbackExpanded }
            )
            if (feedbackExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

            Button(
                onClick = { showSuggestionDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = feedbackColors.first,
                    contentColor = feedbackColors.second
                )
            ) {
                Text("Enviar Sugestão de Melhoria")
            }

            userSuggestions.filter { it.status == com.example.data.ProductSuggestion.STATUS_FIXED }.forEach { suggestion ->
                SuggestionResolvedCard(
                    suggestion = suggestion,
                    onClick = { selectedCorrectedSuggestion = suggestion }
                )
            }
            Text(
                "Histórico de sugestões",
                style = MaterialTheme.typography.titleMedium,
                color = feedbackColors.first
            )
            Text(
                "Consulte antes de enviar uma nova sugestão para evitar pedidos repetidos.",
                style = MaterialTheme.typography.bodySmall
            )
            if (publicSuggestions.isEmpty()) {
                Text("Nenhuma sugestão registrada.", style = MaterialTheme.typography.bodyMedium)
            } else {
                publicSuggestions.forEach { suggestion ->
                    PublicSuggestionCard(suggestion = suggestion)
                }
                        }
                }
            }
        }
    }
    selectedCorrectedSuggestion?.let { suggestion ->

        val message = if (suggestion.appVersion.isBlank() || suggestion.appVersion == com.example.BuildConfig.VERSION_NAME) {
            "Atualize o aplicativo pra verificar as correções solicitadas"
        } else {
            "Verifique as correções solicitadas"
        }
        AlertDialog(
            onDismissRequest = { selectedCorrectedSuggestion = null },
            title = { Text("Solução Corrigida") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Sua sugestão:", style = MaterialTheme.typography.labelLarge)
                    Text(suggestion.text, style = MaterialTheme.typography.bodyLarge)
                    Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedCorrectedSuggestion = null }) {
                    Text("Fechar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        suggestionPendingDeletion = suggestion
                        selectedCorrectedSuggestion = null
                    },
                    enabled = !deletingSuggestion
                ) {
                    Text("Excluir sugestão", color = MaterialTheme.colorScheme.error)
                }
            }
        )
    }

    suggestionPendingDeletion?.let { suggestion ->
        AlertDialog(
            onDismissRequest = { if (!deletingSuggestion) suggestionPendingDeletion = null },
            title = { Text("Excluir sugestão?") },
            text = { Text("A sugestão já foi marcada como corrigida. Deseja removê-la da sua caixa de sugestões?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deletingSuggestion = true
                        coroutineScope.launch {
                            val deleted = com.example.data.FirebaseService.deleteSuggestion(suggestion.id, installationId)
                            deletingSuggestion = false
                            suggestionPendingDeletion = null
                            NotificationHelper.showToast(
                                context,
                                if (deleted) "Sugestão excluída." else "Não foi possível excluir a sugestão.",
                                android.widget.Toast.LENGTH_LONG
                            )
                        }
                    },
                    enabled = !deletingSuggestion
                ) { Text(if (deletingSuggestion) "Excluindo..." else "Excluir") }
            },
            dismissButton = {
                TextButton(onClick = { suggestionPendingDeletion = null }, enabled = !deletingSuggestion) { Text("Cancelar") }
            }
        )
    }

    if (showSuggestionDialog) {
        AlertDialog(
            onDismissRequest = { showSuggestionDialog = false },
            title = { Text("Sugestão") },
            text = {
                OutlinedTextField(
                    value = suggestionText,
                    onValueChange = { suggestionText = it },
                    label = { Text("Descreva sua sugestão") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleanSuggestion = suggestionText.trim()
                        if (cleanSuggestion.isBlank()) {
                            NotificationHelper.showToast(context, "Digite uma sugestão antes de enviar.", android.widget.Toast.LENGTH_SHORT)
                            return@Button
                        }
                        suggestionSending = true
                        coroutineScope.launch {
                            val sent = com.example.data.FirebaseService.submitSuggestion(cleanSuggestion, installationId)
                            suggestionSending = false
                            if (sent) {
                                showSuggestionDialog = false
                                suggestionText = ""
                                NotificationHelper.showToast(context, "Sugestão enviada ao painel do Mestre.", android.widget.Toast.LENGTH_LONG)
                            } else {
                                NotificationHelper.showToast(context, "Não foi possível enviar a sugestão. Tente novamente.", android.widget.Toast.LENGTH_LONG)
                            }
                        }
                    },
                    enabled = !suggestionSending && suggestionText.isNotBlank()
                ) {
                    if (suggestionSending) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Enviar")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showSuggestionDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}



@Composable
private fun SettingsSectionHeader(
    title: String,
    summary: String,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Recolher $title" else "Expandir $title"
            )
        }
    }
}

@Composable
private fun SuggestionResolvedCard(
    suggestion: com.example.data.ProductSuggestion,
    onClick: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Solução Corrigida",
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Toque para ver sua sugestão e as orientações.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                "Ver",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
        }
    }
}


@Composable
private fun PublicSuggestionCard(suggestion: com.example.data.ProductSuggestion) {
    val isFixed = suggestion.status == com.example.data.ProductSuggestion.STATUS_FIXED
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(suggestion.text, style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (isFixed) "Corrigido" else "Pendente",
                style = MaterialTheme.typography.labelLarge,
                color = if (isFixed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            if (isFixed) {
                Text(
                    "A correção solicitada foi concluída.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
