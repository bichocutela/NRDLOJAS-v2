package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.data.acp.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
internal fun AcpProductsPanel(api: AcpApi, onSessionExpired: () -> Unit) {
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    var query by remember { mutableStateOf("") }
    var field by remember { mutableStateOf(AcpSearchField.BARCODE) }
    var category by remember { mutableStateOf<AcpCategory?>(null) }
    var categories by remember { mutableStateOf<List<AcpCategory>>(emptyList()) }
    var categoryError by remember { mutableStateOf<String?>(null) }
    var categoryAttempt by remember { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf<AcpProductPage?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<AcpProduct?>(null) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var generation by remember { mutableIntStateOf(0) }
    var scanning by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<AcpProduct?>(null) }
    var detailBusy by remember { mutableStateOf(false) }
    var detailError by remember { mutableStateOf<String?>(null) }
    var detailTime by remember { mutableStateOf<Long?>(null) }
    var detailJob by remember { mutableStateOf<Job?>(null) }
    var detailGeneration by remember { mutableIntStateOf(0) }

    fun closeDetail() {
        detailGeneration++
        detailJob?.cancel()
        selected = null
        detail = null
        detailTime = null
        detailError = null
        detailBusy = false
    }

    fun openProduct(product: AcpProduct) {
        detailJob?.cancel()
        val ticket = ++detailGeneration
        selected = product
        detail = null
        detailTime = null
        detailError = null
        detailBusy = true
        detailJob = scope.launch {
            try {
                val refreshed = api.refreshProduct(product)
                if (ticket == detailGeneration) {
                    detail = refreshed
                    detailTime = System.currentTimeMillis()
                    // Replace the corresponding list entry without claiming the whole list was refreshed.
                    page = page?.let { old -> old.copy(items = old.items.map { item ->
                        if (item.id == product.id && item.code == product.code && item.barcode == product.barcode) refreshed else item
                    }) }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: AcpUnauthorized) { if (ticket == detailGeneration) { closeDetail(); onSessionExpired() } }
            catch (failure: Exception) { if (ticket == detailGeneration) detailError = acpErrorMessage(failure) }
            finally { if (ticket == detailGeneration) detailBusy = false }
        }
    }

    fun invalidateResults() {
        generation++
        searchJob?.cancel()
        busy = false
        page = null
        closeDetail()
        error = null
    }

    fun search(index: Int = 0) {
        if (query.isBlank()) { error = "Digite um código ou descrição."; return }
        searchJob?.cancel()
        val ticket = ++generation
        val searchText = query.trim()
        val searchField = field
        val searchCategory = category
        busy = true
        error = null
        closeDetail()
        page = null
        keyboard?.hide()
        searchJob = scope.launch {
            try {
                val result = api.searchProducts(searchField, searchText, searchCategory, index)
                if (ticket == generation) page = result
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: AcpUnauthorized) { if (ticket == generation) { page = null; onSessionExpired() } }
            catch (failure: Exception) { if (ticket == generation) { page = null; error = acpErrorMessage(failure) } }
            finally { if (ticket == generation) busy = false }
        }
    }

    LaunchedEffect(api, categoryAttempt) {
        categoryError = null
        try { categories = api.categories() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: AcpUnauthorized) { onSessionExpired() }
        catch (_: Exception) { categoryError = "Tipos de oferta indisponíveis. A busca geral continua disponível." }
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AcpSearchField.entries.forEach { option ->
                FilterChip(selected = field == option, onClick = { field = option; invalidateResults() },
                    label = { Text(option.label) })
            }
        }
        OutlinedTextField(value = query, onValueChange = { query = it.take(200); invalidateResults() },
            label = { Text(field.label) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = if (field == AcpSearchField.DESCRIPTION) KeyboardType.Text else KeyboardType.Number,
                imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { search() }))
        Box {
            OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Tipo de oferta: ${category?.description ?: "Todas"} ▾")
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Todas") }, onClick = { category = null; menu = false; invalidateResults() })
                categories.forEach { option -> DropdownMenuItem(text = { Text(option.description) }, onClick = {
                    category = option; menu = false; invalidateResults()
                }) }
            }
        }
        categoryError?.let { message ->
            Text(message, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { categoryAttempt++ }) { Text("Recarregar tipos de oferta") }
        }
        Button(onClick = { search() }, enabled = !busy && query.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text(if (busy) "Buscando…" else "Buscar")
        }
        OutlinedButton(onClick = { scanning = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text("Ler código com a câmera")
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        val result = page
        if (result != null && !busy) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Consulta: ${acpQueryTime(result.queriedAtMillis)}", style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f).padding(top = 12.dp))
            TextButton(onClick = { search(result.pageIndex) }) { Text("Atualizar") }
        }
        if (result == null && !busy && error == null) Text("Busque um produto para consultar os preços na ACP.")
        if (result != null && result.items.isEmpty() && !busy) Text("Nenhum produto encontrado. Confira o código ou tente outro filtro.")
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(result?.items.orEmpty(), key = { index, product -> "${product.id}:$index" }) { _, product ->
                OutlinedCard(onClick = { openProduct(product) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(product.description, style = MaterialTheme.typography.titleMedium)
                        Text("Código: ${product.code.ifBlank { "não informado" }}", style = MaterialTheme.typography.bodySmall)
                        Text("Preço cadastrado: ${product.value?.brl() ?: "não informado"}", style = MaterialTheme.typography.titleMedium)
                        product.offers().forEach { offer -> AcpOfferPoster(offer, compact = true) }
                        if (product.offers().isNotEmpty()) Text("Vigência não confirmada", style = MaterialTheme.typography.labelSmall)
                        Text(if (product.offers().isEmpty()) "Ver preço e detalhes" else "Ver condições de oferta cadastradas",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        if (result != null && result.totalPages > 1) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { search(result.pageIndex - 1) }, enabled = !busy && result.pageIndex > 0) { Text("Anterior") }
            Text("${result.pageIndex + 1} / ${result.totalPages}", modifier = Modifier.padding(top = 12.dp))
            TextButton(onClick = { search(result.pageIndex + 1) }, enabled = !busy && result.pageIndex + 1 < result.totalPages) { Text("Próxima") }
        }
    }

    if (scanning) AcpBarcodeScanner(onDismiss = { scanning = false }, onResult = { code ->
        scanning = false
        invalidateResults()
        field = AcpSearchField.BARCODE
        category = null
        query = code
        search()
    })

    selected?.let { requested ->
        AlertDialog(onDismissRequest = { closeDetail() }, title = { Text(detail?.description ?: requested.description) }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (detailBusy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Consultando preços na ACP…")
                }
                detailError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                    Text("Não foi possível confirmar os preços agora.")
                    TextButton(onClick = { openProduct(requested) }) { Text("Tentar novamente") }
                }
                detail?.let { product ->
                    detailTime?.let { Text("Consultado em ${acpQueryTime(it)}", style = MaterialTheme.typography.bodySmall) }
                    Text("Código: ${product.code.ifBlank { "não informado" }}\nCód. barras: ${product.barcode.ifBlank { "não informado" }}")
                    Text("Preço principal: ${product.value?.brl() ?: "não informado"}" + (product.unit?.let { " / $it" } ?: ""),
                        style = MaterialTheme.typography.titleMedium)
                    val offers = product.offers()
                    if (offers.isEmpty()) {
                        AcpOfferPoster(AcpOffer("Preço cadastrado", "Nenhuma condição promocional informada neste cadastro.", product.value), compact = false)
                        Text("Isso não confirma a ausência de campanhas.")
                    }
                    offers.forEach { offer ->
                        HorizontalDivider()
                        AcpOfferPoster(offer, compact = false)
                    }
                    product.unitLimitPerCPF?.takeIf { it.signum() > 0 }?.let { Text("Limite cadastrado: ${it.quantity()} unidades por CPF.") }
                    if (product.categories.isNotEmpty()) Text("Categorias: ${product.categories.joinToString()}", style = MaterialTheme.typography.bodySmall)
                    HorizontalDivider()
                    Text("Validade não confirmada", style = MaterialTheme.typography.titleSmall)
                    Text("Valores e condições cadastrados na ACP. Vigência e combinação entre campanhas ainda precisam ser confirmadas.",
                        style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { openProduct(product) }) { Text("Atualizar preços") }
                }
            }
        }, confirmButton = { TextButton(onClick = { closeDetail() }) { Text("Fechar") } })
    }
}

private fun acpQueryTime(value: Long): String = SimpleDateFormat("dd/MM HH:mm:ss", Locale("pt", "BR")).format(Date(value))
