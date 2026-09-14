package com.example.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.CategoryDefinition
import com.example.data.AppearanceSettings
import com.example.data.FirebaseService
import com.example.data.NrdProductImportService
import com.example.data.acp.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class NrdIdentifier { BARCODE, PRODUCT_CODE }

@Composable
internal fun AcpProductsPanel(
    api: AcpApi,
    canAddToNrd: Boolean,
    appearance: AppearanceSettings,
    historyExportBusy: Boolean,
    historyExportMessage: String?,
    onExportHistory: (String, Int) -> Unit,
    onSessionExpired: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val freshStore = remember(context) { AcpSecureStore(context.applicationContext) }
    val keyboard = LocalSoftwareKeyboardController.current
    val clipboard = LocalClipboardManager.current
    val nrdCategoriesFlow = remember { FirebaseService.observeCategories() }
    val nrdDefinitions by nrdCategoriesFlow.collectAsState(initial = CategoryDefinition.defaults)
    val activeNrdCategories = remember(nrdDefinitions) {
        nrdDefinitions.filter { it.isActive }
            .sortedWith(compareBy<CategoryDefinition> { it.displayOrder }.thenBy { it.name })
            .map { it.name }
    }
    val canCopyDiagnostic =
        com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email?.trim()?.lowercase() == "mestre@nrdlojas.com"

    var query by remember { mutableStateOf("") }
    var page by remember { mutableStateOf<AcpProductPage?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var diagnosticMessage by remember { mutableStateOf<String?>(null) }
    var historyPage by remember { mutableStateOf("0") }
    var historyBusy by remember { mutableStateOf(false) }

    var selected by remember { mutableStateOf<AcpProduct?>(null) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var generation by remember { mutableIntStateOf(0) }
    var scanning by remember { mutableStateOf(false) }
    var campaigns by remember { mutableStateOf<List<AcpCampaign>>(emptyList()) }
    var previewCampaignOffers by remember { mutableStateOf<Map<String, List<AcpOffer>>>(emptyMap()) }
    var integration by remember { mutableStateOf<AcpIntegrationInfo?>(null) }
    var detailBusy by remember { mutableStateOf(false) }
    var detailWarning by remember { mutableStateOf<String?>(null) }
    var syncExpanded by remember { mutableStateOf(false) }
    var lastExplicitQuery by remember { mutableStateOf<String?>(null) }

    var detail by remember { mutableStateOf<AcpProduct?>(null) }
    var detailError by remember { mutableStateOf<String?>(null) }
    var detailTime by remember { mutableStateOf<Long?>(null) }
    var detailAttempt by remember { mutableIntStateOf(0) }

    var barcodeDialogProduct by remember { mutableStateOf<com.example.data.Product?>(null) }
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

    fun search(index: Int = 0, interactive: Boolean = true, enrichCampaigns: Boolean = interactive) {
        val searchText = query.trim()
        if (searchText.isBlank()) {
            if (interactive) error = "Digite um código, código de barras ou descrição."
            return
        }
        searchJob?.cancel()
        val ticket = ++generation
        if (interactive) {
            busy = true
            diagnosticMessage = null
            lastExplicitQuery = searchText
            keyboard?.hide()
        } else {
            // Sugestões nunca prendem a interface nem escondem o teclado.
            busy = false
        }
        error = null
        closeDetail()
        if (index == 0 && canCopyDiagnostic && interactive) api.beginDiagnosticSession()
        searchJob = scope.launch {
            try {
                val result = api.searchProductsUnified(searchText, index)
                if (ticket != generation) return@launch

                // Product/all é o caminho crítico. Assim que ele responde, o produto já fica
                // disponível e clicável. Dados complementares não seguram mais a pesquisa.
                page = result
                busy = false

                if (!enrichCampaigns) {
                    previewCampaignOffers = emptyMap()
                    return@launch
                }

                previewCampaignOffers = try {
                    api.campaignOffersFor(result.items)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: AcpUnauthorized) {
                    if (ticket == generation) onSessionExpired()
                    emptyMap()
                } catch (_: Exception) {
                    emptyMap()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: AcpUnauthorized) {
                if (ticket == generation) onSessionExpired()
            } catch (failure: Exception) {
                if (ticket == generation && interactive) error = acpErrorMessage(failure)
            } finally {
                if (ticket == generation) busy = false
            }
        }
    }

    // Comportamento do editor web: enquanto o nome é digitado, os candidatos aparecem abaixo.
    // Um debounce curto evita uma chamada por tecla, e cada nova digitação cancela a anterior.
    LaunchedEffect(query) {
        val clean = query.trim()
        if (clean.isEmpty()) {
            searchJob?.cancel()
            generation++
            busy = false
            page = null
            previewCampaignOffers = emptyMap()
            error = null
            lastExplicitQuery = null
            return@LaunchedEffect
        }
        if (clean.length < 2 || clean == lastExplicitQuery) return@LaunchedEffect
        delay(180)
        if (query.trim() == clean && clean != lastExplicitQuery) {
            search(index = 0, interactive = false, enrichCampaigns = false)
        }
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
            api.refreshProductFreshForUi(requested, freshStore)
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
        page = page?.let { old ->
            old.copy(items = old.items.map { item ->
                if (item.id == requested.id && item.code == requested.code && item.barcode == requested.barcode) product else item
            })
        }
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
            warnings += "As campanhas não puderam ser consultadas agora."
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
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it.take(200)
                    lastExplicitQuery = null
                    error = null
                },
                placeholder = { Text("Faça sua busca") },
                supportingText = { Text("Código, código de barras ou descrição do produto") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { scanning = true }, enabled = !busy) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "Ler código de barras")
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { search() })
            )
        }

        item {
            Button(
                onClick = { search() },
                enabled = !busy && query.isNotBlank(),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) {
                Icon(Icons.Default.Search, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (busy) "Pesquisando…" else "Pesquisar produto", fontWeight = FontWeight.Bold)
            }
        }

        if (page != null && canCopyDiagnostic) {
            item {
                OutlinedButton(
                    onClick = {
                        val text = api.diagnosticText()
                        if (text == null) {
                            diagnosticMessage = "Faça uma busca antes de copiar o diagnóstico."
                        } else {
                            clipboard.setText(AnnotatedString(text))
                            diagnosticMessage = "Diagnóstico copiado. Cole no ChatGPT para análise."
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Copiar diagnóstico")
                }
            }
        }

        if (canCopyDiagnostic) item {
            AcpEvidencePanel(api, freshStore, historyExportBusy, onExportHistory)
        }

        if (canCopyDiagnostic) item {
            OutlinedTextField(
                value = historyPage,
                onValueChange = { historyPage = it.filter(Char::isDigit).take(6) },
                label = { Text("Página do histórico (começa em 0)") },
                enabled = !historyBusy && !historyExportBusy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedButton(
                enabled = !busy && !historyBusy && !historyExportBusy && historyPage.toIntOrNull() != null,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val index = historyPage.toIntOrNull() ?: 0
                    historyBusy = true
                    diagnosticMessage = "Consultando histórico da ACP…"
                    scope.launch {
                        try {
                            val payload = api.captureHistory(index)
                            onExportHistory(payload, index)
                            historyBusy = false
                            diagnosticMessage = null
                        } catch (cancelled: CancellationException) {
                            historyBusy = false
                            throw cancelled
                        } catch (_: AcpUnauthorized) {
                            historyBusy = false
                            onSessionExpired()
                        } catch (_: Exception) {
                            historyBusy = false
                            diagnosticMessage = "Não foi possível capturar o histórico. Tente novamente."
                        }
                    }
                }
            ) { Text(if (historyBusy) "Capturando histórico…" else "Salvar diagnóstico do histórico") }
        }

        if (canCopyDiagnostic) historyExportMessage?.let { message -> item { Text(message, style = MaterialTheme.typography.bodySmall) } }
        if (canCopyDiagnostic) diagnosticMessage?.let { message -> item { Text(message, style = MaterialTheme.typography.bodySmall) } }
        if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }

        if (result != null) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Consulta: ${acpQueryTime(result.queriedAtMillis)}", style = MaterialTheme.typography.bodySmall)
                        if (result.items.isNotEmpty()) {
                            val label = if (result.items.size == 1) "resultado nesta página" else "resultados nesta página"
                            Text("${result.items.size} $label", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    TextButton(onClick = { search(result.pageIndex) }, enabled = !busy) { Text("Atualizar") }
                }
            }
        }

        if (result == null && !busy && error == null) {
            item { Text("Digite ao menos 2 caracteres para ver sugestões, ou pesquise por código/código de barras.") }
        }
        if (result != null && result.items.isEmpty() && !busy) {
            item { Text("Nenhum produto encontrado. Confira o termo e tente novamente.") }
        }

        itemsIndexed(result?.items.orEmpty(), key = { index, product -> "${product.id}:$index" }) { _, product ->
            val directOffers = product.offers()
            val previewOffers = (directOffers + previewCampaignOffers[product.id].orEmpty()).forAutomaticDisplay()
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
                        "Preço: ${product.value?.brl() ?: "não informado"}${product.unit?.let { " / $it" } ?: ""}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    product.unitLimitPerCPF?.takeIf { it.signum() > 0 }?.let {
                        Text("Limite: ${it.quantity()} un. por CPF", style = MaterialTheme.typography.bodySmall)
                    }
                    if (previewOffers.isNotEmpty()) {
                        Spacer(Modifier.height(2.dp))
                        previewOffers.forEach { offer ->
                            AcpOfferPoster(
                                offer = offer,
                                compact = true,
                                productName = product.description,
                                banner = appearance.activeOfferBanner(offer.bannerKey)
                            )
                        }
                    } else {
                        val standardBanner = appearance.activeOfferBanner(com.example.data.OFFER_BANNER_STANDARD)
                        if (standardBanner != null) {
                            AcpOfferPoster(
                                offer = AcpOffer(
                                    "Preço cadastrado",
                                    "Produto sem promoção especial.",
                                    product.value
                                ),
                                compact = true,
                                productName = product.description,
                                banner = standardBanner
                            )
                        } else {
                            Text("Sem promoção explícita identificada no cadastro do produto.", style = MaterialTheme.typography.labelSmall)
                        }
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

    if (scanning) {
        AcpBarcodeScanner(
            onDismiss = { scanning = false },
            onResult = { code ->
                scanning = false
                query = code
                search()
            }
        )
    }

    selected?.let { requested ->
        AlertDialog(
            onDismissRequest = { closeDetail() },
            title = { Text(detail?.description ?: requested.description) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (detail == null && detailBusy) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("Consultando preços…")
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
                            OutlinedButton(
                                onClick = {
                                    val barcodeValue = product.barcode.ifBlank { product.code }.trim()
                                    if (barcodeValue.isNotBlank()) {
                                        barcodeDialogProduct = com.example.data.Product(
                                            code = barcodeValue,
                                            name = product.description,
                                            searchName = product.description.lowercase(Locale.getDefault()),
                                            category = product.categories.firstOrNull().orEmpty().ifBlank { "Varejo" },
                                            unit = product.unit ?: "un"
                                        )
                                    }
                                },
                                enabled = !detailBusy && (product.barcode.isNotBlank() || product.code.isNotBlank()),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Ver Cód Barra")
                            }
                        }
                        if (canAddToNrd) {
                            OutlinedButton(
                                onClick = { prepareAddToNrd(product) },
                                enabled = !detailBusy,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Adicionar ao NRD")
                            }
                        }
                        nrdActionMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
                        Text("Código: ${product.code.ifBlank { "não informado" }}\nCód. barras: ${product.barcode.ifBlank { "não informado" }}")
                        Text(
                            "Preço principal: ${product.value?.brl() ?: "não informado"}${product.unit?.let { " / $it" } ?: ""}",
                            style = MaterialTheme.typography.titleMedium
                        )

                        val hasUsefulProductInfo = product.characteristic != null || product.productFamily != null ||
                            product.categories.isNotEmpty() || product.unitLimitPerCPF?.signum() == 1
                        if (hasUsefulProductInfo) {
                            HorizontalDivider()
                            Text("Informações do produto", style = MaterialTheme.typography.titleMedium)
                            product.characteristic?.let { Text("Característica: $it") }
                            product.productFamily?.let { Text("Família: $it") }
                            if (product.categories.isNotEmpty()) Text("Categorias: ${product.categories.joinToString()}")
                            product.unitLimitPerCPF?.takeIf { it.signum() > 0 }?.let {
                                Text("Limite cadastrado: ${it.quantity()} unidades por CPF.")
                            }
                        }

                        val directOffers = product.offers()
                        val campaignDetailOffers = campaigns.flatMap { it.offersFor(product) }
                        val allOffers = (directOffers + campaignDetailOffers).forAutomaticDisplay()
                        HorizontalDivider()
                        Text("Preços e condições", style = MaterialTheme.typography.titleMedium)
                        if (allOffers.isEmpty()) {
                            AcpOfferPoster(
                                AcpOffer(
                                    "Preço cadastrado",
                                    "Nenhuma condição promocional explícita foi identificada nos dados consultados.",
                                    product.value
                                ),
                                compact = false,
                                productName = product.description,
                                banner = appearance.activeOfferBanner(com.example.data.OFFER_BANNER_STANDARD)
                            )
                        } else {
                            Text("Cartazes automáticos em paisagem", style = MaterialTheme.typography.titleSmall)
                            allOffers.forEachIndexed { index, offer ->
                                if (index > 0) HorizontalDivider()
                                AcpOfferLandscapePoster(
                                    product.description,
                                    offer,
                                    appearance.activeOfferBanner(offer.bannerKey)
                                )
                            }
                        }
                        if (!detailBusy && allOffers.none { it.title == "Cashback" || it.title == "Cashback em valor" }) {
                            Text(
                                if (detailWarning == null) "Cashback não informado nos dados consultados."
                                else "Cashback não confirmado: a consulta complementar ficou incompleta.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        HorizontalDivider()
                        ActiveFlyerOffersForAcpProduct(product)
                        OutlinedCard(onClick = { syncExpanded = !syncExpanded }, modifier = Modifier.fillMaxWidth()) {
                            Column(
                                Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
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
                            campaigns.forEach { campaign ->
                                ElevatedCard(Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text(campaign.name, style = MaterialTheme.typography.titleSmall)
                                        campaign.code?.let { Text("Código: $it") }
                                        campaign.description?.takeIf { it != campaign.name }?.let { Text(it) }
                                        Text("Início: ${acpDateLabel(campaign.startDate) ?: "não informado"} • Fim: ${acpDateLabel(campaign.endDate) ?: "não informado"}")
                                        Text("Ativa: ${campaign.active?.let { if (it) "sim" else "não" } ?: "não informado"} • Autoexclusão: ${campaign.autoExclusion?.let { if (it) "sim" else "não informado"} }")
                                        val rules = campaign.productRules.count { it.matches(product) }
                                        if (rules > 0) {
                                            Text("Regras promocionais vinculadas ao produto: $rules", style = MaterialTheme.typography.labelSmall)
                                        }
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

    barcodeDialogProduct?.let { barcodeProduct ->
        ProductBarcodeDialog(
            product = barcodeProduct,
            onDismiss = { barcodeDialogProduct = null }
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
                            RadioButton(
                                selected = nrdIdentifier == NrdIdentifier.BARCODE,
                                onClick = { if (!nrdSaving) nrdIdentifier = NrdIdentifier.BARCODE },
                                enabled = !nrdSaving
                            )
                            Text("Código de barras • ${product.barcode}")
                        }
                    }
                    if (product.code.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = nrdIdentifier == NrdIdentifier.PRODUCT_CODE,
                                onClick = { if (!nrdSaving) nrdIdentifier = NrdIdentifier.PRODUCT_CODE },
                                enabled = !nrdSaving
                            )
                            Text("Código do produto • ${product.code}")
                        }
                    }

                    OutlinedCard(
                        onClick = { if (!nrdSaving) nrdCategoriesExpanded = !nrdCategoriesExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
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
                                                    if (!nrdSaving) {
                                                        selectedNrdCategories = if (checked) {
                                                            selectedNrdCategories + categoryName
                                                        } else {
                                                            selectedNrdCategories - categoryName
                                                        }
                                                    }
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
            dismissButton = {
                TextButton(onClick = { closeAddToNrd() }, enabled = !nrdSaving) { Text("Cancelar") }
            }
        )
    }
}

private suspend fun AcpApi.searchProductsUnified(query: String, pageIndex: Int): AcpProductPage {
    val clean = query.trim()
    require(clean.isNotBlank() && clean.length <= 200 && pageIndex >= 0)
    val numeric = clean.all(Char::isDigit)
    val field = when {
        !numeric -> AcpSearchField.DESCRIPTION
        clean.length in setOf(8, 12, 13, 14) -> AcpSearchField.BARCODE
        else -> AcpSearchField.CODE
    }
    return searchProducts(field, clean, null, pageIndex)
}

private suspend fun AcpApi.refreshProductFreshForUi(selected: AcpProduct, store: AcpSecureStore): AcpProduct {
    val filters = buildList {
        if (selected.code.isNotBlank()) add("code" to selected.code)
        if (selected.barcode.isNotBlank()) add("barCode" to selected.barcode)
    }
    if (filters.isEmpty()) throw AcpFailure("Produto sem código para atualizar. Faça uma nova busca.")
    val matches = mutableListOf<AcpProduct>()
    for (index in 0 until 10) {
        val page = AcpProductParser.page(
            getFreshForUi(store, "Product/all", filters + listOf("pageIndex" to index.toString(), "pageSize" to "50")),
            index
        )
        matches.addAll(page.items.filter { candidate ->
            (selected.code.isBlank() || candidate.code == selected.code) &&
                (selected.barcode.isBlank() || candidate.barcode == selected.barcode)
        })
        if (matches.size > 1) {
            throw AcpFailure("A consulta retornou mais de um cadastro com esses códigos. Confira o produto no sistema.")
        }
        if (page.items.isEmpty() || index + 1 >= page.totalPages) {
            return matches.singleOrNull()
                ?: throw AcpFailure("Produto não encontrado na atualização. Faça uma nova busca.")
        }
    }
    throw AcpFailure("Não foi possível confirmar o produto entre os resultados. Refine a busca.")
}

private suspend fun AcpApi.getFreshForUi(
    store: AcpSecureStore,
    path: String,
    parameters: List<Pair<String, String>>
): org.json.JSONObject {
    store.clear(acpResponseCacheName(path, parameters))
    return get(path, parameters)
}

private fun acpResponseCacheName(path: String, parameters: List<Pair<String, String>>): String {
    val rawKey = buildString {
        append(path)
        parameters.sortedWith(compareBy<Pair<String, String>> { it.first }.thenBy { it.second }).forEach { (key, value) ->
            append('|').append(key).append('=').append(value)
        }
    }
    val digest = MessageDigest.getInstance("SHA-256").digest(rawKey.toByteArray(Charsets.UTF_8))
    val hex = digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    return "response_cache_$hex"
}

private fun acpQueryTime(value: Long): String =
    SimpleDateFormat("dd/MM HH:mm:ss", Locale("pt", "BR")).format(Date(value))