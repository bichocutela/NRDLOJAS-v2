package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.acp.*
import com.example.data.flyer.*
import com.example.util.OrderProcessService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

/** Process-owned session: hiding the window or navigating never disposes the job or review state. */
internal class VisualMixOrderSession(val id: String = java.util.UUID.randomUUID().toString()) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    var api: AcpApi? = null
    var processingJob: Job? = null
    var taskContext: Context? = null
    val backgroundImport = mutableStateOf(false)
    val readingPhase = mutableStateOf("Preparando documento")
    val readingProgress = mutableIntStateOf(0)
    private var trackingImport = false

    fun trackImport(context: Context) {
        taskContext = context.applicationContext
        if (trackingImport) return
        trackingImport = true
        backgroundImport.value = true
        scope.launch {
            androidx.work.WorkManager.getInstance(context)
                .getWorkInfosForUniqueWorkFlow(OrderImportTasks.workName(id)).collect {
                    val task = OrderImportTasks.get(context, id) ?: return@collect
                    readingPhase.value = task.phase
                    readingProgress.intValue = task.progress
                    busy.value = task.pending
                    failed.value = task.state == "failed" || task.state == "cancelled"
                    error.value = task.error
                    completed.value = !task.pending
                    if (task.state == "succeeded" && analysis.value == null) {
                        analysis.value = VisualMixReviewStore.loadSessions(context).firstOrNull { it.first == id }?.second
                        confirmedKeys.value = VisualMixReviewStore.confirmedKeys(context)
                        draftMessage.value = "Documento lido. Continue a revisão."
                    }
                }
        }
    }

    fun startImport(context: Context, uri: Uri) {
        busy.value = true
        backgroundImport.value = true
        error.value = null
        try {
            name.value = runCatching {
                context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            }.getOrNull() ?: name.value
            OrderImportTasks.start(context, id, uri, name.value)
            trackImport(context)
        } catch (failure: Exception) {
            busy.value = false
            error.value = failure.message ?: "Não foi possível iniciar a leitura."
        }
    }

    fun cancel() {
        error.value = "Processo cancelado. Os produtos já confirmados foram preservados."
        if (busy.value && backgroundImport.value) taskContext?.let { OrderImportTasks.cancel(it, id) }
        processingJob?.cancel()
    }
    val active = mutableStateOf(false)
    val minimized get() = VisualMixOrderProcesses.minimized
    val completed = mutableStateOf(false)
    val failed = mutableStateOf(false)
    val pdfUri = mutableStateOf<Uri?>(null)
    val pdfRequest = mutableLongStateOf(0)
    val analysis = mutableStateOf<FlyerAnalysisResult?>(null)
    val busy = mutableStateOf(false)
    val error = mutableStateOf<String?>(null)
    val compareOffer = mutableStateOf<FlyerOffer?>(null)
    val compareProduct = mutableStateOf<AcpProduct?>(null)
    val compareBusy = mutableStateOf(false)
    val compareError = mutableStateOf<String?>(null)
    val openPreviewAfterLoad = mutableStateOf(false)
    val savingValidity = mutableStateOf(false)
    val confirmedKeys = mutableStateOf<Set<String>>(emptySet())
    val draftAvailable = mutableStateOf(false)
    val draftMessage = mutableStateOf<String?>(null)
    val selectionMode = mutableStateOf(false)
    val selectedKeys = mutableStateOf<Set<String>>(emptySet())
    val batchBusy = mutableStateOf(false)
    val expandedApproved = mutableStateOf<Set<String>>(emptySet())
    val listState = LazyListState()
    private var wasRunning = false
    private var observing = false
    fun observe(context: Context) {
        if (observing) return
        observing = true
        scope.launch {
            snapshotFlow { Triple(running, error.value ?: compareError.value, selectedKeys.value) }
                .collect { (running, failure, selected) ->
                    if (analysis.value != null) VisualMixReviewStore.saveSessionSelection(context, id, selected)
                    // Reading has its own persistent worker notification.
                    updateProcess(context, if (backgroundImport.value) batchBusy.value || savingValidity.value || compareBusy.value else running, "", failure)
                }
        }
    }
    fun open(value: AcpApi) {
        api = value
        active.value = true
        minimized.value = false
    }
    fun minimize() { VisualMixOrderProcesses.minimized.value = true }
    fun resume() { VisualMixOrderProcesses.select(this) }
    val name = mutableStateOf("Novo documento")
    val running get() = busy.value || batchBusy.value || savingValidity.value || compareBusy.value
    fun close(context: Context) {
        VisualMixOrderProcesses.minimized.value = true
    }
    fun updateProcess(context: Context, running: Boolean, phase: String, failure: String?) {
        if (!running && !wasRunning) return
        completed.value = !running
        failed.value = !running && failure != null
        val finished = wasRunning && !running
        wasRunning = running
        VisualMixOrderProcesses.notify(context, if (finished) this else null)
    }

}


