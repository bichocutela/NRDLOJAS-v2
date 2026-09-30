package com.example.ui

import android.graphics.BitmapFactory
import android.net.Uri

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CameraAlt
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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
import com.example.data.WorkScheduleEmployee
import com.example.data.NossaGenteBenefitResult
import com.example.data.NossaGenteHoursResult
import com.example.data.NossaGentePointResult
import com.example.data.NossaGenteProfileResult
import com.example.data.PointEntry
import com.example.data.PointSummary
import com.example.ui.theme.LocalCardAppearanceSettings
import com.example.ui.theme.LocalCardThemeKey
import com.example.ui.theme.LocalExpressiveStyle
import com.example.ui.theme.LocalExpressiveGlassStyle
import com.example.ui.theme.LocalGlassSoftStyle
import com.example.ui.theme.expressiveLiquidGlass
import com.example.ui.theme.glassSoftShadow
import com.example.ui.theme.expressiveShadow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MyPointScreen(
    api: NossaGenteApi,
    onNavigateBack: () -> Unit,
    onSignOut: () -> Unit,
    focusSection: String? = null,
    focusRequestKey: Long = 0L
) {
    val configuration = LocalConfiguration.current
    val expressiveStyle = LocalExpressiveStyle.current
    val expressiveGlassStyle = LocalExpressiveGlassStyle.current
    val glassStyle = LocalGlassSoftStyle.current
    val isExpressive = expressiveStyle.enabled
    val cardAppearance = LocalCardAppearanceSettings.current
    val cardThemeKey = LocalCardThemeKey.current
    val profileThemeKey = if (glassStyle.enabled) glassStyle.accentName else cardThemeKey
    val isDarkProfile = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val profilePalette = if (expressiveGlassStyle.enabled) null else remember(profileThemeKey, isDarkProfile) {
        profileThemePalette(profileThemeKey, isDarkProfile)
    }
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
    var daysOffExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val profileListState = rememberLazyListState()
    val daysOffRequester = remember { BringIntoViewRequester() }
    val hoursRequester = remember { BringIntoViewRequester() }
    val pointRequester = remember { BringIntoViewRequester() }
    val benefitRequester = remember { BringIntoViewRequester() }
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
            val profileRegistration = employeeProfile?.registration.orEmpty().filter(Char::isDigit)
            val today = java.util.Calendar.getInstance()
            val changedPreviousMonth = com.example.util.ScheduleReminderWorker.changedPreviousMonthKey(
                context, workSchedules, profileRegistration, today.get(java.util.Calendar.YEAR), today.get(java.util.Calendar.MONTH) + 1
            )
            selectedScheduleKey = com.example.data.resolveProfileScheduleMonth(
                workSchedules, profileRegistration, today.get(java.util.Calendar.YEAR),
                today.get(java.util.Calendar.MONTH) + 1, today.get(java.util.Calendar.DAY_OF_MONTH), changedPreviousMonth
            )
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

    LaunchedEffect(focusRequestKey, loading, focusSection) {
        if (focusRequestKey <= 0L || loading || focusSection == null) return@LaunchedEffect
        if (focusSection == com.example.util.ProfileNotificationRouting.SECTION_DAYS_OFF) {
            daysOffExpanded = true
        }
        androidx.compose.runtime.withFrameNanos { }
        androidx.compose.runtime.withFrameNanos { }
        when (focusSection) {
            com.example.util.ProfileNotificationRouting.SECTION_PROFILE -> profileListState.animateScrollToItem(0)
            com.example.util.ProfileNotificationRouting.SECTION_DAYS_OFF -> daysOffRequester.bringIntoView()
            com.example.util.ProfileNotificationRouting.SECTION_HOURS -> hoursRequester.bringIntoView()
            com.example.util.ProfileNotificationRouting.SECTION_POINT -> pointRequester.bringIntoView()
            com.example.util.ProfileNotificationRouting.SECTION_BENEFIT -> benefitRequester.bringIntoView()
        }
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
            LazyColumn(state = profileListState, modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(contentPadding), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    employeeProfile?.let { profile ->
                        EmployeeProfileCard(
                            profile = profile,
                            photoModel = employeePhotoModel ?: profile.photoUrl,
                            isExpressive = isExpressive,
                            glassEnabled = glassStyle.enabled,
                            expressiveGlassEnabled = expressiveGlassStyle.enabled,
                            palette = profilePalette
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    MyDaysOffCard(
                        modifier = Modifier.bringIntoViewRequester(daysOffRequester),
                        schedules = workSchedules,
                        selectedKey = selectedScheduleKey,
                        expanded = daysOffExpanded,
                        registration = employeeProfile?.registration,
                        palette = profilePalette,
                        onToggle = { daysOffExpanded = !daysOffExpanded },
                        onSelectMonth = { selectedScheduleKey = it },
                        onDaysOffSaved = { monthKey, days ->
                            val (year, month) = monthKey.split("-").map(String::toInt)
                            val profile = employeeProfile
                            val registration = profile?.registration.orEmpty().filter(Char::isDigit)
                            val existing = workSchedules.firstOrNull { it.monthKey == monthKey }
                            val rows = existing?.employees.orEmpty().toMutableList()
                            val rowIndex = rows.indexOfFirst { it.registration.filter(Char::isDigit) == registration }
                            if (rowIndex >= 0) rows[rowIndex] = rows[rowIndex].copy(daysOff = days)
                            else if (registration.isNotBlank()) rows += WorkScheduleEmployee(
                                registration = registration,
                                name = profile?.name.orEmpty(),
                                daysOff = days,
                                verified = true
                            )
                            val updated = WorkSchedule(monthKey, year, month, rows,
                                System.currentTimeMillis(), (existing?.revision ?: 0) + 1)
                            workSchedules = (workSchedules.filterNot { it.monthKey == monthKey } + updated)
                                .sortedByDescending { it.monthKey }
                            employeeProfile?.registration?.let { com.example.util.ScheduleReminderWorker.cacheSchedules(context, workSchedules, it) }
                            com.example.util.ScheduleReminderWorker.markScheduleChanged(context, registration, monthKey)
                        },
                        onRosterPhotoSaved = { monthKey, url ->
                            val (year, month) = monthKey.split("-").map(String::toInt)
                            val profile = employeeProfile
                            val registration = profile?.registration.orEmpty().filter(Char::isDigit)
                            val existing = workSchedules.firstOrNull { it.monthKey == monthKey }
                            val rows = existing?.employees.orEmpty().toMutableList()
                            val rowIndex = rows.indexOfFirst { it.registration.filter(Char::isDigit) == registration }
                            if (rowIndex >= 0) rows[rowIndex] = rows[rowIndex].copy(rosterPhotoUrl = url)
                            else if (registration.isNotBlank()) rows += WorkScheduleEmployee(
                                registration = registration, name = profile?.name.orEmpty(), verified = true, rosterPhotoUrl = url
                            )
                            val updated = WorkSchedule(monthKey, year, month, rows,
                                existing?.updatedAt ?: 0L, existing?.revision ?: 1)
                            workSchedules = (workSchedules.filterNot { it.monthKey == monthKey } + updated)
                                .sortedByDescending { it.monthKey }
                            employeeProfile?.registration?.let { com.example.util.ScheduleReminderWorker.cacheSchedules(context, workSchedules, it) }
                        }
                    )
                    hours?.let { summary ->
                        val hoursShape = if (isExpressive) RoundedCornerShape(30.dp) else MaterialTheme.shapes.medium
                        Card(
                            modifier = Modifier
                                .bringIntoViewRequester(hoursRequester)
                                .fillMaxWidth()
                                .glassSoftShadow(hoursShape, if (isExpressive) 4.dp else 0.dp)
                                .expressiveShadow(hoursShape, 7.dp),
                            shape = hoursShape,
                            colors = CardDefaults.cardColors(
                                containerColor = profilePalette?.section ?: if (glassStyle.enabled) MaterialTheme.colorScheme.surface
                                else MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Column(Modifier.fillMaxWidth().padding(if (isExpressive) 18.dp else 16.dp)) {
                                Text(
                                    "Banco de horas",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = if (isExpressive) androidx.compose.ui.text.font.FontWeight.ExtraBold else androidx.compose.ui.text.font.FontWeight.Normal,
                                    color = profilePalette?.text
                                )
                                Spacer(Modifier.height(8.dp)); Text("Saldo atual: ${summary.total}", style = MaterialTheme.typography.titleMedium, color = profilePalette?.text)
                                Spacer(Modifier.height(10.dp)); Text("Saldos a vencer", style = MaterialTheme.typography.titleMedium, color = profilePalette?.text)
                                summary.months.forEach { month ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("${month.month}/${month.year}", modifier = Modifier.weight(1f), color = profilePalette?.secondaryText)
                                        Text(month.balance, color = profilePalette?.text)
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
                                .bringIntoViewRequester(pointRequester)
                                .fillMaxWidth()
                                .glassSoftShadow(pointShape, if (isExpressive) 3.dp else 0.dp)
                                .expressiveShadow(pointShape, 6.dp),
                            shape = pointShape,
                            colors = CardDefaults.cardColors(
                                containerColor = profilePalette?.section ?: if (glassStyle.enabled) MaterialTheme.colorScheme.surface
                                else MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Column(Modifier.fillMaxWidth().padding(if (isExpressive) 18.dp else 16.dp)) {
                                Text(
                                    summary.period ?: "Período atual",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = if (isExpressive) androidx.compose.ui.text.font.FontWeight.ExtraBold else androidx.compose.ui.text.font.FontWeight.Normal,
                                    color = profilePalette?.text
                                )
                                summary.status?.let { Text("Status: $it", color = profilePalette?.secondaryText) }; summary.worked?.let { Text("Horas trabalhadas: $it", color = profilePalette?.text) }; summary.balance?.let { Text("Saldo: $it", color = profilePalette?.text) }
                            }
                        }
                    }
                    benefit?.let { summary ->
                        BenefitCardSurface(
                            modifier = Modifier
                                .bringIntoViewRequester(benefitRequester)
                                .glassSoftShadow(RoundedCornerShape(20.dp), 3.dp),
                            backgroundUrl = cardAppearance.activeCardBackground(cardThemeKey),
                            themeKey = cardThemeKey,
                            name = employeeProfile?.name.orEmpty(),
                            limit = summary.limit.orEmpty(),
                            spent = summary.spent.orEmpty(),
                            balance = summary.balance.orEmpty(),
                            period = summary.period.orEmpty(),
                            onClick = { showBenefitDetails = true }
                        )
                    }
                    if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
                }
                if (point?.records.isNullOrEmpty()) item { Text("Nenhum registro de ponto disponível para o período informado.") }
                else items(point!!.records) { PointEntryCard(it, profilePalette) }
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
    modifier: Modifier = Modifier,
    schedules: List<WorkSchedule>, selectedKey: String, expanded: Boolean, registration: String?,
    palette: ProfileThemePalette? = null,
    onToggle: () -> Unit, onSelectMonth: (String) -> Unit,
    onDaysOffSaved: (String, List<Int>) -> Unit,
    onRosterPhotoSaved: (String, String) -> Unit
) {
    var selectorExpanded by remember { mutableStateOf(false) }
    val selected = schedules.firstOrNull { it.monthKey == selectedKey }
    val period = selectedKey.split("-").mapNotNull(String::toIntOrNull)
    val selectedYear = period.getOrNull(0) ?: java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
    val selectedMonth = period.getOrNull(1)?.takeIf { it in 1..12 }
        ?: (java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1)
    val digits = registration.orEmpty().filter(Char::isDigit)
    val employee = selected?.employees?.firstOrNull { it.registration.filter(Char::isDigit) == digits }
    var editing by remember(selectedKey, digits) { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var saveMessage by remember { mutableStateOf<String?>(null) }
    var selectedDays by remember(selectedKey, digits, employee?.daysOff) { mutableStateOf(employee?.daysOff.orEmpty().toSet()) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var photoDialogUrl by remember(selectedKey, digits) { mutableStateOf<String?>(null) }
    var calendarDay by remember(selectedKey, digits) { mutableStateOf<Int?>(null) }
    var photoUploadBusy by remember { mutableStateOf(false) }
    var photoMessage by remember { mutableStateOf<String?>(null) }
    var captureUri by remember { mutableStateOf<Uri?>(null) }
    var captureFile by remember { mutableStateOf<File?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captured ->
        val uri = captureUri
        if (captured && uri != null) {
            photoUploadBusy = true
            photoMessage = null
            scope.launch {
                NossaGenteApi(context.applicationContext).saveMyRosterPhoto(uri, digits, selectedYear, selectedMonth)
                    .onSuccess { url ->
                        onRosterPhotoSaved(selectedKey, url)
                        photoMessage = "Foto salva no perfil para ${monthName(selectedMonth)}/$selectedYear."
                    }
                    .onFailure { photoMessage = it.message ?: "Não foi possível salvar a foto." }
                photoUploadBusy = false
                captureFile?.delete()
                captureFile = null
            }
        } else if (!captured) {
            photoMessage = "A captura foi cancelada."
            captureFile?.delete()
            captureFile = null
        }
    }

    fun captureRosterPhoto() {
        runCatching {
            val directory = File(context.cacheDir, "roster_photos").apply { mkdirs() }
            val file = File.createTempFile("roster_${selectedKey}_", ".jpg", directory)
            captureFile = file
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.onSuccess { uri ->
            captureUri = uri
            runCatching { cameraLauncher.launch(uri) }.onFailure {
                captureFile?.delete()
                captureFile = null
                photoMessage = "Não foi possível abrir a câmera. Verifique se há um aplicativo de câmera disponível."
            }
        }.onFailure {
            photoMessage = "Não foi possível abrir a câmera. Verifique se há um aplicativo de câmera disponível."
        }
    }

    Card(modifier.fillMaxWidth(), shape = if (LocalExpressiveStyle.current.enabled) RoundedCornerShape(28.dp) else MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = palette?.section ?: MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Minhas Folgas", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = palette?.text)
                IconButton(onClick = onToggle) { Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, "Expandir Minhas Folgas", tint = palette?.accent ?: MaterialTheme.colorScheme.onSurface) }
            }
            if (expanded) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.layout.Box(Modifier.weight(1f, fill = false)) {
                        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = palette?.tile ?: MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)) {
                            TextButton(onClick = { selectorExpanded = true }, modifier = Modifier.padding(horizontal = 6.dp)) {
                                val label = "${monthName(selectedMonth)}/$selectedYear"
                                Icon(Icons.Default.CalendarToday, contentDescription = null, tint = palette?.accent ?: MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("$label  ▾", style = MaterialTheme.typography.titleSmall, color = palette?.text)
                            }
                        }
                        if (selectorExpanded) MonthYearPickerDialog(
                            initialYear = selectedYear,
                            initialMonth = selectedMonth,
                            onDismiss = { selectorExpanded = false },
                            onSelect = { year, month -> onSelectMonth("%04d-%02d".format(year, month)); selectorExpanded = false }
                        )
                    }
                }
                if (digits.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.OutlinedButton(
                            onClick = { selectedDays = employee?.daysOff.orEmpty().toSet(); editing = !editing; saveMessage = null },
                            enabled = !saving && !photoUploadBusy,
                            modifier = Modifier.weight(1f),
                            colors = palette?.let { androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = it.accent) }
                                ?: androidx.compose.material3.ButtonDefaults.outlinedButtonColors()
                        ) { Text(if (editing) "Fechar" else "Adicionar folgas", style = MaterialTheme.typography.labelMedium, maxLines = 1) }
                        val savedPhotoUrl = employee?.rosterPhotoUrl?.takeIf { it.isNotBlank() }
                        androidx.compose.material3.OutlinedButton(
                            onClick = { if (savedPhotoUrl != null) photoDialogUrl = savedPhotoUrl else captureRosterPhoto() },
                            enabled = !photoUploadBusy,
                            modifier = Modifier.weight(1f),
                            colors = palette?.let { androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = it.accent) }
                                ?: androidx.compose.material3.ButtonDefaults.outlinedButtonColors()
                        ) {
                            if (photoUploadBusy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            else if (savedPhotoUrl != null) Text("Foto Registrada", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                            else {
                                Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("Registrar Foto", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                            }
                        }
                    }
                    photoMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = if (it.startsWith("Foto salva")) (palette?.accent ?: MaterialTheme.colorScheme.primary) else MaterialTheme.colorScheme.error) }
                }
                when {
                    registration.isNullOrBlank() -> Text("A matrícula do perfil Nossa Gente não está disponível para localizar sua escala.", color = palette?.secondaryText)
                    !editing && employee == null -> Text("Nenhuma folga cadastrada para ${monthName(selectedMonth)}/$selectedYear. Toque em Adicionar folgas para escolher as datas.", color = palette?.secondaryText)
                    else -> {
                        Spacer(Modifier.height(14.dp))
                        Text("Matrícula ${employee?.registration ?: registration}", style = MaterialTheme.typography.labelMedium, color = palette?.secondaryText ?: MaterialTheme.colorScheme.onSurfaceVariant)
                        if (!employee?.shift.isNullOrBlank()) Text(employee!!.shift, style = MaterialTheme.typography.bodyMedium, color = palette?.text)
                        Spacer(Modifier.height(12.dp))
                        Text("Folgas", style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = palette?.text)
                        Spacer(Modifier.height(8.dp))
                        val visibleDays = if (editing) selectedDays.sorted() else employee?.daysOff.orEmpty().distinct().sorted()
                        if (visibleDays.isEmpty()) Text(if (editing) "Toque nas datas para marcar suas folgas." else "Nenhuma folga registrada.", color = palette?.secondaryText)
                        else visibleDays.chunked(4).forEach { week ->
                            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                week.forEach { day ->
                                    val sunday = java.util.GregorianCalendar(selectedYear, selectedMonth - 1, day)
                                        .get(java.util.Calendar.DAY_OF_WEEK) == java.util.Calendar.SUNDAY
                                    Box(Modifier.weight(1f).height(66.dp).background(
                                        if (sunday) (palette?.highlight ?: MaterialTheme.colorScheme.primaryContainer) else (palette?.tile ?: MaterialTheme.colorScheme.surface),
                                        RoundedCornerShape(14.dp)
                                    ).clickable { calendarDay = day }, contentAlignment = Alignment.Center) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(day.toString().padStart(2, '0'), style = MaterialTheme.typography.titleMedium,
                                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = palette?.text)
                                            if (sunday) Text("Domingo", style = MaterialTheme.typography.labelSmall,
                                                color = palette?.accent ?: MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                                repeat(4 - week.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                        if (editing) {
                            Spacer(Modifier.height(14.dp))
                            Text("Selecione as datas de folga • ${monthName(selectedMonth)}/$selectedYear", style = MaterialTheme.typography.bodySmall, color = palette?.secondaryText)
                            Spacer(Modifier.height(6.dp))
                            MyDaysOffCalendar(selectedYear, selectedMonth, selectedDays, palette = palette) { day ->
                                selectedDays = if (day in selectedDays) selectedDays - day else selectedDays + day
                            }
                            Spacer(Modifier.height(8.dp))
                            androidx.compose.material3.Button(
                                onClick = {
                                    saving = true
                                    saveMessage = null
                                    scope.launch {
                                        com.example.data.NossaGenteApi(context.applicationContext)
                                            .saveMyScheduleDaysOff(selectedYear, selectedMonth, selectedDays.toList())
                                            .onSuccess {
                                                onDaysOffSaved(selectedKey, selectedDays.sorted())
                                                editing = false
                                                saveMessage = "Folgas salvas e sincronizadas no seu perfil."
                                            }
                                            .onFailure { saveMessage = it.message ?: "Não foi possível salvar as folgas." }
                                        saving = false
                                    }
                                },
                                enabled = !saving,
                                modifier = Modifier.fillMaxWidth(),
                                colors = palette?.let { androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = it.accent, contentColor = it.onAccent) }
                                    ?: androidx.compose.material3.ButtonDefaults.buttonColors()
                            ) {
                                if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                else Text("Salvar folgas")
                            }
                        }
                        saveMessage?.let { Text(it, color = if (it.startsWith("Folgas salvas")) (palette?.accent ?: MaterialTheme.colorScheme.primary) else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        if (employee?.vacationDays.orEmpty().isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Text("Férias (FE): ${employee?.vacationDays.orEmpty().sorted().joinToString(", ")}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
    photoDialogUrl?.let { url -> RosterPhotoViewerDialog(url, onDismiss = { photoDialogUrl = null }) }
    calendarDay?.let { day ->
        DaysOffCalendarDialog(selectedYear, selectedMonth, day, onDismiss = { calendarDay = null })
    }
}

@Composable
private fun DaysOffCalendarDialog(year: Int, month: Int, selectedDay: Int, onDismiss: () -> Unit) {
    val weekday = java.text.SimpleDateFormat("EEEE", java.util.Locale("pt", "BR")).format(
        java.util.GregorianCalendar(year, month - 1, selectedDay).time
    ).replaceFirstChar { it.uppercase() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$selectedDay de ${monthName(month)} • $weekday") },
        text = {
            Column {
                Text("Confira o dia no calendário completo de ${monthName(month)}/$year.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                MyDaysOffCalendar(year, month, setOf(selectedDay), onToggle = null)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } }
    )
}

@Composable
private fun RosterPhotoViewerDialog(url: String, onDismiss: () -> Unit) {
    var scale by remember(url) { mutableStateOf(1f) }
    var offsetX by remember(url) { mutableStateOf(0f) }
    var offsetY by remember(url) { mutableStateOf(0f) }
    val transformState = rememberTransformableState { zoom, pan, _ ->
        val nextScale = (scale * zoom).coerceIn(1f, 5f)
        scale = nextScale
        offsetX += pan.x
        offsetY += pan.y
        if (scale == 1f) { offsetX = 0f; offsetY = 0f }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.94f))) {
            coil.compose.AsyncImage(
                model = url,
                contentDescription = "Foto completa da escala. Use pinça para ampliar e arraste para conferir os detalhes.",
                modifier = Modifier.fillMaxSize().transformable(transformState).graphicsLayer {
                    scaleX = scale; scaleY = scale; translationX = offsetX; translationY = offsetY
                },
                contentScale = androidx.compose.ui.layout.ContentScale.Fit
            )
            androidx.compose.material3.TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) {
                Text("FECHAR", color = Color.White)
            }
        }
    }
}

@Composable
private fun MonthYearPickerDialog(
    initialYear: Int,
    initialMonth: Int,
    onDismiss: () -> Unit,
    onSelect: (Int, Int) -> Unit
) {
    var year by remember(initialYear, initialMonth) { mutableStateOf(initialYear) }
    var month by remember(initialYear, initialMonth) { mutableStateOf(initialMonth) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Escolher mês e ano") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { if (year > 2000) year-- }) { Text("‹") }
                    Text(year.toString(), style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = { if (year < 2100) year++ }) { Text("›") }
                }
                (1..12).chunked(3).forEach { rowMonths ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        rowMonths.forEach { monthNumber ->
                            val label = monthName(monthNumber).take(3)
                            val selected = month == monthNumber
                            androidx.compose.material3.OutlinedButton(
                                onClick = { month = monthNumber },
                                modifier = Modifier.weight(1f),
                                colors = if (selected) androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                ) else androidx.compose.material3.ButtonDefaults.outlinedButtonColors()
                            ) { Text(label) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSelect(year, month) }) { Text("Selecionar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun MyDaysOffCalendar(year: Int, month: Int, selected: Set<Int>, palette: ProfileThemePalette? = null, onToggle: ((Int) -> Unit)?) {
    val calendar = java.util.GregorianCalendar(year, month - 1, 1)
    val offset = calendar.get(java.util.Calendar.DAY_OF_WEEK) - 1
    val count = calendar.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
    val cells = List(offset) { null } + (1..count).map { it }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb").forEach { day ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Text(day, style = MaterialTheme.typography.labelSmall) }
            }
        }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { day ->
                    if (day == null) Spacer(Modifier.weight(1f).height(42.dp))
                    else Box(
                        Modifier.weight(1f).height(42.dp)
                            .background(if (day in selected) (palette?.highlight ?: MaterialTheme.colorScheme.primaryContainer) else (palette?.tile ?: MaterialTheme.colorScheme.surface),
                                RoundedCornerShape(10.dp))
                            .then(if (onToggle != null) Modifier.clickable { onToggle(day) } else Modifier),
                        contentAlignment = Alignment.Center
                    ) { Text(day.toString(), color = palette?.text ?: if (day in selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface) }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f).height(42.dp)) }
            }
        }
    }
}

private data class ProfileThemePalette(
    val hero: Color,
    val heroText: Color,
    val heroSecondaryText: Color,
    val section: Color,
    val tile: Color,
    val highlight: Color,
    val accent: Color,
    val onAccent: Color,
    val text: Color,
    val secondaryText: Color
)

private fun profileThemePalette(themeKey: String, dark: Boolean): ProfileThemePalette {
    val key = themeKey.trim().lowercase()
        .removePrefix("glass-")
        .removePrefix("expressive-")
    val colors = when (key) {
        "red", "pink" -> listOf(Color(0xFF9A2638), Color(0xFFFFA0AE), Color(0xFFFFEFF2), Color(0xFFFFF8F9), Color(0xFFF9DCE2), Color(0xFF741729), Color(0xFF42111B), Color(0xFF293038))
        "gold", "yellow" -> listOf(Color(0xFF795500), Color(0xFFFFD66B), Color(0xFFFFF7E3), Color(0xFFFFFBF1), Color(0xFFF3E4B9), Color(0xFF624500), Color(0xFF3C321D), Color(0xFF30343A))
        "green" -> listOf(Color(0xFF246447), Color(0xFF8EE0B0), Color(0xFFEDF7F0), Color(0xFFF8FCF9), Color(0xFFD9EDDF), Color(0xFF1D573D), Color(0xFF20372B), Color(0xFF29343A))
        "blue" -> listOf(Color(0xFF245C88), Color(0xFF8CC7FF), Color(0xFFEEF6FC), Color(0xFFF8FBFE), Color(0xFFDCEBF8), Color(0xFF1C507A), Color(0xFF1F2F3F), Color(0xFF29343F))
        "orange" -> listOf(Color(0xFF984612), Color(0xFFFFB479), Color(0xFFFFF2E7), Color(0xFFFFFAF5), Color(0xFFF8E1CE), Color(0xFF79380D), Color(0xFF3D2A1E), Color(0xFF34312E))
        "purple" -> listOf(Color(0xFF59427F), Color(0xFFC5A8FF), Color(0xFFF3EFFA), Color(0xFFFAF8FE), Color(0xFFE5DDF5), Color(0xFF4C386F), Color(0xFF30283C), Color(0xFF32313A))
        "cyan" -> listOf(Color(0xFF00585D), Color(0xFF78D7DD), Color(0xFFEAF7F7), Color(0xFFF7FCFC), Color(0xFFD2EEEE), Color(0xFF004D52), Color(0xFF1D3438), Color(0xFF2B363A))
        else -> listOf(Color(0xFF3D4D5D), Color(0xFFB9DEFA), Color(0xFFF0F4F7), Color(0xFFFAFBFC), Color(0xFFE1E8EE), Color(0xFF344555), Color(0xFF272E35), Color(0xFF303840))
    }
    val hero = colors[0]
    val accent = if (dark) colors[1] else colors[5]
    val surface = if (dark) colors[6] else colors[2]
    val tile = if (dark) colors[7] else colors[3]
    val highlight = if (dark) hero.copy(alpha = 0.56f) else colors[4]
    val text = if (dark) Color(0xFFF4F6F8) else colors[6]
    val secondary = if (dark) Color(0xFFD0D7DE) else colors[5]
    return ProfileThemePalette(
        hero = hero,
        heroText = Color.White,
        heroSecondaryText = Color.White.copy(alpha = 0.88f),
        section = surface,
        tile = tile,
        highlight = highlight,
        accent = accent,
        onAccent = if (dark) Color(0xFF1B2025) else Color.White,
        text = text,
        secondaryText = secondary
    )
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
    expressiveGlassEnabled: Boolean,
    palette: ProfileThemePalette? = null
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
                glassEnabled -> palette?.section ?: MaterialTheme.colorScheme.surface
                else -> palette?.hero ?: MaterialTheme.colorScheme.primaryContainer
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
                        palette?.tile ?: MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    },
                    iconTint = palette?.accent ?: MaterialTheme.colorScheme.primary,
                    onPhotoClick = if (photoModel != null) {
                        { showProfilePhoto = true }
                    } else null
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Nome do usuário",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette?.let { if (glassEnabled) it.secondaryText else it.heroSecondaryText } ?: MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        profile.name ?: "—",
                        style = if (isExpressive) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold,
                        color = palette?.let { if (glassEnabled) it.text else it.heroText } ?: MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Dados sincronizados com o Nossa Gente",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette?.let { if (glassEnabled) it.secondaryText else it.heroSecondaryText } ?: MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ProfileMetric(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.AccessTime,
                    label = "Tempo de Casa",
                    value = profile.tenure ?: "—",
                    isExpressive = isExpressive,
                    palette = palette
                )
                ProfileMetric(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.CalendarToday,
                    label = "Admissão",
                    value = profile.admissionDate ?: "—",
                    isExpressive = isExpressive,
                    palette = palette
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
    isExpressive: Boolean,
    palette: ProfileThemePalette? = null
) {
    val shape = RoundedCornerShape(if (isExpressive) 20.dp else 16.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(palette?.tile ?: MaterialTheme.colorScheme.surface.copy(alpha = if (isExpressive) 0.62f else 0.78f))
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = palette?.accent ?: MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = palette?.secondaryText ?: MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            color = palette?.text ?: MaterialTheme.colorScheme.onSurface
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
private fun PointEntryCard(entry: PointEntry, palette: ProfileThemePalette? = null) {
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
            containerColor = palette?.section ?: if (glassStyle.enabled) MaterialTheme.colorScheme.surface
            else if (expressive) MaterialTheme.colorScheme.surfaceContainerLow
            else MaterialTheme.colorScheme.surface
        )
    ) { Column(Modifier.fillMaxWidth().padding(if (expressive) 16.dp else 14.dp)) {
        Row {
            Icon(
                Icons.Default.AccessTime,
                contentDescription = null,
                tint = palette?.accent ?: if (expressive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Text(
                entry.date ?: "Dia não informado",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (expressive) androidx.compose.ui.text.font.FontWeight.ExtraBold else androidx.compose.ui.text.font.FontWeight.Normal,
                modifier = Modifier.padding(start = 8.dp),
                color = palette?.text
            )
        }
        Spacer(Modifier.height(5.dp)); Text("Entrada: ${entry.entry ?: "—"}   Saída: ${entry.exit ?: "—"}", color = palette?.text)
        entry.interval?.let { Text("Intervalo: $it", color = palette?.text) }; entry.status?.let { Text("Status: $it", color = palette?.secondaryText ?: MaterialTheme.colorScheme.onSurfaceVariant) }
    } }
}
