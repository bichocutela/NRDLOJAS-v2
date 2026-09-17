package com.example.ui

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.acp.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal enum class AcpCatalogKind(val label: String, val family: AcpOfferFamily) {
    CLUB("Clube", AcpOfferFamily.CLUB),
    DE_POR("De/Por", AcpOfferFamily.DE_POR),
    TAKE_PAY("Leve/Pague", AcpOfferFamily.TAKE_PAY),
    PRICE("Preço normal", AcpOfferFamily.PRICE)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AcpOfferCatalog(
    api: AcpApi,
    kind: AcpCatalogKind,
    onClose: () -> Unit,
    onProduct: (AcpProduct) -> Unit,
    onSessionExpired: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var filter by remember(kind) { mutableStateOf("") }
    var pageIndex by remember(kind) { mutableIntStateOf(0) }
    var page by remember(kind) { mutableStateOf<AcpProductPage?>(null) }
    var busy by remember(kind) { mutableStateOf(false) }
    var error by remember(kind) { mutableStateOf<String?>(null) }
    var drag by remember { mutableFloatStateOf(0f) }

    fun load(target: Int) {
        if (busy || target < 0) return
        busy = true
        error = null
        scope.launch {
            try {
                val result = api.offerCatalog(kind.family, filter.trim(), target, 20)
                page = result
                pageIndex = result.pageIndex
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: AcpUnauthorized) {
                onSessionExpired()
            } catch (failure: Exception) {
                error = failure.message ?: "Não foi possível carregar os produtos."
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(kind) { load(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(kind, pageIndex, page?.totalPages) {
                detectHorizontalDragGestures(
                    onDragStart = { drag = 0f },
                    onHorizontalDrag = { _, amount -> drag += amount },
                    onDragEnd = {
                        when {
                            drag < -120f && pageIndex + 1 < (page?.totalPages ?: 0) -> load(pageIndex + 1)
                            drag > 120f && pageIndex > 0 -> load(pageIndex - 1)
                        }
                        drag = 0f
                    }
                )
            },
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(kind.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = onClose) { Text("Fechar") }
        }

        OutlinedTextField(
            value = filter,
            onValueChange = { filter = it.take(120) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(26.dp),
            leadingIcon = { Icon(Icons.Default.Search, null) },
            placeholder = { Text("Pesquisar dentro de ${kind.label}") },
            trailingIcon = { TextButton(onClick = { pageIndex = 0; load(0) }, enabled = !busy) { Text("Buscar") } }
        )

        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        val result = page
        if (result != null) {
            Text("${result.totalCount} produtos • Página ${result.pageIndex + 1} de ${result.totalPages.coerceAtLeast(1)}", style = MaterialTheme.typography.bodySmall)
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(result.items, key = { "catalog:${kind.name}:${it.id}:${it.code}:${it.barcode}" }) { product ->
                    OutlinedCard(onClick = { onProduct(product) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(product.description, fontWeight = FontWeight.SemiBold)
                            Text("Código: ${product.code.ifBlank { "não informado" }} • EAN: ${product.barcode.ifBlank { "não informado" }}", style = MaterialTheme.typography.bodySmall)
                            val offer = product.offers().firstOrNull { it.family == kind.family }
                            val priceText = when (kind) {
                                AcpCatalogKind.PRICE -> product.value?.brl()
                                else -> offer?.price?.brl()
                            }
                            priceText?.let { Text(it, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { load(pageIndex - 1) }, enabled = !busy && pageIndex > 0, shape = RoundedCornerShape(18.dp)) { Text("Anterior") }
                Text("${pageIndex + 1}/${result.totalPages.coerceAtLeast(1)}", fontWeight = FontWeight.SemiBold)
                Button(onClick = { load(pageIndex + 1) }, enabled = !busy && pageIndex + 1 < result.totalPages, shape = RoundedCornerShape(18.dp)) { Text("Próxima") }
            }
            Text("Deslize para a esquerda ou direita para trocar de página.", style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}
