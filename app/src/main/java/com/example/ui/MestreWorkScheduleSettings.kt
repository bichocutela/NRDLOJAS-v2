package com.example.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.data.GeminiMasterService
import com.example.data.NossaGenteApi
import com.example.data.NossaGenteDirectoryResult
import com.example.data.WorkScheduleEmployee
import com.example.data.FirebaseService
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import com.example.data.WorkSchedule
import org.json.JSONArray
import org.json.JSONObject
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

@Composable
internal fun MestreWorkScheduleSettings() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val employees = remember { mutableStateListOf<WorkScheduleEmployee>() }
    val today = remember { java.util.Calendar.getInstance() }
    var month by remember { mutableStateOf((today.get(java.util.Calendar.MONTH) + 1).toString()) }
    var year by remember { mutableStateOf(today.get(java.util.Calendar.YEAR).toString()) }
    var showMonthPicker by remember { mutableStateOf(false) }
    var pickerYear by remember { mutableIntStateOf(today.get(java.util.Calendar.YEAR)) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val rowPreviews = remember { mutableStateMapOf<String, RosterLinePreview>() }
    var selectedImage by remember { mutableStateOf<android.net.Uri?>(null) }
    var imageRotation by remember { mutableIntStateOf(0) }
    var savedSchedules by remember { mutableStateOf<List<WorkSchedule>>(emptyList()) }
    var historyExpanded by remember { mutableStateOf(true) }
    val drafts = remember { ScheduleDraftStore(context.applicationContext) }
    var activePeriod by remember { mutableStateOf<String?>(null) }
    var showManual by remember { mutableStateOf(false) }
    var manualName by remember { mutableStateOf("") }
    var manualRegistration by remember { mutableStateOf("") }
    var manualShift by remember { mutableStateOf("") }
    var showTextInput by remember { mutableStateOf(false) }
    var scheduleText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { savedSchedules = FirebaseService.fetchWorkSchedules() }
    LaunchedEffect(activePeriod, employees.toList()) {
        if (activePeriod != null && employees.isNotEmpty()) drafts.save(activePeriod!!, employees.toList())
    }
    LaunchedEffect(month, year, employees.size) {
        if (activePeriod == null && employees.isNotEmpty()) activePeriod = schedulePeriod(month, year)
    }

    fun addExtractedRows(json: JSONObject) {
        val extractedMonth = json.optInt("month", 0)
        val extractedYear = json.optInt("year", 0)
        if (extractedMonth in 1..12) month = extractedMonth.toString()
        if (extractedYear in 2000..2100) year = extractedYear.toString()
        val key = schedulePeriod(month, year) ?: error("Escolha o mês e ano da escala.")
        val rows = json.optJSONArray("employees") ?: error("A resposta não contém funcionários.")
        val extracted = (0 until rows.length()).mapNotNull { index ->
            val row = rows.optJSONObject(index) ?: return@mapNotNull null
            val registration = row.optString("registration").filter(Char::isDigit)
            val name = row.optString("name").trim()
            if (registration.isBlank() || name.isBlank()) return@mapNotNull null
            WorkScheduleEmployee(registration, name,
                row.optString("shift").takeUnless { it == "null" }.orEmpty(),
                row.intList("daysOff"), row.intList("vacationDays"))
        }
        require(extracted.isNotEmpty()) { "Nenhum funcionário com nome e matrícula foi identificado. Revise o conteúdo." }
        val existing = if (activePeriod == key) employees.toList() else drafts.load(key)
        // Preserve corrections already made in the current draft when importing another source.
        val merged = (extracted + existing).associateBy { it.registration }.toMutableMap()
        savedSchedules.firstOrNull { it.monthKey == key }?.employees?.filter { it.verified }?.forEach { saved ->
            merged[saved.registration] = saved
        }
        employees.clear()
        employees.addAll(merged.values.sortedBy { it.name.lowercase() })
        activePeriod = key
        drafts.save(key, employees.toList())
    }

    fun analyzeText() {
        scope.launch {
            busy = true
            message = "Interpretando o texto da escala…"
            GeminiMasterService.extractWorkScheduleText(scheduleText).onSuccess { json ->
                runCatching { addExtractedRows(json) }
                    .onSuccess {
                        rowPreviews.values.forEach { preview ->
                            preview.full.recycle(); preview.identity.recycle(); preview.marks.recycle()
                        }
                        rowPreviews.clear()
                        selectedImage = null
                        showTextInput = false
                        message = "Texto interpretado. Revise as datas e confirme cada matrícula antes de salvar."
                    }
                    .onFailure { message = it.message ?: "Falha ao estruturar o texto." }
            }.onFailure { message = it.message ?: "Falha ao interpretar o texto." }
            busy = false
        }
    }

    fun analyzeImage(uri: android.net.Uri, rotation: Int) {
        scope.launch {
            busy = true
            val previousRows = employees.toList()
            val previousPeriod = activePeriod
            activePeriod = null
            message = "Lendo a imagem e estruturando a escala…"
            runCatching {
                val image = android.graphics.BitmapFactory.decodeStream(context.contentResolver.openInputStream(uri))
                    ?: error("Não foi possível abrir a foto da escala.")
                val oriented = if (rotation != 0) android.graphics.Bitmap.createBitmap(
                    image, 0, 0, image.width, image.height,
                    android.graphics.Matrix().apply { postRotate(rotation.toFloat()) }, true
                ) else image
                val maxSide = 3000f
                val ratio = (maxSide / maxOf(oriented.width, oriented.height)).coerceAtMost(1f)
                val resized = if (ratio < 1f) android.graphics.Bitmap.createScaledBitmap(
                    oriented, (oriented.width * ratio).toInt(), (oriented.height * ratio).toInt(), true
                ) else oriented
                val bytes = java.io.ByteArrayOutputStream().use { stream ->
                    resized.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, stream)
                    stream.toByteArray()
                }
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                val ocr = try { recognizer.process(InputImage.fromBitmap(resized, 0)).await() } finally { recognizer.close() }
                val ocrLines = ocr.textBlocks.flatMap { it.lines }
                val registrationBoxes = ocrLines.flatMap { it.elements }
                    .mapNotNull { element -> element.boundingBox?.let { element.text.filter(Char::isDigit) to it } } +
                    ocrLines.mapNotNull { line -> line.boundingBox?.let { line.text.filter(Char::isDigit) to it } }
                val json = GeminiMasterService.extractWorkSchedule(
                    android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                ).getOrThrow()
                val extractedMonth = json.optInt("month", 0)
                val extractedYear = json.optInt("year", 0)
                if (extractedMonth in 1..12) month = extractedMonth.toString()
                if (extractedYear in 2000..2100) year = extractedYear.toString()
                val rows = json.optJSONArray("employees") ?: error("A resposta não contém a lista de funcionários.")
                rowPreviews.values.forEach { preview ->
                    preview.full.recycle(); preview.identity.recycle(); preview.marks.recycle()
                }
                rowPreviews.clear()
                employees.clear()
                for (index in 0 until rows.length()) {
                    val row = rows.optJSONObject(index) ?: continue
                    val registration = row.optString("registration").filter(Char::isDigit)
                    val name = row.optString("name").trim()
                    if (registration.isBlank() || name.isBlank()) continue
                    val bounds = registrationBoxes.firstOrNull { it.first == registration }?.second
                        ?: registrationBoxes.firstOrNull { it.first.contains(registration) }?.second
                    if (bounds != null) {
                        val margin = (bounds.height() * 0.8f).toInt().coerceAtLeast(10)
                        val top = (bounds.centerY() - margin).coerceAtLeast(0)
                        val bottom = (bounds.centerY() + margin).coerceAtMost(resized.height)
                        if (bottom > top) {
                            val full = android.graphics.Bitmap.createBitmap(resized, 0, top, resized.width, bottom - top)
                            val split = bounds.right.coerceIn((full.width * 0.35f).toInt(), (full.width * 0.55f).toInt())
                            rowPreviews[registration] = RosterLinePreview(
                                full,
                                android.graphics.Bitmap.createBitmap(full, 0, 0, split, full.height),
                                android.graphics.Bitmap.createBitmap(full, split, 0, full.width - split, full.height)
                            )
                        }
                    }
                    employees += WorkScheduleEmployee(
                        registration = registration,
                        name = name,
                        shift = row.optString("shift").takeUnless { it == "null" }.orEmpty(),
                        daysOff = row.intList("daysOff"),
                        vacationDays = row.intList("vacationDays")
                    )
                }
                require(employees.isNotEmpty()) { "Gemini não extraiu registros com matrícula e nome. Revise a foto." }
                employees.sortBy { it.name.lowercase() }
                val extractedPeriod = schedulePeriod(month, year)
                if (extractedPeriod != null) {
                    activePeriod = extractedPeriod
                    savedSchedules = FirebaseService.fetchWorkSchedules()
                    val pending = if (previousPeriod == extractedPeriod) previousRows else drafts.load(extractedPeriod)
                    val merged = (employees.toList() + pending).associateBy { it.registration }.toMutableMap()
                    savedSchedules.firstOrNull { it.monthKey == extractedPeriod }?.employees?.filter { it.verified }?.forEach { saved ->
                        merged[saved.registration] = saved
                    }
                    employees.clear()
                    employees.addAll(merged.values.sortedBy { it.name.lowercase() })
                    drafts.save(extractedPeriod, employees.toList())
                }
                if (resized !== oriented) resized.recycle()
                if (oriented !== image) oriented.recycle()
                image.recycle()
                "Leitura pronta. Abra um funcionário, confira a linha original, ajuste os dias e confirme a matrícula antes de salvar."
            }.onSuccess { message = it }.onFailure { message = it.message ?: "Falha ao ler a escala." }
            busy = false
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedImage = uri
            imageRotation = 0
            analyzeImage(uri, imageRotation)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Inserir Escala", style = MaterialTheme.typography.headlineSmall)
        Text("Escolha foto, cadastro manual ou texto. Revise os dias, confirme a matrícula na Nossa Gente e salve cada funcionário.", style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = { imagePicker.launch("image/*") }, enabled = !busy, modifier = Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)) {
                Text("Foto", maxLines = 1)
            }
            OutlinedButton(onClick = { showManual = true }, enabled = !busy, modifier = Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)) {
                Text("Editar escala", maxLines = 1, style = MaterialTheme.typography.labelSmall)
            }
            OutlinedButton(onClick = { showTextInput = true }, enabled = !busy, modifier = Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)) {
                Text("Enviar texto", maxLines = 1)
            }
        }
        if (showManual) AlertDialog(
            onDismissRequest = { showManual = false },
            title = { Text("Adicionar funcionário") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Escala de ${scheduleMonthName(month.toIntOrNull() ?: 1)}/$year. Depois selecione as folgas no calendário.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(manualName, { manualName = it }, label = { Text("Nome completo") }, singleLine = true)
                    OutlinedTextField(manualRegistration, { manualRegistration = it.filter(Char::isDigit) }, label = { Text("Matrícula") }, singleLine = true)
                    OutlinedTextField(manualShift, { manualShift = it }, label = { Text("Setor / horário (opcional)") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val reg = manualRegistration.filter(Char::isDigit)
                    val key = schedulePeriod(month, year)
                    when {
                        key == null -> message = "Escolha mês e ano antes de adicionar."
                        reg.isBlank() || manualName.isBlank() -> message = "Informe nome e matrícula."
                        savedSchedules.firstOrNull { it.monthKey == key }?.employees?.any { it.registration == reg && it.verified } == true ->
                            message = "Esta matrícula já foi salva neste mês. Abra o histórico para ver ou corrigir."
                        (if (activePeriod == key) employees.toList() else drafts.load(key)).any { it.registration == reg } ->
                            message = "Esta matrícula já está na revisão. Abra a linha para corrigir."
                        else -> {
                            if (activePeriod != key) {
                                employees.clear()
                                employees.addAll(drafts.load(key))
                                rowPreviews.clear()
                                selectedImage = null
                            }
                            employees += WorkScheduleEmployee(reg, manualName.trim(), manualShift.trim())
                            employees.sortBy { it.name.lowercase() }
                            activePeriod = key
                            showManual = false
                            manualName = ""; manualRegistration = ""; manualShift = ""
                            message = "Funcionário adicionado. Marque as folgas no calendário e consulte a matrícula antes de salvar."
                        }
                    }
                }) { Text("Adicionar e marcar folgas") }
            },
            dismissButton = { TextButton(onClick = { showManual = false }) { Text("Cancelar") } }
        )
        if (showTextInput) AlertDialog(
            onDismissRequest = { if (!busy) showTextInput = false },
            title = { Text("Enviar escala por texto") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Cole nomes, matrículas, horários, folgas e férias. O Gemini organiza as linhas para você revisar.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(scheduleText, { scheduleText = it.take(60000) }, Modifier.fillMaxWidth().height(220.dp),
                        label = { Text("Texto da escala") }, minLines = 8)
                }
            },
            confirmButton = { TextButton(onClick = { analyzeText() }, enabled = !busy && scheduleText.isNotBlank()) { Text("Interpretar e revisar") } },
            dismissButton = { TextButton(onClick = { showTextInput = false }, enabled = !busy) { Text("Cancelar") } }
        )
        selectedImage?.let { uri ->
            TextButton(onClick = {
                imageRotation = (imageRotation + 90) % 360
                analyzeImage(uri, imageRotation)
            }, enabled = !busy) { Text("Girar foto 90° e reler escala") }
        }
        OutlinedButton(onClick = { pickerYear = year.toIntOrNull() ?: today.get(java.util.Calendar.YEAR); showMonthPicker = true }, Modifier.fillMaxWidth()) {
            androidx.compose.material3.Icon(Icons.Default.FactCheck, contentDescription = null)
            Text("  Mês da escala: ${scheduleMonthName(month.toIntOrNull() ?: 1)}/$year  ▾")
        }
        if (showMonthPicker) AlertDialog(
            onDismissRequest = { showMonthPicker = false },
            title = { Text("Escolha o mês da escala") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        TextButton(onClick = { if (pickerYear > 2000) pickerYear-- }) { Text("‹ Anterior") }
                        Text(pickerYear.toString(), style = MaterialTheme.typography.titleLarge)
                        TextButton(onClick = { if (pickerYear < 2100) pickerYear++ }) { Text("Próximo ›") }
                    }
                    (1..12).chunked(3).forEach { months ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            months.forEach { chosen ->
                                val label = scheduleMonthName(chosen).take(3)
                                val selected = chosen == month.toIntOrNull() && pickerYear == year.toIntOrNull()
                                Box(Modifier.weight(1f).height(46.dp)
                                    .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                        RoundedCornerShape(12.dp))
                                    .clickable {
                                        val nextPeriod = schedulePeriod(chosen.toString(), pickerYear.toString())
                                        if (activePeriod != null && activePeriod != nextPeriod && employees.any { it.verified }) {
                                            employees.clear()
                                            message = "Mês alterado. Abra o histórico desse período ou selecione a foto para iniciar outra escala."
                                            activePeriod = null
                                        } else if (employees.isNotEmpty()) activePeriod = nextPeriod
                                        month = chosen.toString()
                                        year = pickerYear.toString()
                                        showMonthPicker = false
                                    }, contentAlignment = androidx.compose.ui.Alignment.Center) {
                                    Text(label, color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    Text("Ao escolher, o calendário das folgas usará este mês e ano.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { showMonthPicker = false }) { Text("Fechar") } }
        )
        if (savedSchedules.isNotEmpty() || drafts.periods().isNotEmpty()) {
            TextButton(onClick = { historyExpanded = !historyExpanded }) {
                Text(if (historyExpanded) "Histórico e rascunhos  ▴" else "Histórico e rascunhos  ▾")
            }
            if (historyExpanded) {
                (savedSchedules.map { it.monthKey } + drafts.periods()).distinct().sortedDescending().forEach { key ->
                    val schedule = savedSchedules.firstOrNull { it.monthKey == key }
                    val draft = drafts.load(key)
                    val completed = schedule?.employees?.filter { it.verified }.orEmpty()
                    val pending = draft.filterNot { row -> completed.any { it.registration == row.registration } }
                        .map { it.copy(verified = false) }
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(schedulePeriodLabel(key), style = MaterialTheme.typography.titleMedium)
                            Text("${completed.size} salvos • ${pending.size} aguardando revisão", style = MaterialTheme.typography.bodySmall)
                            if (completed.isNotEmpty()) Text("Salvos: ${completed.joinToString(", ") { it.name }}", style = MaterialTheme.typography.bodySmall)
                            if (pending.isNotEmpty()) Text("Rascunho: ${pending.joinToString(", ") { it.name }}", style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = {
                                year = key.take(4)
                                month = key.takeLast(2).toInt().toString()
                                employees.clear()
                                employees.addAll((pending + completed).sortedBy { it.name.lowercase() })
                                activePeriod = key
                                rowPreviews.clear()
                                message = "${schedulePeriodLabel(key)} recuperado. Os dados foram preservados; a foto precisa ser selecionada novamente para ver a linha original."
                            }) { Text("Abrir escala e continuar") }
                        }
                    }
                }
            }
        }
        if (busy) CircularProgressIndicator()
        message?.let { Text(it, color = if (it.contains("Falha", true) || it.contains("não ", true) || it.contains("erro", true)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }

        if (employees.isNotEmpty()) {
            Text("Revisão (${employees.count { !it.verified }} pendentes • ${employees.count { it.verified }} salvos) — ordem A–Z", style = MaterialTheme.typography.titleMedium)
            LazyColumn(Modifier.fillMaxWidth().height(430.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(employees, key = { index, row -> "${row.registration}-$index" }) { index, row ->
                    var expanded by remember(row.registration) { mutableStateOf(!row.verified) }
                    var editVacation by remember(row.registration) { mutableStateOf(row.vacationDays.isNotEmpty()) }
                    var matchedName by remember(row.registration, month, year) { mutableStateOf<String?>(null) }
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(row.name, style = MaterialTheme.typography.titleMedium)
                                    Text("Matrícula ${row.registration} • ${row.shift}", style = MaterialTheme.typography.bodySmall)
                                    Text(if (row.verified) "✓ Salvo/Corrigido" else "Pendente de revisão", color = if (row.verified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                                    Text("Folgas: ${row.daysOff.joinToString(", ").ifBlank { "nenhuma" }}", style = MaterialTheme.typography.bodyMedium)
                                    if (row.vacationDays.isNotEmpty()) Text("Férias: ${row.vacationDays.joinToString(", ")}", style = MaterialTheme.typography.bodySmall)
                                }
                                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Fechar" else if (row.verified) "Ver/Corrigir" else "Corrigir") }
                            }
                            if (expanded) {
                                Text(if (row.verified) "✓ Matrícula confirmada pela Nossa Gente" else "Matrícula pendente de confirmação",
                                    color = if (row.verified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelMedium)
                                OutlinedTextField(row.name, { employees[index] = row.copy(name = it, verified = false); matchedName = null }, Modifier.fillMaxWidth(), label = { Text("Nome") }, singleLine = true)
                                OutlinedTextField(row.registration, { employees[index] = row.copy(registration = it.filter(Char::isDigit), verified = false); matchedName = null }, Modifier.fillMaxWidth(), label = { Text("Matrícula") }, singleLine = true)
                                OutlinedTextField(row.shift, { employees[index] = row.copy(shift = it) }, Modifier.fillMaxWidth(), label = { Text("Setor / horário") }, singleLine = true)
                                rowPreviews[row.registration]?.let { preview ->
                                    Text("Linha completa da foto", style = MaterialTheme.typography.titleSmall)
                                    Image(preview.full.asImageBitmap(), contentDescription = "Linha completa de ${row.name}",
                                        modifier = Modifier.fillMaxWidth().height(50.dp), contentScale = ContentScale.FillBounds)
                                    Text("Nome completo e matrícula na foto", style = MaterialTheme.typography.labelMedium)
                                    Image(preview.identity.asImageBitmap(), contentDescription = "Nome e matrícula de ${row.name}",
                                        modifier = Modifier.fillMaxWidth().height(62.dp), contentScale = ContentScale.FillBounds)
                                    Text("Marcações X e FE na foto • deslize para os lados", style = MaterialTheme.typography.labelMedium)
                                    Box(Modifier.fillMaxWidth().height(72.dp).horizontalScroll(rememberScrollState())) {
                                        Image(preview.marks.asImageBitmap(), contentDescription = "Marcações da linha de ${row.name}",
                                            modifier = Modifier.width(920.dp).height(66.dp), contentScale = ContentScale.FillBounds)
                                    }
                                } ?: Text("Sem recorte da foto nesta linha. Confira nome, matrícula e folgas antes de confirmar.",
                                    style = MaterialTheme.typography.bodySmall)
                                Text("Toque para marcar ou desmarcar a folga", style = MaterialTheme.typography.titleSmall)
                                ScheduleDayGrid(row.daysOff, month.toIntOrNull(), year.toIntOrNull()) { day ->
                                    employees[index] = row.copy(daysOff = row.daysOff.toggle(day))
                                }
                                TextButton(onClick = { editVacation = !editVacation }) {
                                    Text(if (editVacation) "Ocultar férias (FE)" else "Editar férias (FE)")
                                }
                                if (editVacation) ScheduleDayGrid(row.vacationDays, month.toIntOrNull(), year.toIntOrNull()) { day ->
                                    employees[index] = row.copy(vacationDays = row.vacationDays.toggle(day))
                                }
                                Text("A conferência consulta o cadastro de colaboradores com a sessão Nossa Gente autenticada em Meu Perfil.",
                                    style = MaterialTheme.typography.bodySmall)
                                Button(onClick = {
                                    scope.launch {
                                        busy = true
                                        val result = NossaGenteApi(context.applicationContext)
                                            .findEmployeeByRegistration(row.registration)
                                        when (result) {
                                            NossaGenteDirectoryResult.Unauthorized -> message = "Entre no Nossa Gente para consultar a matrícula."
                                            is NossaGenteDirectoryResult.Error -> message = result.message
                                            is NossaGenteDirectoryResult.Success -> {
                                                val official = result.employees.firstOrNull {
                                                    it.registration.filter(Char::isDigit) == row.registration.filter(Char::isDigit)
                                                }
                                                matchedName = official?.name
                                                message = if (official == null) "A API não confirmou esta matrícula. Não foi salva."
                                                    else "Funcionário encontrado: ${official.name}. Confira o nome e confirme para salvar."
                                            }
                                        }
                                        busy = false
                                    }
                                }, enabled = !busy && row.registration.isNotBlank()) { Text("Consultar matrícula na Nossa Gente") }
                                matchedName?.let { official ->
                                    Text("Funcionário encontrado: $official", color = MaterialTheme.colorScheme.primary)
                                    Button(onClick = {
                                        scope.launch {
                                            val m = month.toIntOrNull()
                                            val y = year.toIntOrNull()
                                            if (m == null || m !in 1..12 || y == null || y !in 2000..2100) {
                                                message = "Informe mês e ano válidos antes de salvar."
                                                return@launch
                                            }
                                            busy = true
                                            activePeriod = schedulePeriod(month, year)
                                            val confirmed = row.copy(name = official, verified = true)
                                            val saved = FirebaseService.publishVerifiedWorkScheduleEmployee(y, m, confirmed)
                                            if (saved) {
                                                employees[index] = confirmed
                                                expanded = false
                                                savedSchedules = FirebaseService.fetchWorkSchedules()
                                            }
                                            message = if (saved) "${official}: folgas salvas no perfil da matrícula ${row.registration}."
                                                else FirebaseService.lastError ?: "Não foi possível salvar este funcionário."
                                            busy = false
                                        }
                                    }, enabled = !busy) { Text("Confirmar e salvar no perfil") }
                                }
                            }
                        }
                    }
                }
            }

        }
        Spacer(Modifier.height(8.dp))
    }
}

private fun schedulePeriod(month: String, year: String): String? {
    val m = month.toIntOrNull() ?: return null
    val y = year.toIntOrNull() ?: return null
    return if (m in 1..12 && y in 2000..2100) "%04d-%02d".format(y, m) else null
}

private fun schedulePeriodLabel(key: String): String =
    "${scheduleMonthName(key.takeLast(2).toIntOrNull() ?: 1)}/${key.take(4)}"

/** Rascunhos ficam neste aparelho; as linhas confirmadas continuam no Firestore. */
private class ScheduleDraftStore(context: android.content.Context) {
    private val prefs = context.getSharedPreferences("work_schedule_drafts", android.content.Context.MODE_PRIVATE)

    fun periods(): List<String> = prefs.all.keys.filter { it.matches(Regex("\\d{4}-\\d{2}")) }

    fun save(period: String, rows: List<WorkScheduleEmployee>) {
        val json = JSONArray()
        rows.forEach { row ->
            json.put(JSONObject().put("registration", row.registration).put("name", row.name)
                .put("shift", row.shift).put("daysOff", JSONArray(row.daysOff))
                .put("vacationDays", JSONArray(row.vacationDays)).put("verified", row.verified)
                .put("rosterPhotoUrl", row.rosterPhotoUrl))
        }
        prefs.edit().putString(period, json.toString()).apply()
    }

    fun load(period: String): List<WorkScheduleEmployee> = runCatching {
        val json = JSONArray(prefs.getString(period, "[]"))
        (0 until json.length()).mapNotNull { index ->
            val row = json.optJSONObject(index) ?: return@mapNotNull null
            val registration = row.optString("registration").filter(Char::isDigit)
            if (registration.isBlank()) return@mapNotNull null
            WorkScheduleEmployee(registration, row.optString("name"), row.optString("shift"),
                row.intList("daysOff"), row.intList("vacationDays"), row.optBoolean("verified"), row.optString("rosterPhotoUrl"))
        }
    }.getOrDefault(emptyList())
}

private fun JSONObject.intList(key: String): List<Int> = optJSONArray(key)?.let { a -> (0 until a.length()).mapNotNull { a.optInt(it).takeIf { day -> day in 1..31 } }.distinct().sorted() }.orEmpty()
private fun List<Int>.toggle(day: Int): List<Int> =
    (if (day in this) filterNot { it == day } else this + day).distinct().sorted()

private data class RosterLinePreview(
    val full: android.graphics.Bitmap,
    val identity: android.graphics.Bitmap,
    val marks: android.graphics.Bitmap
)

@Composable
private fun ScheduleDayGrid(selected: List<Int>, month: Int?, year: Int?, onToggle: (Int) -> Unit) {
    val lastDay = if (month != null && month in 1..12 && year != null && year in 2000..2100)
        java.util.GregorianCalendar(year, month!! - 1, 1).getActualMaximum(java.util.Calendar.DAY_OF_MONTH) else 31
    val offset = if (month != null && month in 1..12 && year != null && year in 2000..2100)
        java.util.GregorianCalendar(year, month - 1, 1).get(java.util.Calendar.DAY_OF_WEEK) - 1 else 0
    val dates: List<Int?> = List(offset) { null } + (1..lastDay).map { it }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb").forEach { weekday ->
                Box(Modifier.weight(1f), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Text(weekday, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        dates.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { day ->
                    if (day == null) Spacer(Modifier.weight(1f).height(38.dp))
                    else {
                        val active = day in selected
                        Box(
                            Modifier.weight(1f).height(38.dp)
                                .background(
                                    if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { onToggle(day) },
                            contentAlignment = androidx.compose.ui.Alignment.Center
                        ) {
                            Text(day.toString(), color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f).height(38.dp)) }
            }
        }
    }
}
private fun scheduleMonthName(month: Int): String = java.util.Calendar.getInstance()
    .apply { set(java.util.Calendar.MONTH, (month - 1).coerceIn(0, 11)) }
    .getDisplayName(java.util.Calendar.MONTH, java.util.Calendar.LONG, java.util.Locale("pt", "BR"))
    ?.replaceFirstChar { it.uppercase() } ?: "mês"
