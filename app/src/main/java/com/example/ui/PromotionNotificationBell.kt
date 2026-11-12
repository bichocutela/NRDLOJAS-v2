package com.example.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.example.data.UserPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Only new promotions for the explicitly selected favorite store. */
@Composable
internal fun PromotionNotificationBell() {
    val context = LocalContext.current
    val prefs = remember(context) { UserPreferences(context) }
    val scope = rememberCoroutineScope()
    val enabled by prefs.promotionNewOffersNotificationsEnabled.collectAsState(initial = false)
    val favorite by prefs.favoriteStoreCode.collectAsState(initial = null)
    var open by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun save(next: Boolean) {
        scope.launch {
            if (next && favorite.isNullOrBlank()) {
                error = "Defina uma loja favorita para receber novas promoções."
            } else if (next && !prefs.notificationsEnabled.first()) {
                error = "Ative as notificações gerais nas Configurações do NRD."
            } else if (next && !NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                error = "Permita as notificações do NRD nas configurações do aparelho."
            } else {
                prefs.setPromotionNewOffersNotificationsEnabled(next)
                open = false
            }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) save(true) else error = "Permita notificações do NRD no Android."
    }

    IconButton(onClick = { selected = enabled; error = null; open = true }) {
        Icon(if (enabled && !favorite.isNullOrBlank()) Icons.Default.Notifications
             else Icons.Default.NotificationsNone,
             contentDescription = "Notificações de novas promoções")
    }
    if (open) AlertDialog(
        onDismissRequest = { open = false },
        title = { Text("Notificações de Promoções") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Receba avisos apenas quando novos produtos entrarem em promoção na sua loja favorita.")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = selected, onCheckedChange = { selected = it },
                        enabled = !favorite.isNullOrBlank())
                    Spacer(Modifier.width(12.dp))
                    Text(if (selected) "Novas promoções ativadas" else "Novas promoções desativadas")
                }
                if (favorite.isNullOrBlank()) Text("Selecione primeiro sua loja favorita para ativar os avisos.")
                Text("Não inclui avisos de atualização de preços, percentuais de desconto ou cashback.",
                    style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (selected && Build.VERSION.SDK_INT >= 33 &&
                    androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                else save(selected)
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = { open = false }) { Text("Cancelar") } }
    )
}
