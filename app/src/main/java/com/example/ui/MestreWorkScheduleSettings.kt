package com.example.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.data.GeminiMasterService
import com.example.data.NossaGenteApi
import com.example.data.NossaGenteDirectoryResult
import com.example.data.WorkSchedule
import com.example.data.WorkScheduleEmployee
import com.example.data.FirebaseService
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

@Composable
internal fun MestreWorkScheduleSettings() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val employees = remember { mutableStateListOf<WorkScheduleEmployee>() }
    var month by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var verified by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            message = "Lendo a imagem e estruturando a escala…"
            runCatching {
                val image = android.graphics.BitmapFactory.decodeStream(context.contentResolver.openInputStream(uri))
                    ?: error("Não foi possível abrir a foto da escala.")
                val maxSide = 2200f
                val ratio = (maxSide / maxOf(image.width, image.height)).coerceAtMost(1f)
                val resized = if (ratio < 1f) android.graphics.Bitmap.createScaledBitmap(
                    image, (image.width * ratio).toInt(), (image.height * ratio).toInt(), true
                ) else image
                val bytes = java.io.ByteArrayOutputStream().use { stream ->
                    resized.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, stream)
                    stream.toByteArray()
                }
                if (resized !== image) resized.recycle()
                image.recycle()
                val json = GeminiMasterService.extractWorkSchedule(
                    android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                ).getOrThrow()
                val extractedMonth = json.optInt("month", 0)
                val extractedYear = json.optInt("year", 0)
                if (extractedMonth in 1..12) month = extractedMonth.toString()
                if (extractedYear in 2000..2100) year = extractedYear.toString()
                val rows = json.optJSONArray("employees") ?: error("A resposta não contém a lista de funcionários.")
                employees.clear()
                for (index in 0 until rows.length()) {
                    val row = rows.optJSONObject(index) ?: continue
                    val registration = row.optString("registration").filter(Char::isDigit)
                    val name = row.optString("name").trim()
                    if (registration.isBlank() || name.isBlank()) continue
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
                verified = false
                "Leitura pronta. Revise os campos e confirme as matrículas na API Nossa Gente."
            }.onSuccess { message = it }.onFailure { message = it.message ?: "Falha ao ler a escala." }
            busy = false
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Inserir Escala", style = MaterialTheme.typography.headlineSmall)
        Text("Envie uma foto da escala. A foto será analisada pelo Gemini, incluindo as colunas dos dias. Revise tudo e confirme cada matrícula na API Nossa Gente antes de publicar.", style = MaterialTheme.typography.bodyMedium)
        Button(onClick = { imagePicker.launch("image/*") }, enabled = !busy) {
            androidx.compose.material3.Icon(Icons.Default.CloudUpload, contentDescription = null)
            Text("  Selecionar foto da escala")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(month, { month = it.filter(Char::isDigit).take(2); verified = false }, Modifier.weight(1f), label = { Text("Mês (1–12)") }, singleLine = true)
            OutlinedTextField(year, { year = it.filter(Char::isDigit).take(4); verified = false }, Modifier.weight(1f), label = { Text("Ano") }, singleLine = true)
        }
        if (busy) CircularProgressIndicator()
        message?.let { Text(it, color = if (it.contains("Falha", true) || it.contains("não ", true) || it.contains("erro", true)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }

        if (employees.isNotEmpty()) {
            Text("Revisão (${employees.size}) — ordem A–Z", style = MaterialTheme.typography.titleMedium)
            Text("As matrículas só são consideradas confirmadas quando coincidem exatamente com o cadastro Nossa Gente. Se o nome diferir, será substituído pelo nome oficial da API.", style = MaterialTheme.typography.bodySmall)
            Button(onClick = {
                scope.launch {
                    busy = true
                    verified = false
                    when (val result = NossaGenteApi(context.applicationContext).fetchEmployeeDirectory()) {
                        NossaGenteDirectoryResult.Unauthorized -> message = "Entre novamente no Nossa Gente para confirmar as matrículas."
                        is NossaGenteDirectoryResult.Error -> message = result.message
                        is NossaGenteDirectoryResult.Success -> {
                            var missing = 0
                            for (i in employees.indices) {
                                val row = employees[i]
                                val official = result.employees.firstOrNull { it.registration.filter(Char::isDigit) == row.registration.filter(Char::isDigit) }
                                if (official == null) { employees[i] = row.copy(verified = false); missing++ }
                                else employees[i] = row.copy(name = official.name, verified = true)
                            }
                            employees.sortBy { it.name.lowercase() }
                            verified = missing == 0
                            message = if (verified) "Todas as ${employees.size} matrículas foram confirmadas pela API Nossa Gente." else "$missing matrícula(s) não encontradas. Corrija-as e confirme novamente."
                        }
                    }
                    busy = false
                }
            }, enabled = !busy) {
                androidx.compose.material3.Icon(Icons.Default.FactCheck, contentDescription = null)
                Text("  Conferir matrículas na Nossa Gente")
            }
            LazyColumn(Modifier.fillMaxWidth().height(430.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(employees, key = { index, row -> "${row.registration}-$index" }) { index, row ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(if (row.verified) "✓ Confirmado pela Nossa Gente" else "Pendente de confirmação", color = if (row.verified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                            OutlinedTextField(row.name, { employees[index] = row.copy(name = it, verified = false); verified = false }, Modifier.fillMaxWidth(), label = { Text("Nome") }, singleLine = true)
                            OutlinedTextField(row.registration, { employees[index] = row.copy(registration = it.filter(Char::isDigit), verified = false); verified = false }, Modifier.fillMaxWidth(), label = { Text("Matrícula") }, singleLine = true)
                            OutlinedTextField(row.shift, { employees[index] = row.copy(shift = it) }, Modifier.fillMaxWidth(), label = { Text("Setor / horário") }, singleLine = true)
                            OutlinedTextField(row.daysOff.joinToString(","), { employees[index] = row.copy(daysOff = it.parseDays(), verified = false); verified = false }, Modifier.fillMaxWidth(), label = { Text("Folgas (dias do mês, separados por vírgula)") }, singleLine = true)
                            OutlinedTextField(row.vacationDays.joinToString(","), { employees[index] = row.copy(vacationDays = it.parseDays(), verified = false); verified = false }, Modifier.fillMaxWidth(), label = { Text("Férias (dias do mês)") }, singleLine = true)
                        }
                    }
                }
            }
            Button(onClick = {
                scope.launch {
                    val m = month.toIntOrNull()
                    val y = year.toIntOrNull()
                    if (m == null || m !in 1..12 || y == null || y !in 2000..2100) { message = "Informe mês e ano válidos antes de salvar."; return@launch }
                    if (!verified || employees.any { !it.verified }) { message = "Confirme todas as matrículas novamente antes de publicar."; return@launch }
                    busy = true
                    val schedule = WorkSchedule("%04d-%02d".format(y, m), y, m, employees.toList())
                    val saved = FirebaseService.publishWorkSchedule(schedule)
                    message = if (saved) "Escala de ${scheduleMonthName(m!!)} de $y publicada. Revisão atualizada." else FirebaseService.lastError ?: "Não foi possível publicar a escala."
                    busy = false
                }
            }, enabled = !busy && verified && employees.all { it.verified }) { Text("Publicar escala") }
        }
        Spacer(Modifier.height(8.dp))
    }
}

private fun JSONObject.intList(key: String): List<Int> = optJSONArray(key)?.let { a -> (0 until a.length()).mapNotNull { a.optInt(it).takeIf { day -> day in 1..31 } }.distinct().sorted() }.orEmpty()
private fun String.parseDays(): List<Int> = split(',', ';', ' ').mapNotNull { it.trim().toIntOrNull()?.takeIf { day -> day in 1..31 } }.distinct().sorted()
private fun scheduleMonthName(month: Int): String = java.util.Calendar.getInstance()
    .apply { set(java.util.Calendar.MONTH, (month - 1).coerceIn(0, 11)) }
    .getDisplayName(java.util.Calendar.MONTH, java.util.Calendar.LONG, java.util.Locale("pt", "BR"))
    ?.replaceFirstChar { it.uppercase() } ?: "mês"
