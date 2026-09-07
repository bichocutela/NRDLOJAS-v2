package com.example.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.data.DynamicMediaUploader
import com.example.data.DynamicPageBlock
import com.example.data.DynamicPageCodec
import com.example.data.DynamicPageDocument
import com.example.data.DynamicTab
import com.example.ui.theme.glassSoftShadow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageTabsScreen(viewModel: MainViewModel, onNavigateBack: () -> Unit) {
    val tabs by viewModel.dynamicTabs.collectAsState()
    val isSyncingTabs by viewModel.isSyncingTabs.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showEditor by remember { mutableStateOf(false) }
    var editingTab by remember { mutableStateOf<DynamicTab?>(null) }
    var tabToDelete by remember { mutableStateOf<DynamicTab?>(null) }

    var title by remember { mutableStateOf("") }
    var enabled by remember { mutableStateOf(true) }
    var startAt by remember { mutableStateOf<Long?>(null) }
    var endAt by remember { mutableStateOf<Long?>(null) }
    var mode by remember { mutableStateOf(DynamicPageDocument.MODE_PAGE) }
    var blocks by remember { mutableStateOf<List<DynamicPageBlock>>(emptyList()) }

    var showBlockDialog by remember { mutableStateOf(false) }
    var editingBlockIndex by remember { mutableIntStateOf(-1) }
    var blockType by remember { mutableStateOf(DynamicPageBlock.TYPE_TEXT) }
    var blockValue by remember { mutableStateOf("") }
    var isUploading by remember { mutableStateOf(false) }

    var pickingStartDate by remember { mutableStateOf(false) }
    var pickingEndDate by remember { mutableStateOf(false) }

    fun resetEditor() {
        title = ""
        enabled = true
        startAt = null
        endAt = null
        mode = DynamicPageDocument.MODE_PAGE
        blocks = emptyList()
        editingTab = null
    }

    fun openNewEditor() {
        resetEditor()
        showEditor = true
    }

    fun openEditEditor(tab: DynamicTab) {
        editingTab = tab
        title = tab.title
        val document = DynamicPageCodec.decodeDocument(tab.content)
        enabled = document?.enabled ?: true
        startAt = document?.startAt
        endAt = document?.endAt
        mode = document?.mode ?: DynamicPageDocument.MODE_PAGE
        blocks = DynamicPageCodec.blocksFor(tab)
        showEditor = true
    }

    fun openNewBlock(type: String, initialValue: String = "") {
        editingBlockIndex = -1
        blockType = type
        blockValue = initialValue
        showBlockDialog = true
    }

    fun openEditBlock(index: Int) {
        val block = blocks.getOrNull(index) ?: return
        editingBlockIndex = index
        blockType = block.type
        blockValue = block.value
        showBlockDialog = true
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            isUploading = true
            val result = DynamicMediaUploader.upload(context, uri)
            result.onSuccess { uploadedUrl ->
                blockValue = uploadedUrl
                snackbarHostState.showSnackbar("Arquivo enviado com sucesso.")
            }.onFailure { error ->
                snackbarHostState.showSnackbar(error.message ?: "Não foi possível enviar o arquivo.")
            }
            isUploading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Páginas e Cursos") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (!showEditor) {
                FloatingActionButton(
                    onClick = ::openNewEditor,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Criar página")
                }
            }
        }
    ) { innerPadding ->
        if (!showEditor) {
            if (tabs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                        .padding(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.MenuBook,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("Nenhuma página ou curso criado", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Crie conteúdo com texto, imagem, vídeo, áudio e PDF para aparecer no menu dos usuários.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = ::openNewEditor) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Criar primeira página")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        tabs.sortedWith(compareBy<DynamicTab> { it.displayOrder }.thenBy { it.id }),
                        key = { it.id }
                    ) { tab ->
                        val document = DynamicPageCodec.decodeDocument(tab.content)
                        val tabBlocks = DynamicPageCodec.blocksFor(tab)
                        val isVisibleNow = DynamicPageCodec.isVisible(tab)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .glassSoftShadow(MaterialTheme.shapes.medium)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(tab.title, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        buildString {
                                            append(if (document?.mode == DynamicPageDocument.MODE_COURSE) "Curso" else "Página")
                                            append(" • ${tabBlocks.size} conteúdo(s)")
                                            append(if (isVisibleNow) " • Visível" else " • Oculto/Agendado")
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isVisibleNow) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.tertiary
                                    )
                                    if (document?.startAt != null || document?.endAt != null) {
                                        Text(
                                            scheduleLabel(document?.startAt, document?.endAt),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                val ordered = tabs.sortedWith(compareBy<DynamicTab> { it.displayOrder }.thenBy { it.id })
                                val index = ordered.indexOfFirst { it.id == tab.id }
                                IconButton(
                                    onClick = { viewModel.moveTab(tab, -1) },
                                    enabled = !isSyncingTabs && index > 0
                                ) {
                                    Icon(Icons.Default.ArrowUpward, contentDescription = "Mover para cima")
                                }
                                IconButton(
                                    onClick = { viewModel.moveTab(tab, 1) },
                                    enabled = !isSyncingTabs && index >= 0 && index < ordered.lastIndex
                                ) {
                                    Icon(Icons.Default.ArrowDownward, contentDescription = "Mover para baixo")
                                }
                                IconButton(onClick = { openEditEditor(tab) }, enabled = !isSyncingTabs) {
                                    Icon(Icons.Default.Edit, contentDescription = "Editar")
                                }
                                IconButton(onClick = { tabToDelete = tab }, enabled = !isSyncingTabs) {
                                    Icon(Icons.Default.Delete, contentDescription = "Excluir")
                                }
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { showEditor = false }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar para lista")
                        }
                        Text(
                            if (editingTab == null) "Nova página/curso" else "Editar página/curso",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }

                item {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Título") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Formato", style = MaterialTheme.typography.titleSmall)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = mode == DynamicPageDocument.MODE_PAGE,
                                    onClick = { mode = DynamicPageDocument.MODE_PAGE }
                                )
                                Text("Página")
                                Spacer(Modifier.width(14.dp))
                                RadioButton(
                                    selected = mode == DynamicPageDocument.MODE_COURSE,
                                    onClick = { mode = DynamicPageDocument.MODE_COURSE }
                                )
                                Text("Curso")
                            }
                            Text(
                                if (mode == DynamicPageDocument.MODE_COURSE)
                                    "Modo curso: organiza o material como treinamento em módulos e aulas, com progresso local no aparelho e sem login ou matrícula."
                                else
                                    "Modo página: ideal para avisos, orientações e materiais rápidos.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Disponível para usuários", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "Desative para esconder imediatamente sem excluir.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(checked = enabled, onCheckedChange = { enabled = it })
                            }
                            HorizontalDivider()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(onClick = { pickingStartDate = true }, modifier = Modifier.weight(1f)) {
                                    Text(startAt?.let(::formatDate) ?: "Data inicial")
                                }
                                OutlinedButton(onClick = { pickingEndDate = true }, modifier = Modifier.weight(1f)) {
                                    Text(endAt?.let(::formatDate) ?: "Data final")
                                }
                            }
                            if (startAt != null || endAt != null) {
                                TextButton(onClick = { startAt = null; endAt = null }) {
                                    Text("Remover agendamento")
                                }
                            }
                        }
                    }
                }

                if (mode == DynamicPageDocument.MODE_COURSE) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.School, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Como montar o curso", style = MaterialTheme.typography.titleSmall)
                                }
                                Text(
                                    "Use “Novo módulo” para separar assuntos. Em “Nova aula”, a primeira linha é o título e as linhas seguintes são a descrição. Imagens, vídeos, áudios e PDFs adicionados logo depois ficam dentro daquela aula até você criar a próxima aula ou módulo.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }

                item {
                    Text(
                        if (mode == DynamicPageDocument.MODE_COURSE) "Estrutura do curso" else "Conteúdo",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                if (blocks.isEmpty()) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                if (mode == DynamicPageDocument.MODE_COURSE)
                                    "Comece criando um módulo ou uma aula. Depois adicione texto, imagem, vídeo, áudio e PDF na ordem em que o usuário deve estudar."
                                else
                                    "Adicione pelo menos um bloco. Você pode misturar texto, imagem, vídeo, áudio e PDF na mesma página.",
                                modifier = Modifier.padding(14.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                } else {
                    items(blocks.size, key = { blocks[it].id }) { index ->
                        val block = blocks[index]
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(blockIconForMode(block, mode), contentDescription = null)
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(blockLabelForMode(block, mode), style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        if (block.type == DynamicPageBlock.TYPE_TEXT)
                                            block.value.take(90)
                                        else
                                            block.value.substringAfterLast('/').take(90),
                                        maxLines = 2,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        if (index > 0) {
                                            val mutable = blocks.toMutableList()
                                            val item = mutable.removeAt(index)
                                            mutable.add(index - 1, item)
                                            blocks = mutable
                                        }
                                    },
                                    enabled = index > 0
                                ) { Icon(Icons.Default.ArrowUpward, contentDescription = "Mover conteúdo para cima") }
                                IconButton(
                                    onClick = {
                                        if (index < blocks.lastIndex) {
                                            val mutable = blocks.toMutableList()
                                            val item = mutable.removeAt(index)
                                            mutable.add(index + 1, item)
                                            blocks = mutable
                                        }
                                    },
                                    enabled = index < blocks.lastIndex
                                ) { Icon(Icons.Default.ArrowDownward, contentDescription = "Mover conteúdo para baixo") }
                                IconButton(onClick = { openEditBlock(index) }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Editar conteúdo")
                                }
                                IconButton(onClick = { blocks = blocks.filterIndexed { i, _ -> i != index } }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Excluir conteúdo")
                                }
                            }
                        }
                    }
                }

                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Adicionar conteúdo", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(8.dp))
                            if (mode == DynamicPageDocument.MODE_COURSE) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    ContentTypeButton("Novo módulo", Icons.Default.ViewModule, Modifier.weight(1f)) {
                                        openNewBlock(DynamicPageBlock.TYPE_TEXT, "Módulo: ")
                                    }
                                    ContentTypeButton("Nova aula", Icons.Default.School, Modifier.weight(1f)) {
                                        openNewBlock(DynamicPageBlock.TYPE_TEXT, "Título da aula\n")
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                ContentTypeButton("Texto", Icons.Default.TextFields, Modifier.weight(1f)) { openNewBlock(DynamicPageBlock.TYPE_TEXT) }
                                ContentTypeButton("Imagem", Icons.Default.Image, Modifier.weight(1f)) { openNewBlock(DynamicPageBlock.TYPE_IMAGE) }
                                ContentTypeButton("Vídeo", Icons.Default.PlayCircle, Modifier.weight(1f)) { openNewBlock(DynamicPageBlock.TYPE_VIDEO) }
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                ContentTypeButton("Áudio", Icons.Default.Headphones, Modifier.weight(1f)) { openNewBlock(DynamicPageBlock.TYPE_AUDIO) }
                                ContentTypeButton("PDF", Icons.Default.PictureAsPdf, Modifier.weight(1f)) { openNewBlock(DynamicPageBlock.TYPE_PDF) }
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }

                item {
                    Button(
                        onClick = {
                            if (title.isBlank()) {
                                scope.launch { snackbarHostState.showSnackbar("Informe o título da página.") }
                                return@Button
                            }
                            if (blocks.isEmpty()) {
                                scope.launch { snackbarHostState.showSnackbar("Adicione pelo menos um conteúdo.") }
                                return@Button
                            }
                            if (mode == DynamicPageDocument.MODE_COURSE && blocks.all(::isCourseModuleMarker)) {
                                scope.launch { snackbarHostState.showSnackbar("Adicione pelo menos uma aula ao curso.") }
                                return@Button
                            }
                            if (startAt != null && endAt != null && endAt!! < startAt!!) {
                                scope.launch { snackbarHostState.showSnackbar("A data final não pode ser anterior à inicial.") }
                                return@Button
                            }

                            val encoded = DynamicPageCodec.encode(
                                blocks = blocks,
                                enabled = enabled,
                                startAt = startAt,
                                endAt = endAt,
                                mode = mode
                            )
                            val current = editingTab
                            if (current == null) {
                                val order = (tabs.maxOfOrNull { it.displayOrder } ?: -1) + 1
                                viewModel.insertTab(
                                    DynamicTab(
                                        title = title.trim(),
                                        type = "text",
                                        content = encoded,
                                        displayOrder = order
                                    )
                                )
                            } else {
                                viewModel.updateTab(
                                    current.copy(
                                        title = title.trim(),
                                        type = "text",
                                        content = encoded
                                    )
                                )
                            }
                            showEditor = false
                        },
                        enabled = !isSyncingTabs && !isUploading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Text(
                            if (isSyncingTabs) "Salvando..."
                            else if (mode == DynamicPageDocument.MODE_COURSE) "Salvar curso"
                            else "Salvar página"
                        )
                    }
                }
            }
        }

        if (showBlockDialog) {
            AlertDialog(
                onDismissRequest = { if (!isUploading) showBlockDialog = false },
                title = {
                    Text(
                        if (editingBlockIndex >= 0)
                            "Editar ${blockLabelForDialog(blockType, blockValue, mode)}"
                        else
                            "Adicionar ${blockLabelForDialog(blockType, blockValue, mode)}"
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (blockType == DynamicPageBlock.TYPE_TEXT) {
                            OutlinedTextField(
                                value = blockValue,
                                onValueChange = { blockValue = it },
                                label = {
                                    Text(
                                        when {
                                            mode == DynamicPageDocument.MODE_COURSE && isModuleMarkerText(blockValue) -> "Nome do módulo"
                                            mode == DynamicPageDocument.MODE_COURSE && blockValue.startsWith("Título da aula") -> "Título e descrição da aula"
                                            else -> "Texto"
                                        }
                                    )
                                },
                                minLines = 5,
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (mode == DynamicPageDocument.MODE_COURSE) {
                                Text(
                                    "Em uma aula, use a primeira linha como título e as linhas seguintes como descrição. Para módulo, mantenha “Módulo:” no início.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            OutlinedTextField(
                                value = blockValue,
                                onValueChange = { blockValue = it },
                                label = { Text("URL ou link do Drive") },
                                minLines = 2,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedButton(
                                onClick = { filePicker.launch(mimeTypesFor(blockType)) },
                                enabled = !isUploading,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (isUploading) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Enviando...")
                                } else {
                                    Icon(Icons.Default.UploadFile, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Selecionar do aparelho")
                                }
                            }
                            Text(
                                "Você também pode colar uma URL pública. Arquivos enviados pelo aparelho ficam armazenados no Supabase.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (blockValue.isBlank()) return@TextButton
                            val newBlock = if (editingBlockIndex >= 0) {
                                blocks[editingBlockIndex].copy(type = blockType, value = blockValue.trim())
                            } else {
                                DynamicPageBlock(type = blockType, value = blockValue.trim(), displayOrder = blocks.size)
                            }
                            blocks = if (editingBlockIndex >= 0) {
                                blocks.toMutableList().also { it[editingBlockIndex] = newBlock }
                            } else {
                                blocks + newBlock
                            }
                            showBlockDialog = false
                        },
                        enabled = blockValue.isNotBlank() && !isUploading
                    ) { Text(if (editingBlockIndex >= 0) "Salvar" else "Adicionar") }
                },
                dismissButton = {
                    TextButton(onClick = { showBlockDialog = false }, enabled = !isUploading) { Text("Cancelar") }
                }
            )
        }

        tabToDelete?.let { selectedTab ->
            AlertDialog(
                onDismissRequest = { if (!isSyncingTabs) tabToDelete = null },
                title = { Text("Excluir página?") },
                text = { Text("\"${selectedTab.title}\" será removida para todos os usuários.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            tabToDelete = null
                            viewModel.deleteTab(selectedTab)
                        },
                        enabled = !isSyncingTabs
                    ) { Text("Excluir", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { tabToDelete = null }, enabled = !isSyncingTabs) { Text("Cancelar") }
                }
            )
        }

        if (pickingStartDate) {
            PageDatePickerDialog(
                initialDate = startAt,
                onDismiss = { pickingStartDate = false },
                onSelected = {
                    startAt = it
                    pickingStartDate = false
                }
            )
        }

        if (pickingEndDate) {
            PageDatePickerDialog(
                initialDate = endAt,
                onDismiss = { pickingEndDate = false },
                onSelected = {
                    endAt = it?.plus(86_399_999L)
                    pickingEndDate = false
                }
            )
        }
    }
}

@Composable
private fun ContentTypeButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    OutlinedButton(onClick = onClick, modifier = modifier) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, maxLines = 1)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PageDatePickerDialog(
    initialDate: Long?,
    onDismiss: () -> Unit,
    onSelected: (Long?) -> Unit
) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initialDate)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onSelected(state.selectedDateMillis) }) { Text("Confirmar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    ) {
        DatePicker(state = state)
    }
}

