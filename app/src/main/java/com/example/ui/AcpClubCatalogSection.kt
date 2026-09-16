package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
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
import kotlin.math.ceil
import kotlin.math.min

private const val CLUB_DISPLAY_PAGE_SIZE = 20
private const val CLUB_SOURCE_PAGE_SIZE = 250

/**
 * Lista enxuta dos produtos da categoria oficial "Clube de Vantagens" da ACP.
 *
 * O AcpApi já possui um caminho dedicado para esse catálogo: uma chamada Product/all
 * sem filtros e pageSize=100 é convertida internamente em uma consulta fresca da categoria
 * oficial Clube de Vantagens, com lote de 250. Aqui apenas repartimos esse lote em páginas
 * visuais de até 20 itens, sem inferir Clube por texto nem manter um catálogo paralelo.
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
                    "Direto da categoria Clube de Vantagens da ACP • até 20 por página",
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
                Text("Nenhum produto Clube foi retornado pela ACP nesta página.")
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

/**
 * Reaproveita o caminho Clube já existente em AcpApi, que resolve a categoria oficial no
 * servidor e consulta Product/all fresco. A ACP devolve lotes de 250; a UI usa páginas de 20.
 */
private suspend fun AcpApi.clubCatalogPage(displayPage: Int): AcpProductPage {
    require(displayPage >= 0)

    val absoluteStart = displayPage * CLUB_DISPLAY_PAGE_SIZE
    val sourcePageIndex = absoluteStart / CLUB_SOURCE_PAGE_SIZE
    val sourceOffset = absoluteStart % CLUB_SOURCE_PAGE_SIZE

    suspend fun sourcePage(index: Int): AcpProductPage {
        val root = get(
            "Product/all",
            listOf(
                "pageSize" to "100",
                "pageIndex" to index.toString()
            )
        )
        return AcpProductParser.page(root, index)
    }

    val first = sourcePage(sourcePageIndex)
    val totalCount = first.totalCount.coerceAtLeast(0)
    val visualTotalPages = if (totalCount == 0) 0 else ceil(totalCount / CLUB_DISPLAY_PAGE_SIZE.toDouble()).toInt()

    if (absoluteStart >= totalCount) {
        return AcpProductPage(emptyList(), displayPage, visualTotalPages, totalCount)
    }

    val wanted = min(CLUB_DISPLAY_PAGE_SIZE, totalCount - absoluteStart)
    val items = mutableListOf<AcpProduct>()
    items += first.items.drop(sourceOffset).take(wanted)

    if (items.size < wanted && sourcePageIndex + 1 < first.totalPages) {
        val second = sourcePage(sourcePageIndex + 1)
        items += second.items.take(wanted - items.size)
    }

    return AcpProductPage(
        items = items.take(CLUB_DISPLAY_PAGE_SIZE),
        pageIndex = displayPage,
        totalPages = visualTotalPages,
        totalCount = totalCount
    )
}
