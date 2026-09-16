package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.GeminiMasterService
import kotlinx.coroutines.launch

/**
 * Fluxo simples de revisão: descrição do encarte -> EAN sugerido -> ACP.
 * O EAN continua sendo apenas uma pista até o Mestre tocar no produto ACP correto.
 */
@Composable
internal fun FlyerEanLookupSection(
    description: String,
    enabled: Boolean,
    onEanFound: (ean: String, productName: String?) -> Unit
) {
    val scope = rememberCoroutineScope()
    var candidate by remember(description) { mutableStateOf<GeminiMasterService.EanCandidate?>(null) }
    var busy by remember(description) { mutableStateOf(false) }
    var message by remember(description) { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Localizar produto pelo EAN", style = MaterialTheme.typography.titleSmall)
        Text(
            "Toque uma vez: o NRD pesquisa o EAN pela descrição e já confere o mesmo código na ACP.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Button(
            onClick = {
                val clean = description.trim()
                if (clean.length < 3 || busy) return@Button
                busy = true
                message = null
                candidate = null
                scope.launch {
                    GeminiMasterService.findEan(clean)
                        .onSuccess { result ->
                            candidate = result
                            val ean = result.ean?.filter(Char::isDigit).orEmpty()
                            if (ean.isBlank()) {
                                message = "Não encontrei um EAN confiável para esta descrição. Use a busca por descrição na ACP abaixo."
                            } else {
                                message = "EAN $ean encontrado. Conferindo automaticamente na ACP…"
                                onEanFound(ean, result.productName.takeIf { it.isNotBlank() })
                            }
                        }
                        .onFailure { failure ->
                            message = failure.message ?: "Não foi possível pesquisar o EAN agora."
                        }
                    busy = false
                }
            },
            enabled = enabled && !busy && description.trim().length >= 3,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (busy) "Procurando produto…" else "Buscar EAN e conferir na ACP")
        }

        candidate?.let { result ->
            val ean = result.ean?.filter(Char::isDigit).orEmpty()
            if (ean.isNotBlank()) {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("EAN ENCONTRADO", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text(ean, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                        if (result.productName.isNotBlank()) {
                            Text(result.productName, style = MaterialTheme.typography.bodyMedium)
                        }
                        Text("Encartado como: ${description.trim()}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        message?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = if (candidate?.ean.isNullOrBlank()) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
            )
        }
    }
}
