package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.promotions.ProductImageSources
import kotlinx.coroutines.launch

@Composable
internal fun MestreProductImageSources() {
    val saved by remember { ProductImageSources.updates }.collectAsState(initial = ProductImageSources.Policy())
    var draft by remember(saved) { mutableStateOf(saved) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Fotos dos produtos", style = MaterialTheme.typography.titleMedium)
            Text("Sua foto salva tem prioridade. O Nordestão vem primeiro na busca automática. As fontes abaixo procuram somente pelo EAN exato.")
            draft.order.forEachIndexed { index, id ->
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("${index + 2}º · ${ProductImageSources.labels[id]}")
                        if (id == "mercadolivre") Text("Pode bloquear consultas automáticas.", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = id in draft.enabled, enabled = !busy, onCheckedChange = { enabled ->
                        draft = draft.copy(enabled = if (enabled) draft.enabled + id else draft.enabled - id)
                    })
                }
                Row {
                    TextButton(enabled = !busy && index > 0, onClick = {
                        val order = draft.order.toMutableList()
                        java.util.Collections.swap(order, index, index - 1)
                        draft = draft.copy(order = order)
                    }) { Text("Subir") }
                    TextButton(enabled = !busy && index < draft.order.lastIndex, onClick = {
                        val order = draft.order.toMutableList()
                        java.util.Collections.swap(order, index, index + 1)
                        draft = draft.copy(order = order)
                    }) { Text("Descer") }
                }
            }
            Button(enabled = !busy && draft != saved, onClick = {
                busy = true
                scope.launch {
                    try {
                        message = if (ProductImageSources.save(draft)) "Ordem e fontes publicadas para todos os usuários."
                            else "Não foi possível publicar. Tente novamente."
                    } finally { busy = false }
                }
            }) { Text(if (busy) "Publicando…" else "Publicar fontes") }
            message?.let { Text(it) }
        }
    }
}
