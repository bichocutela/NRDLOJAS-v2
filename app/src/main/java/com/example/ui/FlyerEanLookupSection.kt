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
 * Fluxo opcional: descrição do encarte -> Google Search -> EAN -> ACP.
 * Relatórios Visual Mix com EAN/código já extraídos não dependem desta busca para salvar.
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
        Text("Busca web opcional", style = MaterialTheme.typography.titleSmall)
        Text(
            "Use só quando o PDF não trouxer EAN/código suficiente. A cota do Google/Gemini não interfere em salvar uma oferta já vinculada à ACP.",
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
                                message = "O Google não trouxe um EAN confiável. Use o EAN/código do PDF ou a busca por descrição na ACP."
                            } else {
                                message = "EAN $ean encontrado no Google. Conferindo na ACP…"
                                onEanFound(ean, result.productName.takeIf { it.isNotBlank() })
                            }
                        }
                        .onFailure { failure ->
                            val raw = failure.message.orEmpty()
                            message = if (
                                raw.contains("RESOURCE_EXHAUSTED", ignoreCase = true) ||
                                raw.contains("quota", ignoreCase = true) ||
                                raw.contains("rate", ignoreCase = true)
                            ) {
                                "Cota da busca web esgotada. Isso NÃO bloqueia salvar as ofertas já encontradas pelo Visual Mix/ACP."
                            } else {
                                raw.ifBlank { "Não foi possível pesquisar o EAN agora. Isso não impede salvar um vínculo ACP já confirmado." }
                            }
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
