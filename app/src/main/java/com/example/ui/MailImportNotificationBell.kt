package com.example.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.data.UserPreferences
import com.example.util.FcmTopicSubscription
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
internal fun MailImportNotificationBell() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { UserPreferences(context) }
    var enabled by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { enabled = prefs.masterMailDocumentNotificationsEnabled.first() }

    suspend fun save(next: Boolean) {
        busy = true
        error = null
        try {
            if (next && !prefs.notificationsEnabled.first()) {
                error = "Ative as notificações gerais nas Configurações do NRD primeiro."
                return
            }
            if (next && !NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                error = "As notificações do NRD estão bloqueadas no aparelho."
                showSettings = true
                return
            }
            prefs.setMasterMailDocumentNotificationsEnabled(next)
            FcmTopicSubscription.reconcileMasterMailDocuments(
                isMaster = FcmTopicSubscription.isMasterAuthenticated(),
                mailNotificationsEnabled = next,
                notificationsEnabled = prefs.notificationsEnabled.first()
            )
            enabled = next
            open = false
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            error = "Não foi possível salvar. Confira a conexão e tente novamente."
        } finally {
            busy = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        scope.launch {
            if (granted) save(true)
            else {
                error = "Permita as notificações do NRD para receber avisos."
                showSettings = true
            }
        }
    }

    IconButton(onClick = { selected = enabled; error = null; showSettings = false; open = true }) {
        Icon(
            imageVector = if (enabled) Icons.Default.Notifications else Icons.Default.NotificationsNone,
            contentDescription = "Avisos de novos documentos",
            tint = MaterialTheme.colorScheme.primary
        )
    }

    if (open) AlertDialog(
        onDismissRequest = { if (!busy) open = false },
        title = { Text("Avisos de novos documentos") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Receba um aviso neste aparelho quando novos relatórios chegarem para revisão no NRD.")
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = !busy) { selected = !selected }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Switch(checked = selected, enabled = !busy, onCheckedChange = { selected = it })
                    Spacer(Modifier.width(12.dp))
                    Text(if (selected) "Ativado neste aparelho" else "Desativado")
                }
                Text("O sininho só funciona na conta Mestre e não publica ofertas automaticamente.", style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (showSettings) TextButton(onClick = {
                    context.startActivity(
                        if (Build.VERSION.SDK_INT >= 26)
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}"))
                    )
                }) { Text("Permitir no Android") }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                if (!selected) scope.launch { save(false) }
                else if (Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else scope.launch { save(true) }
            }) { Text(if (busy) "Salvando…" else "Salvar") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = { open = false }) { Text("Cancelar") } }
    )
}
