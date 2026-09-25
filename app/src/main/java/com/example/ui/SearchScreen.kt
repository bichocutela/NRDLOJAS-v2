package com.example.ui
import androidx.compose.ui.composed
import androidx.compose.ui.composed
import androidx.compose.ui.layout.ContentScale

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.ui.theme.getDynamicThemeColor
import com.example.ui.theme.LocalGlassSoftStyle
import com.example.ui.theme.LocalExpressiveStyle
import com.example.ui.theme.glassSoftShadow
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.graphics.drawscope.Stroke
import android.os.Vibrator
import android.content.Context
import android.os.VibrationEffect
import android.os.Build
import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import java.text.Normalizer
import java.io.File
import java.io.FileOutputStream
import androidx.core.content.FileProvider

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
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.ui.graphics.FilterQuality
import com.google.zxing.EncodeHintType
import java.util.EnumMap
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ImageBitmap


data class HomeTextPreferences(
    val boldOutline: Boolean = false,
    val uppercaseBold: Boolean = false,
    val largeText: Boolean = false
)

@Composable
fun rememberHomeTextPreferences(userPreferences: com.example.data.UserPreferences): HomeTextPreferences {
    val boldOutline by userPreferences.boldOutline.collectAsStateWithLifecycle(initialValue = false)
    val uppercaseBold by userPreferences.uppercaseBold.collectAsStateWithLifecycle(initialValue = false)
    val largeText by userPreferences.largeText.collectAsStateWithLifecycle(initialValue = false)
    return HomeTextPreferences(boldOutline = boldOutline, uppercaseBold = uppercaseBold, largeText = largeText)
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

data class GlassVisualStyle(
    val enabled: Boolean,
    val alpha: Float,
    val fill: Color,
    val border: Color,
    val highlight: Color
)

@Composable
fun rememberGlassVisualStyle(): GlassVisualStyle {
    val style = LocalGlassSoftStyle.current
    return GlassVisualStyle(
        enabled = style.enabled,
        alpha = style.surfaceAlpha,
        fill = style.surfaceBase,
        border = style.borderColor,
        highlight = style.accent.copy(alpha = if (style.type == "crystal") 0.58f else 0.42f)
    )
}


@Composable
private fun homeDynamicColors(
    index: Int,
    appTheme: String,
    defaultColor: Color,
    defaultOnColor: Color
): Pair<Color, Color> {
    if (appTheme != "expressive") {
        return getDynamicThemeColor(index, appTheme, defaultColor, defaultOnColor)
    }

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val palette = if (isDark) {
        listOf(
            Color(0xFF64151A) to Color(0xFFFFDADB),
            Color(0xFF123D66) to Color(0xFFD7E9FF),
            Color(0xFF164A23) to Color(0xFFD1F8D2),
            Color(0xFF5D3510) to Color(0xFFFFDDBB),
            Color(0xFF59470A) to Color(0xFFFFE9A8)
        )
    } else {
        listOf(
            Color(0xFFFFDADB) to Color(0xFF3B0710),
            Color(0xFFD9E9FF) to Color(0xFF082E55),
            Color(0xFFD8F2D8) to Color(0xFF103B16),
            Color(0xFFFFE0C2) to Color(0xFF5B2D00),
            Color(0xFFFFE9A8) to Color(0xFF473800)
        )
    }
    return palette[index % palette.size]
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SearchScreen(
    viewModel: MainViewModel,
    notificationProductCode: String? = null,
    onOpenDrawer: () -> Unit = {},
    canQuickEditBanner: Boolean = false,
    onQuickEditBanner: (String) -> Unit = {}
) {
    val bannerImageUri by viewModel.userPreferences.bannerImageUri.collectAsState(initial = null)
    val localAppTheme by viewModel.userPreferences.appTheme.collectAsStateWithLifecycle(initialValue = "multicolor")
    val remoteAppearance by FirebaseService.observeAppearanceSettings()
        .collectAsStateWithLifecycle(initialValue = AppearanceSettings())
    val glassStyle = LocalGlassSoftStyle.current
    val expressiveStyle = LocalExpressiveStyle.current
    val isExpressiveTheme = expressiveStyle.enabled
    val isGlassTheme = glassStyle.enabled
    val isStandaloneGlassTheme = isGlassTheme && !expressiveStyle.enabled
    val appTheme = if (isStandaloneGlassTheme) "glass" else localAppTheme
    val glassActionBrush = remember(glassStyle.accent, glassStyle.secondaryAccent) {
        Brush.verticalGradient(
            listOf(glassStyle.accent, glassStyle.secondaryAccent)
        )
    }
    val normalizedTheme = remember(localAppTheme) {
        when (localAppTheme.trim().lowercase()) {
            "multicolor", "glass", "expressive" -> "multicolor"
            "gold" -> "gold"
            "green" -> "green"
            "blue" -> "blue"
            "orange" -> "orange"
            else -> "red"
        }
    }
    val backgroundThemeKey = remember(localAppTheme, isStandaloneGlassTheme) {
        if (isStandaloneGlassTheme) {
            "glass"
        } else {
            when (localAppTheme.trim().lowercase()) {
                "multicolor", "red", "gold", "green", "blue", "orange", "expressive" -> localAppTheme.trim().lowercase()
                else -> "multicolor"
            }
        }
    }
    val activeThemeBackground = remoteAppearance.activeBackgroundFor(backgroundThemeKey)


    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val textPreferences = rememberHomeTextPreferences(viewModel.userPreferences)
    val vibrateOnFound by viewModel.userPreferences.vibrateOnFound.collectAsStateWithLifecycle(initialValue = true)
    val mostUsed by viewModel.mostUsed.collectAsStateWithLifecycle()
    val latestAdded by viewModel.latestAdded.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val latestProductLocal by viewModel.latestProductLocal.collectAsStateWithLifecycle()
    val latestProductFirebase by viewModel.latestProduct.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val newProductsCount by viewModel.newProductsCount.collectAsStateWithLifecycle()
    val homeSettings by viewModel.homeSettings.collectAsStateWithLifecycle()
    val activeCategoryNames by viewModel.activeCategoryNames.collectAsStateWithLifecycle()
    val allProducts by viewModel.allProducts.collectAsStateWithLifecycle()

    var showProductSearchSheet by remember { mutableStateOf(false) }
    var showMostUsedSheet by remember { mutableStateOf(false) }
    var selectedMostUsedProduct by remember { mutableStateOf<Product?>(null) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var showNotificationsSheet by remember { mutableStateOf(false) }
    var selectedNotificationProduct by remember { mutableStateOf<Product?>(null) }
    var handledNotificationProductCode by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(notificationProductCode, allProducts) {
        val code = notificationProductCode?.trim().orEmpty()
        if (code.isBlank() || handledNotificationProductCode == code) return@LaunchedEffect
        val resolvedProduct = allProducts.firstOrNull { it.code.trim() == code }
        if (resolvedProduct != null) {
            handledNotificationProductCode = code
            selectedNotificationProduct = resolvedProduct
            viewModel.onProductSearched(resolvedProduct)
        }
    }

    LaunchedEffect(allProducts) {
        selectedMostUsedProduct = selectedMostUsedProduct?.let { selected ->
            allProducts.firstOrNull { it.code == selected.code } ?: selected
        }
        selectedNotificationProduct = selectedNotificationProduct?.let { selected ->
            allProducts.firstOrNull { it.code == selected.code } ?: selected
        }
    }
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
                .then(
                    if (isGlassTheme) Modifier.background(Color.Transparent)
                    else Modifier.background(MaterialTheme.colorScheme.background)
                )
        ) {
        val screenProfile = rememberNrdScreenProfile()
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val headerHeight = maxWidth / 3f
            val headerShape = if (isExpressiveTheme) {
                RoundedCornerShape(bottomStart = 40.dp, bottomEnd = 40.dp)
            } else {
                RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(headerHeight)
                    .glassSoftShadow(headerShape)
                    .clip(headerShape)
                    .background(
                        if (isGlassTheme) {
                            Color.Transparent
                        } else Color.Transparent
                    )
            ) {
                MaskedThemeBanner(
                    appTheme = if (isStandaloneGlassTheme) "glass" else normalizedTheme,
                    backgroundUrl = activeThemeBackground?.url,
                    imageScale = activeThemeBackground?.imageScale ?: 1f,
                    imageOffsetX = activeThemeBackground?.imageOffsetX ?: 0f,
                    imageOffsetY = activeThemeBackground?.imageOffsetY ?: 0f,
                    imageStretchX = activeThemeBackground?.imageStretchX ?: 1f,
                    imageStretchY = activeThemeBackground?.imageStretchY ?: 1f,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (canQuickEditBanner && activeThemeBackground != null) {
                                Modifier.combinedClickable(
                                    onClick = {},
                                    onDoubleClick = {
                                        onQuickEditBanner(backgroundThemeKey)
                                    }
                                )
                            } else {
                                Modifier
                            }
                        )
                )

                if (homeSettings.showDrawerIcon) {
                    IconButton(
                        onClick = {
                            viewModel.clearNewProductsCount()
                            onOpenDrawer()
                        },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(top = 48.dp, start = 8.dp)
                            .then(
                                if (isGlassTheme) Modifier
                                    .glassSoftShadow(CircleShape, 4.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                    .border(1.dp, glassStyle.borderColor, CircleShape)
                                else if (isExpressiveTheme) Modifier
                                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                                else Modifier.background(Color.Transparent)
                            )
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
                                tint = if (isExpressiveTheme && !isGlassTheme) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
                            )
                        }
                    }
                }
                if (homeSettings.showNotificationIcon && unreadNotifications > 0) {
                    IconButton(
                        onClick = { showNotificationsSheet = true },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 48.dp, end = 8.dp)
                            .then(
                                if (isGlassTheme) Modifier
                                    .glassSoftShadow(CircleShape, 4.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                    .border(1.dp, glassStyle.borderColor, CircleShape)
                                else if (isExpressiveTheme) Modifier
                                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                                else Modifier
                            )
                    ) {
                        BadgedBox(
                            badge = { Badge { Text(unreadNotifications.toString()) } }
                        ) {
                            Icon(
                                Icons.Default.Notifications,
                                contentDescription = "Notificações",
                                tint = if (isExpressiveTheme && !isGlassTheme) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(if (screenProfile.veryCompact) 8.dp else 16.dp))

            Column(
            modifier = Modifier.padding(horizontal = screenProfile.horizontalPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val searchFieldShape = if (isExpressiveTheme) {
                RoundedCornerShape(30.dp)
            } else {
                RoundedCornerShape(32.dp)
            }
            TextField(
                value = searchQuery,
                onValueChange = viewModel::updateSearchQuery,
                singleLine = true,
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
                    .heightIn(min = if (isExpressiveTheme) 60.dp else 56.dp)
                    .glassSoftShadow(searchFieldShape)
                    .clip(searchFieldShape)
                    .border(
                        1.dp,
                        when {
                            isGlassTheme -> glassStyle.borderColor
                            isExpressiveTheme -> MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                            else -> MaterialTheme.colorScheme.outline
                        },
                        searchFieldShape
                    ),
                shape = searchFieldShape,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = when {
                        isGlassTheme -> MaterialTheme.colorScheme.surface
                        isExpressiveTheme -> MaterialTheme.colorScheme.surfaceContainerHigh
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    unfocusedContainerColor = when {
                        isGlassTheme -> MaterialTheme.colorScheme.surface
                        isExpressiveTheme -> MaterialTheme.colorScheme.surfaceContainerHigh
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    disabledContainerColor = when {
                        isGlassTheme -> MaterialTheme.colorScheme.surface
                        isExpressiveTheme -> MaterialTheme.colorScheme.surfaceContainerHigh
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                )
            )
            
            Spacer(modifier = Modifier.height(if (screenProfile.veryCompact) 8.dp else 16.dp))
            
            val searchButtonShape = if (isExpressiveTheme) {
                RoundedCornerShape(30.dp)
            } else {
                RoundedCornerShape(28.dp)
            }
            val openProductSearch = {
                keyboardController?.hide()
                sheetQuery = searchQuery
                showProductSearchSheet = true
            }
            if (isGlassTheme) {
                Surface(
                    onClick = openProductSearch,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isExpressiveTheme) 60.dp else 56.dp)
                        .glassSoftShadow(searchButtonShape),
                    shape = searchButtonShape,
                    color = Color.Transparent,
                    contentColor = glassStyle.onAccent,
                    border = BorderStroke(1.dp, glassStyle.borderColor)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(glassActionBrush),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pesquisar", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            } else {
                Button(
                    onClick = openProductSearch,
                    shape = searchButtonShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isExpressiveTheme) 60.dp else 56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Pesquisar", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(if (screenProfile.veryCompact) 8.dp else 16.dp))

        if (searchQuery.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(horizontal = screenProfile.horizontalPadding, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (searchResults.isEmpty()) {
                    item { SearchEmptyState(onClear = { viewModel.updateSearchQuery("") }) }
                } else {
                    itemsIndexed(searchResults, key = { _, it -> it.code }) { index, product ->
                        ProductCard(product, viewModel, index, appTheme, textPreferences)
                    }
                }
            }
        } else {
            val hasVisibleHomeSection = homeSettings.showCategories ||
                (homeSettings.showMostUsed && mostUsed.isNotEmpty()) ||
                latestAdded.isNotEmpty() ||
                (homeSettings.showHistory && history.isNotEmpty()) ||
                (homeSettings.showFavorites && favorites.isNotEmpty())
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
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
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(mostUsed, key = { _, it -> it.code }) { index, product ->
                                MiniProductCard(
                                    product = product,
                                    viewModel = viewModel,
                                    index = index,
                                    appTheme = appTheme,
                                    textPreferences = textPreferences,
                                    onProductClick = { selected ->
                                        viewModel.onProductSearched(selected)
                                        selectedMostUsedProduct = selected
                                    }
                                )
                            }
                        }
                    }
                }

                if (latestAdded.isNotEmpty()) {
                    item {
                        SectionHeader("Últimos Adicionados", textPreferences)
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            latestAdded.forEachIndexed { index, product ->
                                HistoryItem(product, viewModel, index, appTheme, textPreferences)
                            }
                        }
                    }
                }

                if (homeSettings.showHistory && history.isNotEmpty()) {
                    item {
                        SectionHeader("Histórico Recente", textPreferences, actionLabel = "Limpar Histórico", onAction = { showClearHistoryDialog = true })
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            history.take(5).forEachIndexed { index, product ->
                                HistoryItem(product, viewModel, index, appTheme, textPreferences)
                            }
                        }
                    }
                }

                if (homeSettings.showFavorites && favorites.isNotEmpty()) {
                    item {
                        SectionHeader("Meus Favoritos", textPreferences)
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            favorites.forEachIndexed { index, product ->
                                ProductCard(product, viewModel, index, appTheme, textPreferences)
                            }
                        }
                    }
                }

                if (!hasVisibleHomeSection) {
                    item {
                        Text(
                            "Nenhuma seção da Home está disponível no momento.",
                            modifier = Modifier.padding(horizontal = 16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        }

        val onboardingShown by viewModel.userPreferences.onboardingShown.collectAsState(initial = true)
        if (!onboardingShown) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.8f))
                    .clickable { viewModel.setOnboardingShown() }
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Use a barra de busca para encontrar produtos rapidamente pelo nome ou código.",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(48.dp))
                    Icon(
                        imageVector = Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Toque no coração para favoritar os produtos que você mais usa.",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(48.dp))
                    Button(
                        onClick = { viewModel.setOnboardingShown() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Entendi, vamos lá!")
                    }
                }
            }
        }

        if (showProductSearchSheet) {
            val sheetResultsFlow = remember(sheetQuery) { viewModel.searchProducts(sheetQuery) }
            val sheetResults by sheetResultsFlow.collectAsState(initial = emptyList())
            ModalBottomSheet(
                onDismissRequest = { showProductSearchSheet = false },
                containerColor = if (isGlassTheme) MaterialTheme.colorScheme.surfaceContainerHigh
                else MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Pesquisar Produtos", style = MaterialTheme.typography.headlineSmall)
                    OutlinedTextField(
                        value = sheetQuery,
                        onValueChange = { sheetQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Pesquisar") }
                    )
                    val products = if (sheetQuery.isBlank()) {
                        viewModel.allProducts.value.sortedByDescending { it.id }.take(10)
                    } else sheetResults
                    Text(
                        if (sheetQuery.isBlank()) "Adicionados recentemente" else "Resultados",
                        style = MaterialTheme.typography.titleMedium
                    )
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(products, key = { _, item -> item.code }) { index, product ->
                            ProductCard(product, viewModel, index, appTheme, textPreferences)
                        }
                    }
                }
            }
        }

        if (showMostUsedSheet) {
            ModalBottomSheet(
                onDismissRequest = { showMostUsedSheet = false },
                containerColor = if (isGlassTheme) MaterialTheme.colorScheme.surfaceContainerHigh
                else MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Mais Utilizados", style = MaterialTheme.typography.headlineSmall)
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(mostUsed, key = { _, item -> item.code }) { index, product ->
                            ProductCard(
                                product = product,
                                viewModel = viewModel,
                                index = index,
                                appTheme = appTheme,
                                textPreferences = textPreferences,
                                onProductClick = { selected ->
                                    viewModel.onProductSearched(selected)
                                    selectedMostUsedProduct = selected
                                }
                            )
                        }
                    }
                }
            }
        }

        selectedCategory?.let { category ->
                CategoryProductsSheet(
                category = category,
                viewModel = viewModel,
                appTheme = appTheme,
                textPreferences = textPreferences,
                onDismiss = { selectedCategory = null }
            )
        }

        if (showNotificationsSheet) {
            ModalBottomSheet(
                onDismissRequest = { showNotificationsSheet = false },
                containerColor = if (isGlassTheme) MaterialTheme.colorScheme.surfaceContainerHigh
                else MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Notificações", style = MaterialTheme.typography.headlineSmall)
                        TextButton(onClick = { viewModel.markAllNotificationsRead() }) { Text("Marcar todas como lidas") }
                    }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(notificationHistory, key = { _, item -> item.id }) { _, notification ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .glassSoftShadow(MaterialTheme.shapes.medium)
                                    .clickable {
                                    viewModel.markNotificationRead(notification.id)
                                    val directCode = notification.productCode?.trim().orEmpty()
                                    val notificationTarget = "${notification.title} ${notification.body}".trim()
                                    val normalizedTarget = normalizeNotificationText(notificationTarget)
                                    val codesInText = Regex("\\b\\d{4,14}\\b")
                                        .findAll(notificationTarget)
                                        .map { it.value }
                                        .toSet()
                                    val resolvedProduct = viewModel.allProducts.value.firstOrNull {
                                        directCode.isNotBlank() && it.code.trim() == directCode
                                    } ?: viewModel.allProducts.value.firstOrNull {
                                        it.code.trim() in codesInText
                                    } ?: viewModel.allProducts.value.firstOrNull {
                                        val normalizedName = normalizeNotificationText(it.name)
                                        normalizedName.isNotBlank() && normalizedTarget.contains(normalizedName)
                                    }
                                    selectedNotificationProduct = resolvedProduct
                                    resolvedProduct?.let(viewModel::onProductSearched)
                                    showNotificationsSheet = false
                                },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (notification.read) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(notification.title, fontWeight = FontWeight.Bold)
                                    Text(notification.body, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        when (notification.type) {
                                            "CODE_CHANGED" -> "CÓDIGO ALTERADO"
                                            "BENEFIT_RELEASED" -> "CONVÊNIO LIBERADO"
                                            "BENEFIT_PURCHASE" -> "COMPRA NO CONVÊNIO"
                                            "HOURS_UPDATED" -> "BANCO DE HORAS"
                                            else -> "NOVO PRODUTO"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        selectedNotificationProduct?.let { product ->
            ProductBarcodeDialog(
                product = product,
                onDismiss = { selectedNotificationProduct = null },
                highlightedFromNotification = true,
                onProductUpdated = { updated ->
                    selectedNotificationProduct = updated
                    viewModel.updateProductLocally(updated)
                },
                onProductCodeChanged = { old, newCode ->
                    viewModel.updateProductSuspend(old, old.copy(code = newCode))
                },
                onProductDeleted = { target ->
                    viewModel.deleteProductSuspend(target)
                }
            )
        }

        selectedMostUsedProduct?.let { product ->
            ProductBarcodeDialog(
                product = product,
                onDismiss = { selectedMostUsedProduct = null },
                onProductUpdated = { updated ->
                    selectedMostUsedProduct = updated
                    viewModel.updateProductLocally(updated)
                },
                onProductCodeChanged = { old, newCode ->
                    viewModel.updateProductSuspend(old, old.copy(code = newCode))
                },
                onProductDeleted = { target ->
                    viewModel.deleteProductSuspend(target)
                }
            )
        }

        if (showClearHistoryDialog) {
            AlertDialog(
                onDismissRequest = { showClearHistoryDialog = false },
                title = { Text("Limpar Histórico") },
                text = { Text("Deseja limpar somente o histórico recente de produtos?") },
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

    }

@Composable
fun SectionHeader(
    title: String,
    textPreferences: HomeTextPreferences = HomeTextPreferences(),
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val expressive = LocalExpressiveStyle.current.enabled
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = if (expressive) 5.dp else 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        StylizedText(
            text = title,
            baseStyle = if (expressive) {
                MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.4.sp
                )
            } else {
                MaterialTheme.typography.labelMedium
            },
            boldOutline = textPreferences.boldOutline,
            uppercaseBold = textPreferences.uppercaseBold,
            color = if (expressive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (actionLabel != null && onAction != null) {
            TextButton(
                onClick = onAction,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.textButtonColors(
                    containerColor = if (expressive) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    contentColor = if (expressive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
                )
            ) {
                StylizedText(
                    text = actionLabel,
                    baseStyle = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = if (expressive) FontWeight.Bold else FontWeight.Normal
                    ),
                    boldOutline = textPreferences.boldOutline,
                    uppercaseBold = textPreferences.uppercaseBold,
                    color = if (expressive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun SearchEmptyState(onClear: () -> Unit) {
    val expressive = LocalExpressiveStyle.current.enabled
    val shape = if (expressive) RoundedCornerShape(28.dp) else RoundedCornerShape(20.dp)
    Card(
        modifier = Modifier.fillMaxWidth().glassSoftShadow(shape),
        colors = CardDefaults.cardColors(
            containerColor = if (expressive) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = shape
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text("Nenhum produto encontrado", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Tente uma parte do nome ou confira o código.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onClear) { Text("Limpar busca") }
        }
    }
}

@Composable
fun CategorySection(
    viewModel: MainViewModel,
    appTheme: String,
    textPreferences: HomeTextPreferences = HomeTextPreferences(),
    categories: List<String> = ProductStandards.officialCategories,
    onCategoryClick: (String) -> Unit = {}
) {
    val glass = rememberGlassVisualStyle()
    val expressive = LocalExpressiveStyle.current.enabled
    val categoryColors = listOf(
        MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer,
        MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer,
        MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer,
        MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    )

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(bottom = 2.dp)
    ) {
        itemsIndexed(categories) { index, category ->
            val colors = categoryColors[index % categoryColors.size]
            val dynamicColors = homeDynamicColors(index, appTheme, colors.first, colors.second)
            val categoryGlassFill = if (glass.enabled) glass.fill.copy(alpha = glass.alpha)
            else dynamicColors.first
            val categoryGlassBorder = when {
                glass.enabled && expressive -> dynamicColors.first.copy(alpha = 0.72f)
                glass.enabled -> glass.border
                else -> Color.Transparent
            }
            val categoryShape = if (expressive) RoundedCornerShape(22.dp) else RoundedCornerShape(16.dp)

            Box(

                modifier = Modifier
                    .glassSoftShadow(categoryShape)
                    .clip(categoryShape)
                    .background(categoryGlassFill)
                    .border(1.dp, categoryGlassBorder, categoryShape)
                    .clickable { onCategoryClick(category) }
                    .padding(
                        horizontal = if (expressive) 18.dp else 16.dp,
                        vertical = if (expressive) 11.dp else 10.dp
                    ),
                contentAlignment = Alignment.Center
            ) {
                StylizedText(
                    text = category,
                    baseStyle = if (expressive) {
                        MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold)
                    } else {
                        MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    },
                    boldOutline = textPreferences.boldOutline,
                    uppercaseBold = true,
                    color = when {
                        glass.enabled && expressive -> dynamicColors.second
                        glass.enabled -> MaterialTheme.colorScheme.onSurface
                        else -> dynamicColors.second
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryProductsSheet(
    category: String,
    viewModel: MainViewModel,
    appTheme: String,
    textPreferences: HomeTextPreferences = HomeTextPreferences(),
    onDismiss: () -> Unit
) {
    val glass = rememberGlassVisualStyle()
    var query by remember { mutableStateOf("") }
    val productsFlow = remember(category, query) { viewModel.searchProductsByCategory(category, query) }
    val products by productsFlow.collectAsState(initial = emptyList())
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = if (glass.enabled) glass.fill.copy(alpha = glass.alpha) else MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StylizedText(
                text = category,
                baseStyle = MaterialTheme.typography.headlineSmall,
                boldOutline = textPreferences.boldOutline,
                uppercaseBold = true,
                color = MaterialTheme.colorScheme.onSurface
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Pesquisar em $category") }
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(products, key = { _, item -> item.code }) { index, product ->
                    ProductCard(product, viewModel, index, appTheme, textPreferences)
                }
            }
        }
    }
}

private fun normalizeNotificationText(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
        .lowercase()
        .trim()

@Composable
fun ProductCard(
    product: Product,
    viewModel: MainViewModel,
    index: Int = 0,
    appTheme: String = "multicolor",
    textPreferences: HomeTextPreferences = HomeTextPreferences(),
    onProductClick: ((Product) -> Unit)? = null
) {
    val glass = rememberGlassVisualStyle()
    val expressive = LocalExpressiveStyle.current.enabled
    val cardShape = if (expressive) RoundedCornerShape(30.dp) else RoundedCornerShape(24.dp)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val shareLayer = rememberGraphicsLayer()
    val cardAccent = homeDynamicColors(
        index,
        appTheme,
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.onPrimaryContainer
    )
    val shareAccentColor = cardAccent.first.toArgb()
    val shareCodeColor = MaterialTheme.colorScheme.primary.toArgb()
    var showDialog by remember(product.code) { mutableStateOf(false) }
    if (showDialog) {
        ProductBarcodeDialog(
            product = product,
            onDismiss = { showDialog = false },
            onProductUpdated = { updated -> viewModel.updateProductLocally(updated) },
            onProductCodeChanged = { old, newCode ->
                viewModel.updateProductSuspend(old, old.copy(code = newCode))
            },
            onProductDeleted = { target ->
                viewModel.deleteProductSuspend(target)
            }
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassSoftShadow(cardShape)
            .clip(cardShape)
            .background(
                when {
                    glass.enabled -> glass.fill.copy(alpha = glass.alpha)
                    expressive -> MaterialTheme.colorScheme.surfaceContainerLow
                    else -> MaterialTheme.colorScheme.surface
                }
            )
            .border(
                1.dp,
                when {
                    glass.enabled -> glass.border
                    expressive -> MaterialTheme.colorScheme.outline.copy(alpha = 0.20f)
                    else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                },
                cardShape
            )
            .drawWithContent {
                shareLayer.record {
                    this@drawWithContent.drawContent()
                }
                drawLayer(shareLayer)
            }
            .vibrateClickable(
                viewModel = viewModel,
                onLongClick = {
                    scope.launch {
                        val copied = copyHomeProductCardToClipboard(
                            context = context,
                            layer = shareLayer,
                            product = product,
                            accentColor = shareAccentColor,
                            codeBackgroundColor = shareCodeColor
                        )
                        Toast.makeText(
                            context,
                            if (copied) "Copiado na Área de Transferência"
                            else "Não foi possível copiar o quadradinho.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            ) {
                if (onProductClick != null) {
                    onProductClick(product)
                } else {
                    viewModel.onProductSearched(product)
                    showDialog = true
                }
            }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (product.imageUrl != null) {
            AsyncImage(
                model = product.imageUrl,
                contentDescription = product.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
            )
        } else {
            val dynColors = cardAccent
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(if (expressive) RoundedCornerShape(16.dp) else CircleShape)
                    .background(dynColors.first),
                contentAlignment = Alignment.Center
            ) {
                StylizedText(
                    text = product.name.take(1),
                    baseStyle = MaterialTheme.typography.titleMedium,
                    boldOutline = textPreferences.boldOutline,
                    uppercaseBold = true,
                    color = dynColors.second
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            StylizedText(
                text = product.name,
                baseStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                boldOutline = textPreferences.boldOutline,
                uppercaseBold = textPreferences.uppercaseBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = getCategoryIcon(product.category),
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.width(4.dp))
                StylizedText(
                    text = product.category,
                    baseStyle = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                    boldOutline = textPreferences.boldOutline,
                    uppercaseBold = true,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Box(
            modifier = Modifier
                .clip(if (expressive) RoundedCornerShape(20.dp) else RoundedCornerShape(16.dp))
                .background(if (expressive) cardAccent.first else MaterialTheme.colorScheme.primaryContainer)
                .padding(
                    horizontal = if (expressive) 18.dp else 16.dp,
                    vertical = if (expressive) 10.dp else 8.dp
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = product.code,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, fontSize = 16.sp),
                    color = if (expressive) cardAccent.second else MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = product.unit.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Black),
                    color = if (expressive) cardAccent.second.copy(alpha = 0.78f) else MaterialTheme.colorScheme.primary
                )
            }
        }

    }
}


private suspend fun copyHomeProductCardToClipboard(
    context: Context,
    layer: androidx.compose.ui.graphics.layer.GraphicsLayer,
    product: Product,
    accentColor: Int,
    codeBackgroundColor: Int
): Boolean = runCatching {
    val captured = layer.toImageBitmap().asAndroidBitmap()
    val width = captured.width.coerceAtLeast(1)
    val height = captured.height.coerceAtLeast(1)
    val density = context.resources.displayMetrics.density
    fun dp(value: Float) = value * density

    val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)

    val neutral = 0xFFE5E5E5.toInt()
    val border = 0xFFC7C7C7.toInt()
    val titleColor = 0xFF202124.toInt()
    val metaColor = 0xFF666666.toInt()

    canvas.drawColor(neutral)

    val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = neutral
        style = Paint.Style.FILL
    }
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = border
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }
    val outer = RectF(dp(1f), dp(1f), width - dp(1f), height - dp(1f))
    val radius = dp(22f)
    canvas.drawRoundRect(outer, radius, radius, cardPaint)
    canvas.drawRoundRect(outer, radius, radius, borderPaint)

    val pad = dp(16f)
    val circleSize = dp(48f).coerceAtMost(height - dp(20f))
    val circleCx = pad + circleSize / 2f
    val circleCy = height / 2f

    val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accentColor }
    canvas.drawCircle(circleCx, circleCy, circleSize / 2f, circlePaint)

    val initialPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        textSize = dp(18f)
    }
    val initialBaseline = circleCy - (initialPaint.ascent() + initialPaint.descent()) / 2f
    canvas.drawText(product.name.take(1).uppercase(), circleCx, initialBaseline, initialPaint)

    val codePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        textSize = dp(16f)
    }
    val codeHorizontal = dp(14f)
    val codeWidth = (codePaint.measureText(product.code) + codeHorizontal * 2f)
        .coerceIn(dp(92f), dp(150f))
    val codeHeight = dp(48f).coerceAtMost(height - dp(20f))
    val codeRight = width - pad
    val codeLeft = codeRight - codeWidth
    val codeTop = (height - codeHeight) / 2f
    val codeRect = RectF(codeLeft, codeTop, codeRight, codeTop + codeHeight)
    val codeBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = codeBackgroundColor }
    canvas.drawRoundRect(codeRect, dp(14f), dp(14f), codeBg)
    val codeBaseline = codeRect.centerY() - (codePaint.ascent() + codePaint.descent()) / 2f
    canvas.drawText(product.code, codeRect.centerX(), codeBaseline, codePaint)

    val textX = circleCx + circleSize / 2f + dp(16f)
    val textRight = codeLeft - dp(14f)
    val maxTextWidth = (textRight - textX).coerceAtLeast(dp(80f))

    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = titleColor
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = dp(17f)
    }
    val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = metaColor
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = dp(11f)
    }

    fun drawWrapped(
        text: String,
        paint: Paint,
        startX: Float,
        startY: Float,
        maxWidth: Float,
        maxLines: Int,
        lineHeight: Float
    ): Float {
        var remaining = text.trim()
        var y = startY
        repeat(maxLines) { lineIndex ->
            if (remaining.isEmpty()) return y
            var count = paint.breakText(remaining, true, maxWidth, null).coerceAtLeast(1)
            if (count < remaining.length) {
                val wordEnd = remaining.lastIndexOf(' ', count - 1)
                if (wordEnd > 0) count = wordEnd
            }
            var line = remaining.take(count).trim()
            remaining = remaining.drop(count).trim()
            if (lineIndex == maxLines - 1 && remaining.isNotEmpty()) {
                while (line.isNotEmpty() && paint.measureText("${line}…") > maxWidth) {
                    line = line.dropLast(1)
                }
                line += "…"
                remaining = ""
            }
            canvas.drawText(line, startX, y, paint)
            y += lineHeight
        }
        return y
    }

    val titleLineHeight = dp(20f)
    val metaLineHeight = dp(15f)
    val titleStart = (height / 2f - dp(10f)).coerceAtLeast(dp(24f))
    val afterTitle = drawWrapped(
        product.name,
        titlePaint,
        textX,
        titleStart,
        maxTextWidth,
        2,
        titleLineHeight
    )
    drawWrapped(
        "${getCategoryIcon(product.category)} ${product.category.uppercase()}",
        metaPaint,
        textX,
        (afterTitle + dp(3f)).coerceAtMost(height - dp(10f)),
        maxTextWidth,
        1,
        metaLineHeight
    )

    val file = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "shared_cards").apply { mkdirs() }
        directory.listFiles()?.forEach { old ->
            if (System.currentTimeMillis() - old.lastModified() > 24 * 60 * 60 * 1000L) old.delete()
        }
        val safeName = product.name
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(48)
            .ifBlank { "produto" }
        File(directory, "nrd-home-${safeName}-${System.currentTimeMillis()}.png").also { target ->
            FileOutputStream(target).use { stream ->
                check(output.compress(Bitmap.CompressFormat.PNG, 100, stream))
            }
        }
    }

    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newUri(context.contentResolver, "Produto NRD", uri))
    true
}.getOrDefault(false)

@Composable
fun MiniProductCard(
    product: Product,
    viewModel: MainViewModel,
    index: Int = 0,
    appTheme: String = "multicolor",
    textPreferences: HomeTextPreferences = HomeTextPreferences(),
    onProductClick: ((Product) -> Unit)? = null
) {
    val glass = rememberGlassVisualStyle()
    val expressive = LocalExpressiveStyle.current.enabled
    val cardShape = if (expressive) RoundedCornerShape(28.dp) else RoundedCornerShape(24.dp)
    val cardAccent = homeDynamicColors(
        index,
        appTheme,
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.onPrimaryContainer
    )
    var showDialog by remember(product.code) { mutableStateOf(false) }
    if (showDialog) {
        ProductBarcodeDialog(
            product = product,
            onDismiss = { showDialog = false },
            onProductUpdated = { updated -> viewModel.updateProductLocally(updated) },
            onProductCodeChanged = { old, newCode ->
                viewModel.updateProductSuspend(old, old.copy(code = newCode))
            },
            onProductDeleted = { target ->
                viewModel.deleteProductSuspend(target)
            }
        )
    }
    Column(
        modifier = Modifier
            .widthIn(min = 144.dp, max = 176.dp)
            .heightIn(min = if (textPreferences.largeText) 168.dp else 132.dp)
            .glassSoftShadow(cardShape)
            .clip(cardShape)
            .background(
                when {
                    glass.enabled -> glass.fill.copy(alpha = glass.alpha)
                    expressive -> MaterialTheme.colorScheme.surfaceContainerLow
                    else -> MaterialTheme.colorScheme.surface
                }
            )
            .border(
                1.dp,
                when {
                    glass.enabled -> glass.border
                    expressive -> MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
                    else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                },
                cardShape
            )
            .vibrateClickable(viewModel) {
                if (onProductClick != null) {
                    onProductClick(product)
                } else {
                    viewModel.onProductSearched(product)
                    showDialog = true
                }
            }
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (product.imageUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(product.imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = product.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                )
            } else {
                val dynColors = cardAccent
                Box(
                    modifier = Modifier
                        .size(if (expressive) 36.dp else 32.dp)
                        .clip(if (expressive) RoundedCornerShape(12.dp) else CircleShape)
                        .background(dynColors.first),
                    contentAlignment = Alignment.Center
                ) {
                    StylizedText(
                        text = product.name.take(1),
                        baseStyle = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                        boldOutline = textPreferences.boldOutline,
                        uppercaseBold = true,
                        color = dynColors.second
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (product.isFavorite) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Favorito",
                        tint = Color.Red,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = product.unit.uppercase(),
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp),
                    color = if (expressive) cardAccent.second else MaterialTheme.colorScheme.primary
                )
            }
        }
        
        Column {
            StylizedText(
                text = product.name,
                baseStyle = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                boldOutline = textPreferences.boldOutline,
                uppercaseBold = textPreferences.uppercaseBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = getCategoryIcon(product.category),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                StylizedText(
                    text = product.category,
                    baseStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                    boldOutline = textPreferences.boldOutline,
                    uppercaseBold = true,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = product.code,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black, fontSize = 16.sp),
                color = if (expressive) cardAccent.second else MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun HistoryItem(
    product: Product,
    viewModel: MainViewModel,
    index: Int = 0,
    appTheme: String = "multicolor",
    textPreferences: HomeTextPreferences = HomeTextPreferences()
) {
    val glass = rememberGlassVisualStyle()
    val expressive = LocalExpressiveStyle.current.enabled
    val itemShape = if (expressive) RoundedCornerShape(24.dp) else RoundedCornerShape(16.dp)
    var showDialog by remember { mutableStateOf(false) }
    if (showDialog) {
        ProductBarcodeDialog(
            product = product,
            onDismiss = { showDialog = false },
            onProductUpdated = { updated -> viewModel.updateProductLocally(updated) },
            onProductCodeChanged = { old, newCode ->
                viewModel.updateProductSuspend(old, old.copy(code = newCode))
            },
            onProductDeleted = { target ->
                viewModel.deleteProductSuspend(target)
            }
        )
    }
    val dynColors = homeDynamicColors(index, appTheme, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassSoftShadow(itemShape)
            .clip(itemShape)
            .background(
                when {
                    glass.enabled -> glass.fill.copy(alpha = glass.alpha)
                    expressive -> MaterialTheme.colorScheme.surfaceContainerLow
                    else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                }
            )
            .border(
                1.dp,
                when {
                    glass.enabled && expressive -> dynColors.first.copy(alpha = 0.72f)
                    glass.enabled -> glass.border
                    else -> dynColors.first
                },
                itemShape
            )
            .vibrateClickable(viewModel) {
                viewModel.onProductSearched(product)
                showDialog = true
            }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (expressive) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(dynColors.first),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "Histórico",
                        tint = dynColors.second,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
            } else {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = "Histórico",
                    tint = dynColors.first,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
            }
            Column {
                StylizedText(
                    text = product.name,
                    baseStyle = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 14.sp,
                        fontWeight = if (expressive) FontWeight.ExtraBold else FontWeight.Normal
                    ),
                    boldOutline = textPreferences.boldOutline,
                    uppercaseBold = textPreferences.uppercaseBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = getCategoryIcon(product.category),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    StylizedText(
                        text = product.category,
                        baseStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                        boldOutline = textPreferences.boldOutline,
                        uppercaseBold = true,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "Código: ${product.code}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = if (expressive) FontWeight.SemiBold else FontWeight.Normal
                    ),
                    color = if (expressive) dynColors.second else MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun NordestaoLogo() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LogoCircle(color = Color(0xFFD32F2F), icon = Icons.Default.Eco)
            LogoCircle(color = Color(0xFF388E3C), icon = Icons.Default.Restaurant)
            LogoCircle(color = Color(0xFFF57C00), icon = Icons.Default.BakeryDining)
            LogoCircle(color = Color(0xFF1976D2), icon = Icons.Default.LocalLaundryService)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.Start) {
            Text(
                text = "supermercado",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    fontSize = 8.sp
                ),
                color = Color.DarkGray
            )
            Text(
                text = "Nordestão",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp
                ),
                color = Color(0xFF424242)
            )
        }
    }
}

@Composable
fun LogoCircle(color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(color = color, shape = CircleShape)
            .border(width = 1.dp, color = Color.White, shape = CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(16.dp)
        )
    }
}

fun getCategoryIcon(category: String): String {
    return when (category.lowercase()) {
        "padaria" -> "🥖"
        "açougue" -> "🥩"
        "hortifruti" -> "🥬"
        "frios" -> "🧀"
        "cafeteria" -> "☕"
        "mercearia" -> "🛒"
        else -> "🏷️"
    }
}

fun generateBarcodeBitmap(data: String, profile: String = "Padrão"): ImageBitmap? {
    try {
        val writer = MultiFormatWriter()
        val hints = java.util.EnumMap<EncodeHintType, Any>(EncodeHintType::class.java)
        
        val margin = when(profile) {
            "Symbol" -> 20
            "Datalogic" -> 10
            else -> 10
        }
        hints[EncodeHintType.MARGIN] = margin

        val baseWidth = when(profile) {
            "Symbol" -> 800
            "Datalogic" -> 1200
            else -> 1024
        }
        val baseHeight = when(profile) {
            "Symbol" -> 200
            "Datalogic" -> 300
            else -> 256
        }

        val format = if (data.length == 13 && data.all { it.isDigit() }) {
            BarcodeFormat.EAN_13
        } else {
            BarcodeFormat.CODE_128
        }

        val bitMatrix = writer.encode(data, format, baseWidth, baseHeight, hints)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        return bitmap.asImageBitmap()
    } catch (e: Exception) {
        return null
    }
}

@Composable
fun ThemeBanner(
    appTheme: String,
    backgroundUrl: String? = null,
    imageScale: Float = 1f,
    imageOffsetX: Float = 0f,
    imageOffsetY: Float = 0f,
    imageStretchX: Float = 1f,
    imageStretchY: Float = 1f,
    modifier: Modifier = Modifier
) {
    val normalizedTheme = when (appTheme.trim().lowercase()) {
        "multicolor", "glass", "expressive" -> "multicolor"
        "gold" -> "gold"
        "green" -> "green"
        "blue" -> "blue"
        "orange" -> "orange"
        else -> "red"
    }

    val imageModel: Any = backgroundUrl?.takeIf {
        it.startsWith("https://") || it.startsWith("http://")
    } ?: if (normalizedTheme == "multicolor") {
        R.drawable.theme_multicolor_header
    } else {
        "file:///android_asset/themes/theme_${normalizedTheme}.jpg"
    }

    BoxWithConstraints(modifier = modifier) {
        val safeScale = imageScale.coerceIn(0.5f, 3f)
        val safeOffsetX = imageOffsetX.coerceIn(-1f, 1f)
        val safeOffsetY = imageOffsetY.coerceIn(-1f, 1f)
        val safeStretchX = imageStretchX.coerceIn(0.5f, 2.5f)
        val safeStretchY = imageStretchY.coerceIn(0.5f, 2.5f)
        AsyncImage(
            model = imageModel,
            contentDescription = "Banner do tema $normalizedTheme",
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = safeScale * safeStretchX
                    scaleY = safeScale * safeStretchY
                    translationX = size.width * safeOffsetX
                    translationY = size.height * safeOffsetY
                },
            contentScale = ContentScale.Fit,
            alignment = Alignment.Center
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.vibrateClickable(
    viewModel: MainViewModel,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
): Modifier = composed {
    val vibrateOnClick by viewModel.userPreferences.vibrateOnClick.collectAsState(initial = true)
    val context = LocalContext.current
    val vibrator = remember { context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator }

    fun vibrate() {
        if (vibrateOnClick) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(50)
            }
        }
    }

    if (onLongClick == null) {
        this.clickable {
            vibrate()
            onClick()
        }
    } else {
        this.combinedClickable(
            onClick = {
                vibrate()
                onClick()
            },
            onLongClick = {
                vibrate()
                onLongClick()
            }
        )
    }
}
