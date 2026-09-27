package com.example.ui

import android.graphics.BitmapFactory

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.BenefitSummary
import com.example.data.EmployeeProfile
import com.example.data.HoursSummary
import com.example.data.NossaGenteApi
import com.example.data.FirebaseService
import com.example.data.WorkSchedule
import com.example.data.NossaGenteBenefitResult
import com.example.data.NossaGenteHoursResult
import com.example.data.NossaGentePointResult
import com.example.data.NossaGenteProfileResult
import com.example.data.PointEntry
import com.example.data.PointSummary
import com.example.ui.theme.LocalExpressiveStyle
import com.example.ui.theme.LocalExpressiveGlassStyle
import com.example.ui.theme.LocalGlassSoftStyle
import com.example.ui.theme.expressiveLiquidGlass
import com.example.ui.theme.glassSoftShadow
import com.example.ui.theme.expressiveShadow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyPointScreen(api: NossaGenteApi, onNavigateBack: () -> Unit, onSignOut: () -> Unit) {
    val configuration = LocalConfiguration.current
    val expressiveStyle = LocalExpressiveStyle.current
    val expressiveGlassStyle = LocalExpressiveGlassStyle.current
    val glassStyle = LocalGlassSoftStyle.current
    val isExpressive = expressiveStyle.enabled
    val contentPadding = if (configuration.screenWidthDp < 360) 10.dp else 16.dp
    val purchasesMaxHeight = (configuration.screenHeightDp * 0.42f).coerceIn(160f, 420f).dp
    var employeeProfile by remember { mutableStateOf<EmployeeProfile?>(null) }
    var employeePhotoModel by remember { mutableStateOf<Any?>(null) }
    var point by remember { mutableStateOf<PointSummary?>(null) }
    var hours by remember { mutableStateOf<HoursSummary?>(null) }
    // O card permanece visível mesmo quando o endpoint ainda não devolveu
    // compras, para o usuário sempre ter acesso à área de convênio.
    var benefit by remember { mutableStateOf<BenefitSummary?>(BenefitSummary()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showBenefitDetails by remember { mutableStateOf(false) }
    var benefitNotifications by remember { mutableStateOf(false) }
    var hoursNotifications by remember { mutableStateOf(false) }
    var dayOffNotifications by remember { mutableStateOf(false) }
    var scheduleNotifications by remember { mutableStateOf(false) }
    var pointNotifications by remember { mutableStateOf(false) }
    var showProfileNotificationSettings by remember { mutableStateOf(false) }
    var workSchedules by remember { mutableStateOf<List<WorkSchedule>>(emptyList()) }
    var selectedScheduleKey by remember { mutableStateOf(currentMonthKey()) }
    var daysOffExpanded by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val credentialStore = remember(context) { com.example.data.NossaGenteCredentialStore(context.applicationContext) }

    fun load() {
        if (loading) return
        if (!api.hasSession()) { onSignOut(); return }
        loading = true
        error = null
        scope.launch {
            when (val result = api.fetchEmployeeProfile()) {
                is NossaGenteProfileResult.Success -> {
                    employeeProfile = result.profile
                    employeePhotoModel = null
                    scope.launch {
                        employeePhotoModel = api.fetchProfilePhoto(result.profile.photoUrl)?.let { bytes ->
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        }
                    }
                }
                NossaGenteProfileResult.Unauthorized -> {
                    onSignOut()
                    loading = false
                    return@launch
                }
                is NossaGenteProfileResult.Error -> if (error == null) error = result.message
            }
            when (val result = api.fetchHours()) {
                is NossaGenteHoursResult.Success -> hours = result.hours
                NossaGenteHoursResult.Unauthorized -> onSignOut()
                is NossaGenteHoursResult.Error -> error = result.message
            }
            when (val result = api.fetchPoint()) {
                is NossaGentePointResult.Success -> point = result.point
                NossaGentePointResult.Unauthorized -> onSignOut()
                is NossaGentePointResult.Error -> error = result.message
            }
            when (val result = api.fetchBenefit()) {
                is NossaGenteBenefitResult.Success -> benefit = result.benefit
                NossaGenteBenefitResult.Unauthorized -> onSignOut()
                is NossaGenteBenefitResult.Error -> if (error == null) error = result.message
            }
            workSchedules = FirebaseService.fetchWorkSchedules()
            val currentMonth = currentMonthKey()
            selectedScheduleKey = workSchedules.firstOrNull { it.monthKey == currentMonth }?.monthKey
                ?: workSchedules.firstOrNull()?.monthKey ?: currentMonth
            val profileRegistration = employeeProfile?.registration.orEmpty().filter(Char::isDigit)
            com.example.util.ScheduleReminderWorker.cacheSchedules(context, workSchedules, profileRegistration)
            if (credentialStore.isDayOffNotificationsEnabled() || credentialStore.isScheduleNotificationsEnabled())
                com.example.util.ScheduleReminderWorker.schedule(context)
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        benefitNotifications = credentialStore.isBenefitNotificationsEnabled()
        hoursNotifications = credentialStore.isHoursNotificationsEnabled()
        dayOffNotifications = credentialStore.isDayOffNotificationsEnabled()
        scheduleNotifications = credentialStore.isScheduleNotificationsEnabled()
        pointNotifications = credentialStore.isPointNotificationsEnabled()
        loading = false
        load()
    }

    Scaffold(
        containerColor = if (glassStyle.enabled || isExpressive) androidx.compose.ui.graphics.Color.Transparent else MaterialTheme.colorScheme.background,
        topBar = {
        TopAppBar(
            title = {
                Text(
                    "Meu Perfil",
                    fontWeight = if (isExpressive) androidx.compose.ui.text.font.FontWeight.ExtraBold else androidx.compose.ui.text.font.FontWeight.Normal
                )
            },
            navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, "Voltar") } },
            actions = {
                IconButton(
                    onClick = ::load,
                    enabled = !loading,
                    modifier = if (isExpressive) Modifier.background(
                        MaterialTheme.colorScheme.primaryContainer,
                        RoundedCornerShape(16.dp)
                    ) else Modifier
                ) {
                    if (loading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Refresh, "Atualizar dados do Nossa Gente")
                    }
                }
                IconButton(
                    onClick = { showProfileNotificationSettings = true },
                    modifier = if (isExpressive) Modifier.background(
                        MaterialTheme.colorScheme.secondaryContainer,
                        RoundedCornerShape(16.dp)
                    ) else Modifier
                ) {
                    Icon(Icons.Default.Settings, "Configurações de notificações do perfil")
                }
                TextButton(
                    onClick = onSignOut,
                    shape = if (isExpressive) RoundedCornerShape(18.dp) else MaterialTheme.shapes.small
                ) { Text("Sair") }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = when {
                    glassStyle.enabled -> MaterialTheme.colorScheme.surface.copy(alpha = glassStyle.surfaceAlpha)
                    isExpressive -> androidx.compose.ui.graphics.Color.Transparent
                    else -> MaterialTheme.colorScheme.surface
                }
            )
        )
    }) { padding ->
        if (loading && hours == null && point == null) {
            Column(Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator() }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(contentPadding), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    employeeProfile?.let { profile ->
                        EmployeeProfileCard(
                            profile = profile,
                            photoModel = employeePhotoModel ?: profile.photoUrl,
                            isExpressive = isExpressive,
                            glassEnabled = glassStyle.enabled,
                            expressiveGlassEnabled = expressiveGlassStyle.enabled
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    MyDaysOffCard(
                        schedules = workSchedules,
                        selectedKey = selectedScheduleKey,
                        expanded = daysOffExpanded,
                        registration = employeeProfile?.registration,
                        onToggle = { daysOffExpanded = !daysOffExpanded },
                        onSelectMonth = { selectedScheduleKey = it }
                    )
                    hours?.let { summary ->
                        val hoursShape = if (isExpressive) RoundedCornerShape(30.dp) else MaterialTheme.shapes.medium
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .glassSoftShadow(hoursShape, if (isExpressive) 4.dp else 0.dp)
                                .expressiveShadow(hoursShape, 7.dp),
                            shape = hoursShape,
                            colors = CardDefaults.cardColors(
                                containerColor = if (glassStyle.enabled) MaterialTheme.colorScheme.surface
                                else MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Column(Modifier.fillMaxWidth().padding(if (isExpressive) 18.dp else 16.dp)) {
                                Text(
                                    "Banco de horas",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = if (isExpressive) androidx.compose.ui.text.font.FontWeight.ExtraBold else androidx.compose.ui.text.font.FontWeight.Normal
                                )
                                Spacer(Modifier.height(8.dp)); Text("Saldo atual: ${summary.total}", style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(10.dp)); Text("Saldos a vencer", style = MaterialTheme.typography.titleMedium)
                                summary.months.forEach { month ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("${month.month}/${month.year}", modifier = Modifier.weight(1f))
                                        Text(month.balance)
                                    }
                                }
                            }
                        }
                    }
                    point?.takeIf { summary ->
                        !summary.period.isNullOrBlank() || !summary.status.isNullOrBlank() ||
                            !summary.worked.isNullOrBlank() || !summary.balance.isNullOrBlank() || summary.records.isNotEmpty()
                    }?.let { summary ->
                        val pointShape = if (isExpressive) RoundedCornerShape(28.dp) else MaterialTheme.shapes.medium
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .glassSoftShadow(pointShape, if (isExpressive) 3.dp else 0.dp)
                                .expressiveShadow(pointShape, 6.dp),
                            shape = pointShape,
                            colors = CardDefaults.cardColors(
                                containerColor = if (glassStyle.enabled) MaterialTheme.colorScheme.surface
                                else MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Column(Modifier.fillMaxWidth().padding(if (isExpressive) 18.dp else 16.dp)) {
                                Text(
                                    summary.period ?: "Período atual",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = if (isExpressive) androidx.compose.ui.text.font.FontWeight.ExtraBold else androidx.compose.ui.text.font.FontWeight.Normal
                                )
                                summary.status?.let { Text("Status: $it") }; summary.worked?.let { Text("Horas trabalhadas: $it") }; summary.balance?.let { Text("Saldo: $it") }
                            }
                        }
                    }
                    benefit?.let { summary ->
                        val benefitShape = if (isExpressive) RoundedCornerShape(30.dp) else MaterialTheme.shapes.medium
                        Card(
                            onClick = { showBenefitDetails = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .glassSoftShadow(benefitShape, if (isExpressive) 4.dp else 0.dp)
                                .expressiveShadow(benefitShape, 7.dp),
                            shape = benefitShape,
                            colors = CardDefaults.cardColors(
                                containerColor = if (glassStyle.enabled) MaterialTheme.colorScheme.surface
                                else MaterialTheme.colorScheme.tertiaryContainer
                            )
                        ) {
                            Column(Modifier.fillMaxWidth().padding(if (isExpressive) 18.dp else 16.dp)) {
                                Text(
                                    "Convênio",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = if (isExpressive) androidx.compose.ui.text.font.FontWeight.ExtraBold else androidx.compose.ui.text.font.FontWeight.Normal
                                )
                                summary.period?.let { Text("Período: $it") }
                                summary.updatedAt?.let { Text("Atualizado em: $it", style = MaterialTheme.typography.bodySmall) }
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Limite: ${summary.limit ?: "—"}")
                                    Text("Gasto: ${summary.spent ?: "—"}")
                                    Text("Saldo: ${summary.balance ?: "—"}")
                                }
                                Text("Toque para ver as compras", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
                }
                if (point?.records.isNullOrEmpty()) item { Text("Nenhum registro de ponto disponível para o período informado.") }
                else items(point!!.records) { PointEntryCard(it) }
            }
        }
    }
    if (showBenefitDetails) {
        Dialog(
            onDismissRequest = { showBenefitDetails = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            val dialogShape = if (isExpressive) RoundedCornerShape(32.dp) else MaterialTheme.shapes.large
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = contentPadding)
                    .heightIn(max = (configuration.screenHeightDp * 0.88f).dp)
                    .glassSoftShadow(dialogShape, if (isExpressive) 6.dp else 0.dp)
                    .expressiveShadow(dialogShape, 9.dp),
                shape = dialogShape,
                colors = CardDefaults.cardColors(
                    containerColor = if (glassStyle.enabled) MaterialTheme.colorScheme.surface
                    else MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Convênio",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = if (isExpressive) androidx.compose.ui.text.font.FontWeight.ExtraBold else androidx.compose.ui.text.font.FontWeight.Normal
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Notificações do Convênio", style = MaterialTheme.typography.titleMedium)
                            Text("Gerencie as notificações do perfil pela engrenagem no topo da tela.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    androidx.compose.material3.HorizontalDivider()
                    Text("Compras", style = MaterialTheme.typography.titleMedium)
                    Box(Modifier.weight(1f, fill = false).fillMaxWidth()) {
                        BenefitPurchasesList(benefit, maxHeight = purchasesMaxHeight)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showBenefitDetails = false }) { Text("Fechar") }
                    }
                }
            }
        }
    }
    if (showProfileNotificationSettings) {
        Dialog(onDismissRequest = { showProfileNotificationSettings = false }) {
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Notificações do Meu Perfil", style = MaterialTheme.typography.titleLarge)
                    ProfileNotificationSwitch("Banco de horas", hoursNotifications) { enabled ->
                        hoursNotifications = enabled; credentialStore.setHoursNotificationsEnabled(enabled)
                        if (enabled) com.example.util.HoursNotificationWorker.schedule(context, resetSnapshot = true) else com.example.util.HoursNotificationWorker.cancel(context)
                    }
                    ProfileNotificationSwitch("Atualizações de ponto", pointNotifications) { enabled ->
                        pointNotifications = enabled; credentialStore.setPointNotificationsEnabled(enabled)
                        if (enabled) com.example.util.PointNotificationWorker.schedule(context, resetSnapshot = true) else com.example.util.PointNotificationWorker.cancel(context)
                    }
                    ProfileNotificationSwitch("Convênio", benefitNotifications) { enabled ->
                        benefitNotifications = enabled; credentialStore.setBenefitNotificationsEnabled(enabled)
                        if (enabled) com.example.util.BenefitNotificationWorker.schedule(context, resetSnapshot = true) else com.example.util.BenefitNotificationWorker.cancel(context)
                    }
                    ProfileNotificationSwitch("Lembretes de folga (véspera e no dia)", dayOffNotifications) { enabled ->
                        dayOffNotifications = enabled; credentialStore.setDayOffNotificationsEnabled(enabled)
                        if (enabled) com.example.util.ScheduleReminderWorker.schedule(context) else if (!scheduleNotifications) com.example.util.ScheduleReminderWorker.cancel(context)
                    }
                    ProfileNotificationSwitch("Escala inserida ou alterada", scheduleNotifications) { enabled ->
                        scheduleNotifications = enabled; credentialStore.setScheduleNotificationsEnabled(enabled)
                        if (enabled) com.example.util.ScheduleReminderWorker.schedule(context)
                        else if (!dayOffNotifications) com.example.util.ScheduleReminderWorker.cancel(context)
                    }
                    Text("Os lembretes de folga usam a escala publicada e a matrícula do perfil Nossa Gente.", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { showProfileNotificationSettings = false }) { Text("Concluir") }
                }
            }
        }
    }
}

