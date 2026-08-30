package com.example.ui
import androidx.compose.ui.composed
import androidx.compose.ui.composed
import androidx.compose.ui.layout.ContentScale

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.BakeryDining
import androidx.compose.material.icons.filled.Sanitizer
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.SetMeal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.collectLatest

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.ui.theme.getDynamicThemeColor
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.ui.res.painterResource
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.Image
import com.example.R
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.drawscope.Stroke
import android.os.Vibrator
import android.content.Context
import android.os.VibrationEffect
import android.os.Build
import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import java.text.Normalizer

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import androidx.compose.foundation.verticalScroll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.example.data.AppearanceSettings
import com.example.data.FirebaseService
import com.example.data.Product
import com.example.data.ProductStandards
import com.example.data.AppNotification
import android.graphics.Bitmap
import androidx.compose.ui.graphics.FilterQuality
import com.google.zxing.EncodeHintType
import java.util.EnumMap
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ImageBitmap


data class HomeTextPreferences(
    val boldOutline: Boolean = false,
    val uppercaseBold: Boolean = false
)

@Composable
fun rememberHomeTextPreferences(userPreferences: com.example.data.UserPreferences): HomeTextPreferences {
    val boldOutline by userPreferences.boldOutline.collectAsStateWithLifecycle(initialValue = false)
    val uppercaseBold by userPreferences.uppercaseBold.collectAsStateWithLifecycle(initialValue = false)
    return HomeTextPreferences(boldOutline = boldOutline, uppercaseBold = uppercaseBold)
}

