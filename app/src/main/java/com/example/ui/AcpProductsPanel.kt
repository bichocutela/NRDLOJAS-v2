package com.example.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
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
    var promotionBusy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var promotionWarning by remember { mutableStateOf<String?>(null) }
    var campaignOffers by remember { mutableStateOf<Map<String, List<AcpOffer>>>(emptyMap()) }
    var selected by remember { mutableStateOf<AcpProduct?>(null) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var generation by remember { mutableIntStateOf(0) }
    var scanning by remember { mutableStateOf(false) }
    var campaigns by remember { mutableStateOf<List<AcpCampaign>>(emptyList()) }
    var integration by remember { mutableStateOf<AcpIntegrationInfo?>(null) }
    var detailBusy by remember { mutableStateOf(false) }
    var detailWarning by remember { mutableStateOf<String?>(null) }

    fun invalidateResults() {
        generation++
        searchJob?.cancel()
        busy = false
        promotionBusy = false
        page = null
        campaignOffers = emptyMap()
        selected = null
        error = null
        promotionWarning = null
    }

    fun search(index: Int = 0) {
        if (query.isBlank()) { error = "Digite um código ou descrição."; return }
        searchJob?.cancel()
        val ticket = ++generation
        val searchText = query.trim()
        val searchField = field
        val searchCategory = category
        busy = true
        promotionBusy = false
        campaignOffers = emptyMap()
        promotionWarning = null
        error = null
        selected = null
        keyboard?.hide()
        searchJob = scope.launch {
            try {
                val result = api.searchProducts(searchField, searchText, searchCategory, index)
                if (ticket != generation) return@launch
                page = result
                busy = false
                if (result.items.isNotEmpty()) {
                    promotionBusy = true
                    try {
                        campaignOffers = api.campaignOffersFor(result.items)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: AcpUnauthorized) {
                        if (ticket == generation) { page = null; onSessionExpired() }
                    } catch (_: Exception) {
                        if (ticket == generation) promotionWarning = "Produtos carregados, mas as campanhas da ACP não puderam ser cruzadas agora."
                    } finally {
                        if (ticket == generation) promotionBusy = false
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: AcpUnauthorized) {
                if (ticket == generation) { page = null; onSessionExpired() }
            } catch (failure: Exception) {
                if (ticket == generation) { page = null; error = acpErrorMessage(failure) }
            } finally {
                if (ticket == generation) busy = false
            }
        }
    }

    LaunchedEffect(api, categoryAttempt) {
        categoryError = null
        try { categories = api.categories() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: AcpUnauthorized) { onSessionExpired() }
        catch (_: Exception) { categoryError = "Tipos de oferta indisponíveis. A busca geral continua disponível." }
    }

    LaunchedEffect(selected) {
        val product = selected ?: return@LaunchedEffect
        campaigns = emptyList()
        integration = null
        detailWarning = null
        detailBusy = true
        try {
            campaigns = api.campaignsFor(product)
            integration = api.integrationInfo(product)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: AcpUnauthorized) {
            selected = null
            onSessionExpired()
        } catch (_: Exception) {
            detailWarning = "Parte dos detalhes complementares da ACP não pôde ser carregada."
        } finally {
            detailBusy = false
        }
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AcpSearchField.entries.forEach { option ->
                FilterChip(selected = field == option, onClick = { field = option; invalidateResults() }, label = { Text(option.label) })
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it.take(200); invalidateResults() },
            label = { Text(field.label) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                keyboardType = if (field == AcpSearchField.DESCRIPTION) KeyboardType.Text else KeyboardType.Number,
                imeAction = ImeAction.Search
            ),
            keyboardActions = KeyboardActions(onSearch = { search() })
        )
        Box {
            OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Tipo de oferta: ${category?.description ?: "Todas"} ▾")
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Todas") }, onClick = { category = null; menu = false; invalidateResults() })
                categories.forEach { option ->
                    DropdownMenuItem(text = { Text(option.description) }, onClick = { category = option; menu = false; invalidateResults() })
                }
            }
        }
        categoryError?.let {
            Text(it, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { categoryAttempt++ }) { Text("Recarregar tipos de oferta") }
        }
        Button(onClick = { search() }, enabled = !busy && query.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text(if (busy) "Buscando…" else "Buscar")
        }
        OutlinedButton(onClick = { scanning = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text("Ler código com a câmera")
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (promotionBusy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("Cruzando campanhas e promoções da ACP…", style = MaterialTheme.typography.labelSmall)
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        promotionWarning?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

        val result = page
        if (result == null && !busy && error == null) Text("Busque um produto para consultar os preços na ACP.")
        if (result != null && result.items.isEmpty() && !busy) Text("Nenhum produto encontrado. Confira o código ou tente outro filtro.")

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(result?.items.orEmpty(), key = { index, product -> "${product.id}:$index" }) { _, product ->
                val directOffers = product.offers()
                val linkedOffers = campaignOffers[product.id].orEmpty()
                val allOffers = (directOffers + linkedOffers).distinctBy { Triple(it.title, it.price, it.detail) }
                OutlinedCard(onClick = { selected = product }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(product.description, style = MaterialTheme.typography.titleMedium)
                        Text("Código: ${product.code.ifBlank { "não informado" }}", style = MaterialTheme.typography.bodySmall)
                        Text("Preço cadastrado: ${product.value?.brl() ?: "não informado"}", style = MaterialTheme.typography.titleMedium)
                        allOffers.forEach { AcpOfferPoster(it, compact = true) }
                        if (allOffers.isEmpty() && !promotionBusy) Text("Sem promoção identificada nos dados consultados.", style = MaterialTheme.typography.labelSmall)
                        if (linkedOffers.isNotEmpty()) Text("Promoção encontrada em Campanhas ACP", style = MaterialTheme.typography.labelSmall)
                        Text("Ver ficha completa ACP", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        if (result != null && result.totalPages > 1) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { search(result.pageIndex - 1) }, enabled = !busy && result.pageIndex > 0) { Text("Anterior") }
                Text("${result.pageIndex + 1} / ${result.totalPages}", modifier = Modifier.padding(top = 12.dp))
                TextButton(onClick = { search(result.pageIndex + 1) }, enabled = !busy && result.pageIndex + 1 < result.totalPages) { Text("Próxima") }
            }
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

    selected?.let { product ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(product.description) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Código: ${product.code.ifBlank { "não informado" }}\nCód. barras: ${product.barcode.ifBlank { "não informado" }}")
                    Text("Preço principal: ${product.value?.brl() ?: "não informado"}${product.unit?.let { " / $it" } ?: ""}", style = MaterialTheme.typography.titleMedium)

                    val directOffers = product.offers()
                    val campaignDetailOffers = campaigns.flatMap { it.offersFor(product) }
                    val allOffers = (directOffers + campaignOffers[product.id].orEmpty() + campaignDetailOffers)
                        .distinctBy { Triple(it.title, it.price, it.detail) }
                    if (allOffers.isEmpty()) {
                        AcpOfferPoster(AcpOffer("Preço cadastrado", "Nenhuma condição promocional informada neste cadastro ou nas campanhas vinculadas.", product.value), compact = false)
                    } else {
                        allOffers.forEach { HorizontalDivider(); AcpOfferPoster(it, compact = false) }
                    }

                    product.unitLimitPerCPF?.takeIf { it.signum() > 0 }?.let { Text("Limite cadastrado: ${it.quantity()} unidades por CPF.") }
                    if (product.categories.isNotEmpty()) Text("Categorias: ${product.categories.joinToString()}")

                    HorizontalDivider()
                    Text("Integração ACP", style = MaterialTheme.typography.titleMedium)
                    if (detailBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    integration?.let { info ->
                        Text("Estoque: ${info.stock ?: "não informado"}")
                        Text("Vencimento: ${info.dueDate ?: "não informado"}")
                        if (info.rawSummary.isNotEmpty()) {
                            Text(info.rawSummary.joinToString("\n") { "${it.first}: ${it.second}" }, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (!detailBusy && integration == null) Text("A ACP não retornou informação complementar para este produto.")

                    HorizontalDivider()
                    Text("Campanhas vinculadas", style = MaterialTheme.typography.titleMedium)
                    if (!detailBusy && campaigns.isEmpty()) Text("Nenhuma campanha vinculada foi identificada na resposta da ACP.")
                    campaigns.forEach { c ->
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(c.name, style = MaterialTheme.typography.titleSmall)
                                c.code?.let { Text("Código: $it") }
                                c.description?.takeIf { it != c.name }?.let { Text(it) }
                                Text("Início: ${c.startDate ?: "não informado"} • Fim: ${c.endDate ?: "não informado"}")
                                Text("Ativa: ${c.active?.let { if (it) "sim" else "não" } ?: "não informado"} • Autoexclusão: ${c.autoExclusion?.let { if (it) "sim" else "não" } ?: "não informado"}")
                                val rules = c.productRules.count { it.matches(product) }
                                if (rules > 0) Text("Regras promocionais vinculadas ao produto: $rules", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    detailWarning?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("Fechar") } }
        )
    }
}
