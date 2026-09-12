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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.CategoryDefinition
import com.example.data.FirebaseService
import com.example.data.NrdProductImportService
import com.example.data.acp.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class NrdIdentifier { BARCODE, PRODUCT_CODE }

@Composable
internal fun AcpProductsPanel(api: AcpApi, canAddToNrd: Boolean, onSessionExpired: () -> Unit) {
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val clipboard = LocalClipboardManager.current
    val nrdCategoriesFlow = remember { FirebaseService.observeCategories() }
    val nrdDefinitions by nrdCategoriesFlow.collectAsState(initial = CategoryDefinition.defaults)
    val activeNrdCategories = remember(nrdDefinitions) {
        nrdDefinitions.filter { it.isActive }
            .sortedWith(compareBy<CategoryDefinition> { it.displayOrder }.thenBy { it.name })
            .map { it.name }
    }

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
    var diagnosticMessage by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<AcpProduct?>(null) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var generation by remember { mutableIntStateOf(0) }
    var scanning by remember { mutableStateOf(false) }
    var campaigns by remember { mutableStateOf<List<AcpCampaign>>(emptyList()) }
    var integration by remember { mutableStateOf<AcpIntegrationInfo?>(null) }
    var detailBusy by remember { mutableStateOf(false) }
    var detailWarning by remember { mutableStateOf<String?>(null) }
    var syncExpanded by remember { mutableStateOf(false) }

    var detail by remember { mutableStateOf<AcpProduct?>(null) }
    var detailError by remember { mutableStateOf<String?>(null) }
    var detailTime by remember { mutableStateOf<Long?>(null) }
    var detailAttempt by remember { mutableIntStateOf(0) }

    var addToNrdProduct by remember { mutableStateOf<AcpProduct?>(null) }
    var nrdIdentifier by remember { mutableStateOf(NrdIdentifier.BARCODE) }
    var selectedNrdCategories by remember { mutableStateOf<Set<String>>(emptySet()) }
    var nrdCategoriesExpanded by remember { mutableStateOf(false) }
    var nrdSaving by remember { mutableStateOf(false) }
    var nrdError by remember { mutableStateOf<String?>(null) }
    var nrdActionMessage by remember { mutableStateOf<String?>(null) }

    fun closeAddToNrd() {
        if (nrdSaving) return
        addToNrdProduct = null
        selectedNrdCategories = emptySet()
        nrdCategoriesExpanded = false
        nrdError = null
    }

    fun prepareAddToNrd(product: AcpProduct) {
        addToNrdProduct = product
        nrdIdentifier = if (product.barcode.isNotBlank()) NrdIdentifier.BARCODE else NrdIdentifier.PRODUCT_CODE
        selectedNrdCategories = emptySet()
        nrdCategoriesExpanded = false
        nrdError = null
        nrdActionMessage = null
    }

    fun closeDetail() {
        selected = null
        detail = null
        campaigns = emptyList()
        integration = null
        detailTime = null
        detailError = null
        detailWarning = null
        syncExpanded = false
        addToNrdProduct = null
        nrdActionMessage = null
    }

    fun openProduct(product: AcpProduct) {
        closeDetail()
        detailBusy = true
        selected = product
        detailAttempt++
    }

    fun invalidateResults() {
        generation++
        searchJob?.cancel()
        busy = false
        page = null
        closeDetail()
        error = null
        diagnosticMessage = null
    }

    fun search(index: Int = 0) {
        if (query.isBlank()) { error = "Digite um código ou descrição."; return }
        searchJob?.cancel()
        val ticket = ++generation
        val searchText = query.trim()
        val searchField = field
        val searchCategory = category
        busy = true
        diagnosticMessage = null
        error = null
        closeDetail()
        page = null
        keyboard?.hide()
        if (index == 0) api.beginDiagnosticSession()
        searchJob = scope.launch {
            try {
                val result = api.searchProducts(searchField, searchText, searchCategory, index)
                if (ticket != generation) return@launch
                page = result
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
        catch (_: Exception) { categoryError = "Categorias ACP indisponíveis. A busca geral continua disponível." }
    }

    LaunchedEffect(selected, detailAttempt) {
        val requested = selected ?: return@LaunchedEffect
        detail = null
        detailError = null
        detailTime = null
        campaigns = emptyList()
        integration = null
        detailWarning = null
        detailBusy = true
        val warnings = mutableListOf<String>()
        val product = try {
            api.refreshProduct(requested)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: AcpUnauthorized) {
            closeDetail()
            onSessionExpired()
            return@LaunchedEffect
        } catch (failure: Exception) {
            detailError = acpErrorMessage(failure)
            detailBusy = false
            return@LaunchedEffect
        }
        detail = product
        detailTime = System.currentTimeMillis()
        page = page?.let { old -> old.copy(items = old.items.map { item ->
            if (item.id == requested.id && item.code == requested.code && item.barcode == requested.barcode) product else item
        }) }
        try {
            campaigns = api.campaignsFor(product)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: AcpUnauthorized) {
            closeDetail()
            onSessionExpired()
            detailBusy = false
            return@LaunchedEffect
        } catch (_: Exception) {
            warnings += "As campanhas da ACP não puderam ser consultadas agora."
        }
        try {
            integration = api.integrationInfo()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: AcpUnauthorized) {
            closeDetail()
            onSessionExpired()
            detailBusy = false
            return@LaunchedEffect
        } catch (_: Exception) {
            warnings += "O status de sincronização não pôde ser consultado agora."
        } finally {
            detailWarning = warnings.takeIf { it.isNotEmpty() }?.joinToString(" ")
            detailBusy = false
        }
    }

    val result = page
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AcpSearchField.entries.forEach { option ->
                    FilterChip(
                        selected = field == option,
                        onClick = { field = option; invalidateResults() },
                        label = { Text(option.label) }
                    )
                }
            }
        }

        item {
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
        }

        item {
            Box {
                OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Categoria ACP: ${category?.description ?: "Todas"} ▾")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Todas") }, onClick = {
                        category = null
                        menu = false
                        invalidateResults()
                    })
                    categories.forEach { option ->
                        DropdownMenuItem(text = { Text(option.description) }, onClick = {
                            category = option
                            menu = false
                            invalidateResults()
                        })
                    }
                }
            }
        }

        categoryError?.let { message ->
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = { categoryAttempt++ }) { Text("Recarregar") }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { search() },
                    enabled = !busy && query.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) { Text(if (busy) "Buscando…" else "Buscar") }
                OutlinedButton(
                    onClick = { scanning = true },
                    enabled = !busy,
                    modifier = Modifier.weight(1f)
                ) { Text("Câmera") }
            }
        }

        if (page != null) {
            item {
                OutlinedButton(onClick = {
                    val text = api.diagnosticText()
                    if (text == null) diagnosticMessage = "Faça uma busca antes de copiar o diagnóstico."
                    else {
                        clipboard.setText(AnnotatedString(text))
                        diagnosticMessage = "Diagnóstico ACP copiado. Cole no ChatGPT para análise."
                    }
                }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text("Copiar diagnóstico ACP")
                }
            }
            item {
                Text(
                    "O diagnóstico registra respostas, endpoints e parâmetros desta busca. Dados sensíveis não são incluídos.",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        diagnosticMessage?.let { message ->
            item { Text(message, style = MaterialTheme.typography.bodySmall) }
        }
        if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }

        if (result != null && !busy) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Consulta: ${acpQueryTime(result.queriedAtMillis)}", style = MaterialTheme.typography.bodySmall)
                        if (result.items.isNotEmpty()) {
                            val label = if (result.totalCount == 1) "produto encontrado" else "produtos encontrados"
                            Text(
                                "${result.totalCount} $label • página ${result.pageIndex + 1} de ${result.totalPages.coerceAtLeast(1)}",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                    TextButton(onClick = { search(result.pageIndex) }) { Text("Atualizar") }
                }
            }
        }

        if (result == null && !busy && error == null) {
            item { Text("Busque um produto para consultar os preços na ACP.") }
        }
        if (result != null && result.items.isEmpty() && !busy) {
            item { Text("Nenhum produto encontrado. Confira o código ou tente outro filtro.") }
        }

        itemsIndexed(result?.items.orEmpty(), key = { index, product -> "${product.id}:$index" }) { _, product ->
            val directOffers = product.offers().forAutomaticDisplay()
            OutlinedCard(
                onClick = { openProduct(product) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(product.description, style = MaterialTheme.typography.titleMedium)
                    val identifiers = buildString {
                        append("Código: ${product.code.ifBlank { "não informado" }}")
                        if (product.barcode.isNotBlank()) append(" • EAN: ${product.barcode}")
                    }
                    Text(identifiers, style = MaterialTheme.typography.bodySmall)
                    Text(
                        "Preço ACP: ${product.value?.brl() ?: "não informado"}${product.unit?.let { " / $it" } ?: ""}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    product.unitLimitPerCPF?.takeIf { it.signum() > 0 }?.let {
                        Text("Limite: ${it.quantity()} un. por CPF", style = MaterialTheme.typography.bodySmall)
                    }
                    if (product.categories.isNotEmpty()) {
                        Text("Categorias: ${product.categories.joinToString()}", style = MaterialTheme.typography.labelSmall)
                    }
                    if (directOffers.isNotEmpty()) {
                        Spacer(Modifier.height(2.dp))
                        directOffers.forEach { AcpOfferPoster(it, compact = true) }
                    } else {
                        Text("Sem promoção explícita identificada no cadastro do produto.", style = MaterialTheme.typography.labelSmall)
                    }
                    Text("Ver ficha completa", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        if (result != null && result.totalPages > 1) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { search(result.pageIndex - 1) },
                        enabled = !busy && result.pageIndex > 0
                    ) { Text("Anterior") }
                    Text("${result.pageIndex + 1} / ${result.totalPages}", style = MaterialTheme.typography.labelLarge)
                    TextButton(
                        onClick = { search(result.pageIndex + 1) },
                        enabled = !busy && result.pageIndex + 1 < result.totalPages
                    ) { Text("Próxima") }
                }
            }
        }
    }

    if (scanning) AcpBarcodeScanner(onDismiss = { scanning = false }, onResult = { code -> scanning = false; invalidateResults(); field = AcpSearchField.BARCODE; category = null; query = code; search() })

    selected?.let { requested ->
        AlertDialog(
            onDismissRequest = { closeDetail() },
            title = { Text(detail?.description ?: requested.description) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (detail == null && detailBusy) {
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
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { openProduct(product) }, enabled = !detailBusy, modifier = Modifier.weight(1f)) {
                                Text("Atualizar preços")
                            }
                            if (canAddToNrd) {
                                OutlinedButton(onClick = { prepareAddToNrd(product) }, enabled = !detailBusy, modifier = Modifier.weight(1f)) {
                                    Text("Adicionar ao NRD")
                                }
                            }
                        }
                        nrdActionMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
                        Text("Código: ${product.code.ifBlank { "não informado" }}\nCód. barras: ${product.barcode.ifBlank { "não informado" }}")
                        Text("Preço principal: ${product.value?.brl() ?: "não informado"}${product.unit?.let { " / $it" } ?: ""}", style = MaterialTheme.typography.titleMedium)

                        val hasUsefulProductInfo = product.characteristic != null || product.productFamily != null ||
                            product.categories.isNotEmpty() || product.unitLimitPerCPF?.signum() == 1
                        if (hasUsefulProductInfo) {
                            HorizontalDivider()
                            Text("Informações do produto", style = MaterialTheme.typography.titleMedium)
                            product.characteristic?.let { Text("Característica: $it") }
                            product.productFamily?.let { Text("Família: $it") }
                            if (product.categories.isNotEmpty()) Text("Categorias ACP: ${product.categories.joinToString()}")
                            product.unitLimitPerCPF?.takeIf { it.signum() > 0 }?.let { Text("Limite cadastrado: ${it.quantity()} unidades por CPF.") }
                        }

                        val directOffers = product.offers()
                        val campaignDetailOffers = campaigns.flatMap { it.offersFor(product) }
                        val allOffers = (directOffers + campaignDetailOffers).forAutomaticDisplay()
                        HorizontalDivider()
                        Text("Preços e condições", style = MaterialTheme.typography.titleMedium)
                        if (allOffers.isEmpty()) {
                            AcpOfferPoster(AcpOffer("Preço cadastrado", "Nenhuma condição promocional explícita foi identificada nos dados consultados.", product.value), compact = false)
                        } else {
                            Text("Prévia automática do destaque", style = MaterialTheme.typography.titleSmall)
                            AcpOfferLandscapePoster(product.description, allOffers.first())
                            if (allOffers.size > 1) Text("Outras condições encontradas", style = MaterialTheme.typography.titleSmall)
                            allOffers.forEach { HorizontalDivider(); AcpOfferPoster(it, compact = false) }
                        }
                        if (!detailBusy && allOffers.none { it.title == "Cashback" || it.title == "Cashback em valor" }) {
                            Text(if (detailWarning == null) "Cashback não informado nos dados consultados da ACP."
                                else "Cashback não confirmado: a consulta complementar ficou incompleta.",
                                style = MaterialTheme.typography.bodySmall)
                        }


                        HorizontalDivider()
                        OutlinedCard(onClick = { syncExpanded = !syncExpanded }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("Sincronização", style = MaterialTheme.typography.titleMedium)
                                    Text(if (syncExpanded) "▲" else "▼")
                                }
                                if (syncExpanded) {
                                    if (detailBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
                                    integration?.let { info ->
                                        Text("Código de status: ${info.status?.toString() ?: "não informado"}")
                                        Text("Última execução: ${acpDateLabel(info.lastRun, includeTime = true) ?: "não informada"}")
                                        Text("Última execução completa: ${acpDateLabel(info.lastCompleteRun, includeTime = true) ?: "não informada"}")
                                        info.message?.let { Text("Mensagem: $it") }
                                        info.id?.let { Text("ID da integração: $it", style = MaterialTheme.typography.bodySmall) }
                                    }
                                    if (!detailBusy && integration == null) Text("Status de sincronização indisponível.")
                                }
                            }
                        }

                        if (campaigns.isNotEmpty()) {
                            HorizontalDivider()
                            Text("Campanhas vinculadas", style = MaterialTheme.typography.titleMedium)
                            campaigns.forEach { c ->
                                ElevatedCard(Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text(c.name, style = MaterialTheme.typography.titleSmall)
                                        c.code?.let { Text("Código: $it") }
                                        c.description?.takeIf { it != c.name }?.let { Text(it) }
                                        Text("Início: ${acpDateLabel(c.startDate) ?: "não informado"} • Fim: ${acpDateLabel(c.endDate) ?: "não informado"}")
                                        Text("Ativa: ${c.active?.let { if (it) "sim" else "não" } ?: "não informado"} • Autoexclusão: ${c.autoExclusion?.let { if (it) "sim" else "não" } ?: "não informado"}")
                                        val rules = c.productRules.count { it.matches(product) }
                                        if (rules > 0) Text("Regras promocionais vinculadas ao produto: $rules", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                        detailWarning?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { closeDetail() }) { Text("Fechar") } }
        )
    }

    addToNrdProduct?.let { product ->
        val selectedCode = when (nrdIdentifier) {
            NrdIdentifier.BARCODE -> product.barcode
            NrdIdentifier.PRODUCT_CODE -> product.code
        }.trim()
        val orderedCategories = activeNrdCategories.filter { it in selectedNrdCategories }
        AlertDialog(
            onDismissRequest = { closeAddToNrd() },
            title = { Text("Adicionar ao NRD") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(product.description, style = MaterialTheme.typography.titleMedium)
                    Text("Código do produto: ${product.code.ifBlank { "não informado" }}")
                    Text("Código de barras: ${product.barcode.ifBlank { "não informado" }}")
                    HorizontalDivider()
                    Text("Código que será usado no NRD", style = MaterialTheme.typography.titleSmall)
                    if (product.barcode.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = nrdIdentifier == NrdIdentifier.BARCODE, onClick = { if (!nrdSaving) nrdIdentifier = NrdIdentifier.BARCODE }, enabled = !nrdSaving)
                            Text("Código de barras • ${product.barcode}")
                        }
                    }
                    if (product.code.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = nrdIdentifier == NrdIdentifier.PRODUCT_CODE, onClick = { if (!nrdSaving) nrdIdentifier = NrdIdentifier.PRODUCT_CODE }, enabled = !nrdSaving)
                            Text("Código do produto • ${product.code}")
                        }
                    }

                    OutlinedCard(onClick = { if (!nrdSaving) nrdCategoriesExpanded = !nrdCategoriesExpanded }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Categorias (${selectedNrdCategories.size})", style = MaterialTheme.typography.titleSmall)
                                Text(if (nrdCategoriesExpanded) "▲" else "▼")
                            }
                            if (selectedNrdCategories.isNotEmpty() && !nrdCategoriesExpanded) {
                                Text(orderedCategories.joinToString(), style = MaterialTheme.typography.bodySmall)
                            }
                            if (nrdCategoriesExpanded) {
                                if (activeNrdCategories.isEmpty()) {
                                    Text("Nenhuma categoria ativa disponível no NRD.", color = MaterialTheme.colorScheme.error)
                                } else {
                                    activeNrdCategories.forEach { categoryName ->
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(
                                                checked = categoryName in selectedNrdCategories,
                                                onCheckedChange = { checked ->
                                                    if (!nrdSaving) selectedNrdCategories = if (checked) selectedNrdCategories + categoryName else selectedNrdCategories - categoryName
                                                },
                                                enabled = !nrdSaving
                                            )
                                            Text(categoryName)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (orderedCategories.isNotEmpty()) {
                        Text("O produto aparecerá em: ${orderedCategories.joinToString()}.", style = MaterialTheme.typography.bodySmall)
                    }
                    nrdError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (nrdSaving) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !nrdSaving && selectedCode.isNotBlank() && orderedCategories.isNotEmpty(),
                    onClick = {
                        nrdSaving = true
                        nrdError = null
                        scope.launch {
                            try {
                                val result = NrdProductImportService.addProduct(
                                    name = product.description,
                                    code = selectedCode,
                                    categories = orderedCategories
                                )
                                if (result.success) {
                                    nrdActionMessage = "Produto adicionado ao NRD em ${orderedCategories.size} categoria(s)."
                                    addToNrdProduct = null
                                    selectedNrdCategories = emptySet()
                                    nrdCategoriesExpanded = false
                                } else {
                                    nrdError = result.message ?: "Não foi possível adicionar o produto ao NRD."
                                }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                nrdError = "Não foi possível adicionar o produto ao NRD."
                            } finally {
                                nrdSaving = false
                            }
                        }
                    }
                ) { Text(if (nrdSaving) "Salvando…" else "Salvar") }
            },
            dismissButton = { TextButton(onClick = { closeAddToNrd() }, enabled = !nrdSaving) { Text("Cancelar") } }
        )
    }
}

private fun acpQueryTime(value: Long): String = SimpleDateFormat("dd/MM HH:mm:ss", Locale("pt", "BR")).format(Date(value))