private fun blockLabel(type: String): String = when (type) {
    DynamicPageBlock.TYPE_IMAGE -> "Imagem"
    DynamicPageBlock.TYPE_VIDEO -> "Vídeo"
    DynamicPageBlock.TYPE_AUDIO -> "Áudio"
    DynamicPageBlock.TYPE_PDF -> "PDF"
    else -> "Texto"
}

private fun blockIcon(type: String) = when (type) {
    DynamicPageBlock.TYPE_IMAGE -> Icons.Default.Image
    DynamicPageBlock.TYPE_VIDEO -> Icons.Default.PlayCircle
    DynamicPageBlock.TYPE_AUDIO -> Icons.Default.Headphones
    DynamicPageBlock.TYPE_PDF -> Icons.Default.PictureAsPdf
    else -> Icons.Default.TextFields
}

private fun blockLabelForMode(block: DynamicPageBlock, mode: String): String {
    if (mode != DynamicPageDocument.MODE_COURSE || block.type != DynamicPageBlock.TYPE_TEXT) {
        return blockLabel(block.type)
    }
    return if (isCourseModuleMarker(block)) "Módulo" else "Aula / Texto"
}

private fun blockIconForMode(block: DynamicPageBlock, mode: String) = when {
    mode == DynamicPageDocument.MODE_COURSE && isCourseModuleMarker(block) -> Icons.Default.ViewModule
    mode == DynamicPageDocument.MODE_COURSE && block.type == DynamicPageBlock.TYPE_TEXT -> Icons.Default.School
    else -> blockIcon(block.type)
}

