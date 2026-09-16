package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.GeminiMasterService
import kotlinx.coroutines.launch

/**
 * Etapa explícita de apoio do Gemini para a revisão do encarte.
 *
 * O EAN retornado aqui é apenas uma pista. O vínculo comercial só é confirmado
 * quando o Mestre usa o código na busca ACP e seleciona o produto correto.
 */
@Composable
internal fun FlyerEanLookupSection(
    description: String,
    enabled: Boolean,
    onUseInAcp: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var candidate by remember(description) { mutableStateOf<GeminiMasterService.EanCandidate?>(null) }
    var busy by remember(description) { mutableStateOf(false) }
    var message by remember(description) { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("EAN sugerido pelo Gemini", style = MaterialTheme.typography.titleSmall)
        Text(
            "Use pela descrição do encarte como apoio. Depois confira o mesmo código e a descrição diretamente na ACP antes de confirmar o vínculo.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedButton(
            onClick = {
                val clean = description.trim()
                if (clean.length < 3 || busy) return@OutlinedButton
                busy = true
                message = null
                candidate = null
                scope.launch {
                    GeminiMasterService.findEan(clean)
                        .onSuccess { result ->
                            candidate = result
                            message = if (result.ean.isNullOrBlank()) {
                                "O Gemini não encontrou um EAN confiável para esta descrição. Você ainda pode pesquisar a descrição diretamente na ACP."
                            } else null
                        }
                        .onFailure { failure ->
                            message = failure.message ?: "Não foi possível buscar um EAN agora."
                        }
                    busy = false
                }
            },
            enabled = enabled && !busy && description.trim().length >= 3,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (busy) "Buscando EAN…" else "Buscar EAN pela descrição")
        }

        candidate?.let { result ->
            val ean = result.ean?.filter(Char::isDigit).orEmpty()
            if (ean.isNotBlank()) {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text("CÓDIGO ENCONTRADO", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text(ean, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                        if (result.productName.isNotBlank()) {
                            Text("Descrição encontrada: ${result.productName}", style = MaterialTheme.typography.bodyMedium)
                        }
                        Text("Descrição do encarte: ${description.trim()}", style = MaterialTheme.typography.bodySmall)
                        if (result.reason.isNotBlank()) {
                            Text(result.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (result.sources.isNotEmpty()) {
                            Text(
                                "Pesquisa apoiada por ${result.sources.size} fonte(s). A ACP continua sendo a confirmação final.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("EAN", ean))
                                    message = "EAN $ean copiado."
                                },
                                enabled = enabled
                            ) { Text("Copiar EAN") }
                            Button(
                                onClick = {
                                    onUseInAcp(ean)
                                    message = "EAN preenchido na busca ACP. Confira a descrição retornada antes de selecionar o produto."
                                },
                                enabled = enabled
                            ) { Text("Usar na busca ACP") }
                        }
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
