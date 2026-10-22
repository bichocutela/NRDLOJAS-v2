package com.example.ui.training

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
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
import com.example.util.TrainingNotifications
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
internal fun TrainingNotificationBell(public: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf(TrainingNotifications.mode(context)) }
    var chosen by remember { mutableStateOf(current) }
    var open by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showSystemSettings by remember { mutableStateOf(false) }
    fun applyMode() {
        scope.launch {
            busy = true; error = null
            try {
                if (chosen != TrainingNotifications.Mode.OFF && !UserPreferences(context).notificationsEnabled.first()) {
                    error = "Ative as notificações gerais nas Configurações do NRD primeiro."
                } else {
                    kotlinx.coroutines.withTimeout(15_000) { TrainingNotifications.setMode(context, chosen) }
                    current = chosen; open = false
                }
            } catch (_: kotlinx.coroutines.TimeoutCancellationException) { error = "A conexão demorou. Confira a internet e tente novamente." }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = "Não foi possível salvar. Confira a conexão e tente novamente." }
            finally { busy = false }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) applyMode() else { error = "Permita as notificações do NRD para receber os avisos."; showSystemSettings = true }
    }
    IconButton(enabled = public, onClick = { chosen = current; error = null; showSystemSettings = false; open = true }) {
        Icon(if (current == TrainingNotifications.Mode.OFF) Icons.Default.NotificationsNone else Icons.Default.Notifications,
            contentDescription = "Notificações de treinamentos")
    }
    if (open) AlertDialog(onDismissRequest = { if (!busy) open = false },
        title = { Text("Notificações de treinamentos") },
        text = { Column {
            Text("Ative ou desative os avisos de novos treinamentos.")
            TrainingNotifications.Mode.entries.forEach { option ->
                Row(Modifier.fillMaxWidth().clickable(enabled = !busy) { chosen = option }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = chosen == option, enabled = !busy, onClick = { chosen = option })
                    Column {
                        Text(if (option == TrainingNotifications.Mode.OFF) "Desativadas" else "Ativadas")
                        Text(if (option == TrainingNotifications.Mode.OFF) "Não receber avisos" else "Avisar quando houver novo treinamento", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (showSystemSettings) TextButton(onClick = {
                context.startActivity(if (Build.VERSION.SDK_INT >= 26)
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}")))
            }) { Text("Permitir no Android") }
        } },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            if (chosen == TrainingNotifications.Mode.OFF) applyMode()
            else if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            else if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                error = "As notificações do NRD estão bloqueadas no Android."; showSystemSettings = true
            } else applyMode()
        }) { Text(if (busy) "Salvando…" else "Salvar") } },
        dismissButton = { TextButton(enabled = !busy, onClick = { open = false }) { Text("Cancelar") } })
}