@Composable
fun StylizedText(
    text: String,
    baseStyle: TextStyle,
    boldOutline: Boolean,
    uppercaseBold: Boolean,
    color: Color = Color.Unspecified,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip
) {
    val finalText = if (uppercaseBold) text.uppercase() else text
    val weight = if (uppercaseBold || boldOutline) FontWeight.Bold else baseStyle.fontWeight
    val styleWithMods = baseStyle.copy(
        fontWeight = weight,
        color = if (boldOutline) Color.Transparent else color
    )

    Box(modifier = modifier) {
        if (boldOutline) {
            androidx.compose.material3.Text(
                text = finalText,
                style = styleWithMods.copy(drawStyle = Stroke(width = 2f)),
                color = color,
                maxLines = maxLines,
                overflow = overflow
            )
        }
        androidx.compose.material3.Text(
            text = finalText,
            style = styleWithMods,
            color = if (boldOutline) Color.White else color,
            maxLines = maxLines,
            overflow = overflow
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(viewModel: MainViewModel, onOpenDrawer: () -> Unit = {}) {
    val bannerImageUri by viewModel.userPreferences.bannerImageUri.collectAsState(initial = null)
    val localAppTheme by viewModel.userPreferences.appTheme.collectAsStateWithLifecycle(initialValue = "multicolor")
    val remoteAppearance by FirebaseService.observeAppearanceSettings()
        .collectAsStateWithLifecycle(initialValue = AppearanceSettings())
    val appTheme = if (remoteAppearance.overrideLocalTheme) remoteAppearance.theme else localAppTheme
    
    val normalizedTheme = remember(appTheme) { 
        when (appTheme.trim().lowercase()) {
            "multicolor" -> "multicolor"
            "gold" -> "gold"
            "green" -> "green"
            "blue" -> "blue"
            "orange" -> "orange"
            else -> "red"
        }
    }


    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val textPreferences = rememberHomeTextPreferences(viewModel.userPreferences)
    val vibrateOnFound by viewModel.userPreferences.vibrateOnFound.collectAsStateWithLifecycle(initialValue = true)
    val mostUsed by viewModel.mostUsed.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val latestProductLocal by viewModel.latestProductLocal.collectAsStateWithLifecycle()
    val latestProductFirebase by viewModel.latestProduct.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val newProductsCount by viewModel.newProductsCount.collectAsStateWithLifecycle()
    val homeSettings by viewModel.homeSettings.collectAsStateWithLifecycle()
    val activeCategoryNames by viewModel.activeCategoryNames.collectAsStateWithLifecycle()

    var showProductSearchSheet by remember { mutableStateOf(false) }
    var showMostUsedSheet by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var showNotificationsSheet by remember { mutableStateOf(false) }
    var selectedNotificationProduct by remember { mutableStateOf<Product?>(null) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var sheetQuery by remember { mutableStateOf("") }
    val notificationHistory by viewModel.notificationHistory.collectAsStateWithLifecycle()
    val unreadNotifications = notificationHistory.count { !it.read }
    val mostUsedListState = rememberLazyListState()
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val vibrator = remember { context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator }
    var hadSearchResults by remember { mutableStateOf(false) }
    val voiceLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrBlank()) viewModel.updateSearchQuery(spokenText)
        }
    }
    LaunchedEffect(searchQuery, searchResults, vibrateOnFound) {
        val hasSearchResults = searchQuery.isNotBlank() && searchResults.isNotEmpty()
        if (hasSearchResults && !hadSearchResults && vibrateOnFound) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(50)
            }
        }
        hadSearchResults = hasSearchResults
    }

    LaunchedEffect(mostUsed, homeSettings.carouselIntervalSeconds) {
        if (mostUsed.isEmpty() || !homeSettings.showMostUsed) return@LaunchedEffect
        while (true) {
            delay(homeSettings.carouselIntervalSeconds * 1000L)
            if (!mostUsedListState.isScrollInProgress) {
                val nextIndex = (mostUsedListState.firstVisibleItemIndex + 1) % mostUsed.size
                mostUsedListState.animateScrollToItem(nextIndex)
            }
        }
    }

    Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val headerHeight = maxWidth / 3f

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(headerHeight)
                    .clip(
                        RoundedCornerShape(
                            bottomStart = 32.dp,
                            bottomEnd = 32.dp
                        )
                    )
                    .background(Color.White)
            ) {
                ThemeBanner(
                    appTheme = normalizedTheme,
                    backgroundUrl = remoteAppearance.activeBackgroundFor(normalizedTheme)?.url,
                    modifier = Modifier.fillMaxSize()
                )

                IconButton(
                    onClick = {
                        viewModel.clearNewProductsCount()
                        onOpenDrawer()
                    },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 48.dp, start = 8.dp)
                        .background(Color.Transparent)
                ) {
                    BadgedBox(
                        badge = {
                            if (newProductsCount > 0) {
                                Badge { Text(newProductsCount.toString()) }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Menu",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                IconButton(
                    onClick = { showNotificationsSheet = true },
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 48.dp, end = 8.dp)
                ) {
                    BadgedBox(
                        badge = { if (unreadNotifications > 0) Badge { Text(unreadNotifications.toString()) } }
                    ) {
                        Icon(Icons.Default.Notifications, contentDescription = "Notificações", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SearchBar(
                query = searchQuery,
                onQueryChange = viewModel::updateSearchQuery,
                onSearch = { keyboardController?.hide() },
                active = false,
                onActiveChange = { keyboardController?.hide() },
                placeholder = { Text("Pesquisar produto...", style = MaterialTheme.typography.bodyLarge) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Pesquisar", modifier = Modifier.size(28.dp)) },
                trailingIcon = {
                    Row {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Limpar")
                            }

                        } else {
                            IconButton(onClick = {
                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
                                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Diga o nome ou código do produto")
                                }
                                voiceLauncher.launch(intent)
                            }) {
                                Icon(Icons.Default.Mic, contentDescription = "Pesquisar por voz", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(32.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(32.dp)),
                colors = SearchBarDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    dividerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                )
            ) {}
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = {
                    sheetQuery = searchQuery
                    showProductSearchSheet = true
                },
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.Search, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Pesquisar", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (searchQuery.isNotEmpty()) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (searchResults.isEmpty()) {
                    item {
                        SearchEmptyState(onClear = { viewModel.updateSearchQuery("") })
                    }
                } else {
                    itemsIndexed(searchResults, key = { _, it -> it.code }) { index, product ->
                        ProductCard(product, viewModel, index, appTheme, textPreferences)
                    }
                }
            }
        } else {
            val hasVisibleHomeSection = homeSettings.showCategories ||
                (homeSettings.showMostUsed && mostUsed.isNotEmpty()) ||
                (homeSettings.showHistory && history.isNotEmpty()) ||
                (homeSettings.showFavorites && favorites.isNotEmpty())
            LazyColumn(
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                if (homeSettings.showCategories) {
                    item {
                        CategorySection(
                            viewModel = viewModel,
                            appTheme = appTheme,
                            textPreferences = textPreferences,
                            categories = activeCategoryNames,
                            onCategoryClick = { selectedCategory = it }
                        )
                    }
                }

                if (homeSettings.showMostUsed && mostUsed.isNotEmpty()) {
                    item {
                        SectionHeader("Mais Utilizados", textPreferences, actionLabel = "VER TODOS", onAction = { showMostUsedSheet = true })
                        LazyRow(
                            state = mostUsedListState,
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            itemsIndexed(mostUsed, key = { _, it -> it.code }) { index, product ->
                                MiniProductCard(product, viewModel, index, appTheme, textPreferences)
                            }
                        }
                    }
                }

                if (homeSettings.showHistory && history.isNotEmpty()) {
                    item {
                        SectionHeader("Histórico Recente", textPreferences, actionLabel = "LIMPAR", onAction = { showClearHistoryDialog = true })
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            itemsIndexed(history.take(10), key = { _, it -> it.code }) { index, product ->
                                MiniProductCard(product, viewModel, index, appTheme, textPreferences)
                            }
                        }
                    }
                }

                if (homeSettings.showFavorites && favorites.isNotEmpty()) {
                    item {
                        SectionHeader("Meus Favoritos", textPreferences)
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            itemsIndexed(favorites, key = { _, it -> it.code }) { index, product ->
                                MiniProductCard(product, viewModel, index, appTheme, textPreferences)
                            }
                        }
                    }
                }

                if (!hasVisibleHomeSection) {
                    item {
                        Text(
                            "Nenhuma seção da Home está visível no momento.",
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text("Limpar histórico?") },
            text = { Text("Isso remove o histórico recente deste aparelho.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearHistory()
                    showClearHistoryDialog = false
                }) { Text("Limpar") }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) { Text("Cancelar") }
            }
        )
    }

    if (showProductSearchSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showProductSearchSheet = false },
            sheetState = sheetState
        ) {
            ProductSearchSheet(
                products = searchResults,
                query = sheetQuery,
                onQueryChange = {
                    sheetQuery = it
                    viewModel.updateSearchQuery(it)
                },
                onProductSelected = {
                    viewModel.onProductSearched(it)
                    showProductSearchSheet = false
                },
                viewModel = viewModel,
                appTheme = appTheme,
                textPreferences = textPreferences
            )
        }
    }

    if (showMostUsedSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showMostUsedSheet = false },
            sheetState = sheetState
        ) {
            ProductSearchSheet(
                products = mostUsed,
                query = "",
                onQueryChange = {},
                onProductSelected = {
                    viewModel.onProductSearched(it)
                    showMostUsedSheet = false
                },
                viewModel = viewModel,
                appTheme = appTheme,
                textPreferences = textPreferences,
                showSearch = false
            )
        }
    }

    selectedCategory?.let { category ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { selectedCategory = null },
            sheetState = sheetState
        ) {
            CategoryProductsSheet(
                category = category,
                viewModel = viewModel,
                appTheme = appTheme,
                textPreferences = textPreferences,
                onDismiss = { selectedCategory = null }
            )
        }
    }

    if (showNotificationsSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showNotificationsSheet = false },
            sheetState = sheetState
        ) {
            NotificationHistorySheet(
                notifications = notificationHistory,
                onMarkRead = viewModel::markNotificationRead,
                onMarkAllRead = viewModel::markAllNotificationsRead,
                onNotificationClick = { notification ->
                    val product = allProducts.find { p -> notification.body.contains(p.name, ignoreCase = true) }
                    if (product != null) {
                        selectedNotificationProduct = product
                        showNotificationsSheet = false
                    }
                }
            )
        }
    }

    selectedNotificationProduct?.let { product ->
        ProductBarcodeDialog(
            product = product,
            onDismiss = { selectedNotificationProduct = null },
            onFavoriteToggle = { viewModel.toggleFavorite(product) }
        )
    }
}