@Composable
private fun ProfileNotificationSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun MyDaysOffCard(
    schedules: List<WorkSchedule>, selectedKey: String, expanded: Boolean, registration: String?,
    onToggle: () -> Unit, onSelectMonth: (String) -> Unit
) {
    var selectorExpanded by remember { mutableStateOf(false) }
    val selected = schedules.firstOrNull { it.monthKey == selectedKey }
    val digits = registration.orEmpty().filter(Char::isDigit)
    val employee = selected?.employees?.firstOrNull { it.registration.filter(Char::isDigit) == digits }
    Card(Modifier.fillMaxWidth(), shape = if (LocalExpressiveStyle.current.enabled) RoundedCornerShape(28.dp) else MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Minhas Folgas", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                IconButton(onClick = onToggle) { Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, "Expandir Minhas Folgas") }
            }
            if (expanded) {
                androidx.compose.foundation.layout.Box {
                    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)) {
                        TextButton(onClick = { selectorExpanded = true }, modifier = Modifier.padding(horizontal = 6.dp)) {
                            val label = selected?.let { "${monthName(it.month)}/${it.year}" } ?: "Escala do mês atual indisponível"
                            Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("$label  ▾", style = MaterialTheme.typography.titleSmall)
                        }
                    }
                    DropdownMenu(expanded = selectorExpanded, onDismissRequest = { selectorExpanded = false }) {
                        schedules.forEach { schedule ->
                            val label = "${monthName(schedule.month)}/${schedule.year}"
                            DropdownMenuItem(text = { Text(label) }, onClick = { onSelectMonth(schedule.monthKey); selectorExpanded = false })
                        }
                    }
                }
                when {
                    registration.isNullOrBlank() -> Text("A matrícula do perfil Nossa Gente não está disponível para localizar sua escala.")
                    selected == null -> Text("Ainda não há escala publicada para este mês.")
                    employee == null -> Text("Não encontramos a matrícula ${registration} nesta escala.")
                    else -> {
                        Spacer(Modifier.height(14.dp))
                        Text("Matrícula ${employee.registration}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (employee.shift.isNotBlank()) Text(employee.shift, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(12.dp))
                        Text("Folgas", style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        if (employee.daysOff.isEmpty()) Text("Nenhuma folga registrada.")
                        else employee.daysOff.distinct().sorted().chunked(4).forEach { week ->
                            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                week.forEach { day ->
                                    val sunday = java.util.GregorianCalendar(selected.year, selected.month - 1, day)
                                        .get(java.util.Calendar.DAY_OF_WEEK) == java.util.Calendar.SUNDAY
                                    Box(Modifier.weight(1f).height(66.dp).background(
                                        if (sunday) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                        RoundedCornerShape(14.dp)
                                    ), contentAlignment = Alignment.Center) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(day.toString().padStart(2, '0'), style = MaterialTheme.typography.titleMedium,
                                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                            if (sunday) Text("Domingo", style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                                repeat(4 - week.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                        if (employee.vacationDays.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Text("Férias (FE): ${employee.vacationDays.sorted().joinToString(", ")}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

private fun currentMonthKey(): String {
    val calendar = java.util.Calendar.getInstance()
    return "%04d-%02d".format(calendar.get(java.util.Calendar.YEAR), calendar.get(java.util.Calendar.MONTH) + 1)
}

private fun monthName(month: Int): String {
    val calendar = java.util.Calendar.getInstance().apply { set(java.util.Calendar.MONTH, (month - 1).coerceIn(0, 11)) }
    return calendar.getDisplayName(java.util.Calendar.MONTH, java.util.Calendar.LONG, java.util.Locale("pt", "BR"))
        ?.replaceFirstChar { it.uppercase() } ?: "Mês"
}

@Composable
private fun EmployeeProfileCard(
    profile: EmployeeProfile,
    photoModel: Any?,
    isExpressive: Boolean,
    glassEnabled: Boolean,
    expressiveGlassEnabled: Boolean
) {
    val expressiveGlass = LocalExpressiveGlassStyle.current
    var showProfilePhoto by remember(photoModel) { mutableStateOf(false) }
    val shape = if (isExpressive) {
        RoundedCornerShape(topStart = 34.dp, topEnd = 24.dp, bottomEnd = 32.dp, bottomStart = 26.dp)
    } else {
        MaterialTheme.shapes.large
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                when {
                    expressiveGlassEnabled -> Modifier.expressiveLiquidGlass(
                        shape = shape,
                        accent = expressiveGlass.accent,
                        secondaryAccent = expressiveGlass.secondaryAccent,
                        intensity = 0.96f,
                        elevation = 8.dp,
                        waves = true,
                        bubbleSeed = profile.name.orEmpty().hashCode()
                    )
                    else -> Modifier
                        .glassSoftShadow(shape, if (isExpressive) 4.dp else 0.dp)
                        .expressiveShadow(shape, 7.dp)
                }
            ),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = when {
                expressiveGlassEnabled -> Color.Transparent
                glassEnabled -> MaterialTheme.colorScheme.surface
                else -> MaterialTheme.colorScheme.primaryContainer
            }
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(if (isExpressive) 18.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                NossaGenteProfileAvatar(
                    photoModel = photoModel,
                    size = if (isExpressive) 52.dp else 48.dp,
                    iconSize = if (isExpressive) 28.dp else 26.dp,
                    shape = RoundedCornerShape(if (isExpressive) 18.dp else 16.dp),
                    backgroundColor = if (expressiveGlassEnabled) {
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.46f)
                    } else {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    },
                    iconTint = MaterialTheme.colorScheme.primary,
                    onPhotoClick = if (photoModel != null) {
                        { showProfilePhoto = true }
                    } else null
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Nome do usuário",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        profile.name ?: "—",
                        style = if (isExpressive) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Dados sincronizados com o Nossa Gente",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ProfileMetric(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.AccessTime,
                    label = "Tempo de Casa",
                    value = profile.tenure ?: "—",
                    isExpressive = isExpressive
                )
                ProfileMetric(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.CalendarToday,
                    label = "Admissão",
                    value = profile.admissionDate ?: "—",
                    isExpressive = isExpressive
                )
            }
        }
    }

    if (showProfilePhoto && photoModel != null) {
        NossaGenteProfilePhotoDialog(
            photoModel = photoModel,
            userName = profile.name,
            onDismiss = { showProfilePhoto = false }
        )
    }
}

@Composable
private fun ProfileMetric(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    isExpressive: Boolean
) {
    val shape = RoundedCornerShape(if (isExpressive) 20.dp else 16.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = if (isExpressive) 0.62f else 0.78f))
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun BenefitPurchasesList(benefit: BenefitSummary?, maxHeight: androidx.compose.ui.unit.Dp) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val thumbHeight = 52.dp

    BoxWithConstraints(modifier = Modifier.fillMaxWidth().heightIn(max = maxHeight)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(end = 22.dp).verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (benefit?.purchases.isNullOrEmpty()) {
                Text("Nenhuma compra informada pela API.")
            } else {
                benefit!!.purchases.forEach { purchase ->
                    Text(formatBenefitDate(purchase.date, purchase.time))
                    Text("${purchase.place ?: "Local não informado"} · ${purchase.amount ?: "Valor não informado"}")
                    purchase.description?.let {
                        Text("Desconta em: ${formatBenefitDate(it)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        if (scrollState.maxValue > 0) {
            val availablePx = with(density) { (maxHeight - thumbHeight).coerceAtLeast(1.dp).toPx() }
            val thumbOffsetPx = ((scrollState.value.toFloat() / scrollState.maxValue) * availablePx).roundToInt()
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .fillMaxHeight()
                    .width(20.dp)
                    .pointerInput(scrollState.maxValue, availablePx) {
                        detectTapGestures { position ->
                            val target = ((position.y / size.height) * scrollState.maxValue)
                                .roundToInt().coerceIn(0, scrollState.maxValue)
                            scope.launch { scrollState.animateScrollTo(target) }
                        }
                    }
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxHeight()
                        .width(4.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset { IntOffset(0, thumbOffsetPx) }
                        .width(10.dp)
                        .height(thumbHeight)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primary)
                        .pointerInput(scrollState.maxValue, availablePx) {
                            detectVerticalDragGestures { change, dragAmount ->
                                change.consume()
                                val delta = (dragAmount / availablePx * scrollState.maxValue).roundToInt()
                                scope.launch {
                                    scrollState.scrollTo((scrollState.value + delta).coerceIn(0, scrollState.maxValue))
                                }
                            }
                        }
                )
            }
        }
    }
}

private fun formatBenefitDate(date: String?, separateTime: String? = null): String {
    val raw = date?.trim().orEmpty()
    if (raw.isBlank()) return "Data não informada"
    val iso = Regex("^(\\d{4})-(\\d{2})-(\\d{2})(?:[T ](\\d{2}):(\\d{2})(?::\\d{2})?)?.*$")
        .matchEntire(raw)
    if (iso != null) {
        val (year, month, day, hour, minute) = iso.destructured
        val time = separateTime?.trim().orEmpty().takeIf { it.isNotBlank() }
            ?: if (hour.isNotBlank() && (hour != "00" || minute != "00")) "$hour:$minute" else null
        return "$day/$month/$year" + (time?.let { " às ${it.take(5)}" } ?: "")
    }
    val brDate = Regex("^(\\d{2}/\\d{2}/\\d{4})(?:[ T](\\d{2}):(\\d{2})(?::\\d{2})?)?.*$")
        .matchEntire(raw)
    if (brDate != null) {
        val (dayMonthYear, hour, minute) = brDate.destructured
        val time = separateTime?.trim().orEmpty().takeIf { it.isNotBlank() }
            ?: if (hour.isNotBlank() && (hour != "00" || minute != "00")) "$hour:$minute" else null
        return dayMonthYear + (time?.let { " às ${it.take(5)}" } ?: "")
    }
    return raw.removeSuffix(" 00:00:00")
}

@Composable
private fun PointEntryCard(entry: PointEntry) {
    val expressive = LocalExpressiveStyle.current.enabled
    val glassStyle = LocalGlassSoftStyle.current
    val cardShape = if (expressive) RoundedCornerShape(24.dp) else MaterialTheme.shapes.medium
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .glassSoftShadow(cardShape, if (expressive) 3.dp else 0.dp)
            .expressiveShadow(cardShape, 6.dp),
        shape = cardShape,
        colors = CardDefaults.cardColors(
            containerColor = if (glassStyle.enabled) MaterialTheme.colorScheme.surface
            else if (expressive) MaterialTheme.colorScheme.surfaceContainerLow
            else MaterialTheme.colorScheme.surface
        )
    ) { Column(Modifier.fillMaxWidth().padding(if (expressive) 16.dp else 14.dp)) {
        Row {
            Icon(
                Icons.Default.AccessTime,
                contentDescription = null,
                tint = if (expressive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Text(
                entry.date ?: "Dia não informado",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (expressive) androidx.compose.ui.text.font.FontWeight.ExtraBold else androidx.compose.ui.text.font.FontWeight.Normal,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        Spacer(Modifier.height(5.dp)); Text("Entrada: ${entry.entry ?: "—"}   Saída: ${entry.exit ?: "—"}")
        entry.interval?.let { Text("Intervalo: $it") }; entry.status?.let { Text("Status: $it", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    } }
}
