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
 * Fluxo simples: descrição do encarte -> Google Search -> EAN -> ACP.
 * O EAN continua sendo apenas uma pista até o Mestre confirmar o produto ACP.
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
        Text("Buscar código de barras na web", style = MaterialTheme.typography.titleSmall)
        Text(
            "O NRD pesquisa a descrição no Google, pega um EAN válido e já confere o mesmo código na ACP.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Button(
            onClick = {
                val clean = description.trim()
                if (clean.length < 3 || busy) return@Button
                busy = true
                message = "Pesquisando no Google…"
                candidate = null
                scope.launch {
                    GeminiMasterService.findEan(clean)
                        .onSuccess { result ->
                            candidate = result
                            val ean = result.ean?.filter(Char::isDigit).orEmpty()
                            if (ean.isBlank()) {
                                message = "O Google não trouxe um EAN confiável para este produto. Use a busca por descrição na ACP abaixo."
                            } else {
                                message = "EAN $ean encontrado no Google. Conferindo na ACP…"
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
            Text(if (busy) "Pesquisando no Google…" else "Pesquisar EAN no Google e conferir na ACP")
        }

        candidate?.let { result ->
            val ean = result.ean?.filter(Char::isDigit).orEmpty()
            if (ean.isNotBlank()) {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("EAN ENCONTRADO NO GOOGLE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text(ean, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                        if (result.productName.isNotBlank()) {
                            Text(result.productName, style = MaterialTheme.typography.bodyMedium)
                        }
                        Text("Descrição do encarte: ${description.trim()}", style = MaterialTheme.typography.bodySmall)
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
