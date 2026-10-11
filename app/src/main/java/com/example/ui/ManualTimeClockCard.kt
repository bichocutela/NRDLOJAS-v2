package com.example.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.WorkSchedule
import com.example.data.timeclock.*
import com.google.firebase.auth.FirebaseAuth
import java.util.Calendar
import java.util.Locale

@Composable
internal fun ManualTimeClockCard(palette: ProfileThemePalette?, schedules: List<WorkSchedule>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repository = remember(context) { TimeClockRepository(context.applicationContext) }
    var revision by remember { mutableIntStateOf(0) }
    var selectedDate by remember { mutableStateOf(TimeClock.today()) }
    var showTools by remember { mutableStateOf(false) }
    var nowMinute by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { nowMinute = Calendar.getInstance().let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }; kotlinx.coroutines.delay(30_000) } }
    var showHours by remember { mutableStateOf(false) }
    var showReport by remember { mutableStateOf(false) }
    var editingDate by remember { mutableStateOf<String?>(null) }
    var confirmation by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val manualFolgas = remember(schedules) {
        schedules.flatMap { schedule -> schedule.employees.flatMap { employee -> employee.daysOff.map { day -> "${schedule.monthKey}-${day.toString().padStart(2, '0')}" } } }.toSet()
    }
    DisposableEffect(repository) {
        val auth = FirebaseAuth.getInstance()
        val listener = FirebaseAuth.AuthStateListener {
            revision++
            com.example.util.PersonalClockWorker.schedule(context)
            val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -1) }.let { java.text.SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(it.time) }
            val unfinished = repository.days().firstOrNull { it.date == yesterday && !it.complete && it.punches[0] != null }
            val now = Calendar.getInstance()
            val minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
            selectedDate = if (unfinished != null && minute <= unfinished.punches[0]!!) yesterday else TimeClock.today()
            editingDate = null; confirmation = null
        }
        val stopChanges = repository.observeChanges { revision++ }
        com.example.util.PersonalClockWorker.schedule(context)
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener); stopChanges() }
    }
    val savedDays = remember(revision) { repository.days() }
    val days = remember(savedDays, manualFolgas) { savedDays.map { if (it.date in manualFolgas) it.copy(dayOff = true, absent = false) else it } }
    val hours = remember(revision) { repository.hours() }
    val closingDay = remember(revision) { repository.closingDay() }
    val current = days.firstOrNull { it.date == selectedDate }
        ?: Workday(selectedDate, expectedMinutes = hours.expectedMinutes, dayOff = selectedDate in manualFolgas, scheduledStart = hours.start, scheduledBreak = hours.breakMinutes)
    val month = TimeClock.competence(selectedDate, closingDay)
    val periodDates = TimeClock.periodDates(month, closingDay).toSet()
    val totals = TimeClock.totals(days.filter { it.date in periodDates })
    val adjustments = remember(revision) { repository.adjustments() }
    val adjustedBalance = totals.balance + adjustments.filter { it.date in periodDates }.sumOf { it.minutes }
    val text = palette?.text ?: MaterialTheme.colorScheme.onSurface
    val secondary = palette?.secondaryText ?: MaterialTheme.colorScheme.onSurfaceVariant
    val accent = palette?.accent ?: MaterialTheme.colorScheme.primary
    val onAccent = palette?.onAccent ?: MaterialTheme.colorScheme.onPrimary
    val tile = palette?.tile ?: MaterialTheme.colorScheme.surfaceContainer
    fun selectDate(edit: Boolean = false) {
        val cal = TimeClock.calendar(selectedDate)
        DatePickerDialog(context, { _, y, m, d -> selectedDate = "%04d-%02d-%02d".format(y, m + 1, d); if (edit) editingDate = selectedDate; error = null },
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).apply { datePicker.maxDate = System.currentTimeMillis(); show() }
    }
    Card(modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = palette?.section ?: MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Meu ponto · Banco de horas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = text)
                    Text(repository.backupStatus(), style = MaterialTheme.typography.bodySmall, color = secondary)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { selectDate() }) { Text(TimeClock.displayDate(selectedDate), color = accent) }
                TextButton(onClick = { showHours = true }) { Text("Minha jornada", color = accent) }
            }
            Text("${TimeClock.time(current.scheduledStart)} → ${TimeClock.time(current.scheduledStart + current.expectedMinutes + current.scheduledBreak)} · almoço ${TimeClock.duration(current.scheduledBreak)} · jornada ${TimeClock.duration(current.expectedMinutes)}", style = MaterialTheme.typography.bodySmall, color = secondary)
            if (selectedDate == TimeClock.today()) {
                val nextLabel = current.punches.indexOfFirst { it == null }.let { if (it < 0) "Dia concluído" else "Próxima batida: ${TimeClock.labels[it]}" }
                val elapsed = ClockJourney.workedSoFar(current, nowMinute + if ((current.punches[0] ?: 0) > nowMinute && current.punches.any { (it ?: 0) >= 1440 }) 1440 else 0)
                Column(Modifier.fillMaxWidth().background(tile, RoundedCornerShape(14.dp)).padding(12.dp)) {
                    Text("Resumo de hoje", fontWeight = FontWeight.Bold, color = text)
                    Text(if (current.dayOff) "Hoje é folga" else nextLabel, color = text)
                    Text("Trabalhadas: ${TimeClock.duration(elapsed)}", color = secondary)
                    if (!current.dayOff && !current.complete) ClockJourney.predictedExit(current)?.let { Text("Previsão para completar a jornada: ${TimeClock.time(it)}", color = secondary) }
                }
            }
            ClockJourney.pending(days, TimeClock.today()).take(3).forEach { pending ->
                TextButton(onClick = { selectedDate = pending.date; editingDate = pending.date }) {
                    Text("Pendente ${TimeClock.displayDate(pending.date)} · completar batidas", color = accent)
                }
            }
            for (row in 0..1) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (col in 0..1) {
                    val index = row * 2 + col
                    val next = current.punches.indexOfFirst { it == null }
                    val now = Calendar.getInstance()
                    val minuteOfDay = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
                    val dateOffset = ((TimeClock.calendar(TimeClock.today()).timeInMillis - TimeClock.calendar(selectedDate).timeInMillis) / 86_400_000L).toInt()
                    val canPunch = index == next && !current.absent && (dateOffset == 0 || (dateOffset == 1 && next > 0 && minuteOfDay + 1440 - (current.punches[0] ?: 0) <= 1440))
                    Card(Modifier.weight(1f), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = if (canPunch) accent else tile)) {
                        Column(Modifier.fillMaxWidth().clickable(enabled = canPunch) {
                            val moment = Calendar.getInstance()
                            confirmation = index to (dateOffset * 1440 + moment.get(Calendar.HOUR_OF_DAY) * 60 + moment.get(Calendar.MINUTE))
                        }.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(TimeClock.labels[index], style = MaterialTheme.typography.labelLarge, color = if (canPunch) onAccent else secondary)
                            Text(TimeClock.time(current.punches[index]), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = if (canPunch) onAccent else text)
                            Text(if (canPunch) "Toque para registrar" else if (current.punches[index] != null) "Registrado" else "Aguardando", style = MaterialTheme.typography.labelSmall, color = if (canPunch) onAccent else secondary)
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = current.dayOff, enabled = selectedDate !in manualFolgas, onClick = {
                    runCatching { repository.save(current.copy(dayOff = !current.dayOff, absent = false)) }
                        .onSuccess { revision++; error = null }.onFailure { error = it.message }
                }, label = { Text(if (selectedDate in manualFolgas) "Folga do perfil" else "Folga") })
                Text(if (selectedDate in manualFolgas) "Identificada em Minhas Folgas" else "Marque se hoje não tem jornada.", style = MaterialTheme.typography.bodySmall, color = secondary, modifier = Modifier.weight(1f))
            }
            Text(when { current.absent -> "Ausência registrada"; current.complete -> "Dia concluído · saldo ${TimeClock.duration(current.balanceMinutes ?: 0, true)}"; current.dayOff -> "Folga · sem débito de jornada"; current.punches.any { it != null } -> "Dia em andamento · saldo pendente"; else -> "Registre as quatro batidas ou corrija um dia anterior." }, color = text, style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ClockMetric("Créditos", TimeClock.duration(totals.credit, true), text, secondary, tile, Modifier.weight(1f))
                ClockMetric("Débitos", TimeClock.duration(-totals.debit), text, secondary, tile, Modifier.weight(1f))
                ClockMetric("Saldo", TimeClock.duration(adjustedBalance, true), text, secondary, tile, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { editingDate = selectedDate }, modifier = Modifier.weight(1f)) { Text("Corrigir dia", color = accent) }
                Button(onClick = { showReport = true }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = onAccent)) { Text("Relatório") }
            }
            OutlinedButton(onClick = { selectDate(edit = true) }, modifier = Modifier.fillMaxWidth()) {
                Text("Adicionar ponto anterior / esquecido", color = accent)
            }
            TextButton(onClick = { showTools = true }, modifier = Modifier.fillMaxWidth()) { Text("Escalas, lembretes e compensações", color = accent) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
    confirmation?.let { (index, minute) ->
        AlertDialog(onDismissRequest = { confirmation = null }, title = { Text("Registrar ${TimeClock.labels[index].lowercase()}?") },
            text = { Text("${TimeClock.displayDate(selectedDate)} às ${TimeClock.time(minute)}") },
            confirmButton = { TextButton(onClick = {
                runCatching { repository.punch(selectedDate, index, minute, selectedDate in manualFolgas) }
                    .onSuccess { revision++; error = null }.onFailure { error = it.message }
                confirmation = null
            }) { Text("Registrar") } }, dismissButton = { TextButton(onClick = { confirmation = null }) { Text("Cancelar") } })
    }
    if (showHours) WorkHoursDialog(hours, closingDay, onDismiss = { showHours = false }) { value, closing ->
        repository.saveHours(value); repository.saveClosingDay(closing); revision++; showHours = false
    }
    editingDate?.let { date ->
        val day = days.firstOrNull { it.date == date } ?: Workday(date, expectedMinutes = hours.expectedMinutes, dayOff = date in manualFolgas, scheduledStart = hours.start, scheduledBreak = hours.breakMinutes)
        WorkdayEditor(day, onDismiss = { editingDate = null }) { value -> repository.save(value); revision++; editingDate = null }
    }
    if (showTools) ClockToolsDialog(repository, selectedDate, onDismiss = { showTools = false }) { revision++ }
    if (showReport) TimeClockReport(month, closingDay, days, manualFolgas, adjustments, palette, onDismiss = { showReport = false }, onEdit = { showReport = false; selectedDate = it; editingDate = it })
}