internal object VisualMixOrderProcesses {
    val sessions = mutableStateListOf<VisualMixOrderSession>()
    val selected = mutableStateOf<String?>(null)
    val minimized = mutableStateOf(false)
    val importGate = kotlinx.coroutines.sync.Semaphore(2)
    private var serviceRunning = false
    private var restored = false
    private var resumeRequested = false
    private var resumeDocument: String? = null
    fun stopAll(context: Context) {
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        androidx.work.WorkManager.getInstance(context).cancelAllWorkByTag("order-import")
        OrderImportTasks.all(context).filter { it.pending }.forEach { OrderImportTasks.cancel(context, it.id) }
        sessions.forEach {
            it.scope.cancel()
            manager.cancel(it.id, it.id.hashCode())
        }
        context.stopService(Intent(context, OrderProcessService::class.java))
        manager.cancel(OrderProcessService.NOTIFICATION_ID)
        sessions.clear()
        selected.value = null
        minimized.value = false
        serviceRunning = false
        restored = false
    }
    fun restore(context: Context) {
        if (restored) return
        restored = true
        VisualMixReviewStore.loadSessions(context).forEach { (id, result, selection) ->
            if (sessions.any { it.id == id }) return@forEach
            val session = VisualMixOrderSession(id)
            session.api = AcpApi(context)
            session.analysis.value = result
            session.name.value = result.name
            session.selectedKeys.value = selection
            session.selectionMode.value = selection.isNotEmpty()
            session.completed.value = true
            session.failed.value = true
            session.draftMessage.value = "Rascunho recuperado. Confira os produtos pendentes antes de continuar."
            sessions += session
        }
        OrderImportTasks.all(context).forEach { task ->
            val session = sessions.firstOrNull { it.id == task.id }
                ?: if (task.state == "succeeded") return@forEach
                else VisualMixOrderSession(task.id).also {
                    it.api = AcpApi(context)
                    it.name.value = task.name
                    sessions += it
                }
            session.busy.value = task.pending
            session.readingPhase.value = task.phase
            session.readingProgress.intValue = task.progress
            session.error.value = task.error
            session.trackImport(context)
            if (task.pending) OrderImportTasks.enqueue(context, task)
        }
        selected.value = sessions.firstOrNull()?.id
        minimized.value = sessions.isNotEmpty() && !resumeRequested
        resumeDocument?.let { id -> sessions.firstOrNull { it.id == id }?.let { selected.value = it.id } }
        resumeRequested = false
        resumeDocument = null
    }
    fun open(api: AcpApi): VisualMixOrderSession {
        val session = sessions.firstOrNull { it.id == selected.value }
            ?: VisualMixOrderSession().also { sessions += it }
        session.open(api)
        select(session)
        return session
    }
    fun add(api: AcpApi, uri: Uri): VisualMixOrderSession {
        sessions.removeAll { it.analysis.value == null && it.pdfUri.value == null && !it.running && !it.backgroundImport.value }
        val session = VisualMixOrderSession()
        session.open(api)
        session.pdfUri.value = uri
        session.pdfRequest.longValue = 1
        sessions += session
        select(session)
        return session
    }
    fun finalize(context: Context, session: VisualMixOrderSession): Boolean {
        val result = session.analysis.value ?: return false
        if (session.running || !VisualMixReviewStore.finalizeSession(context, session.id, result)) return false
        if (session.backgroundImport.value) OrderImportTasks.remove(context, session.id)
        session.scope.cancel()
        context.getSystemService(android.app.NotificationManager::class.java).cancel(session.id, session.id.hashCode())
        sessions.remove(session)
        selected.value = sessions.firstOrNull()?.id
        sessions.forEach { it.draftAvailable.value = VisualMixReviewStore.hasDraft(context) }
        notify(context)
        if (sessions.isEmpty()) {
            minimized.value = true
            context.stopService(Intent(context, OrderProcessService::class.java))
            context.getSystemService(android.app.NotificationManager::class.java).cancel(OrderProcessService.NOTIFICATION_ID)
            serviceRunning = false
        }
        return true
    }

    fun reopen(context: Context, api: AcpApi, id: String, result: FlyerAnalysisResult): Boolean {
        if (!VisualMixReviewStore.reopenSession(context, id, result)) return false
        val session = VisualMixOrderSession(id)
        session.analysis.value = result
        session.name.value = result.name
        session.completed.value = true
        session.open(api)
        sessions += session
        select(session)
        return true
    }

