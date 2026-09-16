package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.acp.AcpApi
import com.example.data.acp.AcpFailure
import com.example.data.acp.AcpProduct
import com.example.data.acp.AcpProductPage
import com.example.data.acp.AcpProductParser
import com.example.data.acp.AcpUnauthorized
import com.example.data.acp.brl
import kotlinx.coroutines.CancellationException
import java.text.Normalizer

private const val CLUB_DISPLAY_PAGE_SIZE = 20

/**
 * Lista dos produtos que a própria ACP identifica como Clube de Vantagens.
 * A chamada sentinela pageSize=100 é reconhecida pelo AcpApi e convertida para
 * Product/all filtrado pela categoria oficial do Clube, com pageSize real de 20.
 */
@Composable
internal fun AcpClubCatalogSection(
    api: AcpApi,
    onProductSelected: (AcpProduct) -> Unit,
    onSessionExpired: () -> Unit
) {
    var requestedPage by remember(api) { mutableIntStateOf(0) }
    var page by remember(api) { mutableStateOf<AcpProductPage?>(null) }
    var busy by remember(api) { mutableStateOf(true) }
    var error by remember(api) { mutableStateOf<String?>(null) }
    var reloadKey by remember(api) { mutableIntStateOf(0) }

    LaunchedEffect(api, requestedPage, reloadKey) {
        busy = true
        error = null
        try {
            page = api.clubCatalogPage(requestedPage)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: AcpUnauthorized) {
            onSessionExpired()
        } catch (failure: Exception) {
            error = when (failure) {
                is AcpFailure -> failure.message ?: "Não foi possível carregar os produtos Clube da ACP."
                else -> "Não foi possível carregar os produtos Clube da ACP agora."
            }
        } finally {
            busy = false
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Produtos Clube", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "Sincronizado com a categoria Clube de Vantagens da ACP • até 20 por página",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            TextButton(onClick = { reloadKey++ }, enabled = !busy) { Text("Atualizar") }
        }

        if (busy) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator()
            }
        }

        error?.let { message ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(message, color = MaterialTheme.colorScheme.error)
                    Button(onClick = { reloadKey++ }, enabled = !busy) { Text("Tentar novamente") }
                }
            }
        }

        val result = page
        if (!busy && error == null && result != null) {
            if (result.items.isEmpty()) {
                Text("Nenhum produto marcado como Clube foi retornado pela ACP nesta página.")
            } else {
                result.items.take(CLUB_DISPLAY_PAGE_SIZE).forEach { product ->
                    ClubProductCard(product = product, onClick = { onProductSelected(product) })
                }
            }

            if (result.totalPages > 1) {
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { requestedPage-- },
                        enabled = !busy && result.pageIndex > 0
                    ) { Text("Anterior") }
                    Text(
                        "${result.pageIndex + 1} / ${result.totalPages}",
                        style = MaterialTheme.typography.labelLarge
                    )
                    TextButton(
                        onClick = { requestedPage++ },
                        enabled = !busy && result.pageIndex + 1 < result.totalPages
                    ) { Text("Próxima") }
                }
            }
        }
    }
}

@Composable
private fun ClubProductCard(product: AcpProduct, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistChip(onClick = onClick, label = { Text("CLUBE") })
                product.clubValue?.takeIf { it.signum() > 0 }?.let { clubPrice ->
                    Text(clubPrice.brl(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
            Text(product.description, style = MaterialTheme.typography.titleMedium)
            Text(
                buildString {
                    append("Código: ${product.code.ifBlank { "não informado" }}")
                    if (product.barcode.isNotBlank()) append(" • EAN: ${product.barcode}")
                },
                style = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Normal: ${product.value?.brl() ?: "não informado"}", style = MaterialTheme.typography.bodyMedium)
                if (product.clubValue == null || product.clubValue.signum() <= 0) {
                    Text("Preço Clube não informado", style = MaterialTheme.typography.bodySmall)
                }
            }
            Text("Ver ficha completa", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun AcpProduct.isClubMarkedByAcp(): Boolean {
    val categoryMarked = categories.any { category ->
        val normalized = Normalizer.normalize(category, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9]"), "")
        normalized == "clubedevantagens" || normalized == "clubvantagens"
    }
    return categoryMarked || (clubValue?.signum() ?: 0) > 0
}

/** Consulta uma página real de 20 produtos da categoria Clube diretamente no Product/all. */
private suspend fun AcpApi.clubCatalogPage(displayPage: Int): AcpProductPage {
    require(displayPage >= 0)
    val root = get(
        "Product/all",
        listOf(
            // Sentinela interna: AcpApi substitui por pageSize=20 e acrescenta
            // productCategoryIds da categoria Clube de Vantagens resolvida na ACP.
            "pageSize" to "100",
            "pageIndex" to displayPage.toString()
        )
    )
    val source = AcpProductParser.page(root, displayPage)
    return source.copy(items = source.items.filter { it.isClubMarkedByAcp() }.take(CLUB_DISPLAY_PAGE_SIZE))
}
