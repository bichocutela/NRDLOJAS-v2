package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.NossaGenteApi
import com.example.data.NossaGenteCredentialStore
import com.example.data.NossaGenteLoginResult
import com.example.data.Promotion
import com.example.data.PromotionChange
import com.example.data.PromotionChangeType
import com.example.data.StoreCatalog
import com.example.data.UserPreferences
import com.example.ui.theme.glassSoftShadow
import com.example.ui.theme.LocalGlassSoftStyle
import com.example.ui.theme.LocalExpressiveStyle
import com.example.ui.theme.expressiveShadow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive

private const val INITIAL_OFFER_PAGE = 48
private const val OFFER_PAGE_INCREMENT = 48
private const val CATEGORY_PREVIEW_LIMIT = 10
private const val MAX_SEARCH_LENGTH = 80
private const val ALL_STORES_LABEL = "Todas"
private const val UNKNOWN_STORE_LABEL = "Loja não informada"

private enum class OfferSortOption(val label: String) {
    NAME("Nome"),
    VALID_UNTIL("Data de validade"),
    ADDED("Ordem de adição"),
    DISCOUNT_DESC("Maior desconto"),
    DISCOUNT_ASC("Menor desconto"),
    PRICE_ASC("Preço menor para maior"),
    PRICE_DESC("Preço maior para menor")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromotionsLoginScreen(
    api: NossaGenteApi,
    onLoginSuccess: () -> Unit,
    onNavigateBack: () -> Unit,
    reuseExistingSession: Boolean = true,
    title: String = "Acesso às promoções"
) {
    var cpf by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var saveCredentials by remember { mutableStateOf(false) }
    var activateProfile by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val expressive = LocalExpressiveStyle.current.enabled
    val glassStyle = LocalGlassSoftStyle.current
    val credentialStore = remember(context) { NossaGenteCredentialStore(context.applicationContext) }

    LaunchedEffect(credentialStore) {
        val saved = withContext(Dispatchers.IO) { credentialStore.load() }
        if (saved != null) {
            cpf = saved.cpf
            password = saved.password
            saveCredentials = true
        }
        activateProfile = credentialStore.isProfileEnabled()
    }

    LaunchedEffect(api, reuseExistingSession) {
        if (reuseExistingSession && api.hasSession()) {
            onLoginSuccess()
        }
    }

    Scaffold(
        containerColor = if (glassStyle.enabled || expressive) Color.Transparent else MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        title,
                        fontWeight = if (expressive) FontWeight.ExtraBold else FontWeight.Normal
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (expressive || glassStyle.enabled) Color.Transparent else MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text("Entre com o mesmo acesso do Nossa Gente", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                "Use seu CPF e sua senha do Nossa Gente. Se quiser, você pode salvar o acesso neste aparelho para não precisar digitar toda vez.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = if (expressive) RoundedCornerShape(28.dp) else MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = if (expressive) MaterialTheme.colorScheme.surfaceContainerLow
                    else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { activateProfile = !activateProfile },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.Checkbox(
                        checked = activateProfile,
                        onCheckedChange = { checked ->
                            activateProfile = checked
                            scope.launch(Dispatchers.IO) { credentialStore.setProfileEnabled(checked) }
                        }
                    )
                    Column(Modifier.padding(end = 12.dp)) {
                        Text("Ativar Meu Perfil", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Sincronizar ponto e convênio do Nossa Gente neste aparelho.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = cpf,
                onValueChange = { value -> cpf = value.filter(Char::isDigit).take(11) },
                label = { Text("CPF") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                shape = if (expressive) RoundedCornerShape(22.dp) else MaterialTheme.shapes.extraSmall
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Senha") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                shape = if (expressive) RoundedCornerShape(22.dp) else MaterialTheme.shapes.extraSmall
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.Checkbox(
                    checked = saveCredentials,
                    onCheckedChange = { checked ->
                        saveCredentials = checked
                        if (!checked) {
                            scope.launch(Dispatchers.IO) { credentialStore.clear() }
                        }
                    }
                )
                Text(
                    text = "Salvar CPF e senha neste aparelho",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Text(
                text = "O acesso salvo fica criptografado localmente neste aparelho.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    if (isLoading) return@Button
                    error = null
                    isLoading = true
                    scope.launch {
                        when (val result = api.login(cpf, password)) {
                            NossaGenteLoginResult.Success -> {
                                if (api.hasSession()) {
                                    if (saveCredentials) {
                                        withContext(Dispatchers.IO) {
                                            credentialStore.save(cpf = cpf, password = password)
                                        }
                                    } else {
                                        withContext(Dispatchers.IO) { credentialStore.clear() }
                                        password = ""
                                    }
                                    withContext(Dispatchers.IO) {
                                        credentialStore.setProfileEnabled(activateProfile)
                                    }
                                    onLoginSuccess()
                                } else {
                                    error = "A sessão não ficou disponível. Tente novamente."
                                }
                            }
                            is NossaGenteLoginResult.Error -> error = result.message
                        }
                        isLoading = false
                    }
                },
                enabled = !isLoading && cpf.length == 11 && password.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = if (expressive) 54.dp else 48.dp),
                shape = if (expressive) RoundedCornerShape(22.dp) else MaterialTheme.shapes.small
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Entrar")
                }
            }
            if (error != null) {
                Spacer(Modifier.height(10.dp))
                Text(error!!, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromotionsScreen(
    api: NossaGenteApi,
    onNavigateBack: () -> Unit,
    onLogout: () -> Unit,
    showReactivateProfile: Boolean = false,
    onReactivateProfile: () -> Unit = {},
    showSyncDiagnostics: Boolean = false,
    showNewOnOpen: Boolean = false,
    newOffersRequestId: Long = 0L
) {
    val model: PromotionsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val ui by model.state.collectAsStateWithLifecycle()
    var offerGroups by remember { mutableStateOf<List<OfferGroup>>(emptyList()) }
    var showNewOffers by rememberSaveable { mutableStateOf(false) }
    val isLoading = ui.loading
    val isChecking = ui.sync.visibleNetwork && ui.initialized
    // Infrastructure failures (including quota) are diagnostics exclusive to the Mestre.
    // Ordinary users see only a neutral fallback if there is no cached catalog.
    val error = ui.sync.error?.takeIf { !ui.initialized }?.let {
        if (showSyncDiagnostics) it else "Ofertas temporariamente indisponíveis. Tente atualizar mais tarde."
    }
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedStore by rememberSaveable { mutableStateOf("0012") }
    var favoriteStoreCode by rememberSaveable { mutableStateOf<String?>(null) }
    var sortOptionName by rememberSaveable { mutableStateOf(OfferSortOption.ADDED.name) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var visibleOfferCount by rememberSaveable { mutableStateOf(INITIAL_OFFER_PAGE) }
    val context = LocalContext.current
    val stores by remember { com.example.data.promotions.PromotionStores.observe() }
        .collectAsStateWithLifecycle(initialValue = com.example.data.promotions.PromotionStores.defaults)
    val enabledStores = stores.filter { it.enabled }.map { it.code }.toSet()
    val selectableStores = enabledStores + if (enabledStores.size > 1) setOf(ALL_STORES_LABEL) else emptySet()
    val compactHeader = LocalConfiguration.current.screenWidthDp < 420
    val glassStyle = LocalGlassSoftStyle.current
    val expressive = LocalExpressiveStyle.current.enabled
    val userPreferences = remember { UserPreferences(context) }
    val sortOption = OfferSortOption.values().firstOrNull { it.name == sortOptionName }
        ?: OfferSortOption.ADDED
    var enlargedImageUrl by remember { mutableStateOf<String?>(null) }
    var selectedOffer by remember { mutableStateOf<OfferGroup?>(null) }
    val scope = rememberCoroutineScope()
    fun handleRefreshClick() {
        if (!isLoading && !isChecking) model.refresh()
    }
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(ui.sync.error, ui.initialized, showSyncDiagnostics) {
        if (ui.initialized && showSyncDiagnostics) {
            ui.sync.error?.let { snackbarHostState.showSnackbar(it) }
        }
    }
    LaunchedEffect(showNewOnOpen, newOffersRequestId) {
        if (showNewOnOpen) {
            showNewOffers = true
            selectedCategory = null
            searchQuery = ""
        }
    }

    LaunchedEffect(Unit) { favoriteStoreCode = userPreferences.favoriteStoreCode.first() }
    LaunchedEffect(ui.offers) {
        offerGroups = withContext(Dispatchers.Default) { buildOfferGroups(ui.offers) }
        if (selectedCategory != null && offerGroups.none { it.category == selectedCategory }) selectedCategory = null
    }
    val storeOptions = if (enabledStores.size > 1) listOf(ALL_STORES_LABEL) + StoreCatalog.codes else StoreCatalog.codes
    LaunchedEffect(stores) {
        if (selectedStore !in selectableStores) selectedStore = enabledStores.firstOrNull() ?: "0012"
        selectedOffer = null
        offerGroups = offerGroups.map { it.copy(stores = it.stores.filter { store -> store.storeCode in enabledStores }) }
            .filter { it.stores.isNotEmpty() }
        model.storesChanged()
    }
    val normalizedQuery = searchQuery.trim().lowercase()
    val visibleOffers = remember(offerGroups, selectedStore, normalizedQuery, selectedCategory, sortOption, enabledStores, showNewOffers, ui.latestAddedIds) {
        val filtered = offerGroups.filter { offer ->
            val matchesCategory = selectedCategory == null || offer.category == selectedCategory
            val matchesStore = selectedStore == ALL_STORES_LABEL || offer.stores.any { it.storeCode == selectedStore }
            val matchesSearch = normalizedQuery.isBlank() ||
                offer.name.lowercase().contains(normalizedQuery) ||
                offer.code.lowercase().contains(normalizedQuery) ||
                offer.barcode.contains(normalizedQuery)
            val matchesNew = !showNewOffers || offer.stores.any {
                (selectedStore == ALL_STORES_LABEL || it.storeCode == selectedStore) &&
                    com.example.data.promotions.offerIdentity(it.storeCode, offer.code) in ui.latestAddedIds
            }
            matchesCategory && matchesStore && matchesSearch && matchesNew && offer.stores.any { it.storeCode in enabledStores }
        }
        sortOfferGroups(filtered, sortOption)
    }
    val categoryGroups = remember(offerGroups, selectedStore, normalizedQuery, enabledStores, showNewOffers, ui.latestAddedIds) {
        offerGroups
            .asSequence()
            .filter { offer ->
                val matchesStore = selectedStore == ALL_STORES_LABEL || offer.stores.any { it.storeCode == selectedStore }
                val matchesSearch = normalizedQuery.isBlank() ||
                    offer.name.lowercase().contains(normalizedQuery) ||
                    offer.code.lowercase().contains(normalizedQuery) ||
                offer.barcode.contains(normalizedQuery)
                val matchesNew = !showNewOffers || offer.stores.any {
                    (selectedStore == ALL_STORES_LABEL || it.storeCode == selectedStore) &&
                        com.example.data.promotions.offerIdentity(it.storeCode, offer.code) in ui.latestAddedIds
                }
                matchesStore && matchesSearch && matchesNew && offer.stores.any { it.storeCode in enabledStores }
            }
            .groupBy { it.category }
            .toList()
            .sortedBy { it.first.lowercase() }
    }

    LaunchedEffect(selectedCategory, selectedStore, normalizedQuery, sortOptionName, showNewOffers, ui.latestAddedIds) {
        visibleOfferCount = INITIAL_OFFER_PAGE
    }

    LaunchedEffect(favoriteStoreCode, selectableStores) {
        if (favoriteStoreCode in enabledStores) selectedStore = favoriteStoreCode!!
        if (selectedStore !in selectableStores) selectedStore = enabledStores.firstOrNull() ?: "0012"
    }

    val newOffersCount = offerGroups.count { offer ->
        offer.stores.any { store -> store.storeCode in enabledStores &&
            (selectedStore == ALL_STORES_LABEL || store.storeCode == selectedStore) &&
            com.example.data.promotions.offerIdentity(store.storeCode, offer.code) in ui.latestAddedIds }
    }

    selectedOffer?.let { offer ->
        PromotionDetailsDialog(
            offer = offer,
            selectedStore = selectedStore,
            onDismiss = { selectedOffer = null },
            onOpenStore = { storeCode ->
                if (storeCode in enabledStores) selectedStore = storeCode
                selectedCategory = null
                searchQuery = ""
                selectedOffer = null
            }
        )
    }

    if (enlargedImageUrl != null) {
        PromotionImageDialog(
            imageUrl = enlargedImageUrl!!,
            onDismiss = { enlargedImageUrl = null }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = if (glassStyle.enabled || expressive) Color.Transparent else MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (selectedCategory == null) "Promoção" else selectedCategory.orEmpty(),
                            fontWeight = if (expressive) FontWeight.ExtraBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(Modifier.width(6.dp))
                        NewOffersButton(
                            changeCount = newOffersCount,
                            highlighted = showNewOffers || newOffersCount > 0,
                            showingNew = showNewOffers,
                            enabled = !isLoading,
                            onClick = {
                                showNewOffers = !showNewOffers
                                selectedCategory = null
                                searchQuery = ""
                            }
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (selectedCategory != null) selectedCategory = null else onNavigateBack()
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    IconButton(
                        onClick = ::handleRefreshClick,
                        enabled = !isLoading && !isChecking,
                        modifier = Modifier
                            .glassSoftShadow(CircleShape, 4.dp)
                            .background(
                                color = if (glassStyle.enabled) {
                                    MaterialTheme.colorScheme.surface
                                } else if (expressive) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                                },
                                shape = CircleShape
                            )
                            .then(
                                if (glassStyle.enabled) Modifier.border(
                                    1.dp,
                                    glassStyle.borderColor,
                                    CircleShape
                                ) else Modifier
                            )
                    ) {
                        if (isChecking) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Buscar novas ofertas agora",
                                tint = if (expressive) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                    if (compactHeader) {
                        var profileActionsExpanded by remember { mutableStateOf(false) }
                        IconButton(onClick = { profileActionsExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Mais opções da conta")
                        }
                        DropdownMenu(
                            expanded = profileActionsExpanded,
                            onDismissRequest = { profileActionsExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        favoriteStoreCode?.let { code -> "Loja favorita: $code" } ?: "Definir loja favorita",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                onClick = {
                                    profileActionsExpanded = false
                                    favoriteStoreCode?.let { code ->
                                        favoriteStoreCode = null
                                        selectedStore = enabledStores.firstOrNull() ?: "0012"
                                        scope.launch { userPreferences.setFavoriteStoreCode(null) }
                                    } ?: storeOptions.firstOrNull { it in enabledStores }?.let { store ->
                                        favoriteStoreCode = store
                                        selectedStore = store
                                        scope.launch { userPreferences.setFavoriteStoreCode(store) }
                                    }
                                },
                                leadingIcon = { Icon(Icons.Default.Favorite, contentDescription = null) }
                            )
                            if (showReactivateProfile) {
                                DropdownMenuItem(
                                    text = { Text("Reativar Meu Perfil") },
                                    onClick = {
                                        profileActionsExpanded = false
                                        onReactivateProfile()
                                    },
                                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) }
                                )
                            }

                        }
                    } else {
                        FavoriteStoreSelector(
                            storeOptions = storeOptions,
                            enabledStores = enabledStores,
                            favoriteStoreCode = favoriteStoreCode,
                            onFavoriteStoreChange = { code ->
                                favoriteStoreCode = code
                                selectedStore = code?.takeIf { it in enabledStores } ?: enabledStores.firstOrNull() ?: "0012"
                                scope.launch { userPreferences.setFavoriteStoreCode(code) }
                            }
                        )
                        if (showReactivateProfile) {
                            IconButton(
                                onClick = onReactivateProfile,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = "Reativar Meu Perfil",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (expressive || glassStyle.enabled) Color.Transparent else MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        when {
            isLoading -> LoadingPromotionsState(innerPadding)
            error != null -> ErrorPromotionsState(
                innerPadding = innerPadding,
                message = error!!,
                onRetry = { model.refresh() }
            )
            selectedCategory != null -> PromotionCategoryList(
                innerPadding = innerPadding,
                categoryName = selectedCategory.orEmpty(),
                selectedStore = selectedStore,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it.take(MAX_SEARCH_LENGTH) },
                sortOption = sortOption,
                onSortOptionChange = { sortOptionName = it.name },
                visibleOffers = visibleOffers,
                visibleOfferCount = visibleOfferCount,
                onLoadMore = {
                    visibleOfferCount = (visibleOfferCount + OFFER_PAGE_INCREMENT).coerceAtMost(visibleOffers.size)
                },
                onOfferClick = { selectedOffer = it },
                onBack = { selectedCategory = null },
                isRefreshing = isChecking,
                onRefresh = ::handleRefreshClick
            )
            else -> PromotionsHome(
                innerPadding = innerPadding,
                storeOptions = storeOptions,
                selectedStore = selectedStore,
                enabledStores = selectableStores,
                onStoreSelected = { if (it in selectableStores) selectedStore = it },
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it.take(MAX_SEARCH_LENGTH) },
                categories = categoryGroups,
                onCategoryClick = { selectedCategory = it },
                onOfferClick = { selectedOffer = it },
                isRefreshing = isChecking,
                onRefresh = ::handleRefreshClick
            )
        }
    }
}

@Composable
private fun LoadingPromotionsState(innerPadding: PaddingValues) {
    val transition = rememberInfiniteTransition(label = "promotions-loading-line")

    Column(
        modifier = Modifier
            .padding(innerPadding)
            .fillMaxSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val lineColors = listOf(
                Color(0xFFE53935), // vermelho
                Color(0xFF43A047), // verde
                Color(0xFFFB8C00), // laranja
                Color(0xFF1E88E5), // azul
                Color(0xFFFFC107)  // dourado
            )
            lineColors.forEachIndexed { index, color ->
                val alpha = transition.animateFloat(
                    initialValue = 0.45f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(850, delayMillis = index * 110),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "promotions-loading-segment-$index"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(color.copy(alpha = alpha.value))
                )
            }
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.LocalOffer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text("Carregando ofertas…", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ErrorPromotionsState(
    innerPadding: PaddingValues,
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier.padding(innerPadding).fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.LocalOffer, contentDescription = null, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text(message, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onRetry) { Text("Tentar novamente") }
    }
}

@Composable
private fun EmptyPromotionsState(innerPadding: PaddingValues, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.padding(innerPadding).fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.LocalOffer, contentDescription = null, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text("Nenhuma promoção disponível no momento.")
        Spacer(Modifier.height(8.dp))
        Text("Toque em atualizar para consultar novamente.", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onRetry) { Text("Atualizar") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PromotionsHome(
    innerPadding: PaddingValues,
    storeOptions: List<String>,
    enabledStores: Set<String>,
    selectedStore: String,
    onStoreSelected: (String) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    categories: List<Pair<String, List<OfferGroup>>>,
    onCategoryClick: (String) -> Unit,
    onOfferClick: (OfferGroup) -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit
) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.padding(innerPadding).fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
        item {
            StoreTabs(
                storeOptions = storeOptions,
                enabledStores = enabledStores,
                selectedStore = selectedStore,
                onStoreSelected = onStoreSelected
            )
        }
        item {
            SearchField(query = searchQuery, onQueryChange = onSearchQueryChange)
        }
        item {
            Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                if (categories.isEmpty()) Text("Nenhuma oferta vigente nesta loja.")
                Text("Ofertas por categoria", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Escolha uma categoria para ver todos os produtos em oferta.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        if (categories.isEmpty()) {
            item {
                Text(
                    "Nenhum produto encontrado para esta busca ou loja.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            items(categories, key = { it.first }) { (categoryName, offers) ->
                CategoryPreviewSection(
                    categoryName = categoryName,
                    offers = offers.take(CATEGORY_PREVIEW_LIMIT),
                    totalOffers = offers.size,
                    onCategoryClick = onCategoryClick,
                    onOfferClick = onOfferClick
                )
            }
        }
        item {
            Text(
                "Atualização automática a cada minuto. Puxe para baixo ou toque em atualizar para buscar novas ofertas agora.",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall
            )
        }
        }
    }
}

@Composable
private fun FavoriteStoreSelector(
    storeOptions: List<String>,
    enabledStores: Set<String>,
    favoriteStoreCode: String?,
    onFavoriteStoreChange: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val safeOptions = storeOptions.filter { it != ALL_STORES_LABEL }
    Box {
        IconButton(
            onClick = { expanded = true },
            enabled = safeOptions.isNotEmpty()
        ) {
            Icon(
                imageVector = if (favoriteStoreCode.isNullOrBlank()) Icons.Default.FavoriteBorder else Icons.Default.Favorite,
                contentDescription = if (favoriteStoreCode.isNullOrBlank()) {
                    "Escolher loja favorita"
                } else {
                    "Loja favorita: ${StoreCatalog.nameFor(favoriteStoreCode)}"
                },
                tint = if (favoriteStoreCode.isNullOrBlank()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Todas as lojas") },
                onClick = {
                    onFavoriteStoreChange(null)
                    expanded = false
                }
            )
            safeOptions.forEach { code ->
                DropdownMenuItem(
                    enabled = code in enabledStores,
                    text = {
                        Column {
                            Text(StoreCatalog.nameFor(code))
                            Text(code, style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    onClick = {
                        onFavoriteStoreChange(code)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun StoreTabs(
    storeOptions: List<String>,
    enabledStores: Set<String>,
    selectedStore: String,
    onStoreSelected: (String) -> Unit
) {
    val safeStores = if (storeOptions.isEmpty()) listOf(ALL_STORES_LABEL) else storeOptions
    val expressive = LocalExpressiveStyle.current.enabled
    ScrollableTabRow(
        selectedTabIndex = safeStores.indexOf(selectedStore).coerceAtLeast(0),
        containerColor = if (expressive) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface
    ) {
        safeStores.forEach { store ->
            Tab(
                selected = store == selectedStore,
                enabled = store in enabledStores,
                onClick = { onStoreSelected(store) },
                text = {
                    Text(
                        text = if (store == ALL_STORES_LABEL) ALL_STORES_LABEL else StoreCatalog.nameFor(store),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    val expressive = LocalExpressiveStyle.current.enabled
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .expressiveShadow(if (expressive) RoundedCornerShape(24.dp) else RoundedCornerShape(10.dp), 6.dp),
        singleLine = true,
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "Limpar busca")
                }
            }
        },
        placeholder = { Text("Buscar produto") },
        shape = if (expressive) RoundedCornerShape(24.dp) else RoundedCornerShape(10.dp)
    )
}

@Composable
private fun CategoryPreviewSection(
    categoryName: String,
    offers: List<OfferGroup>,
    totalOffers: Int,
    onCategoryClick: (String) -> Unit,
    onOfferClick: (OfferGroup) -> Unit
) {
    val expressive = LocalExpressiveStyle.current.enabled
    val sectionShape = if (expressive) RoundedCornerShape(30.dp) else RoundedCornerShape(0.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = if (expressive) 8.dp else 0.dp)
            .then(
                if (expressive) Modifier
                    .expressiveShadow(sectionShape, 7.dp)
                    .clip(sectionShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
                else Modifier
            )
    ) {
        if (!expressive) {
            HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCategoryClick(categoryName) }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    categoryName,
                    style = if (expressive) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                    fontWeight = if (expressive) FontWeight.ExtraBold else FontWeight.Bold
                )
                Text(
                    "$totalOffers produto(s) em oferta",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = { onCategoryClick(categoryName) }) { Text("Ver todos") }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(offers, key = { it.id }) { offer ->
                CompactOfferCard(offer = offer, onOfferClick = onOfferClick)
            }
        }
    }
}

@Composable
private fun CompactOfferCard(offer: OfferGroup, onOfferClick: (OfferGroup) -> Unit) {
    val expressive = LocalExpressiveStyle.current.enabled
    val cardShape = if (expressive) RoundedCornerShape(26.dp) else RoundedCornerShape(12.dp)
    Card(
        modifier = Modifier
            .widthIn(min = 156.dp, max = 176.dp)
            .clickable { onOfferClick(offer) }
            .glassSoftShadow(cardShape)
            .expressiveShadow(cardShape, 6.dp),
        shape = cardShape,
        colors = CardDefaults.cardColors(
            containerColor = if (expressive) MaterialTheme.colorScheme.surfaceContainerHigh
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column {
            ProductImage(
                imageUrl = offer.imageUrl,
                contentDescription = offer.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(136.dp),
                onClick = { onOfferClick(offer) },
                validTo = offer.validTo,
                ean = offer.barcode, category = offer.category
            )
            Column(modifier = Modifier.padding(8.dp)) {
                DiscountBadge(discount = offer.bestDiscount, compact = true)
                Spacer(Modifier.height(4.dp))
                Text(
                    offer.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Text("Código ${offer.code}", style = MaterialTheme.typography.labelSmall)
                Text("EAN: ${offer.barcode.ifBlank { "não informado" }}", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                PriceSummary(offer = offer, compact = true)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PromotionCategoryList(
    innerPadding: PaddingValues,
    categoryName: String,
    selectedStore: String,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    sortOption: OfferSortOption,
    onSortOptionChange: (OfferSortOption) -> Unit,
    visibleOffers: List<OfferGroup>,
    visibleOfferCount: Int,
    onLoadMore: () -> Unit,
    onOfferClick: (OfferGroup) -> Unit,
    onBack: () -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit
) {
    val offersToRender = visibleOffers.take(visibleOfferCount)
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.padding(innerPadding).fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Categorias")
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "$categoryName • ${visibleOffers.size}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                OfferSortSelector(
                    selected = sortOption,
                    onSelected = onSortOptionChange
                )
            }
        }
        item {
            SearchField(query = searchQuery, onQueryChange = onSearchQueryChange)
        }
        item {
            Text(
                if (selectedStore == ALL_STORES_LABEL) "Preços por loja" else "Filtrado por ${StoreCatalog.nameFor(selectedStore)}",
                modifier = Modifier.padding(horizontal = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (offersToRender.isEmpty()) {
            item {
                Text(
                    "Nenhum produto encontrado para este filtro.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            items(offersToRender, key = { it.id }) { offer ->
                DetailedOfferCard(
                    offer = offer,
                    selectedStore = selectedStore,
                    onOfferClick = onOfferClick
                )
            }
        }
        if (visibleOfferCount < visibleOffers.size) {
            item {
                Button(
                    onClick = onLoadMore,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                ) {
                    Text("Carregar mais ofertas (${visibleOffers.size - visibleOfferCount} restantes)")
                }
            }
        }
        }
    }
}

@Composable
private fun OfferSortSelector(
    selected: OfferSortOption,
    onSelected: (OfferSortOption) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
        ) {
            Text("Ordenar", maxLines = 1)
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Default.ExpandMore,
                contentDescription = "Escolher ordem das ofertas",
                modifier = Modifier.size(18.dp)
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            OfferSortOption.values().forEach { option ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(option.label)
                            if (option == selected) {
                                Text("Selecionado", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DetailedOfferCard(
    offer: OfferGroup,
    selectedStore: String,
    onOfferClick: (OfferGroup) -> Unit
) {
    val expressive = LocalExpressiveStyle.current.enabled
    val cardShape = if (expressive) RoundedCornerShape(30.dp) else RoundedCornerShape(16.dp)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val shareLayer = rememberGraphicsLayer()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .drawWithContent {
                shareLayer.record {
                    this@drawWithContent.drawContent()
                }
                drawLayer(shareLayer)
            }
            .combinedClickable(
                onClick = { onOfferClick(offer) },
                onLongClick = {
                    scope.launch {
                        val copied = copyProductCardToClipboard(
                            context = context,
                            layer = shareLayer,
                            productName = offer.name
                        )
                        android.widget.Toast.makeText(
                            context,
                            if (copied) "Copiado na Área de Transferência"
                            else "Não foi possível copiar o quadradinho.",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            )
            .glassSoftShadow(cardShape)
            .expressiveShadow(cardShape, 7.dp),
        shape = cardShape,
        colors = CardDefaults.cardColors(
            containerColor = if (expressive) MaterialTheme.colorScheme.surfaceContainerLow
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                ProductImage(
                    imageUrl = offer.imageUrl,
                    contentDescription = "Ver imagem de ${offer.name}",
                    modifier = Modifier
                        .size(width = 112.dp, height = 128.dp)
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant,
                            if (expressive) RoundedCornerShape(18.dp) else RoundedCornerShape(10.dp)
                        ),
                    onClick = { onOfferClick(offer) },
                    validTo = offer.validTo,
                ean = offer.barcode, category = offer.category
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    DiscountBadge(discount = offer.bestDiscount, compact = false)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        offer.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (offer.code.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text("Código ${offer.code} • EAN ${offer.barcode.ifBlank { "não informado" }}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (offer.validity.isNotBlank()) {
                        Spacer(Modifier.height(5.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(offer.validity, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    PriceSummary(offer = offer, selectedStore = selectedStore, compact = false)
                }
            }
            Spacer(Modifier.height(10.dp))
            StorePriceSelector(offer = offer, selectedStore = selectedStore)
        }
    }
}

@Composable
private fun StorePriceSelector(offer: OfferGroup, selectedStore: String) {
    var expanded by remember(offer.id, selectedStore) { mutableStateOf(false) }
    var pickedStore by remember(offer.id, selectedStore) {
        mutableStateOf(
            if (selectedStore != ALL_STORES_LABEL && offer.stores.any { it.storeCode == selectedStore }) {
                selectedStore
            } else {
                offer.stores.firstOrNull()?.storeCode.orEmpty()
            }
        )
    }
    val pickedOffer = offer.stores.firstOrNull { it.storeCode == pickedStore } ?: offer.stores.firstOrNull()

    Column(modifier = Modifier.fillMaxWidth()) {
        Box {
            OutlinedButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Storefront, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (pickedStore.isBlank()) "Escolher loja" else "Preço na loja: ${StoreCatalog.nameFor(pickedStore)}",
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Fechar lojas" else "Abrir lojas"
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                offer.stores.forEach { storeOffer ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(StoreCatalog.nameFor(storeOffer.storeCode))
                                Text(
                                    storeOffer.storeCode,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    storeOffer.offerPrice ?: "Preço não informado",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        },
                        onClick = {
                            pickedStore = storeOffer.storeCode
                            expanded = false
                        }
                    )
                }
            }
        }
        AnimatedVisibility(visible = pickedOffer != null) {
            pickedOffer?.let { storeOffer ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Oferta nesta loja", style = MaterialTheme.typography.bodyMedium)
                    Column(horizontalAlignment = Alignment.End) {
                        storeOffer.regularPrice?.let {
                            Text(
                                "De $it",
                                style = MaterialTheme.typography.bodySmall,
                                textDecoration = TextDecoration.LineThrough,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            storeOffer.offerPrice ?: "Preço não informado",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PriceSummary(
    offer: OfferGroup,
    selectedStore: String? = null,
    compact: Boolean
) {
    val storeOffer = if (selectedStore != null && selectedStore != ALL_STORES_LABEL) {
        offer.stores.firstOrNull { it.storeCode == selectedStore } ?: offer.stores.firstOrNull()
    } else {
        offer.bestOffer
    }
    Column {
        storeOffer?.regularPrice?.let {
            Text(
                "De $it",
                style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                textDecoration = TextDecoration.LineThrough,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            storeOffer?.offerPrice ?: "Preço não informado",
            style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun DiscountBadge(discount: String?, compact: Boolean) {
    if (discount.isNullOrBlank()) return
    val expressive = LocalExpressiveStyle.current.enabled
    Surface(
        color = if (expressive) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primary,
        contentColor = if (expressive) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimary,
        shape = if (expressive) RoundedCornerShape(16.dp) else RoundedCornerShape(6.dp)
    ) {
        Text(
            text = "-${discount.removePrefix("-")}",
            modifier = Modifier.padding(horizontal = if (compact) 7.dp else 9.dp, vertical = 4.dp),
            style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ValidityBadge(
    validTo: String?,
    modifier: Modifier = Modifier,
    compact: Boolean = true
) {
    val label = validTo.toExpiryLabel() ?: return
    val expressive = LocalExpressiveStyle.current.enabled
    Surface(
        modifier = modifier,
        color = if (expressive) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primary,
        contentColor = if (expressive) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimary,
        shape = if (expressive) RoundedCornerShape(16.dp) else CircleShape,
        tonalElevation = 2.dp
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(
                horizontal = if (compact) 8.dp else 11.dp,
                vertical = if (compact) 4.dp else 6.dp
            ),
            style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun ProductImage(
    imageUrl: String?,
    contentDescription: String,
    modifier: Modifier,
    onClick: (String) -> Unit,
    validTo: String? = null,
    ean: String? = null,
    category: String = "Outras ofertas"
) {
    val context = LocalContext.current
    var external by remember(ean, category) { mutableStateOf<com.example.data.promotions.BarcodeImage?>(null) }
    LaunchedEffect(imageUrl, ean, category) {
        if (imageUrl.isNullOrBlank()) external = com.example.data.promotions.ProductImageRepository.get(context).find(ean, category)
    }
    val effectiveUrl = imageUrl?.takeIf { it.isNotBlank() } ?: external?.url
    val expressive = LocalExpressiveStyle.current.enabled
    val shape = if (expressive) RoundedCornerShape(20.dp) else RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(
                if (!effectiveUrl.isNullOrBlank()) {
                    Modifier.clickable { onClick(effectiveUrl) }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.Image,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(38.dp)
        )
        if (!effectiveUrl.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(effectiveUrl).size(360, 280).crossfade(false).build(),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
        ValidityBadge(
            validTo = validTo,
            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
            compact = true
        )
    }
}

@Composable
private fun PromotionImageDialog(imageUrl: String, onDismiss: () -> Unit) {
    val dialogShape = RoundedCornerShape(18.dp)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 620.dp)
                .glassSoftShadow(dialogShape),
            shape = dialogShape,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar imagem")
                    }
                }
                Box(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 260.dp, max = 540.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(imageUrl).size(1200, 900).crossfade(false).build(),
                        contentDescription = "Imagem ampliada da oferta",
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Fit
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Toque fora da imagem ou no X para fechar.",
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun PromotionDetailsDialog(
    offer: OfferGroup,
    selectedStore: String,
    onDismiss: () -> Unit,
    onOpenStore: (String) -> Unit
) {
    val dialogShape = RoundedCornerShape(22.dp)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.9f)
                .glassSoftShadow(dialogShape),
            shape = dialogShape,
            color = MaterialTheme.colorScheme.surface
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Detalhes da oferta",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Preços e disponibilidade por loja",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Fechar detalhes da oferta")
                        }
                    }
                }
                item {
                    ProductImage(imageUrl = offer.imageUrl, contentDescription = offer.name,
                        modifier = Modifier.fillMaxWidth().height(270.dp), onClick = {},
                        validTo = offer.validTo, ean = offer.barcode, category = offer.category)
                }
                item {
                    SelectionContainer {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                offer.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                            if (offer.code.isNotBlank()) {
                                Text(
                                    "Código ${offer.code} • EAN ${offer.barcode.ifBlank { "não informado" }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                offer.category,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (offer.validity.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.CalendarToday,
                                        contentDescription = null,
                                        modifier = Modifier.size(17.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "Validade: ${offer.validity}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("A partir de", style = MaterialTheme.typography.labelMedium)
                                        Text(
                                            offer.bestOffer?.offerPrice ?: "Preço não informado",
                                            style = MaterialTheme.typography.headlineSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    DiscountBadge(discount = offer.bestDiscount, compact = false)
                                }
                            }
                    }
                    }
                }
                item {
                    val store = offer.stores.firstOrNull { it.storeCode == selectedStore } ?: offer.bestOffer
                    PromotionProductDetailsContent(store?.detailsJson, store?.barcode, offer.code, offer.name)
                }
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Lojas com promoção",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Toque em “Ver na loja” para abrir esta oferta já filtrada na unidade escolhida.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                items(
                    items = offer.stores,
                    key = { store -> "${store.storeCode}|${store.offerPrice.orEmpty()}|${store.regularPrice.orEmpty()}" }
                ) { storeOffer ->
                    StoreOfferDetailCard(
                        storeOffer = storeOffer,
                        onOpenStore = onOpenStore
                    )
                }
            }
        }
    }
}

@Composable
private fun StoreOfferDetailCard(
    storeOffer: StoreOffer,
    onOpenStore: (String) -> Unit
) {
    val expressive = LocalExpressiveStyle.current.enabled
    val storeAvailable = storeOffer.storeCode.isNotBlank() && storeOffer.storeCode != UNKNOWN_STORE_LABEL
    val shape = if (expressive) RoundedCornerShape(24.dp) else RoundedCornerShape(14.dp)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .glassSoftShadow(shape, 2.dp)
            .expressiveShadow(shape, 5.dp),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (expressive) MaterialTheme.colorScheme.surfaceContainerHigh
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = CircleShape
                ) {
                    Icon(
                        Icons.Default.Storefront,
                        contentDescription = null,
                        modifier = Modifier.padding(8.dp).size(18.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                SelectionContainer(modifier = Modifier.weight(1f)) {
                    Column {
                        Text(
                            if (storeAvailable) StoreCatalog.nameFor(storeOffer.storeCode) else UNKNOWN_STORE_LABEL,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        if (storeAvailable) {
                            Text(
                                "Loja ${storeOffer.storeCode.padStart(4, '0')}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                }
                }
                DiscountBadge(discount = storeOffer.discount, compact = true)
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                SelectionContainer(modifier = Modifier.weight(1f)) {
                    Column {
                        storeOffer.regularPrice?.let { regular ->
                            Text(
                                "De $regular",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textDecoration = TextDecoration.LineThrough
                            )
                        }
                        Text(
                            storeOffer.offerPrice ?: "Preço não informado",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                }
                }
                if (storeAvailable) {
                    TextButton(onClick = { onOpenStore(storeOffer.storeCode) }) {
                        Icon(Icons.Default.Storefront, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Ver na loja")
                    }
                }
            }
        }
    }
}

private data class OfferGroup(
    val id: String,
    val category: String,
    val code: String,
    val name: String,
    val imageUrl: String?,
    val validFrom: String?,
    val validTo: String?,
    val stores: List<StoreOffer>
) {
    val barcode: String get() = stores.firstOrNull { !it.barcode.isNullOrBlank() }?.barcode.orEmpty()
    val bestOffer: StoreOffer?
        get() = stores.minByOrNull { it.offerNumeric ?: Double.MAX_VALUE }

    val bestDiscount: String?
        get() = stores.firstOrNull { !it.discount.isNullOrBlank() }?.discount

    val validity: String
        get() = listOfNotNull(validFrom.toDisplayDate(), validTo.toDisplayDate())
            .joinToString(" até ")
}

private data class StoreOffer(
    val storeCode: String,
    val offerPrice: String?,
    val regularPrice: String?,
    val discount: String?,
    val imageUrl: String?,
    val linkUrl: String?,
    val offerNumeric: Double?,
    val barcode: String? = null,
    val detailsJson: String? = null
)

private fun List<OfferGroup>.findOfferForChange(change: PromotionChange): OfferGroup? {
    val storeCode = change.storeCode.trim()

    fun OfferGroup.matchesStore(): Boolean =
        storeCode.isBlank() ||
            storeCode == UNKNOWN_STORE_LABEL ||
            stores.any { it.storeCode == storeCode }

    val code = change.productCode.trim()
    if (code.isNotBlank()) {
        firstOrNull { offer ->
            offer.code == code &&
                offer.category == change.category &&
                offer.validFrom == change.newValidFrom &&
                offer.validTo == change.newValidTo &&
                offer.matchesStore()
        }?.let { return it }

        firstOrNull { offer ->
            offer.code == code && offer.matchesStore()
        }?.let { return it }
    }

    return firstOrNull { offer ->
        offer.name.equals(change.productName, ignoreCase = true) &&
            (change.category.isBlank() || offer.category == change.category) &&
            offer.matchesStore()
    }
}

private fun buildOfferGroups(promotions: List<Promotion>): List<OfferGroup> {
    val grouped = linkedMapOf<String, MutableOfferGroup>()
    promotions.forEach { promotion ->
        val category = promotion.description.trim().ifBlank { "Outras ofertas" }
        promotion.products.forEach { product ->
            val name = product.name.trim().ifBlank { product.code.ifBlank { "Produto em oferta" } }
            val key = listOf(category, product.code, name, promotion.validFrom.orEmpty(), promotion.validTo.orEmpty())
                .joinToString("|")
            val group = grouped.getOrPut(key) {
                MutableOfferGroup(
                    id = key,
                    category = category,
                    code = product.code,
                    name = name,
                    imageUrl = product.imageUrl ?: promotion.imageUrl,
                    validFrom = promotion.validFrom,
                    validTo = promotion.validTo
                )
            }
            val store = product.storeCode?.trim().orEmpty().ifBlank { UNKNOWN_STORE_LABEL }
            val storeKey = listOf(store, product.offerPrice.orEmpty(), product.regularPrice.orEmpty()).joinToString("|")
            if (group.stores.none { it.key == storeKey }) {
                group.stores += MutableStoreOffer(
                    key = storeKey,
                    storeCode = store,
                    offerPrice = product.offerPrice,
                    regularPrice = product.regularPrice,
                    discount = product.discount,
                    imageUrl = product.imageUrl ?: promotion.imageUrl,
                    linkUrl = product.linkUrl,
                    offerNumeric = product.offerPrice.toNumericPrice(),
                    barcode = product.barcode,
                    detailsJson = product.detailsJson
                )
            }
        }
    }
    return grouped.values.map { group ->
        OfferGroup(
            id = group.id,
            category = group.category,
            code = group.code,
            name = group.name,
            imageUrl = group.imageUrl,
            validFrom = group.validFrom,
            validTo = group.validTo,
            stores = group.stores
                .map { store ->
                    StoreOffer(
                        storeCode = store.storeCode,
                        offerPrice = store.offerPrice,
                        regularPrice = store.regularPrice,
                        discount = store.discount,
                        imageUrl = store.imageUrl,
                        linkUrl = store.linkUrl,
                        offerNumeric = store.offerNumeric,
                        barcode = store.barcode,
                        detailsJson = store.detailsJson
                    )
                }
                .sortedBy { it.storeCode.lowercase() }
        )
    }
}

private fun sortOfferGroups(offers: List<OfferGroup>, option: OfferSortOption): List<OfferGroup> = when (option) {
    OfferSortOption.NAME -> offers.sortedWith(compareBy<OfferGroup> { it.name.lowercase() }.thenBy { it.id })
    OfferSortOption.VALID_UNTIL -> offers.sortedWith(
        compareBy<OfferGroup> { it.validTo.isNullOrBlank() }
            .thenBy { it.validTo.orEmpty() }
            .thenBy { it.name.lowercase() }
    )
    OfferSortOption.ADDED -> offers
    OfferSortOption.DISCOUNT_DESC -> offers.sortedWith(
        compareByDescending<OfferGroup> { it.maxDiscountPercent() ?: -1.0 }
            .thenBy { it.name.lowercase() }
    )
    OfferSortOption.DISCOUNT_ASC -> offers.sortedWith(
        compareBy<OfferGroup> { it.maxDiscountPercent() ?: Double.MAX_VALUE }
            .thenBy { it.name.lowercase() }
    )
    OfferSortOption.PRICE_ASC -> offers.sortedWith(
        compareBy<OfferGroup> { it.bestOffer?.offerNumeric ?: Double.MAX_VALUE }
            .thenBy { it.name.lowercase() }
    )
    OfferSortOption.PRICE_DESC -> offers.sortedWith(
        compareByDescending<OfferGroup> { it.bestOffer?.offerNumeric ?: -1.0 }
            .thenBy { it.name.lowercase() }
    )
}

private fun OfferGroup.maxDiscountPercent(): Double? = stores
    .mapNotNull { it.discount.toNumericPercent() }
    .maxOrNull()

private fun String?.toNumericPercent(): Double? = this
    ?.replace("%", "")
    ?.replace(",", ".")
    ?.trim()
    ?.toDoubleOrNull()

private fun String?.toExpiryLabel(): String? {
    val raw = this?.trim().orEmpty()
    if (raw.isBlank()) return null
    Regex("""(\d{4})-(\d{2})-(\d{2})""").find(raw)?.let { match ->
        return "Até ${match.groupValues[3]}/${match.groupValues[2]}"
    }
    Regex("""(\d{2})[/-](\d{2})[/-](\d{2,4})""").find(raw)?.let { match ->
        return "Até ${match.groupValues[1]}/${match.groupValues[2]}"
    }
    return "Até ${raw.take(10)}"
}

private fun String?.toDisplayDate(): String? {
    val raw = this?.trim().orEmpty()
    if (raw.isBlank()) return null
    Regex("""(\d{4})-(\d{2})-(\d{2})""").find(raw)?.let { match ->
        return "${match.groupValues[3]}/${match.groupValues[2]}/${match.groupValues[1]}"
    }
    Regex("""(\d{2})[/-](\d{2})[/-](\d{2,4})""").find(raw)?.let { match ->
        return "${match.groupValues[1]}/${match.groupValues[2]}/${match.groupValues[3]}"
    }
    return raw.take(10)
}

private data class MutableOfferGroup(
    val id: String,
    val category: String,
    val code: String,
    val name: String,
    val imageUrl: String?,
    val validFrom: String?,
    val validTo: String?,
    val stores: MutableList<MutableStoreOffer> = mutableListOf()
)

private data class MutableStoreOffer(
    val key: String,
    val storeCode: String,
    val offerPrice: String?,
    val regularPrice: String?,
    val discount: String?,
    val imageUrl: String?,
    val linkUrl: String?,
    val offerNumeric: Double?,
    val barcode: String? = null,
    val detailsJson: String? = null
)

private fun String?.toNumericPrice(): Double? = this
    ?.replace("R$", "", ignoreCase = true)
    ?.trim()
    ?.let { raw ->
        if (raw.contains(",")) raw.replace(".", "").replace(",", ".") else raw.replace(",", "")
    }
    ?.toDoubleOrNull()

@Composable
private fun NewOffersButton(
    changeCount: Int,
    highlighted: Boolean,
    showingNew: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val glassStyle = LocalGlassSoftStyle.current
    val expressive = LocalExpressiveStyle.current.enabled
    val containerColor = if (highlighted) {
        MaterialTheme.colorScheme.primaryContainer
    } else if (glassStyle.enabled) {
        MaterialTheme.colorScheme.surface
    } else if (expressive) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0f)
    }
    val contentColor = if (highlighted) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val buttonShape = if (expressive) RoundedCornerShape(20.dp) else RoundedCornerShape(14.dp)
    Surface(
        modifier = Modifier.glassSoftShadow(buttonShape, 4.dp),
        onClick = onClick,
        enabled = enabled,
        color = containerColor,
        contentColor = contentColor,
        shape = buttonShape,
        tonalElevation = if (highlighted) 2.dp else 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.LocalOffer, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                if (showingNew) "Todas as ofertas" else "Ofertas novas",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (changeCount > 0) {
                Spacer(Modifier.width(4.dp))
                Surface(
                    color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (highlighted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    shape = CircleShape
                ) {
                    Text(
                        changeCount.coerceAtMost(999).toString(),
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun NewOffersDialog(
    changes: List<PromotionChange>,
    selectedStore: String,
    limitedBySafetyCap: Boolean,
    canOpenOffer: (PromotionChange) -> Boolean,
    onDismiss: () -> Unit,
    onOfferClick: (PromotionChange) -> Unit
) {
    val groupedChanges = remember(changes, selectedStore) {
        if (selectedStore != ALL_STORES_LABEL) {
            listOf(selectedStore to changes)
        } else {
            changes
                .groupBy { it.storeCode.ifBlank { UNKNOWN_STORE_LABEL } }
                .toList()
                .sortedBy { (storeCode, _) ->
                    if (storeCode == UNKNOWN_STORE_LABEL) storeCode.lowercase()
                    else StoreCatalog.nameFor(storeCode).lowercase()
                }
        }
    }
    val currentStoreLabel = if (selectedStore == ALL_STORES_LABEL) {
        "Todas as lojas"
    } else {
        StoreCatalog.nameFor(selectedStore)
    }
    val addedCount = changes.count { it.type == PromotionChangeType.ADDED }
    val changedCount = changes.count { it.type == PromotionChangeType.CHANGED }
    val removedCount = changes.count { it.type == PromotionChangeType.REMOVED }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val dialogShape = RoundedCornerShape(20.dp)
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.9f)
                .glassSoftShadow(dialogShape),
            shape = dialogShape,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 10.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Últimas mudanças", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(
                            if (selectedStore == ALL_STORES_LABEL) {
                                "Última movimentação de ofertas, organizada por loja"
                            } else {
                                "Loja atual: $currentStoreLabel"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar alterações de ofertas")
                    }
                }

                if (limitedBySafetyCap) {
                    Text(
                        "O catálogo ultrapassou o limite de segurança do histórico. As alterações que conseguiram ser registradas continuam listadas abaixo.",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Text(
                    "${changes.size} mudança(s) na última atualização • $currentStoreLabel",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Entraram: $addedCount  •  Alteradas: $changedCount  •  Saíram: $removedCount",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )

                if (changes.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.LocalOffer, contentDescription = null, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (selectedStore == ALL_STORES_LABEL) {
                                "Nenhuma mudança de oferta registrada ainda."
                            } else {
                                "Nenhuma mudança de oferta registrada para $currentStoreLabel ainda."
                            }
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "As últimas mudanças ficam salvas aqui até o NRD detectar uma nova atualização de ofertas.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        groupedChanges.forEach { (storeCode, storeChanges) ->
                            item(key = "change-store-$storeCode") {
                                Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp)) {
                                    Text(
                                        if (storeCode == UNKNOWN_STORE_LABEL) UNKNOWN_STORE_LABEL else StoreCatalog.nameFor(storeCode),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (storeCode != UNKNOWN_STORE_LABEL) {
                                        Text(
                                            "Loja ${storeCode.padStart(4, '0')} • ${storeChanges.size} mudança(s)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            items(storeChanges, key = { it.stableKey }) { change ->
                                PromotionChangeCard(
                                    change = change,
                                    canOpen = canOpenOffer(change),
                                    onOfferClick = onOfferClick
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PromotionChangeCard(
    change: PromotionChange,
    canOpen: Boolean,
    onOfferClick: (PromotionChange) -> Unit
) {
    val expressive = LocalExpressiveStyle.current.enabled
    val cardShape = if (expressive) RoundedCornerShape(24.dp) else RoundedCornerShape(14.dp)
    val oldValidity = listOfNotNull(
        change.oldValidFrom.toDisplayDate(),
        change.oldValidTo.toDisplayDate()
    ).joinToString(" até ")
    val newValidity = listOfNotNull(
        change.newValidFrom.toDisplayDate(),
        change.newValidTo.toDisplayDate()
    ).joinToString(" até ")
    val validity = when (change.type) {
        PromotionChangeType.ADDED -> newValidity
        PromotionChangeType.CHANGED -> {
            if (change.validityChanged && oldValidity.isNotBlank() && newValidity.isNotBlank() && oldValidity != newValidity) {
                "$oldValidity → $newValidity"
            } else {
                newValidity.ifBlank { oldValidity }
            }
        }
        PromotionChangeType.REMOVED -> oldValidity
    }
    val priceText = when (change.type) {
        PromotionChangeType.ADDED -> "Preço: ${change.newOfferPrice ?: "não informado"}"
        PromotionChangeType.CHANGED -> {
            val oldPrice = change.oldOfferPrice
            val newPrice = change.newOfferPrice
            if (!oldPrice.isNullOrBlank() && !newPrice.isNullOrBlank() && oldPrice != newPrice) {
                "Preço: $oldPrice → $newPrice"
            } else {
                "Preço: ${newPrice ?: oldPrice ?: "não informado"}"
            }
        }
        PromotionChangeType.REMOVED -> "Último preço: ${change.oldOfferPrice ?: "não informado"}"
    }
    val validToForImage = if (change.type == PromotionChangeType.REMOVED) change.oldValidTo else change.newValidTo
    val badgeContainerColor = when (change.type) {
        PromotionChangeType.ADDED -> MaterialTheme.colorScheme.primaryContainer
        PromotionChangeType.CHANGED -> MaterialTheme.colorScheme.tertiaryContainer
        PromotionChangeType.REMOVED -> MaterialTheme.colorScheme.errorContainer
    }
    val badgeContentColor = when (change.type) {
        PromotionChangeType.ADDED -> MaterialTheme.colorScheme.onPrimaryContainer
        PromotionChangeType.CHANGED -> MaterialTheme.colorScheme.onTertiaryContainer
        PromotionChangeType.REMOVED -> MaterialTheme.colorScheme.onErrorContainer
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .glassSoftShadow(cardShape)
            .expressiveShadow(cardShape, 5.dp)
            .then(if (canOpen) Modifier.clickable { onOfferClick(change) } else Modifier),
        colors = CardDefaults.cardColors(
            containerColor = if (expressive) MaterialTheme.colorScheme.surfaceContainerLow
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = cardShape
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.Top) {
            if (!change.imageUrl.isNullOrBlank()) {
                ProductImage(
                    imageUrl = change.imageUrl,
                    contentDescription = change.productName,
                    modifier = Modifier.size(76.dp),
                    onClick = { if (canOpen) onOfferClick(change) },
                    validTo = validToForImage
                )
                Spacer(Modifier.width(8.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Surface(
                    color = badgeContainerColor,
                    contentColor = badgeContentColor,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        change.type.displayLabel(),
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    change.productName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${StoreCatalog.labelFor(change.storeCode)} • código ${change.productCode}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    priceText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (change.type == PromotionChangeType.REMOVED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
                if (change.type == PromotionChangeType.CHANGED) {
                    val reason = when {
                        change.priceChanged && change.validityChanged -> "Alteração: preço/condição e validade"
                        change.priceChanged -> "Alteração: preço/condição"
                        change.validityChanged -> "Alteração: validade"
                        else -> "Alteração: dados da oferta"
                    }
                    Text(
                        reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (validity.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CalendarToday,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (change.type == PromotionChangeType.REMOVED) "Validade anterior: $validity" else "Validade: $validity",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    when {
                        canOpen -> "Toque para abrir esta oferta"
                        change.type == PromotionChangeType.REMOVED -> "Esta oferta saiu do catálogo"
                        else -> "Esta oferta não está disponível no catálogo atual"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (canOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun PromotionChangeType.displayOrder(): Int = when (this) {
    PromotionChangeType.ADDED -> 0
    PromotionChangeType.CHANGED -> 1
    PromotionChangeType.REMOVED -> 2
}

private fun PromotionChangeType.displayLabel(): String = when (this) {
    PromotionChangeType.ADDED -> "ENTROU EM OFERTA"
    PromotionChangeType.CHANGED -> "OFERTA ALTERADA"
    PromotionChangeType.REMOVED -> "SAIU DA OFERTA"
}
