package com.example.ui

import com.example.util.NotificationHelper
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
    val boldOutline by viewModel.userPreferences.boldOutline.collectAsState(initial = false)
    val uppercaseBold by viewModel.userPreferences.uppercaseBold.collectAsState(initial = false)
    
    val notificationsEnabled by viewModel.userPreferences.notificationsEnabled.collectAsState(initial = true)
    val notificationsProductAddedEnabled by viewModel.userPreferences.notificationsProductAddedEnabled.collectAsState(initial = true)
    val notificationsCodeChangedEnabled by viewModel.userPreferences.notificationsCodeChangedEnabled.collectAsState(initial = true)


    var showSuggestionDialog by remember { mutableStateOf(false) }
    var suggestionText by remember { mutableStateOf("") }

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
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
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
            
            val appTheme by viewModel.userPreferences.appTheme.collectAsState(initial = "multicolor")
            Text("Preferências de Interface", style = MaterialTheme.typography.titleMedium, color = getDynamicThemeColor(0, appTheme, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary).first)

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
            
            HorizontalDivider()
            
            
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
            
            HorizontalDivider()

            Text("Vibração", style = MaterialTheme.typography.titleMedium, color = getDynamicThemeColor(2, appTheme, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary).first)
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Vibrar ao clicar no balão")
                Switch(checked = vibrateOnClick, onCheckedChange = { coroutineScope.launch { viewModel.userPreferences.setVibrateOnClick(it) } })
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Vibrar ao achar produto")
                Switch(checked = vibrateOnFound, onCheckedChange = { coroutineScope.launch { viewModel.userPreferences.setVibrateOnFound(it) } })
            }
            
            HorizontalDivider()
            
            
            Text("Notificações", style = MaterialTheme.typography.titleMedium, color = getDynamicThemeColor(3, appTheme, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary).first)
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Notificações Gerais")
                Switch(checked = notificationsEnabled, onCheckedChange = { coroutineScope.launch { viewModel.userPreferences.setNotificationsEnabled(it) } })
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
            
            HorizontalDivider()

            
            Text("Feedback", style = MaterialTheme.typography.titleMedium, color = getDynamicThemeColor(4, appTheme, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary).first)
            
            Button(
                onClick = { showSuggestionDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Enviar Sugestão de Melhoria")
            }
        }
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
                Button(onClick = { 
                    showSuggestionDialog = false
                    suggestionText = ""
                    NotificationHelper.showToast(context, "Agradecemos pela sua sugestão! Ela será visível para o ADM.", android.widget.Toast.LENGTH_LONG)
                }) {
                    Text("Enviar")
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
