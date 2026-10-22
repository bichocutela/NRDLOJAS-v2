package com.example.ui.training

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.data.DynamicMediaUploader
import com.example.data.training.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingAdminScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf<TrainingConfig?>(null) }
    var saved by remember { mutableStateOf<TrainingConfig?>(null) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    val dirty = draft != saved
    fun exit() { if (busy) return; if (dirty) confirmExit = true else onBack() }
    BackHandler { if (preview) preview = false else exit() }
    LaunchedEffect(Unit) {
        try {
            val current = TrainingRepository.load() ?: TrainingRepository.defaults(context)
            draft = current; saved = current
        } catch (_: Exception) { message = "Não foi possível carregar. Volte e tente novamente antes de editar." }
        finally { loading = false }
    }
    val config = draft
    if (preview && config != null) { TrainingScreen(config, { preview = false }, preview = true); return }
    Scaffold(topBar = { TopAppBar(title = { Text("Aba Treinamento") }, navigationIcon = { IconButton(onClick = ::exit) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (loading) CircularProgressIndicator()
            if (config != null) {
                Text("Visibilidade", style = MaterialTheme.typography.titleLarge)
                TrainingVisibility.entries.forEach { visibility ->
                    Row { RadioButton(selected = config.visibility == visibility, enabled = !busy, onClick = { draft = config.copy(visibility = visibility) })
                        TextButton(enabled = !busy, onClick = { draft = config.copy(visibility = visibility) }) { Text(when (visibility) { TrainingVisibility.OFF -> "Desativado"; TrainingVisibility.TEST -> "Ativar Teste — somente Mestre"; TrainingVisibility.ALL -> "Ativar Todos" }) } }
                }
                Text("Teste mantém o conteúdo restrito ao Mestre. Salve para aplicar a configuração nos aparelhos conectados.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !busy && dirty, onClick = { scope.launch {
                        busy = true
                        try {
                            val previous = saved
                            val next = TrainingRepository.save(config, previous?.revision ?: 0)
                            draft = next; saved = next; message = "Treinamento salvo."
                            if (next.visibility == TrainingVisibility.ALL) {
                                val notices = next.missions.filter { m -> m.enabled && (previous?.visibility != TrainingVisibility.ALL || previous?.missions.orEmpty().none { it.id == m.id && it.enabled }) }
                                var failed = false
                                notices.forEach { mission -> if (runCatching { TrainingRepository.announce(mission.id) }.isFailure) failed = true }
                                message = if (failed) "Treinamento salvo. Alguns avisos não foram confirmados; use Avisar novo treinamento para tentar novamente." else "Treinamento salvo e publicado."
                            }
                        }
                        catch (e: Exception) { message = e.message ?: "Não foi possível salvar." }
                        finally { busy = false }
                    } }) { Text(if (busy) "Salvando…" else if (config.visibility == TrainingVisibility.ALL) "Salvar e publicar" else "Salvar") }
                    OutlinedButton(enabled = !busy, onClick = { preview = true }) { Text("Prévia") }
                }
                TextButton(enabled = !busy && dirty, onClick = { draft = saved; message = "Alterações descartadas." }) { Text("Descartar alterações") }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                HorizontalDivider()
                Text("Teclas e comandos", style = MaterialTheme.typography.titleLarge)
                Text("Edite inclusive as posições vazias. Variáveis nos textos: {entrada}, {tecla}, {missao}. Comandos operam apenas neste simulador.")
                var keyId by remember { mutableStateOf(config.keys.first().id) }
                TrainingPicker("Tecla", config.keys.map { it.id to it.label.ifBlank { "Posição ${it.id} (vazia)" }.replace('\n', ' ') }, keyId, enabled = !busy) { keyId = it }
                val key = config.keys.firstOrNull { it.id == keyId } ?: config.keys.first()
                fun keyChange(next: TrainingKey) { draft = config.copy(keys = config.keys.map { if (it.id == key.id) next else it }) }
                TrainingField("Nome da tecla", key.label, !busy) { keyChange(key.copy(label = it)) }
                TrainingPicker("Comando", KeyAction.entries.map { it.name to it.title }, key.action.name, enabled = !busy) { keyChange(key.copy(action = KeyAction.valueOf(it))) }
                if (key.action == KeyAction.APPEND) TrainingField("Valor acrescentado à entrada", key.value, !busy) { keyChange(key.copy(value = it)) }
                Row { Checkbox(checked = key.overrideGuided, enabled = !busy, onCheckedChange = { keyChange(key.copy(overrideGuided = it)) }); Text("Usar os visores desta tecla também em Aprender e Prova") }
                TrainingField("Mensagem no visor verde", key.operatorText, !busy) { keyChange(key.copy(operatorText = it)) }
                TrainingImageField("Imagem no visor verde", key.operatorImage, !busy, { busy = it }) { draft = draft?.let { latest -> latest.copy(keys = latest.keys.map { k -> if (k.id == key.id) k.copy(operatorImage = it) else k }) } }
                TrainingField("Mensagem no visor do cliente", key.customerText, !busy) { keyChange(key.copy(customerText = it)) }
                TrainingImageField("Imagem no visor do cliente", key.customerImage, !busy, { busy = it }) { draft = draft?.let { latest -> latest.copy(keys = latest.keys.map { k -> if (k.id == key.id) k.copy(customerImage = it) else k }) } }
                HorizontalDivider()
                Text("Treinamentos e etapas", style = MaterialTheme.typography.titleLarge)
                var missionId by remember { mutableStateOf(config.missions.first().id) }
                TrainingPicker("Treinamento", config.missions.map { it.id to it.title }, missionId, enabled = !busy) { missionId = it }
                val mission = config.missions.firstOrNull { it.id == missionId } ?: config.missions.first()
                fun missionChange(next: TrainingMission) { draft = config.copy(missions = config.missions.map { if (it.id == mission.id) next else it }) }
                TrainingField("Título", mission.title, !busy) { missionChange(mission.copy(title = it)) }
                TrainingField("Descrição", mission.subtitle, !busy) { missionChange(mission.copy(subtitle = it)) }
                Row { Switch(checked = mission.enabled, enabled = !busy, onCheckedChange = { missionChange(mission.copy(enabled = it)) }); Text("  Treinamento disponível") }
                var stepIndex by remember(mission.id) { mutableIntStateOf(0) }
                val index = stepIndex.coerceIn(0, mission.steps.lastIndex)
                TrainingPicker("Etapa", mission.steps.mapIndexed { i, s -> i.toString() to "${i + 1}. ${config.keys.find { it.id == s.keyId }?.label.orEmpty().replace('\n', ' ')}" }, index.toString(), enabled = !busy) { stepIndex = it.toInt() }
                val step = mission.steps[index]
                fun stepChange(next: TrainingStep) { missionChange(mission.copy(steps = mission.steps.mapIndexed { i, s -> if (i == index) next else s })) }
                TrainingPicker("Tecla esperada", config.keys.filter { it.label.isNotBlank() }.map { it.id to it.label.replace('\n', ' ') }, step.keyId, enabled = !busy) { stepChange(step.copy(keyId = it)) }
                TrainingField("Instrução", step.instruction, !busy) { stepChange(step.copy(instruction = it)) }
                TrainingField("Mensagem no visor verde da etapa", step.operatorText, !busy) { stepChange(step.copy(operatorText = it)) }
                TrainingImageField("Imagem no visor verde da etapa", step.operatorImage, !busy, { busy = it }) { draft = draft?.let { latest -> latest.copy(missions = latest.missions.map { m -> if (m.id == mission.id) m.copy(steps = m.steps.mapIndexed { i, s -> if (i == index) s.copy(operatorImage = it) else s }) else m }) } }
                TrainingField("Mensagem no visor do cliente da etapa", step.customerText, !busy) { stepChange(step.copy(customerText = it)) }
                TrainingImageField("Imagem no visor do cliente da etapa", step.customerImage, !busy, { busy = it }) { draft = draft?.let { latest -> latest.copy(missions = latest.missions.map { m -> if (m.id == mission.id) m.copy(steps = m.steps.mapIndexed { i, s -> if (i == index) s.copy(customerImage = it) else s }) else m }) } }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(enabled = !busy && index > 0, onClick = { val rows = mission.steps.toMutableList(); java.util.Collections.swap(rows, index, index - 1); missionChange(mission.copy(steps = rows)); stepIndex = index - 1 }) { Text("Subir") }
                    TextButton(enabled = !busy && index < mission.steps.lastIndex, onClick = { val rows = mission.steps.toMutableList(); java.util.Collections.swap(rows, index, index + 1); missionChange(mission.copy(steps = rows)); stepIndex = index + 1 }) { Text("Descer") }
                    TextButton(enabled = !busy && mission.steps.size > 1, onClick = { missionChange(mission.copy(steps = mission.steps.filterIndexed { i, _ -> i != index })); stepIndex = (index - 1).coerceAtLeast(0) }) { Text("Excluir etapa") }
                }
                OutlinedButton(enabled = !busy && mission.steps.size < 100, onClick = { missionChange(mission.copy(steps = mission.steps + TrainingStep(config.keys.first { it.label.isNotBlank() }.id, "", "", ""))); stepIndex = mission.steps.size }) { Text("Adicionar etapa") }
                OutlinedButton(enabled = !busy && config.missions.size < 60, onClick = {
                    val id = UUID.randomUUID().toString()
                    draft = config.copy(missions = config.missions + TrainingMission(id, "Novo treinamento", steps = listOf(TrainingStep(config.keys.first { it.label.isNotBlank() }.id, "", "", ""))))
                    missionId = id
                }) { Text("Novo treinamento") }
                Text("Novos treinamentos são anunciados ao salvar em Ativar Todos. Notificações só podem ser publicadas para treinamentos salvos em Ativar Todos. Cada treinamento é anunciado uma vez.", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(enabled = !busy && !dirty && config.visibility == TrainingVisibility.ALL && mission.enabled, onClick = { scope.launch {
                    busy = true
                    try { message = TrainingRepository.announce(mission.id) }
                    catch (e: Exception) { message = e.message ?: "Não foi possível enviar o aviso." }
                    finally { busy = false }
                } }) { Text("Avisar novo treinamento") }
            }
            if (message.isNotBlank()) Text(message, modifier = Modifier.fillMaxWidth())
        }
    }
    if (confirmExit) AlertDialog(onDismissRequest = { confirmExit = false }, title = { Text("Descartar alterações?") }, text = { Text("Existem alterações que ainda não foram salvas.") },
        confirmButton = { TextButton(onClick = onBack) { Text("Descartar e sair") } }, dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("Continuar editando") } })
}