@Composable
fun ThemeBanner(appTheme: String, backgroundUrl: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val fallbackRes = when (appTheme) {
        "gold" -> R.drawable.theme_gold
        "green" -> R.drawable.theme_green
        "blue" -> R.drawable.theme_blue
        "orange" -> R.drawable.theme_orange
        "red" -> R.drawable.theme_red
        else -> R.drawable.theme_multicolor_header
    }
    if (!backgroundUrl.isNullOrBlank()) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(backgroundUrl)
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        Image(
            painter = painterResource(fallbackRes),
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
private fun SearchEmptyState(onClear: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(12.dp))
        Text("Nenhum produto encontrado", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text("Tente outro nome ou código.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(12.dp))
        TextButton(onClick = onClear) { Text("Limpar pesquisa") }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    textPreferences: HomeTextPreferences,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        StylizedText(
            text = title,
            baseStyle = MaterialTheme.typography.titleLarge,
            boldOutline = textPreferences.boldOutline,
            uppercaseBold = textPreferences.uppercaseBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
private fun CategorySection(
    viewModel: MainViewModel,
    appTheme: String,
    textPreferences: HomeTextPreferences,
    categories: List<String>,
    onCategoryClick: (String) -> Unit
) {
    Column {
        SectionHeader("Categorias", textPreferences)
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(categories) { category ->
                CategoryChip(category = category, appTheme = appTheme, onClick = { onCategoryClick(category) })
            }
        }
    }
}

@Composable
private fun CategoryChip(category: String, appTheme: String, onClick: () -> Unit) {
    val icon = when {
        category.contains("hort", ignoreCase = true) -> Icons.Default.Eco
        category.contains("açou", ignoreCase = true) || category.contains("carne", ignoreCase = true) -> Icons.Default.Restaurant
        category.contains("pad", ignoreCase = true) -> Icons.Default.BakeryDining
        category.contains("limp", ignoreCase = true) -> Icons.Default.Sanitizer
        category.contains("lav", ignoreCase = true) -> Icons.Default.LocalLaundryService
        category.contains("peix", ignoreCase = true) -> Icons.Default.SetMeal
        else -> Icons.Default.NewReleases
    }
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.height(6.dp))
            Text(category, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun MiniProductCard(
    product: Product,
    viewModel: MainViewModel,
    index: Int,
    appTheme: String,
    textPreferences: HomeTextPreferences
) {
    Card(
        onClick = { viewModel.onProductSearched(product) },
        modifier = Modifier.width(180.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            if (!product.imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = product.imageUrl,
                    contentDescription = product.name,
                    modifier = Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
            Text(product.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(4.dp))
            Text(product.code, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ProductCard(
    product: Product,
    viewModel: MainViewModel,
    index: Int,
    appTheme: String,
    textPreferences: HomeTextPreferences
) {
    val dynColors = getDynamicThemeColor(index, appTheme, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, dynColors.first),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!product.imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = product.imageUrl,
                    contentDescription = product.name,
                    modifier = Modifier.size(72.dp).clip(RoundedCornerShape(14.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(14.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(product.name, style = MaterialTheme.typography.titleMedium)
                Text("Código: ${product.code}", style = MaterialTheme.typography.bodyMedium)
                Text(product.category, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { viewModel.toggleFavorite(product) }) {
                Icon(
                    if (product.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorito",
                    tint = if (product.isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun ProductSearchSheet(
    products: List<Product>,
    query: String,
    onQueryChange: (String) -> Unit,
    onProductSelected: (Product) -> Unit,
    viewModel: MainViewModel,
    appTheme: String,
    textPreferences: HomeTextPreferences,
    showSearch: Boolean = true
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        if (showSearch) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                label = { Text("Pesquisar") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.heightIn(max = 520.dp)
        ) {
            itemsIndexed(products, key = { _, it -> it.code }) { index, product ->
                Card(onClick = { onProductSelected(product) }, modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(product.name, style = MaterialTheme.typography.titleSmall)
                            Text("${product.code} • ${product.category}", style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { viewModel.toggleFavorite(product) }) {
                            Icon(if (product.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = null)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryProductsSheet(
    category: String,
    viewModel: MainViewModel,
    appTheme: String,
    textPreferences: HomeTextPreferences,
    onDismiss: () -> Unit
) {
    val products by viewModel.getProductsByCategory(category).collectAsState(initial = emptyList())
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        Text(category, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.heightIn(max = 540.dp)
        ) {
            itemsIndexed(products, key = { _, it -> it.code }) { index, product ->
                Card(onClick = {
                    viewModel.onProductSearched(product)
                    onDismiss()
                }, modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(product.name, style = MaterialTheme.typography.titleSmall)
                            Text(product.code, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationHistorySheet(
    notifications: List<AppNotification>,
    onMarkRead: (Long) -> Unit,
    onMarkAllRead: () -> Unit,
    onNotificationClick: (AppNotification) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Notificações", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = onMarkAllRead) { Text("Marcar todas") }
        }
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.heightIn(max = 520.dp)
        ) {
            items(notifications, key = { it.id }) { notification ->
                Card(
                    onClick = {
                        onMarkRead(notification.id)
                        onNotificationClick(notification)
                    },
                    colors = CardDefaults.cardColors(
                        containerColor = if (notification.read) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(notification.title, style = MaterialTheme.typography.titleSmall)
                        Text(notification.body, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