    fun select(session: VisualMixOrderSession) {
        selected.value = session.id
        minimized.value = false
    }
    fun resume(id: String? = null) {
        if (sessions.isEmpty()) { resumeRequested = true; resumeDocument = id }
        id?.let { target -> sessions.firstOrNull { it.id == target }?.let { selected.value = it.id } }
        minimized.value = false
    }
    fun notify(context: Context, finished: VisualMixOrderSession? = null) {
        val running = sessions.count { it.running && (!it.backgroundImport.value || it.batchBusy.value || it.savingValidity.value || it.compareBusy.value) }
        val failures = sessions.count { it.failed.value }
        val result = if (failures > 0) "$failures documento(s) precisam de revisão. Toque para acompanhar."
            else "Toque para continuar na tela Importar Ordem."
        val intent = Intent(context, OrderProcessService::class.java)
            .putExtra("running", running > 0)
            .putExtra("phase", "$running processo(s) em andamento")
            .putExtra("failure", if (running == 0 && failures > 0) result else null as String?)
            .putExtra("result", result)
            .putExtra("completedId", finished?.id)
            .putExtra("completedName", finished?.analysis?.value?.name ?: finished?.name?.value)
            .putExtra("completedError", finished?.error?.value ?: finished?.compareError?.value)
        if (running > 0 && !serviceRunning) runCatching { ContextCompat.startForegroundService(context, intent) }
        else if (serviceRunning || finished != null) runCatching { context.startService(intent) }
        serviceRunning = running > 0
    }
}

@Composable
internal fun VisualMixOrderDocumentStack(session: VisualMixOrderSession) {
    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier.fillMaxWidth().heightIn(max = 160.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(VisualMixOrderProcesses.sessions.size, key = { VisualMixOrderProcesses.sessions[it].id }) { index ->
            val document = VisualMixOrderProcesses.sessions[index]
            FilterChip(
                modifier = Modifier.fillMaxWidth(),
                selected = document.id == session.id,
                onClick = { VisualMixOrderProcesses.select(document) },
                label = {
                    Column {
                        Text(document.analysis.value?.name ?: document.name.value, maxLines = 1)
                        Text(if (document.batchBusy.value || document.savingValidity.value) "Confirmando produtos…"
                            else if (document.busy.value) if (document.backgroundImport.value) document.readingPhase.value + " • " + document.readingProgress.intValue + "%" else "Verificando ofertas…"
                            else if (document.failed.value) "Revisar pendências"
                            else if (document.completed.value) "Processo Concluído"
                            else "Pronto para importar", style = MaterialTheme.typography.labelSmall)
                    }
                },
                trailingIcon = {
                    if (document.running) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    else if (document.completed.value) Text(if (document.failed.value) "!" else "✓")
                }
            )
        }
    }
}

@Composable
internal fun VisualMixOrderProcessOverlay() {
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val auth = remember { com.google.firebase.auth.FirebaseAuth.getInstance() }
    val master by produceState(initialValue = auth.currentUser?.email?.equals("mestre@nrdlojas.com", true) == true) {
        val listener = com.google.firebase.auth.FirebaseAuth.AuthStateListener {
            value = it.currentUser?.email?.equals("mestre@nrdlojas.com", true) == true
        }
        auth.addAuthStateListener(listener)
        awaitDispose { auth.removeAuthStateListener(listener) }
    }
    if (!master) {
        LaunchedEffect(Unit) { if (VisualMixOrderProcesses.sessions.isNotEmpty()) VisualMixOrderProcesses.stopAll(context) }
        return
    }
    LaunchedEffect(Unit) { VisualMixOrderProcesses.restore(context) }
    VisualMixOrderProcesses.sessions.forEach { session ->
        key(session.id) {
            val api = session.api
            if (api != null) {
            LaunchedEffect(session.id) {
                session.observe(context)
                session.confirmedKeys.value = VisualMixReviewStore.confirmedKeys(context)
                session.draftAvailable.value = VisualMixReviewStore.hasDraft(context)
            }
            VisualMixOrderImportDialog(
                api = api, session = session,
                initialPdfUri = session.pdfUri.value,
                initialPdfRequestKey = session.pdfRequest.longValue,
                onInitialPdfConsumed = { session.pdfUri.value = null },
                onDismiss = { session.close(context) }
            )
            }
        }
    }
    if (VisualMixOrderProcesses.minimized.value && VisualMixOrderProcesses.sessions.isNotEmpty()) {
        val running = VisualMixOrderProcesses.sessions.any { it.running }
        val failed = VisualMixOrderProcesses.sessions.any { it.failed.value }
        val color = if (running) MaterialTheme.colorScheme.primary else if (failed) MaterialTheme.colorScheme.error else Color(0xFF299B62)
        val alpha = if (!running) 1f else {
            val transition = rememberInfiniteTransition(label = "order-process")
            val pulse by transition.animateFloat(0.45f, 1f,
                infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "order-process-pulse")
            pulse
        }
        Box(Modifier.fillMaxSize().statusBarsPadding().padding(top = 8.dp, end = 8.dp), contentAlignment = Alignment.TopEnd) {
            IconButton(onClick = { VisualMixOrderProcesses.resume() },
                modifier = Modifier.semantics { contentDescription = if (running) "Importação em andamento. Retomar" else "Processo concluído. Ver resultado" }) {
                Box(Modifier.size(14.dp).background(color.copy(alpha = alpha), CircleShape))
            }
        }
    }
}