@Composable
private fun TrainingField(label: String, value: String, enabled: Boolean, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, enabled = enabled, modifier = Modifier.fillMaxWidth())
}
@Composable
private fun TrainingPicker(label: String, options: List<Pair<String, String>>, selected: String, enabled: Boolean = true, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(enabled = enabled, onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text("$label: ${options.find { it.first == selected }?.second.orEmpty()}") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (id, title) -> DropdownMenuItem(enabled = enabled, text = { Text(title) }, onClick = { expanded = false; onChange(id) }) }
        }
    }
}
@Composable
private fun TrainingImageField(label: String, value: String, enabled: Boolean, onBusy: (Boolean) -> Unit, onChange: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val currentOnChange by rememberUpdatedState(onChange)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            busy = true; onBusy(true); error = null
            val target = currentOnChange
            try {
                val url = withContext(Dispatchers.IO) {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                    check(bounds.outWidth > 0 && bounds.outHeight > 0) { "Imagem inválida." }
                    var sample = 1
                    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2
                    val source = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: error("Imagem inválida.")
                    val scale = minOf(1f, 1200f / maxOf(source.width, source.height))
                    val bitmap = if (scale < 1f) Bitmap.createScaledBitmap(source, (source.width * scale).toInt().coerceAtLeast(1), (source.height * scale).toInt().coerceAtLeast(1), true) else source
                    val file = File.createTempFile("training_", ".webp", context.cacheDir)
                    try {
                        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.WEBP, 80, it) }
                        check(file.length() <= 700_000) { "Imagem muito grande. Escolha uma imagem menor." }
                        DynamicMediaUploader.upload(context, Uri.fromFile(file)).getOrThrow()
                    } finally { file.delete(); if (bitmap !== source) bitmap.recycle(); source.recycle() }
                }
                target(url)
            } catch (e: Exception) { error = e.message ?: "Falha ao inserir imagem." }
            finally { busy = false; onBusy(false) }
        }
    }
    Column {
        TrainingField("$label (link HTTPS)", value, enabled && !busy, onChange)
        TextButton(enabled = enabled && !busy, onClick = { picker.launch("image/*") }) { Text(if (busy) "Enviando imagem…" else "Escolher imagem do aparelho") }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