@Composable
private fun ClockMetric(label: String, value: String, text: Color, secondary: Color, tile: Color, modifier: Modifier) {
    Column(modifier.background(tile, RoundedCornerShape(14.dp)).padding(10.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = secondary)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = text)
    }
}

@Composable
private fun ClockTimeField(label: String, value: Int, onChange: (Int) -> Unit) {
    val context = LocalContext.current
    OutlinedButton(onClick = { TimePickerDialog(context, { _, h, m -> onChange(h * 60 + m) }, value / 60, value % 60, true).show() }, modifier = Modifier.fillMaxWidth()) {
        Text("$label · ${TimeClock.time(value)}")
    }
}

@Composable
private fun WorkHoursDialog(initial: WorkHours, initialClosing: Int, onDismiss: () -> Unit, onSave: (WorkHours, Int) -> Unit) {
    var start by remember { mutableIntStateOf(initial.start) }
    var end by remember { mutableIntStateOf(initial.end) }
    var lunch by remember { mutableIntStateOf(initial.breakMinutes) }
    var closing by remember { mutableStateOf(initialClosing.toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Minha jornada") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ClockTimeField("Entrada prevista", start) { start = it }
            ClockTimeField("Saída prevista", end) { end = it }
            ClockTimeField("Duração do almoço", lunch) { lunch = it }
            OutlinedTextField(closing, onValueChange = { closing = it.filter(Char::isDigit).take(2) }, label = { Text("Dia de fechamento (0 = mês civil)") }, singleLine = true)
            Text("Nos relatórios enviados: fechamento 24, período de 25 a 24.", style = MaterialTheme.typography.bodySmall)
            Text("As alterações valem para novos dias. O histórico mantém a jornada registrada em cada data.", style = MaterialTheme.typography.bodySmall)
            val hours = WorkHours(start, end, lunch)
            if (hours.expectedMinutes > 0) Text("Jornada líquida: ${TimeClock.duration(hours.expectedMinutes)}")
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(onClick = { runCatching { WorkHours(start, end, lunch).also { it.validate(); val close = closing.toIntOrNull(); require(close != null && close in 0..31) { "Informe fechamento entre 0 e 31." }; onSave(it, close) } }.onFailure { error = it.message } }) { Text("Salvar jornada") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
private fun WorkdayEditor(initial: Workday, onDismiss: () -> Unit, onSave: (Workday) -> Unit) {
    var times by remember(initial.date) { mutableStateOf(initial.punches.map { it?.let { t -> "%02d:%02d".format(t % 1440 / 60, t % 60) } ?: "" }) }
    var dayOff by remember(initial.date) { mutableStateOf(initial.dayOff) }
    var absent by remember(initial.date) { mutableStateOf(initial.absent) }
    var note by remember(initial.date) { mutableStateOf(initial.note) }
    var scheduledStart by remember(initial.date) { mutableIntStateOf(initial.scheduledStart) }
    var scheduledBreak by remember(initial.date) { mutableIntStateOf(initial.scheduledBreak) }
    var holiday by remember(initial.date) { mutableStateOf(initial.holiday) }
    var extraTimes by remember(initial.date) { mutableStateOf(List(4) { index -> initial.extras.getOrNull(index)?.let { "%02d:%02d".format(it % 1440 / 60, it % 60) } ?: "" }) }
    var expected by remember(initial.date) { mutableIntStateOf(initial.expectedMinutes) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Dia ${TimeClock.displayDate(initial.date)}") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Use HH:mm. Horários que cruzam meia-noite aparecem como +1d.", style = MaterialTheme.typography.bodySmall)
            times.forEachIndexed { index, value ->
                OutlinedTextField(value, onValueChange = { updated -> times = times.toMutableList().apply { this[index] = updated.take(5) } }, label = { Text(TimeClock.labels[index]) }, singleLine = true, enabled = !absent, modifier = Modifier.fillMaxWidth())
            }
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Folga / dia sem jornada", Modifier.weight(1f)); Switch(dayOff, onCheckedChange = { dayOff = it; if (it) absent = false }) }
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Ausência (debitar jornada)", Modifier.weight(1f)); Switch(absent, onCheckedChange = { absent = it; if (it) dayOff = false }) }
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Feriado", Modifier.weight(1f)); Switch(holiday, onCheckedChange = { holiday = it; if (it) absent = false }) }
            ClockTimeField("Entrada prevista neste dia", scheduledStart) { scheduledStart = it }
            ClockTimeField("Almoço previsto neste dia", scheduledBreak) { scheduledBreak = it }
            ClockTimeField("Jornada prevista neste dia", expected) { expected = it }
            Text("Períodos extras (opcional)", fontWeight = FontWeight.Bold)
            Text("Informe os pares entrada/saída separados da jornada normal, como no espelho enviado. Não repita horários já contados.", style = MaterialTheme.typography.bodySmall)
            extraTimes.forEachIndexed { index, value ->
                OutlinedTextField(value, onValueChange = { updated -> extraTimes = extraTimes.toMutableList().apply { this[index] = updated.take(5) } }, label = { Text("Extra ${index / 2 + 1} · ${if (index % 2 == 0) "entrada" else "saída"}") }, enabled = !absent, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            OutlinedTextField(note, onValueChange = { note = it.take(500) }, label = { Text("Observação (opcional)") }, modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(onClick = {
        runCatching {
            val punches = if (absent) List(4) { null } else TimeClock.normalize(times.map { value -> if (value.isBlank()) null else requireNotNull(TimeClock.parseTime(value)) { "Informe horários válidos no formato HH:mm." } })
            val extras = if (absent) emptyList() else extraTimes.chunked(2).flatMap { pair ->
                if (pair.all { it.isBlank() }) emptyList() else {
                    require(pair.all { it.isNotBlank() }) { "Complete a entrada e saída do período extra." }
                    val values = pair.map { requireNotNull(TimeClock.parseTime(it)) { "Informe horários extras válidos." } }
                    listOf(values[0], values[1] + if (values[1] < values[0]) 1440 else 0)
                }
            }
            onSave(initial.copy(punches = punches, expectedMinutes = expected, dayOff = dayOff, absent = absent, note = note.trim(), edited = true,
                scheduledStart = scheduledStart, scheduledBreak = scheduledBreak, holiday = holiday, extras = extras))
        }.onFailure { error = it.message ?: "Confira os horários." }
    }) { Text("Salvar dia") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
private fun TimeClockReport(initialMonth: String, closingDay: Int, days: List<Workday>, folgas: Set<String>, adjustments: List<ClockAdjustment>, palette: ProfileThemePalette?, onDismiss: () -> Unit, onEdit: (String) -> Unit) {
    val context = LocalContext.current
    var exportError by remember { mutableStateOf<String?>(null) }
    var month by remember { mutableStateOf(initialMonth) }
    val period = TimeClock.periodDates(month, closingDay)
    val ledger = adjustments.filter { it.date in period }
    val records = days.filter { it.date in period }.associateBy { it.date }
    val totals = TimeClock.totals(records.values.toList())
    val text = palette?.text ?: MaterialTheme.colorScheme.onSurface
    val secondary = palette?.secondaryText ?: MaterialTheme.colorScheme.onSurfaceVariant
    val accent = palette?.accent ?: MaterialTheme.colorScheme.primary
    val tile = palette?.tile ?: MaterialTheme.colorScheme.surfaceContainer
    val monthLabel = java.text.SimpleDateFormat("MMMM/yyyy", Locale("pt", "BR")).format(TimeClock.calendar("$month-01").time).replaceFirstChar { it.uppercase() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(Modifier.fillMaxSize().padding(12.dp), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = palette?.section ?: MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Espelho do ponto", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = text)
                    TextButton(onClick = onDismiss) { Text("Fechar", color = accent) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { month = TimeClock.shiftMonth(month, -1) }) { Text("‹", color = accent) }
                    Text(monthLabel, style = MaterialTheme.typography.titleMedium, color = text)
                    TextButton(onClick = { month = TimeClock.shiftMonth(month, 1) }) { Text("›", color = accent) }
                }
                Text("${TimeClock.displayDate(period.first())} a ${TimeClock.displayDate(period.last())}", style = MaterialTheme.typography.bodySmall, color = secondary)
                Text("Trabalhadas ${TimeClock.duration(totals.worked)} · previstas ${TimeClock.duration(totals.expected)}", style = MaterialTheme.typography.bodySmall, color = secondary)
                Text("Créditos ${TimeClock.duration(totals.credit, true)} · débitos ${TimeClock.duration(-totals.debit)} · saldo ${TimeClock.duration(totals.balance + ledger.sumOf { it.minutes }, true)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = text)
                Text("Compensações no período: ${TimeClock.duration(ledger.sumOf { it.minutes }, true)}", color = secondary, style = MaterialTheme.typography.bodySmall)
                Text("${totals.pending} dia(s) pendente(s). Sem batidas não gera débito; registre ausência para descontar. Controle em minutos reais, sem tolerância automática, conversão noturna ou pagamento de adicionais.", style = MaterialTheme.typography.bodySmall, color = secondary, modifier = Modifier.padding(vertical = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(true to "Exportar PDF", false to "Planilha CSV").forEach { (pdf, label) ->
                        OutlinedButton(onClick = { runCatching { ClockExport.share(context, month, closingDay, days, folgas, adjustments, pdf) }.onFailure { exportError = "Não foi possível exportar. Tente novamente." } }) { Text(label, color = accent) }
                    }
                }
                exportError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ledger, key = { "adjustment:" + it.id }) { entry ->
                        Text("${TimeClock.displayDate(entry.date)} · compensação ${TimeClock.duration(entry.minutes, true)} · ${entry.reason}", color = text, modifier = Modifier.padding(8.dp))
                    }
                    items(period, key = { it }) { date ->
                        val day = records[date]
                        val future = date > TimeClock.today()
                        Column(Modifier.fillMaxWidth().background(tile, RoundedCornerShape(14.dp)).clickable(enabled = !future) { onEdit(date) }.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(TimeClock.displayDate(date), fontWeight = FontWeight.Bold, color = text)
                                Text(day?.balanceMinutes?.let { TimeClock.duration(it, true) } ?: if (day?.dayOff == true || date in folgas) "Folga" else if (day?.punches?.any { it != null } == true) "Pendente" else "—", color = text)
                            }
                            Text("Entrada  /  Almoço ida  /  Volta  /  Saída", style = MaterialTheme.typography.labelSmall, color = secondary)
                            Text(day?.punches?.joinToString("  ·  ") { TimeClock.time(it) } ?: "—  ·  —  ·  —  ·  —", style = MaterialTheme.typography.bodyMedium, color = text)
                            if (day != null) {
                                Text(if (day.absent) "Ausência · débito ${TimeClock.duration(day.expectedMinutes)}" else "Trabalhadas ${day.workedMinutes?.let { TimeClock.duration(it) } ?: "pendente"} · previstas ${TimeClock.duration(if (day.dayOff || day.holiday) 0 else day.expectedMinutes)}${if (day.edited) " · registro manual" else ""}", style = MaterialTheme.typography.bodySmall, color = secondary)
                                if (day.extras.isNotEmpty()) Text("Extras: " + day.extras.chunked(2).joinToString(" · ") { "${TimeClock.time(it[0])} → ${TimeClock.time(it[1])}" }, style = MaterialTheme.typography.bodySmall, color = text)
                                day.occurrences().forEach { occurrence -> Text(occurrence, style = MaterialTheme.typography.bodySmall, color = secondary) }
                                if (day.note.isNotBlank()) Text(day.note, style = MaterialTheme.typography.bodySmall, color = text)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClockToolsDialog(repository: TimeClockRepository, date: String, onDismiss: () -> Unit, onChanged: () -> Unit) {
    var enabled by remember { mutableStateOf(repository.reminders()) }
    var name by remember { mutableStateOf("") }
    var start by remember { mutableIntStateOf(repository.hours().start) }
    var end by remember { mutableIntStateOf(repository.hours().end) }
    var lunch by remember { mutableIntStateOf(repository.hours().breakMinutes) }
    var lunchAt by remember { mutableIntStateOf(repository.lunchAt()) }
    var amount by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var debit by remember { mutableStateOf(true) }
    var revision by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    fun action(block: () -> Unit) { runCatching(block).onSuccess { revision++; onChanged(); error = null }.onFailure { error = it.message } }
    val templates = remember(revision) { repository.templates() }
    val adjustments = remember(revision) { repository.adjustments() }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Organizar minha jornada") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Lembretes", fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Lembrar das batidas", Modifier.weight(1f)); Switch(enabled, onCheckedChange = { value -> action { repository.setReminders(value); enabled = value } }) }
            ClockTimeField("Horário do lembrete de almoço", lunchAt) { value -> action { repository.saveLunchAt(value); lunchAt = value } }
            Text("Entrada e saída seguem a jornada do dia. Retorno segue a batida de almoço. Avisos podem atrasar com economia de bateria; precisam da permissão de notificações. Folgas não recebem lembrete.", style = MaterialTheme.typography.bodySmall)
            Text("Escalas diferentes", fontWeight = FontWeight.Bold)
            Text("Aplicar em ${TimeClock.displayDate(date)}. As outras datas permanecem com sua jornada.", style = MaterialTheme.typography.bodySmall)
            templates.forEach { (label, hours) -> OutlinedButton(onClick = { action { repository.applyTemplate(date, hours) } }, modifier = Modifier.fillMaxWidth()) { Text("$label · ${TimeClock.time(hours.start)}–${TimeClock.time(hours.end)}") } }
            OutlinedTextField(name, onValueChange = { name = it.take(40) }, label = { Text("Nome (ex.: Domingo)") })
            ClockTimeField("Entrada", start) { start = it }; ClockTimeField("Saída", end) { end = it }; ClockTimeField("Almoço", lunch) { lunch = it }
            OutlinedButton(onClick = { action { repository.saveTemplate(name, WorkHours(start, end, lunch)); name = "" } }) { Text("Salvar modelo de jornada") }
            Text("Compensação de horas", fontWeight = FontWeight.Bold)
            Text("Movimentação separada em ${TimeClock.displayDate(date)}. Registre horas utilizadas ou ajuste de saldo sem alterar as batidas.", style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically) { Text(if (debit) "Utilizar horas (descontar saldo)" else "Adicionar crédito", Modifier.weight(1f)); Switch(debit, onCheckedChange = { debit = it }) }
            OutlinedTextField(amount, onValueChange = { amount = it.take(5) }, label = { Text("Quantidade HH:mm") }, singleLine = true)
            OutlinedTextField(reason, onValueChange = { reason = it.take(200) }, label = { Text("Motivo") })
            OutlinedButton(onClick = { action {
                val minutes = requireNotNull(TimeClock.parseTime(amount)) { "Informe a duração HH:mm." }
                repository.addAdjustment(ClockAdjustment(java.util.UUID.randomUUID().toString(), date, if (debit) -minutes else minutes, reason.trim())); amount = ""; reason = ""
            } }) { Text("Registrar compensação") }
            adjustments.filter { it.date == date }.forEach { entry ->
                Text("${TimeClock.duration(entry.minutes, true)} · ${entry.reason}")
                TextButton(onClick = { action { repository.removeAdjustment(entry.id) } }) { Text("Remover movimentação") }
            }
            Text("Se a saída antecipada já gerou débito no ponto, não registre o mesmo desconto novamente.", style = MaterialTheme.typography.bodySmall)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Concluir") } })
}
