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
    fun cancel() {
        error.value = "Processo cancelado. Os produtos já confirmados foram preservados."
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
                    analysis.value?.let { VisualMixReviewStore.saveSession(context, id, it, selected) }
                    updateProcess(context, running, "", failure)
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
    fun restore(context: Context) {
        if (restored) return
        restored = true
        if (sessions.isNotEmpty()) return
        VisualMixReviewStore.loadSessions(context).forEach { (id, result, selection) ->
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
        selected.value = sessions.firstOrNull()?.id
        minimized.value = sessions.isNotEmpty()
    }
    fun open(api: AcpApi): VisualMixOrderSession {
        val session = sessions.firstOrNull { it.id == selected.value }
            ?: VisualMixOrderSession().also { sessions += it }
        session.open(api)
        select(session)
        return session
    }
    fun add(api: AcpApi, uri: Uri): VisualMixOrderSession {
        sessions.removeAll { it.analysis.value == null && it.pdfUri.value == null && !it.running }
        val session = VisualMixOrderSession()
        session.open(api)
        session.pdfUri.value = uri
        session.pdfRequest.longValue = 1
        sessions += session
        select(session)
        return session
    }
    fun select(session: VisualMixOrderSession) {
        selected.value = session.id
        minimized.value = false
    }
    fun resume(id: String? = null) {
        id?.let { target -> sessions.firstOrNull { it.id == target }?.let { selected.value = it.id } }
        minimized.value = false
    }
    fun notify(context: Context, finished: VisualMixOrderSession? = null) {
        val running = sessions.count { it.running }
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
    androidx.compose.foundation.lazy.LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(VisualMixOrderProcesses.sessions.size, key = { VisualMixOrderProcesses.sessions[it].id }) { index ->
            val document = VisualMixOrderProcesses.sessions[index]
            FilterChip(
                selected = document.id == session.id,
                onClick = { VisualMixOrderProcesses.select(document) },
                label = {
                    Text(document.analysis.value?.name ?: document.name.value,
                        maxLines = 1, modifier = Modifier.widthIn(max = 180.dp))
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
    val master = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email
        ?.equals("mestre@nrdlojas.com", ignoreCase = true) == true
    if (!master) return
    LaunchedEffect(Unit) { VisualMixOrderProcesses.restore(context) }
    VisualMixOrderProcesses.sessions.forEach { session ->
        key(session.id) {
            val api = session.api ?: return@key
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
