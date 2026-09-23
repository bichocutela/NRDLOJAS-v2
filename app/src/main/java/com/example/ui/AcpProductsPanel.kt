package com.example.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class NrdIdentifier { BARCODE, PRODUCT_CODE }

private const val AUTO_PRICE_REFRESH_MILLIS = 15_000L
private const val FEATURED_LOAD_IDLE_DELAY_MILLIS = 1_200L

private enum class AcpFeaturedSort(val label: String) {
    MAIOR_DESCONTO("Maior desconto"),
    MENOS_DESCONTO("Menos desconto"),
    NOME("Nome"),
    MENOR_PRECO("Menor preço"),
    MAIOR_PRECO("Maior preço")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
    val offerValidityByKey by remember { AcpOfferValidityStore.observeAll() }
        .collectAsState(initial = emptyMap())
    val freshStore = remember(context) { AcpSecureStore(context.applicationContext) }
    val keyboard = LocalSoftwareKeyboardController.current
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
    var refreshing by remember { mutableStateOf(false) }
    var refreshMessage by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

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

    var featuredOffers by remember { mutableStateOf<List<AcpFeaturedOffer>>(emptyList()) }
    var featuredLoading by remember { mutableStateOf(false) }
    var featuredVisible by remember { mutableStateOf(true) }
    var featuredExpanded by remember { mutableStateOf(false) }
    var featuredSort by remember { mutableStateOf(AcpFeaturedSort.MENOR_PRECO) }
    var featuredServerPage by remember { mutableIntStateOf(0) }
    var featuredHasMore by remember { mutableStateOf(true) }
    var featuredJob by remember { mutableStateOf<Job?>(null) }
    var featuredRefreshJob by remember { mutableStateOf<Job?>(null) }
    val remoteHomeSettings by remember { FirebaseService.observeHomeSettings() }
        .collectAsState(initial = com.example.data.RemoteHomeSettings())
    val featuredIntervalSeconds = (remoteHomeSettings.carouselIntervalSeconds ?: 4).coerceIn(3, 30)

