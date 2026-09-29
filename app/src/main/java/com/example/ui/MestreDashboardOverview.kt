package com.example.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.GeminiMasterService
import com.example.data.FirebaseService
import com.example.ui.theme.LocalExpressiveStyle
import com.example.ui.theme.LocalGlassSoftStyle
import com.example.ui.theme.glassSoftShadow
import com.example.ui.theme.expressiveShadow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
internal fun MestreDashboardOverview(
    pendingSuggestions: Int,
    productCount: Int,
    activeCategoryCount: Int,
    categoryCount: Int,
    latestBackupAt: Long?,
    importEnabled: Boolean,
    onOpenCatalog: () -> Unit,
    onOpenCategories: () -> Unit,
    onManageTabs: () -> Unit,
    onImportProducts: () -> Unit
) {
    val profile = rememberNrdScreenProfile()
    val expressive = LocalExpressiveStyle.current.enabled
    Text(
        "Visão geral",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = if (expressive) FontWeight.ExtraBold else FontWeight.Normal
    )
    Text(
        "Acompanhe o aplicativo e acesse as tarefas mais usadas.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(8.dp))

    NrdTwoActionLayout(
        stackOnCompact = true,
        first = { m -> DashboardMetricCard("Pendências", pendingSuggestions.toString(), Icons.Default.PendingActions, m) },
        second = { m -> DashboardMetricCard("Produtos", productCount.toString(), Icons.Default.Inventory, m) }
    )
    Spacer(modifier = Modifier.height(6.dp))
    NrdTwoActionLayout(
        stackOnCompact = true,
        first = { m -> DashboardMetricCard("Categorias ativas", "$activeCategoryCount de $categoryCount", Icons.Default.Category, m) },
        second = { m -> DashboardMetricCard("Último backup", latestBackupAt?.let(::formatDashboardDate) ?: "Nenhum", Icons.Default.Backup, m) }
    )

    Spacer(modifier = Modifier.height(14.dp))
    Text("Ações rápidas", style = MaterialTheme.typography.titleMedium)
    Spacer(modifier = Modifier.height(6.dp))
    NrdTwoActionLayout(
        stackOnCompact = true,
        first = { m -> DashboardQuickAction("Produtos", "Gerenciar catálogo", Icons.Default.Inventory, onOpenCatalog, modifier = m) },
        second = { m -> DashboardQuickAction("Categorias", "Organizar grupos", Icons.Default.Category, onOpenCategories, modifier = m) }
    )
    Spacer(modifier = Modifier.height(6.dp))
    NrdTwoActionLayout(
        stackOnCompact = true,
        first = { m -> DashboardQuickAction("Abas", "Organizar conteúdo", Icons.Default.ViewCarousel, onManageTabs, modifier = m) },
        second = { m -> DashboardQuickAction("Importar", if (importEnabled) "CSV ou TSV" else "Aguarde...", Icons.Default.UploadFile, onImportProducts, enabled = importEnabled, modifier = m) }
    )

    Spacer(modifier = Modifier.height(14.dp))
    MestreGeminiConnectionCard()
}

@Composable
private fun MestreGeminiConnectionCard() {
    val coroutineScope = rememberCoroutineScope()
    var isTesting by remember { mutableStateOf(false) }
    var resultText by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    val expressive = LocalExpressiveStyle.current.enabled
    val profile = rememberNrdScreenProfile()
    val geminiShape = if (expressive) RoundedCornerShape(if (profile.compact) 22.dp else 26.dp) else MaterialTheme.shapes.medium
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .glassSoftShadow(geminiShape)
            .expressiveShadow(geminiShape, 6.dp),
        shape = geminiShape
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CloudSync,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Inteligência NRD", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Gemini exclusivo do Mestre. A chave permanece protegida no Supabase.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            FilledTonalButton(
                enabled = !isTesting,
                onClick = {
                    coroutineScope.launch {
                        isTesting = true
                        isError = false
                        resultText = null
                        val result = GeminiMasterService.ping()
                        result.fold(
                            onSuccess = { reply ->
                                resultText = reply.text
                                isError = false
                            },
                            onFailure = { error ->
                                resultText = error.message ?: "Não foi possível testar o Gemini."
                                isError = true
                            }
                        )
                        isTesting = false
                    }
                }
            ) {
                if (isTesting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Testando...")
                } else {
                    Text("Testar Gemini")
                }
            }

            resultText?.let { message ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
internal fun MestrePanelAreaNavigation(
    onOpenCatalog: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAdvanced: () -> Unit
) {
    Text("Áreas do painel", style = MaterialTheme.typography.titleMedium)
    Spacer(modifier = Modifier.height(6.dp))
    PanelAreaCard(
        title = "Conteúdo e catálogo",
        description = "Produtos, categorias, abas e importação",
        icon = Icons.Default.Inventory,
        onClick = onOpenCatalog
    )
    Spacer(modifier = Modifier.height(6.dp))
    PanelAreaCard(
        title = "Configuração do aplicativo",
        description = "Home, aparência e notificações globais",
        icon = Icons.Default.Settings,
        onClick = onOpenSettings
    )
    Spacer(modifier = Modifier.height(6.dp))
    PanelAreaCard(
        title = "Ferramentas avançadas",
        description = "Diagnóstico, sincronização e backups",
        icon = Icons.Default.CloudSync,
        onClick = onOpenAdvanced
    )
}

@Composable
internal fun MestreContentHub(
    importEnabled: Boolean,
    onManageProducts: () -> Unit,
    onAddProduct: () -> Unit,
    onOpenCategories: () -> Unit,
    onManageTabs: () -> Unit,
    onImportProducts: () -> Unit
) {
    Text("Produtos", style = MaterialTheme.typography.titleMedium)
    Text(
        "Edite o catálogo existente ou cadastre um novo item.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(6.dp))
    val contentShape = if (LocalExpressiveStyle.current.enabled) RoundedCornerShape(24.dp) else MaterialTheme.shapes.medium
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .glassSoftShadow(contentShape)
            .expressiveShadow(contentShape, 5.dp),
        shape = contentShape
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Inventory,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Gerenciar produtos", style = MaterialTheme.typography.titleSmall)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilledTonalButton(onClick = onManageProducts, modifier = Modifier.weight(1f)) {
                    Text("Editar")
                }
                OutlinedButton(onClick = onAddProduct, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.AddBox, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Adicionar")
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(12.dp))
    Text("Outras ferramentas", style = MaterialTheme.typography.titleMedium)
    Spacer(modifier = Modifier.height(6.dp))
    PanelAreaCard(
        title = "Categorias",
        description = "Criar, ordenar, renomear ou ocultar grupos",
        icon = Icons.Default.Category,
        onClick = onOpenCategories
    )
    Spacer(modifier = Modifier.height(6.dp))
    PanelAreaCard(
        title = "Abas do aplicativo",
        description = "Criar e organizar conteúdo adicional",
        icon = Icons.Default.ViewCarousel,
        onClick = onManageTabs
    )
    Spacer(modifier = Modifier.height(6.dp))
    PanelAreaCard(
        title = "Importar planilha",
        description = if (importEnabled) "Adicionar produtos por CSV ou TSV" else "Importação em andamento...",
        icon = Icons.Default.UploadFile,
        onClick = onImportProducts,
        enabled = importEnabled
    )
    Spacer(modifier = Modifier.height(6.dp))
    FlyerImportEntryCard()
}

@Composable
internal fun MestreSettingsHub(
    onOpenHome: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenCardAppearance: () -> Unit,
    onOpenBubbles: () -> Unit,
    onOpenConsultationAppearance: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenNovelties: () -> Unit,
    onOpenWorkSchedule: () -> Unit
) {
    Text("Escolha o que deseja configurar", style = MaterialTheme.typography.titleMedium)
    Text(
        "Cada alteração global é publicada separadamente para todos os usuários.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(8.dp))
    PanelAreaCard(
        title = "Tela Home",
        description = "Seções, quantidade de produtos e carrossel",
        icon = Icons.Default.Home,
        onClick = onOpenHome
    )
    Spacer(modifier = Modifier.height(6.dp))
    PanelAreaCard(
        title = "Aparência global",
        description = "Tema, modo visual e fundos programados",
        icon = Icons.Default.Palette,
        onClick = onOpenAppearance
    )
    Spacer(modifier = Modifier.height(6.dp))
    PanelAreaCard(
        title = "Aparência Cartão",
        description = "Fundo do cartão-convênio por tema",
        icon = Icons.Default.CreditCard,
        onClick = onOpenCardAppearance
    )
    Spacer(modifier = Modifier.height(6.dp))
    PanelAreaCard(
        title = "Movimentos das Bolhas",
        description = "Velocidade e trajetos do Glass Expressivo",
        icon = Icons.Default.Waves,
        onClick = onOpenBubbles
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
        description = "Políticas aplicadas aos aparelhos dos usuários",
        icon = Icons.Default.Notifications,
        onClick = onOpenNotifications
    )
    Spacer(modifier = Modifier.height(6.dp))
    PanelAreaCard(
        title = "Inserir Novidade",
        description = "Crie avisos visuais por versão do aplicativo",
        icon = Icons.Default.NewReleases,
        onClick = onOpenNovelties
    )
    Spacer(modifier = Modifier.height(6.dp))
    PanelAreaCard(
        title = "Inserir Escala",
        description = "Ler foto, conferir matrículas na Nossa Gente e publicar folgas",
        icon = Icons.Default.CalendarMonth,
        onClick = onOpenWorkSchedule
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MestreNoveltySettings() {
    val coroutineScope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }
    val savedNovelties by FirebaseService.observeNovelties().collectAsState(initial = emptyList())
    var saveMessage by remember { mutableStateOf<String?>(null) }
    var noveltyText by rememberSaveable { mutableStateOf("Tema Novo: Expressivo disponível") }
    var target by rememberSaveable { mutableStateOf("all") }
    var targetVersion by rememberSaveable { mutableStateOf(com.example.BuildConfig.VERSION_NAME) }
    var model by rememberSaveable { mutableStateOf("ribbon") }
    var color by rememberSaveable { mutableStateOf("red") }
    var size by rememberSaveable { mutableStateOf("medium") }
    var location by rememberSaveable { mutableStateOf("menu") }
    var startDate by rememberSaveable { mutableStateOf("") }
    var endDate by rememberSaveable { mutableStateOf("") }
    var enabled by rememberSaveable { mutableStateOf(true) }
    var editingNoveltyId by rememberSaveable { mutableStateOf<String?>(null) }
    var savedSectionExpanded by rememberSaveable { mutableStateOf(false) }
    var formExpanded by rememberSaveable { mutableStateOf(false) }
    var appearanceExpanded by rememberSaveable { mutableStateOf(false) }
    var expandedNoveltyId by rememberSaveable { mutableStateOf<String?>(null) }
    var showPreview by remember { mutableStateOf(false) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    fun formatPickerDate(value: Long?): String =
        value?.let {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }.format(Date(it))
        }.orEmpty()

    fun parsePickerDate(value: String): Long? =
        value.takeIf(String::isNotBlank)?.let {
            runCatching {
                SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }.parse(it)?.time
            }.getOrNull()
        }

    fun audienceSummary(audience: String, version: String): String = when (audience) {
        "new" -> "Somente versão " + version
        "previous" -> "Versões anteriores à " + version
        else -> "Todas as versões"
    }

    fun resetForm() {
        editingNoveltyId = null
        noveltyText = "Tema Novo: Expressivo disponível"
        target = "all"
        targetVersion = com.example.BuildConfig.VERSION_NAME
        model = "ribbon"
        color = "red"
        size = "medium"
        location = "menu"
        startDate = ""
        endDate = ""
        enabled = true
        appearanceExpanded = false
        saveMessage = null
    }

    Text("Avisos do aplicativo", style = MaterialTheme.typography.titleMedium)
    Text(
        "Edite avisos salvos ou crie um novo. Eles aparecem no menu do app; esta tela não envia notificação push.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.height(12.dp))
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Avisos salvos (" + savedNovelties.size + ")", style = MaterialTheme.typography.titleSmall)
                    Text(
                        savedNovelties.count { it.enabled }.toString() + " registros marcados como ativos • toque em um aviso para abrir os detalhes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { savedSectionExpanded = !savedSectionExpanded }) {
                    Text(if (savedSectionExpanded) "Recolher ▴" else "Abrir ▾")
                }
            }
            if (savedSectionExpanded) {
                Spacer(modifier = Modifier.height(8.dp))
                if (savedNovelties.isEmpty()) {
                    Text("Ainda não há avisos salvos.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    savedNovelties.forEach { novelty ->
                        val itemExpanded = expandedNoveltyId == novelty.id
                        OutlinedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expandedNoveltyId = if (itemExpanded) null else novelty.id }
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(novelty.text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            (if (novelty.enabled) "Ativo" else "Desativado") + " • " + audienceSummary(novelty.target, novelty.version),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (novelty.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(if (itemExpanded) "▴" else "▾", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                }
                                if (itemExpanded) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    val dateRange = when {
                                        novelty.startDate.isBlank() && novelty.endDate.isBlank() -> "Sem período definido"
                                        else -> novelty.startDate.ifBlank { "sem início" } + " a " + novelty.endDate.ifBlank { "sem fim" }
                                    }
                                    Text(
                                        "Local: menu lateral • Período: " + dateRange,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (novelty.createdAt > 0L) {
                                        Text(
                                            "Salvo em " + SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(Date(novelty.createdAt)),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedButton(
                                        onClick = {
                                            editingNoveltyId = novelty.id
                                            noveltyText = novelty.text
                                            target = novelty.target.ifBlank { "all" }
                                            targetVersion = novelty.version.ifBlank { com.example.BuildConfig.VERSION_NAME }
                                            model = novelty.model
                                            color = novelty.color
                                            size = novelty.size
                                            location = novelty.location
                                            startDate = novelty.startDate
                                            endDate = novelty.endDate
                                            enabled = novelty.enabled
                                            appearanceExpanded = true
                                            formExpanded = true
                                            saveMessage = null
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Editar este aviso")
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(10.dp))
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (editingNoveltyId == null) "Criar novidade" else "Editar aviso salvo",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        if (editingNoveltyId == null) "Preencha o aviso e escolha quem poderá vê-lo."
                        else "Ao salvar, este aviso se torna a configuração atual do aplicativo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { formExpanded = !formExpanded }) {
                    Text(if (formExpanded) "Recolher ▴" else "Abrir ▾")
                }
            }
            if (formExpanded) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(onClick = { showPreview = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Pré-visualizar aviso")
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text("Texto do aviso", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = noveltyText,
                    onValueChange = { noveltyText = it },
                    label = { Text("Texto exibido na fita") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))
                Text("Quem verá este aviso?", style = MaterialTheme.typography.titleSmall)
                val audiences = listOf(
                    Triple("all", "Todos os usuários", "Qualquer versão do aplicativo."),
                    Triple("new", "Somente a versão nova", "A versão instalada precisa ser igual à indicada."),
                    Triple("previous", "Versões anteriores", "A versão instalada precisa ser menor que a indicada.")
                )
                audiences.forEach { (key, label, description) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { target = key }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = target == key, onClick = { target = key })
                        Column(modifier = Modifier.padding(start = 4.dp)) {
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (target != "all") {
                    OutlinedTextField(
                        value = targetVersion,
                        onValueChange = { targetVersion = it },
                        label = { Text("Versão de referência") },
                        supportingText = {
                            Text(
                                if (target == "new") "Exibe somente na versão " + targetVersion + "."
                                else "Exibe em versões inferiores a " + targetVersion + "."
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Text(
                    "Este filtro controla a fita no menu. Não envia push; versões que não têm o recurso de avisos não conseguem exibi-la.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = { appearanceExpanded = !appearanceExpanded }) {
                    Text(if (appearanceExpanded) "Aparência e período ▴" else "Aparência e período ▾")
                }
                if (appearanceExpanded) {
                    Text("Modelo", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        FilterChip(selected = model == "ribbon", onClick = { model = "ribbon" }, label = { Text("Fita") })
                        FilterChip(selected = model == "tag", onClick = { model = "tag" }, label = { Text("Etiqueta") })
                    }
                    Text("Cor", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf("red" to "Vermelha", "blue" to "Azul", "green" to "Verde").forEach { (key, label) ->
                            FilterChip(selected = color == key, onClick = { color = key }, label = { Text(label) })
                        }
                    }
                    Text("Tamanho", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf("small" to "P", "medium" to "M", "large" to "G").forEach { (key, label) ->
                            FilterChip(selected = size == key, onClick = { size = key }, label = { Text(label) })
                        }
                    }
                    Text("Local de exibição: menu lateral", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = { showStartPicker = true }, modifier = Modifier.weight(1f)) {
                            Text(if (startDate.isBlank()) "Escolher início" else startDate)
                        }
                        Button(onClick = { showEndPicker = true }, modifier = Modifier.weight(1f)) {
                            Text(if (endDate.isBlank()) "Escolher fim" else endDate)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Aviso ativo", modifier = Modifier.weight(1f))
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        val currentEditingId = editingNoveltyId
                        coroutineScope.launch {
                            isSaving = true
                            saveMessage = null
                            val saved = FirebaseService.saveNoveltySettings(
                                text = noveltyText,
                                enabled = enabled,
                                target = target,
                                version = targetVersion,
                                model = model,
                                color = color,
                                size = size,
                                location = location,
                                startDate = startDate,
                                endDate = endDate,
                                documentId = currentEditingId
                            )
                            if (saved) {
                                saveMessage = when {
                                    currentEditingId != null -> "Aviso atualizado."
                                    enabled -> "Aviso salvo e publicado."
                                    else -> "Aviso salvo e desativado."
                                }
                                editingNoveltyId = null
                                noveltyText = "Tema Novo: Expressivo disponível"
                                target = "all"
                                targetVersion = com.example.BuildConfig.VERSION_NAME
                                model = "ribbon"
                                color = "red"
                                size = "medium"
                                location = "menu"
                                startDate = ""
                                endDate = ""
                                enabled = true
                                appearanceExpanded = false
                                expandedNoveltyId = currentEditingId
                                savedSectionExpanded = true
                                formExpanded = false
                            } else {
                                saveMessage = FirebaseService.lastError ?: "Não foi possível salvar o aviso."
                            }
                            isSaving = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSaving && noveltyText.isNotBlank() &&
                        (target == "all" || targetVersion.isNotBlank())
                ) {
                    Text(
                        when {
                            isSaving -> "Salvando..."
                            editingNoveltyId != null -> "Salvar alterações"
                            enabled -> "Salvar e publicar aviso"
                            else -> "Salvar aviso desativado"
                        }
                    )
                }
                if (editingNoveltyId != null) {
                    TextButton(
                        onClick = {
                            resetForm()
                            formExpanded = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cancelar edição")
                    }
                }
            }
            saveMessage?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    it,
                    color = if (it.startsWith("Aviso ")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
        }
    }

    if (showStartPicker) {
        val startPicker = rememberDatePickerState(initialSelectedDateMillis = parsePickerDate(startDate))
        DatePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    startDate = formatPickerDate(startPicker.selectedDateMillis)
                    showStartPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showStartPicker = false }) { Text("Cancelar") } }
        ) { DatePicker(state = startPicker) }
    }
    if (showEndPicker) {
        val endPicker = rememberDatePickerState(initialSelectedDateMillis = parsePickerDate(endDate))
        DatePickerDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    endDate = formatPickerDate(endPicker.selectedDateMillis)
                    showEndPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showEndPicker = false }) { Text("Cancelar") } }
        ) { DatePicker(state = endPicker) }
    }
    if (showPreview) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showPreview = false },
            confirmButton = { TextButton(onClick = { showPreview = false }) { Text("Fechar") } },
            title = { Text("Prévia do aviso") },
            text = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = when (color) {
                                "blue" -> Color(0xFF1565C0)
                                "green" -> Color(0xFF2E7D32)
                                else -> Color(0xFFD91C1C)
                            },
                            shape = if (model == "tag") RoundedCornerShape(10.dp)
                            else RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp, topEnd = 2.dp, bottomEnd = 2.dp)
                        ) {
                            Text(noveltyText, color = Color.White, modifier = Modifier.padding(10.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Público: " + audienceSummary(target, targetVersion))
                    Text(if (enabled) "Estado: ativo" else "Estado: desativado")
                }
            }
        )
    }
}

@Composable
private fun DashboardMetricCard(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    val expressive = LocalExpressiveStyle.current.enabled
    val profile = rememberNrdScreenProfile()
    val shape = if (expressive) RoundedCornerShape(if (profile.compact) 20.dp else 24.dp) else MaterialTheme.shapes.medium
    ElevatedCard(
        modifier = modifier
            .heightIn(min = if (expressive && profile.compact) 76.dp else 84.dp)
            .glassSoftShadow(shape)
            .expressiveShadow(shape, 6.dp),
        shape = shape
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DashboardQuickAction(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val expressive = LocalExpressiveStyle.current.enabled
    val profile = rememberNrdScreenProfile()
    val shape = if (expressive) RoundedCornerShape(if (profile.compact) 20.dp else 24.dp) else MaterialTheme.shapes.medium
    OutlinedCard(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .heightIn(min = if (expressive && profile.compact) 72.dp else 78.dp)
            .glassSoftShadow(shape)
            .expressiveShadow(shape, 5.dp),
        shape = shape
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PanelAreaCard(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    val expressive = LocalExpressiveStyle.current.enabled
    val profile = rememberNrdScreenProfile()
    val shape = if (expressive) RoundedCornerShape(if (profile.compact) 20.dp else 24.dp) else MaterialTheme.shapes.medium
    OutlinedCard(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .glassSoftShadow(shape)
            .expressiveShadow(shape, 5.dp),
        shape = shape
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatDashboardDate(timestamp: Long): String =
    SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR")).format(Date(timestamp))