private fun blockLabelForDialog(type: String, value: String, mode: String): String {
    if (mode == DynamicPageDocument.MODE_COURSE && type == DynamicPageBlock.TYPE_TEXT) {
        return if (isModuleMarkerText(value)) "módulo" else "aula/texto"
    }
    return blockLabel(type).lowercase(Locale("pt", "BR"))
}

private fun isCourseModuleMarker(block: DynamicPageBlock): Boolean =
    block.type == DynamicPageBlock.TYPE_TEXT && isModuleMarkerText(block.value)

private fun isModuleMarkerText(value: String): Boolean {
    val firstLine = value.lineSequence().firstOrNull()?.trim().orEmpty()
    return firstLine.startsWith("módulo:", ignoreCase = true) ||
        firstLine.startsWith("modulo:", ignoreCase = true) ||
        firstLine.startsWith("módulo ", ignoreCase = true) ||
        firstLine.startsWith("modulo ", ignoreCase = true)
}

private fun mimeTypesFor(type: String): Array<String> = when (type) {
    DynamicPageBlock.TYPE_IMAGE -> arrayOf("image/jpeg", "image/png", "image/webp")
    DynamicPageBlock.TYPE_VIDEO -> arrayOf("video/mp4", "video/webm", "video/quicktime")
    DynamicPageBlock.TYPE_AUDIO -> arrayOf("audio/mpeg", "audio/mp4", "audio/aac", "audio/ogg", "audio/wav", "audio/x-wav")
    DynamicPageBlock.TYPE_PDF -> arrayOf("application/pdf")
    else -> arrayOf("*/*")
}

private fun formatDate(timestamp: Long): String =
    SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(Date(timestamp))

private fun scheduleLabel(startAt: Long?, endAt: Long?): String = when {
    startAt != null && endAt != null -> "${formatDate(startAt)} até ${formatDate(endAt)}"
    startAt != null -> "A partir de ${formatDate(startAt)}"
    endAt != null -> "Até ${formatDate(endAt)}"
    else -> "Sem agendamento"
}
