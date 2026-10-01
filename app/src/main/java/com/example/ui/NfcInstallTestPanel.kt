package com.example.ui

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.nfc.cardemulation.CardEmulation
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.nfc.InstallLinkNfcService
import com.example.nfc.InstallLinkNfcSession
import com.example.util.ReleaseCheckResult
import com.example.util.UpdateChecker
import kotlinx.coroutines.launch

private tailrec fun Context.nfcActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.nfcActivity()
    else -> null
}

/** Experimental and isolated: remove this call and the NFC package/manifest entries to revert. */
@Composable
internal fun NfcInstallTestPanel() {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val activity = remember(context) { context.nfcActivity() }
    val adapter = remember(context) { NfcAdapter.getDefaultAdapter(context) }
    val supported = remember(context) {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION)
    }
    var nfcEnabled by remember { mutableStateOf(adapter?.isEnabled == true) }
    var url by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var transmitting by remember { mutableStateOf(false) }

    DisposableEffect(owner, url) {
        val emulation = if (adapter != null && supported) CardEmulation.getInstance(adapter) else null
        fun stop() {
            InstallLinkNfcSession.stop()
            transmitting = false
            if (activity != null) runCatching { emulation?.unsetPreferredService(activity) }
        }
        fun resume() {
            nfcEnabled = adapter?.isEnabled == true
            val link = url
            if (link == null || !nfcEnabled || activity == null || emulation == null) {
                stop()
                return
            }
            val activated = runCatching {
                emulation.setPreferredService(activity, ComponentName(context, InstallLinkNfcService::class.java))
            }.getOrDefault(false)
            if (activated) {
                InstallLinkNfcSession.start(link)
                transmitting = true
            } else {
                stop()
                message = "O Android não permitiu ativar a etiqueta virtual neste aparelho."
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> resume()
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> stop()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) resume()
        onDispose {
            owner.lifecycle.removeObserver(observer)
            stop()
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Compartilhar por NFC · Teste", style = MaterialTheme.typography.titleMedium)
            Text("Teste experimental: envia o mesmo endereço do QR code. A leitura automática depende do outro aparelho.")
            when {
                adapter == null -> Text("Este aparelho não possui NFC.")
                !supported -> Text("Este aparelho não suporta a etiqueta NFC virtual.")
                !nfcEnabled -> {
                    Text("Ative o NFC para testar.")
                    OutlinedButton(onClick = {
                        runCatching { context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) }
                            .onFailure { message = "Abra as configurações do aparelho para ativar o NFC." }
                    }) { Text("Abrir configurações NFC") }
                }
                else -> {
                    Text(if (transmitting) "Teste ativo: aproxime outro celular desbloqueado, com NFC ligado."
                        else "Teste desativado")
                    if (url == null) {
                        Button(enabled = !loading, onClick = {
                            loading = true
                            message = null
                            scope.launch {
                                try {
                                    when (val result = UpdateChecker.checkLatestRelease()) {
                                        is ReleaseCheckResult.Success -> {
                                            if (result.downloadUrl.startsWith("https://") && result.downloadUrl.length < 8000) {
                                                url = result.downloadUrl
                                            } else message = "O endereço retornado não é compatível com o teste."
                                        }
                                        else -> message = "Não foi possível obter o link. Confira a internet e tente novamente."
                                    }
                                } finally { loading = false }
                            }
                        }) { Text(if (loading) "Buscando endereço…" else "Ativar teste NFC") }
                    } else {
                        Text(url.orEmpty(), style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = {
                            InstallLinkNfcSession.stop()
                            transmitting = false
                            url = null
                            message = null
                        }) { Text("Desativar teste") }
                    }
                    Text("Mantenha esta tela aberta. Ao sair do painel ou colocar o app em segundo plano, a transmissão para. Receber o link não confirma download nem instalação.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
