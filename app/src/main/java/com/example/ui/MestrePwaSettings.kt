package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
internal fun MestrePwaSettings() {
    val document = remember { FirebaseFirestore.getInstance().collection("config").document("appSettings") }
    var loaded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var promotions by remember { mutableStateOf(false) }
    var prices by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(document) {
        try {
            val settings = document.get().await()
            promotions = settings.getBoolean("pwaShowPromotions") == true
            prices = settings.getBoolean("pwaShowPriceConsultation") == true
            loaded = true
        } catch (_: Exception) { message = "Não foi possível carregar as opções do PWA. Reabra esta tela para tentar novamente." }
    }
    Text("PWA", style = MaterialTheme.typography.titleLarge)
    Text("Escolha as abas exibidas na versão web. Por padrão, ambas ficam ocultas.")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = promotions, enabled = loaded && !busy, onCheckedChange = { promotions = it })
        Text("Mostrar Promoções no PWA", modifier = Modifier.padding(start = 8.dp))
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = prices, enabled = loaded && !busy, onCheckedChange = { prices = it })
        Text("Mostrar Consultar Preços no PWA", modifier = Modifier.padding(start = 8.dp))
    }
    Button(enabled = loaded && !busy, onClick = {
        busy = true
        message = null
        scope.launch {
            try {
                document.set(mapOf("pwaShowPromotions" to promotions, "pwaShowPriceConsultation" to prices), SetOptions.merge()).await()
                message = "Opções do PWA salvas."
            } catch (_: Exception) { message = "Não foi possível salvar. Verifique a conexão e tente novamente." }
            finally { busy = false }
        }
    }) { Text(if (busy) "Salvando..." else "Salvar opções do PWA") }
    message?.let { Text(it) }
}
