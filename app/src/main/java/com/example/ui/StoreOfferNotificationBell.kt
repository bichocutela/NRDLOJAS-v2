package com.example.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.example.util.StoreOfferNotificationWorker
import com.example.util.StoreOfferNotifications
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
internal fun StoreOfferNotificationBell() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf(StoreOfferNotifications.mode(context)) }
    var selected by remember { mutableStateOf(current) }
    var dialog by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showAndroidSettings by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    fun saveSelection() {
        scope.launch {
            busy = true
            error = null
            try {
                if (selected != StoreOfferNotifications.Mode.OFF &&
                    !UserPreferences(context).notificationsEnabled.first()) {
                    error = "Ative as notificações gerais nas Configurações do NRD."
                    return@launch
                }
                if (selected != StoreOfferNotifications.Mode.OFF &&
                    !NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                    error = "As notificações do NRD estão bloqueadas no Android."
                    showAndroidSettings = true
                    return@launch
                }
                StoreOfferNotifications.setMode(context, selected)
                current = selected
                dialog = false
                if (selected != StoreOfferNotifications.Mode.OFF) StoreOfferNotificationWorker.enqueueNow(context)
            } catch (_: Exception) {
                error = "Não foi possível salvar a preferência de notificações."
            } finally {
                busy = false
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) saveSelection()
        else {
            error = "Permita notificações do NRD nas configurações do aparelho."
            showAndroidSettings = true
        }
    }

    IconButton(onClick = { selected = current; error = null; showAndroidSettings = false; dialog = true }) {
        Icon(if (current == StoreOfferNotifications.Mode.OFF) Icons.Default.NotificationsNone
            else Icons.Default.Notifications, contentDescription = "Configurar notificações de ofertas")
    }
    if (dialog) AlertDialog(
        onDismissRequest = { if (!busy) dialog = false },
        title = { Text("Notificações de Ofertas") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Escolha o tipo de oferta para receber avisos no seu aparelho. O padrão é acima de 50%.")
                StoreOfferNotifications.Mode.entries.forEach { option ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable(enabled = !busy) { selected = option }
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selected == option, enabled = !busy, onClick = { selected = option })
                        Spacer(Modifier.width(4.dp))
                        Column {
                            Text(option.label)
                            Text(option.explanation, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Text("Os avisos são da loja preferida, somente para ofertas novas ou atualizadas. Os demais produtos continuam visíveis.", style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (showAndroidSettings) TextButton(onClick = {
                    context.startActivity(
                        if (Build.VERSION.SDK_INT >= 26)
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            android.net.Uri.parse("package:" + context.packageName))
                    )
                }) { Text("Abrir permissões do Android") }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                if (selected != StoreOfferNotifications.Mode.OFF && Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else saveSelection()
            }) { Text(if (busy) "Salvando…" else "Salvar") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = { dialog = false }) { Text("Cancelar") } }
    )
}
