Warning: truncated output (original token count: 19597)
Total output lines: 1641

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.data.CategoryDefinition
import com.example.data.AppearanceSettings
import com.example.data.FirebaseService
import com.example.data.NrdProductImportService
import com.example.data.acp.*
import com.example.ui.theme.LocalExpressiveStyle
import com.example.ui.theme.LocalGlassSoftStyle
import com.example.ui.theme.glassSoftShadow
import com.example.ui.theme.expressiveShadow
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

/** A scanner sets the search text and starts the explicit request in the same frame.
 *  The query observer must not cancel that in-flight request. */
internal fun preserveExplicitAcpSearch(query: String, lastExplicitQuery: String?): Boolean =
    query.trim().isNotEmpty() && query.trim() == lastExplicitQuery

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
    externalPdfUri: String? = null,
    externalPdfRequestKey: Long = 0L,
    onExternalPdfConsumed: () -> Unit = {},
    onSessionExpired: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val expressiveStyle = LocalExpressiveStyle.current
    val glassStyle = LocalGlassSoftStyle.current
    val isExpressive = expressiveStyle.enabled
    val screenProfile = rememberNrdScreenProfile()
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
    var offersLoading by remember { mutableStateOf(false) }
    var offersWarning by remember { mutableStateOf<String?>(null) }
    var integration by remember { mutableStateOf<AcpIntegrationInfo?>(null) }
    var detailBusy by remember { mutableStateOf(false) }
    var detailWarning by remember { mutableStateOf<String?>(null) }
    var syncExpanded by remember { mutableStateOf(false) }
    var lastExplicitQuery by remember { mutableStateOf<String?>(null) }

    var featuredOffers by remember { mutableStateOf<List<AcpFeaturedOffer>>(emptyList()) }
    var featuredLoading by remember { mutableStateOf(false) }
    var featuredError by remember { mutableStateOf<String?>(null) }
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

    fun refreshFeaturedFromAcp(): Job {
        featuredRefreshJob?.takeIf { it.isActive }?.let { return it }
        return scope.launch {
            featuredLoading = true
            featuredError = null
            try {
                val latest = api.featuredOffersPage(page = 0, forceFresh = true)
                if (query.isBlank() && featuredVisible) {
                    // A ACP é a fonte da verdade: a primeira página nova substitui a antiga.
                    // Assim uma oferta removida da ACP não sobrevive apenas por estar em cache.
                    featuredOffers = latest.items
                    featuredServerPage = 0
                    featuredHasMore = latest.hasMore
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (query.isBlank() && featuredVisible) featuredOffers = emptyList()
                featuredError = acpErrorMessage(failure)
            } finally {
                featuredLoading = false
                featuredRefreshJob = null
            }
        }.also { featuredRefreshJob = it }
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
        // Ao abrir, consulta a ACP imediatamente antes de considerar a lista sincronizada.
        refreshFeaturedFromAcp().join()
        while (true) {
            delay(5 * 60 * 1000L)
            if (query.isBlank() && featuredVisible) refreshFeaturedFromAcp().join()
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
    var visualMixImportOpen by rememberSaveable { mutableStateOf(false) }

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

    fun search(index: Int = 0, interactive: Boolean = true, queryOverride: String? = null) {
        // Scanner callbacks can run before Compose recomposes after `query = code`.
        // Accept the scanned value directly so the explicit search never reads the
        // previous query from the closure.
        val searchText = (queryOverride ?: query).trim()
        if (searchText.isBlank()) {
            if (interactive) error = "Digite um código, código de barras ou descrição."
            return
        }
        searchJob?.cancel()
        val ticket = ++generation
        refreshing = false
        featuredJob?.cancel()
        featuredRefreshJob?.cancel()
        api.stopBackgroundSync()
        featuredVisible = false
        featuredExpanded = false
        if (interactive) {
            busy = true
            lastExplicitQuery = searchText
            keyboard?.hide()
        } else {
            // Sugestões nunca prendem a interface nem escondem o teclado.
            busy = true
        }
        error = null
        offersWarning = null
        closeDetail()
        if (index == 0 && canCopyDiagnostic && interactive) api.beginDiagnosticSession()
        searchJob = scope.launch {
            try {
                val result = if (interactive) {
                    // Busca explícita sempre vai direto ao ACP. As sugestões em segundo plano
                    // consultam somente a primeira página; a busca completa fica para o botão.
                    api.searchProductsUnifiedFresh(searchText, index, freshStore, forceDescriptionRefresh = index == 0)
                } else {
                    api.searchProductsSuggestion(searchText, index)
                }
                if (ticket != generation) return@launch

                // Product/all é o caminho crítico. Assim que ele responde, o produto já fica
                // disponível e clicável. Dados complementares não seguram mais a pesquisa.
                page = result
                busy = false
                previewCampaignOffers = emptyMap()
                offersLoading = true
                val campaignOffers = try {
                    api.campaignOffersFor(result.items)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: AcpUnauthorized) {
                    if (ticket == generation) onSessionExpired()
                    emptyMap()
                } catch (_: Exception) {
                    if (ticket == generation) offersWarning = "Não foi possível conferir as campanhas. Toque em Atualizar para tentar novamente."
                    emptyMap()
                }
                if (ticket != generation) return@launch
                previewCampaignOffers = campaignOffers
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: AcpUnauthorized) {
                if (ticket == generation) onSessionExpired()
            } catch (failure: Exception) {
                if (ticket == generation) error = acpErrorMessage(failure)
            } finally {
                if (ticket == generation) { busy = false; offersLoading = false }
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
                    // Sem pesquisa aberta, o gesto atualiza justamente os destaques visíveis.
                    featuredRefreshJob?.cancel()
                    val latest = api.featuredOffersPage(page = 0, forceFresh = true)
                    if (ticket != generation) return@launch
                    featuredOffers = latest.items
                    featuredServerPage = 0
                    featuredHasMore = latest.hasMore
                    featuredError = null
                    integration = try { api.integrationInfo() } catch (_: Exception) { null }
                    refreshMessage = "Ofertas em destaque atualizadas agora pelo ACP."
                } else {
                    val targetPage = page?.pageIndex ?: 0
                    val fresh = api.searchProductsUnifiedFresh(clean, targetPage, freshStore)
                    if (ticket != generation) return@launch
                    page = fresh
                    lastExplicitQuery = clean
                    offersLoading = true
                    offersWarning = null
                    val refreshedOffers = try {
                        api.campaignOffersFor(fresh.items)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        if (ticket == generation) offersWarning = "Não foi possível conferir as campanhas. Toque em Atualizar para tentar novamente."
                        emptyMap()
                    }
                    if (ticket != generation) return@launch
                    previewCampaignOffers = refreshedOffers
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
                if (ticket == generation) { refreshing = false; offersLoading = false }
            }
        }
    }

    // Comportamento do editor web: enquanto o nome é digitado, os candidatos aparecem abaixo.
    // Um debounce curto evita uma chamada por tecla, e cada nova digitação cancela a anterior.
    LaunchedEffect(query) {
        val clean = query.trim()
        if (preserveExplicitAcpSearch(clean, lastExplicitQuery)) return@LaunchedEffect
        searchJob?.cancel()
        generation++
        busy = false
        refreshing = false
        offersLoading = false
        offersWarning = null
        previewCampaignOffers = emptyMap()
        page = null
        if (clean != lastExplicitQuery) lastExplicitQuery = null
        if (clean.isEmpty()) {
            searchJob?.cancel()
            generation++
            busy = false
            page = null
            previewCampaignOffers = emptyMap()
            error = null
            lastExplicitQuery = null
            featuredVisible = true
            api.warmFeaturedCatalog()
            return@LaunchedEffect
        }
        if (clean.length < 2 || clean == lastExplicitQuery) return@LaunchedEffect
        delay(300)
        if (query.trim() == clean && clean != lastExplicitQuery) {
            search(index = 0, interactive = false)
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
            val ticket = generation
            if (clean.length < 2 || busy || refreshing) continue

            try {
                val fresh = api.searchProductsUnifiedFresh(clean, currentPage.pageIndex, freshStore)
                if (ticket != generation || query.trim() != clean || page?.pageIndex != currentPage.pageIndex) continue

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
                val refreshedOffers = try {
                    api.campaignOffersFor(fresh.items)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    previewCampaignOffers
                }
                if (ticket == generation && query.trim() == clean && page?.pageIndex == currentPage.pageIndex) {
                    previewCampaignOffers = refreshedOffers
                    offersWarning = null
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
            detailWarning = warnings.takeIf { it.isNotE…7597 tokens truncated… Text("Categorias (${selectedNrdCategories.size})", style = MaterialTheme.typography.titleSmall)
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
    val clean = com.example.data.storeProductLookupCode(query)
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
    store: AcpSecureStore,
    forceDescriptionRefresh: Boolean = true
): AcpProductPage {
    val clean = com.example.data.storeProductLookupCode(query)
    require(clean.isNotBlank() && clean.length <= 200 && pageIndex >= 0)
    if (forceDescriptionRefresh) campaignSnapshot = null
    val numeric = clean.all(Char::isDigit)
    val preferred = when {
        !numeric -> AcpSearchField.DESCRIPTION
        clean.length in setOf(8, 12, 13, 14) -> AcpSearchField.BARCODE
        else -> AcpSearchField.CODE
    }
    if (!numeric) return searchProducts(AcpSearchField.DESCRIPTION, clean, null, pageIndex, forceFresh = forceDescriptionRefresh)

    val request = acpSearchRequest(clean)

    fun clear(field: AcpSearchField, searchValue: String, page: Int) {
        val parameters = listOf(
            "pageSize" to "20",
            "pageIndex" to page.toString(),
            field.parameter to searchValue
        )
        store.clear(acpResponseCacheName("Product/all", parameters))
    }

    // Product/all normalmente usa cache diário. O gesto explícito de atualizar é a exceção:
    // limpamos somente as chaves da pesquisa visível, sem varrer nem apagar o restante do cache.
    clear(request.field, request.query, pageIndex)
    if (numeric && pageIndex == 0) {
        val alternate = if (preferred == AcpSearchField.BARCODE) AcpSearchField.CODE else AcpSearchField.BARCODE
        clear(alternate, clean, 0)
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
            ) { Text("Filtro") }
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
        ) { Text("Ocultar") }
        TextButton(
            onClick = onToggleExpanded,
            modifier = Modifier.weight(1.2f),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) { Text(if (expanded) "Ver menos" else "Ver todos") }
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
    val expressive = LocalExpressiveStyle.current.enabled
    val glassStyle = LocalGlassSoftStyle.current
    val profile = rememberNrdScreenProfile()
    val cardShape = if (expressive) {
        RoundedCornerShape(
            when {
                profile.veryCompact -> 20.dp
                profile.compact -> 22.dp
                else -> 28.dp
            }
        )
    } else {
        MaterialTheme.shapes.medium
    }
    OutlinedCard(
        onClick = { onOpen(item) },
        modifier = Modifier
            .fillMaxWidth()
            .glassSoftShadow(cardShape, if (expressive) 3.dp else 0.dp)
            .expressiveShadow(cardShape, 6.dp),
        shape = cardShape,
        colors = CardDefaults.outlinedCardColors(
            containerColor = when {
                glassStyle.enabled -> MaterialTheme.colorScheme.surface
                expressive -> MaterialTheme.colorScheme.surfaceContainerLow
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = if (expressive) {
                        if (profile.compact) 13.dp else 15.dp
                    } else {
                        10.dp
                    },
                    vertical = if (expressive) {
                        if (profile.compact) 12.dp else 14.dp
                    } else {
                        10.dp
                    }
                ),
            verticalArrangement = Arrangement.spacedBy(if (expressive) 7.dp else 5.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                AutomaticProductThumbnail(item.product.code, item.product.description, item.product.barcode, item.product.imageUrl, Modifier.size(48.dp))
                Text(
                    item.product.description,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (expressive) FontWeight.ExtraBold else FontWeight.Normal,
                    modifier = Modifier.weight(1f).heightIn(min = if (expressive) 44.dp else 40.dp)
                )
            }
            Text(
                "Código: ${item.product.code.ifBlank { "não informado" }}" +
                    item.product.barcode.takeIf { it.isNotBlank() }?.let { " • EAN: $it" }.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 20.dp)
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