    fun refreshFeaturedFromAcp() {
        if (featuredRefreshJob?.isActive == true || featuredLoading) return
        featuredRefreshJob = scope.launch {
            try {
                val latest = api.featuredOffersPage(page = 0, forceFresh = true)
                if (query.isBlank() && featuredVisible) {
                    val refreshedKeys = latest.items.mapTo(mutableSetOf()) { "${it.product.id}|${it.offer.family}" }
                    featuredOffers = (latest.items + featuredOffers.filterNot {
                        "${it.product.id}|${it.offer.family}" in refreshedKeys
                    }).distinctBy { "${it.product.id}|${it.offer.family}" }
                    featuredHasMore = latest.hasMore || featuredServerPage > 0
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Keep showing the last successful list; the next refresh retries.
            } finally {
                featuredRefreshJob = null
            }
        }
    }

    fun loadFeaturedPage(serverPage: Int, append: Boolean, loadAll: Boolean = false) {
        if (append) featuredRefreshJob?.cancel()
        if (featuredLoading || serverPage < 0) return
        featuredLoading = true
        featuredJob = scope.launch {
            try {
                var nextPage = serverPage
                var shouldAppend = append
                var more = true
                do {
                    val result = api.featuredOffersPage(nextPage)
                    val loaded = result.items
                    if (shouldAppend) {
                        featuredOffers = (featuredOffers + loaded)
                            .distinctBy { "${it.product.id}|${it.offer.family}" }
                    } else {
                        featuredOffers = loaded
                    }
                    featuredServerPage = nextPage
                    more = result.hasMore
                    featuredHasMore = more
                    shouldAppend = true
                    nextPage++
                    // Yield between ACP pages so search and the rest of the screen
                    // remain responsive while the complete ordering is assembled.
                    if (loadAll && more) delay(40)
                } while (loadAll && more)
            } catch (_: Exception) {
                if (!append) featuredOffers = emptyList()
                featuredHasMore = false
            } finally {
                featuredLoading = false
                featuredJob = null
            }
            if (!append && !loadAll) refreshFeaturedFromAcp()
        }
    }

    LaunchedEffect(api) {
        // Draw cached cards first, then silently replace them with current ACP data.
        delay(FEATURED_LOAD_IDLE_DELAY_MILLIS)
        loadFeaturedPage(0, append = false)
        while (true) {
            delay(5 * 60 * 1000L)
            if (query.isBlank() && featuredVisible) refreshFeaturedFromAcp()
        }
    }

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
            featuredJob?.cancel()
            featuredRefreshJob?.cancel()
            api.stopBackgroundSync()
            busy = true
            featuredVisible = false
            featuredExpanded = false
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
                val result = if (interactive) {
                    // Busca explícita sempre vai direto ao ACP. As sugestões em segundo plano
                    // continuam podendo aproveitar o cache para não consultar a cada tecla.
                    api.searchProductsUnifiedFresh(searchText, index, freshStore)
                } else {
                    api.searchProductsUnified(searchText, index)
                }
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
                if (interactive && ticket == generation) api.warmFeaturedCatalog()
            }
        }
    }

    fun refreshFromAcp() {
        if (refreshing) return
        searchJob?.cancel()
        val ticket = ++generation
        refreshing = true
        error = null
        refreshMessage = null
        closeDetail()
        searchJob = scope.launch {
            try {
                // Confirma/reutiliza a sessão existente. Se ela expirou, AcpApi renova sem
                // afetar o login do NRD e sem transformar o gesto em logout.
                api.confirmAccess()
                val clean = query.trim()
                if (clean.isBlank()) {
                    // Sem uma pesquisa aberta não há uma lista de preços para substituir.
                    // Ainda assim validamos a sessão e a integração com o ACP.
                    integration = try { api.integrationInfo() } catch (_: Exception) { null }
                    refreshMessage = "Conexão com o ACP atualizada. Pesquise um produto para carregar o preço mais recente."
                } else {
                    val targetPage = page?.pageIndex ?: 0
                    val fresh = api.searchProductsUnifiedFresh(clean, targetPage, freshStore)
                    if (ticket != generation) return@launch
                    page = fresh
                    lastExplicitQuery = clean
                    previewCampaignOffers = try {
                        api.campaignOffersFor(fresh.items)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        emptyMap()
                    }
                    refreshMessage = "Preços atualizados agora pelo ACP."
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (ticket == generation) {
                    // Mantém a tela autenticada. O gesto nunca expulsa o usuário da conta.
                    error = acpErrorMessage(failure)
                }
            } finally {
                if (ticket == generation) refreshing = false
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

    // Mantém a consulta visível próxima do ACP sem exigir gesto do usuário.
    // O ACP não envia eventos para este cliente; por isso fazemos uma leitura leve e silenciosa
    // somente enquanto existe uma busca aberta. "Atualizar" e pull-to-refresh continuam sendo
    // o caminho imediato, sem aguardar o próximo ciclo.
    LaunchedEffect(api, query, page?.pageIndex) {
        while (true) {
            delay(AUTO_PRICE_REFRESH_MILLIS)
            val clean = query.trim()
            val currentPage = page ?: continue
            if (clean.length < 2 || busy || refreshing) continue

            try {
                val fresh = api.searchProductsUnifiedFresh(clean, currentPage.pageIndex, freshStore)
                if (query.trim() != clean || page?.pageIndex != currentPage.pageIndex) continue

                page = fresh
                val currentSelected = selected
                if (currentSelected != null) {
                    fresh.items.firstOrNull { item -> item.id == currentSelected.id }?.let { updated ->
                        detail = updated
                        detailTime = System.currentTimeMillis()
                        selected = updated
                    }
                }

                // Mantém também os selos/condições que já são montados para a lista.
                previewCampaignOffers = try {
                    api.campaignOffersFor(fresh.items)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    previewCampaignOffers
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: AcpUnauthorized) {
                onSessionExpired()
                return@LaunchedEffect
            } catch (_: Exception) {
                // Falha automática é silenciosa. O usuário mantém os últimos dados visíveis
                // e ainda pode usar Atualizar/pull-to-refresh para uma tentativa imediata.
            }
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
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { refreshFromAcp() },
        modifier = Modifier.fillMaxSize()
    ) {
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
                    refreshMessage = null
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

        if (featuredVisible && (featuredLoading || featuredOffers.isNotEmpty())) {
            item {
                AcpFeaturedOffers(
                    offers = featuredOffers,
                    loading = featuredLoading,
                    expanded = featuredExpanded,
                    sort = featuredSort,
                    intervalSeconds = featuredIntervalSeconds,
                    serverPage = featuredServerPage,
                    hasMore = featuredHasMore,
                    appearance = appearance,
                    validityByOffer = offerValidityByKey,
                    onToggleExpanded = { featuredExpanded = !featuredExpanded },
                    onHide = { featuredVisible = false },
                    onSortChanged = { featuredSort = it },
                    onLoadNextServerPage = { loadFeaturedPage(featuredServerPage + 1, append = true) },
                    onOpen = { openProduct(it.product) }
                )
            }
        } else if (!featuredVisible) {
            item {
                OutlinedButton(
                    onClick = { featuredVisible = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Text("Mostrar ofertas em destaque", fontWeight = FontWeight.Bold)
                }
            }
        }

        item {
            VisualMixOrderImportButton(api)
        }

        if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        refreshMessage?.let { message ->
            item { Text(message, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
        }
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
                    TextButton(onClick = { refreshFromAcp() }, enabled = !busy && !refreshing) { Text("Atualizar") }
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
            val previewOffers = (directOffers + previewCampaignOffers[product.id].orEmpty())
                .forAutomaticDisplay()
                .filter { offer ->
                    val saved = offerValidityByKey[AcpOfferValidityStore.keyFor(product.description, offer.family)]
                    product.isWithinOfferValidity(saved)
                }
            val shareLayer = rememberGraphicsLayer()
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawWithContent {
                        shareLayer.record {
                            this@drawWithContent.drawContent()
                        }
                        drawLayer(shareLayer)
                    }
                    .combinedClickable(
                        enabled = !busy,
                        onClick = { openProduct(product) },
                        onLongClick = {
                            scope.launch {
                                val copied = copyProductCardToClipboard(
                                    context = context,
                                    layer = shareLayer,
                                    productName = product.description
                                )
                                Toast.makeText(
                                    context,
                                    if (copied) "Copiado na Área de Transferência"
                                    else "Não foi possível copiar o quadradinho.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
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
                                banner = appearance.activeOfferBanner(offer.bannerKey),
                                validityOverride = product.offerValidityOr(
                                    offerValidityByKey[AcpOfferValidityStore.keyFor(product.description, offer.family)]
                                )
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
                                banner = standardBanner,
                                validityOverride = product.offerValidityOr(
                                    offerValidityByKey[AcpOfferValidityStore.keyFor(product.description, AcpOfferFamily.PRICE)]
                                )
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
            title = { SelectionContainer { Text(detail?.description ?: requested.description) } },
            text = {
                SelectionContainer {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                        NrdTwoActionLayout(
                            stackOnCompact = true,
                            first = { actionModifier ->
                                TextButton(
                                    onClick = { openProduct(product) },
                                    enabled = !detailBusy,
                                    modifier = actionModifier
                                ) { Text("Atualizar preços", maxLines = 2) }
                            },
                            second = { actionModifier ->
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
                                    modifier = actionModifier
                                ) { Text("Ver Cód Barra", maxLines = 2) }
                            }
                        )
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
                                Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
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


internal suspend fun copyProductCardToClipboard(
    context: android.content.Context,
    layer: androidx.compose.ui.graphics.layer.GraphicsLayer,
    productName: String,
    backgroundColor: Int? = null
): Boolean = runCatching {
    val imageBitmap = layer.toImageBitmap()
    val bitmap = imageBitmap.asAndroidBitmap()
    val exportBitmap = if (backgroundColor == null) {
        bitmap
    } else {
        Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888).also { flattened ->
            Canvas(flattened).apply {
                drawColor(backgroundColor)
                drawBitmap(bitmap, 0f, 0f, null)
            }
        }
    }
    val file = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "shared_cards").apply { mkdirs() }
        directory.listFiles()?.forEach { old ->
            if (System.currentTimeMillis() - old.lastModified() > 24 * 60 * 60 * 1000L) old.delete()
        }
        val safeName = productName
            .lowercase(Locale.getDefault())
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(48)
            .ifBlank { "produto" }
        File(directory, "nrd-${safeName}-${System.currentTimeMillis()}.png").also { target ->
            FileOutputStream(target).use { output ->
                check(exportBitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
        }
    }
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newUri(context.contentResolver, "Produto NRD", uri))
    true
}.getOrDefault(false)

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

private suspend fun AcpApi.searchProductsUnifiedFresh(
    query: String,
    pageIndex: Int,
    store: AcpSecureStore
): AcpProductPage {
    val clean = query.trim()
    require(clean.isNotBlank() && clean.length <= 200 && pageIndex >= 0)
    val numeric = clean.all(Char::isDigit)
    val preferred = when {
        !numeric -> AcpSearchField.DESCRIPTION
        clean.length in setOf(8, 12, 13, 14) -> AcpSearchField.BARCODE
        else -> AcpSearchField.CODE
    }

    fun clear(field: AcpSearchField, page: Int) {
        val parameters = listOf(
            "pageSize" to "20",
            "pageIndex" to page.toString(),
            field.parameter to clean
        )
        store.clear(acpResponseCacheName("Product/all", parameters))
    }

    // Product/all normalmente usa cache diário. O gesto explícito de atualizar é a exceção:
    // limpamos somente as chaves da pesquisa visível, sem varrer nem apagar o restante do cache.
    clear(preferred, pageIndex)
    if (numeric && pageIndex == 0) {
        val alternate = if (preferred == AcpSearchField.BARCODE) AcpSearchField.CODE else AcpSearchField.BARCODE
        clear(alternate, 0)
    }
    return searchProductsUnified(clean, pageIndex)
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

@Composable
private fun AcpFeaturedOffers(
    offers: List<AcpFeaturedOffer>,
    loading: Boolean,
    expanded: Boolean,
    sort: AcpFeaturedSort,
    intervalSeconds: Int,
    serverPage: Int,
    hasMore: Boolean,
    appearance: AppearanceSettings,
    validityByOffer: Map<String, AcpOfferValidity>,
    onToggleExpanded: () -> Unit,
    onHide: () -> Unit,
    onSortChanged: (AcpFeaturedSort) -> Unit,
    onLoadNextServerPage: () -> Unit,
    onOpen: (AcpFeaturedOffer) -> Unit
) {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val compactScreen = screenWidthDp < 420
    val featuredCardWidth = (screenWidthDp - 48).coerceIn(220, 280).dp
    val orderedOffers = remember(offers, sort, validityByOffer) {
        val comparator = when (sort) {
            AcpFeaturedSort.MAIOR_DESCONTO -> compareByDescending<AcpFeaturedOffer> { it.discountAmount() }
            AcpFeaturedSort.MENOS_DESCONTO -> compareBy<AcpFeaturedOffer> { it.discountAmount() }
            AcpFeaturedSort.NOME -> compareBy<AcpFeaturedOffer> { it.product.description.lowercase() }
            AcpFeaturedSort.MENOR_PRECO -> compareBy<AcpFeaturedOffer> { it.offer.price }
            AcpFeaturedSort.MAIOR_PRECO -> compareByDescending<AcpFeaturedOffer> { it.offer.price }
        }
        offers
            .filter { item ->
                val saved = validityByOffer[AcpOfferValidityStore.keyFor(item.product.description, item.offer.family)]
                item.product.isWithinOfferValidity(saved)
            }
            .sortedWith(comparator.thenBy { it.product.description })
    }
    var filterMenuExpanded by remember { mutableStateOf(false) }
    var expandedPage by rememberSaveable { mutableIntStateOf(0) }
    val pages = remember(orderedOffers) { orderedOffers.chunked(30) }
    val displayPage = expandedPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
    val carouselOffers = orderedOffers.take(20)
    val carouselState = rememberLazyListState()

    LaunchedEffect(carouselOffers, intervalSeconds, expanded) {
        if (expanded || carouselOffers.size < 2) return@LaunchedEffect
        while (true) {
            delay(intervalSeconds * 1000L)
            val next = (carouselState.firstVisibleItemIndex + 1) % carouselOffers.size
            carouselState.animateScrollToItem(next)
        }
    }

    LaunchedEffect(serverPage) {
        if (serverPage == 0) expandedPage = 0
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (compactScreen) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("Ofertas em destaque", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Condições promocionais informadas pela ACP", style = MaterialTheme.typography.bodySmall)
            }
            if (offers.isNotEmpty()) {
                AcpFeaturedOfferActions(
                    sort = sort,
                    expanded = expanded,
                    onSortChanged = onSortChanged,
                    onHide = onHide,
                    onToggleExpanded = onToggleExpanded,
                    filterMenuExpanded = filterMenuExpanded,
                    onFilterMenuExpandedChange = { filterMenuExpanded = it }
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Ofertas em destaque", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Condições promocionais informadas pela ACP", style = MaterialTheme.typography.bodySmall)
                }
                if (offers.isNotEmpty()) {
                    AcpFeaturedOfferActions(
                        modifier = Modifier.widthIn(max = 240.dp),
                        sort = sort,
                        expanded = expanded,
                        onSortChanged = onSortChanged,
                        onHide = onHide,
                        onToggleExpanded = onToggleExpanded,
                        filterMenuExpanded = filterMenuExpanded,
                        onFilterMenuExpandedChange = { filterMenuExpanded = it }
                    )
                }
            }
        }
        if (loading && offers.isEmpty()) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else if (expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                pages.getOrNull(displayPage).orEmpty().forEach { item ->
                    AcpFeaturedOfferCard(
                        item,
                        appearance,
                        item.product.offerValidityOr(validityByOffer[AcpOfferValidityStore.keyFor(item.product.description, item.offer.family)]),
                        onOpen
                    )
                }
                if (pages.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(displayPage, pages.size, hasMore, loading) {
                                var handled = false
                                detectHorizontalDragGestures { _, dragAmount ->
                                    if (!handled && dragAmount < -80f) {
                                        if (displayPage < pages.lastIndex) expandedPage++
                                        else if (hasMore && !loading) onLoadNextServerPage()
                                        handled = true
                                    }
                                    if (!handled && dragAmount > 80f && displayPage > 0) {
                                        expandedPage--
                                        handled = true
                                    }
                                }
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(
                            onClick = { expandedPage-- },
                            enabled = displayPage > 0 && !loading
                        ) { Text("Anterior") }
                        Text("Página ${displayPage + 1}/${pages.size}", style = MaterialTheme.typography.labelMedium)
                        TextButton(
                            onClick = {
                                if (displayPage < pages.lastIndex) expandedPage++ else onLoadNextServerPage()
                            },
                            enabled = !loading && (displayPage < pages.lastIndex || hasMore)
                        ) { Text(if (displayPage < pages.lastIndex) "Próxima" else "Mais ofertas") }
                    }
                }
            }
        } else {
            LazyRow(
                state = carouselState,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                items(carouselOffers, key = { "${it.product.id}:${it.offer.family}" }) { item ->
                    Box(modifier = Modifier.width(featuredCardWidth)) {
                        AcpFeaturedOfferCard(
                            item,
                            appearance,
                            item.product.offerValidityOr(validityByOffer[AcpOfferValidityStore.keyFor(item.product.description, item.offer.family)]),
                            onOpen
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AcpFeaturedOfferActions(
    modifier: Modifier = Modifier,
    sort: AcpFeaturedSort,
    expanded: Boolean,
    onSortChanged: (AcpFeaturedSort) -> Unit,
    onHide: () -> Unit,
    onToggleExpanded: () -> Unit,
    filterMenuExpanded: Boolean,
    onFilterMenuExpandedChange: (Boolean) -> Unit
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Box(modifier = Modifier.weight(1f)) {
            TextButton(
                onClick = { onFilterMenuExpandedChange(true) },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) { Text("Filtro", maxLines = 1) }
            DropdownMenu(
                expanded = filterMenuExpanded,
                onDismissRequest = { onFilterMenuExpandedChange(false) }
            ) {
                AcpFeaturedSort.values().forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            onSortChanged(option)
                            onFilterMenuExpandedChange(false)
                        },
                        trailingIcon = if (option == sort) ({ Text("✓") }) else null
                    )
                }
            }
        }
        TextButton(
            onClick = onHide,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) { Text("Ocultar", maxLines = 1) }
        TextButton(
            onClick = onToggleExpanded,
            modifier = Modifier.weight(1.2f),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) { Text(if (expanded) "Ver menos" else "Ver todos", maxLines = 1) }
    }
}

private fun AcpFeaturedOffer.discountAmount(): java.math.BigDecimal {
    val reference = offer.referencePrice ?: return java.math.BigDecimal.ZERO
    val price = offer.price ?: return java.math.BigDecimal.ZERO
    return reference.subtract(price)
}

@Composable
private fun AcpFeaturedOfferCard(
    item: AcpFeaturedOffer,
    appearance: AppearanceSettings,
    validity: AcpOfferValidity?,
    onOpen: (AcpFeaturedOffer) -> Unit
) {
    OutlinedCard(
        onClick = { onOpen(item) },
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                item.product.description,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp)
            )
            Text(
                "Código: ${item.product.code.ifBlank { "não informado" }}" +
                    item.product.barcode.takeIf { it.isNotBlank() }?.let { " • EAN: $it" }.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().heightIn(min = 20.dp)
            )
            AcpOfferPoster(
                offer = item.offer,
                compact = true,
                productName = item.product.description,
                banner = appearance.activeOfferBanner(item.offer.bannerKey),
                validityOverride = validity
            )
            Text("Toque para ver todas as condições", style = MaterialTheme.typography.labelSmall)
        }
    }
}
