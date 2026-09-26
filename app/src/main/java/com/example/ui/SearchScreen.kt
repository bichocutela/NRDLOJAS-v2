package com.example.ui
import androidx.compose.ui.composed
import androidx.compose.ui.composed
import androidx.compose.ui.layout.ContentScale

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.BakeryDining
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.ShoppingBasket
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
import com.example.ui.theme.LocalExpressiveGlassStyle
import com.example.ui.theme.LocalNrdDarkMode
import com.example.ui.theme.ExpressiveGlassStyle
import com.example.ui.theme.glassSoftShadow
import com.example.ui.theme.expressiveLiquidGlass
import com.example.ui.theme.expressiveShadow
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
import androidx.compose.ui.graphics.vector.ImageVector
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

    val isDark = LocalNrdDarkMode.current
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

private fun expressiveGlassCardAccent(style: ExpressiveGlassStyle, index: Int): Pair<Color, Color> {
    if (!style.enabled) return style.accent to style.onAccent

    // Assinatura visual do mock: fundo pode ser dourado, mas os cards continuam
    // alternando rosa, azul, laranja, dourado e verde.
    val signaturePalette = listOf(
        Color(0xFFEF4E6D),
        Color(0xFF2587DF),
        Color(0xFFF57C2C),
        Color(0xFFE7B21A),
        Color(0xFF35A75A)
    )
    // A Home do Glass Expressivo mantém a paleta viva da referência; a cor
    // selecionada continua controlando as ações e a iluminação do tema.
    return signaturePalette[index % signaturePalette.size] to style.onAccent
}

private fun expressiveGlassCardSecondary(style: ExpressiveGlassStyle, index: Int): Color {
    val signature = listOf(
        Color(0xFFFFA7B5),
        Color(0xFF8FD0FF),
        Color(0xFFFFBE82),
        Color(0xFFFFE17A),
        Color(0xFF8BE6A4)
    )
    return signature[index % signature.size]
}

@Composable
private fun homeStrongColors(index: Int): Pair<Color, Color> {
    val isDark = LocalNrdDarkMode.current
    val palette = if (isDark) {
        listOf(
            Color(0xFFFF7078) to Color(0xFF2B070A),
            Color(0xFF6DB6FF) to Color(0xFF041C32),
            Color(0xFFFF8B54) to Color(0xFF321003),
            Color(0xFFFFBD45) to Color(0xFF2D1C00),
            Color(0xFF63CF80) to Color(0xFF062510)
        )
    } else {
        listOf(
            Color(0xFFEF4E56) to Color.White,
            Color(0xFF2B86D9) to Color.White,
            Color(0xFFF0444C) to Color.White,
            Color(0xFFF79A18) to Color.White,
            Color(0xFF349B50) to Color.White
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
    canQuickAddProduct: Boolean = false,
    onQuickEditBanner: (String) -> Unit = {}
) {
    val bannerImageUri by viewModel.userPreferences.bannerImageUri.collectAsState(initial = null)
    val localAppTheme by viewModel.userPreferences.appTheme.collectAsStateWithLifecycle(initialValue = "multicolor")
    val remoteAppearance by FirebaseService.observeAppearanceSettings()
        .collectAsStateWithLifecycle(initialValue = AppearanceSettings())
    val glassStyle = LocalGlassSoftStyle.current
    val expressiveStyle = LocalExpressiveStyle.current
    val expressiveGlassStyle = LocalExpressiveGlassStyle.current
    val isExpressiveTheme = expressiveStyle.enabled
    val isGlassSoftTheme = glassStyle.enabled
    val isExpressiveGlassTheme = expressiveGlassStyle.enabled
    val isStandaloneGlassTheme = isGlassSoftTheme
    val appTheme = if (isStandaloneGlassTheme) "glass" else localAppTheme
    val glassActionBrush = remember(glassStyle.accent, glassStyle.secondaryAccent) {
        Brush.verticalGradient(
            listOf(glassStyle.accent, glassStyle.secondaryAccent)
        )
    }
    val expressiveGlassActionBrush = remember(
        expressiveGlassStyle.accent,
        expressiveGlassStyle.secondaryAccent,
        expressiveGlassStyle.tertiaryAccent
    ) {
        Brush.linearGradient(
            listOf(
                expressiveGlassStyle.accent.copy(alpha = 0.64f),
                expressiveGlassStyle.secondaryAccent.copy(alpha = 0.34f),
                expressiveGlassStyle.tertiaryAccent.copy(alpha = 0.46f),
                expressiveGlassStyle.accent.copy(alpha = 0.56f)
            )
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
    var showQuickAddProduct by remember { mutableStateOf(false) }
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
                    if (isGlassSoftTheme || isExpressiveTheme) Modifier.background(Color.Transparent)
                    else Modifier.background(MaterialTheme.colorScheme.background)
                )
        ) {
        val screenProfile = rememberNrdScreenProfile()
        val compactExpressive = isExpressiveTheme && screenProfile.compact
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = if (compactExpressive) 8.dp else if (isExpressiveTheme) 12.dp else 0.dp,
                    top = if (compactExpressive) 4.dp else if (isExpressiveTheme) 6.dp else 0.dp,
                    end = if (compactExpressive) 8.dp else if (isExpressiveTheme) 12.dp else 0.dp
                )
        ) {
            val headerHeight = maxWidth / 3f
            val headerShape = if (isExpressiveTheme) {
                RoundedCornerShape(if (compactExpressive) 24.dp else 28.dp)
            } else {
                RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(headerHeight)
                    .glassSoftShadow(headerShape)
                    .expressiveShadow(headerShape, 8.dp)
                    .clip(headerShape)
                    .background(
                        if (isGlassSoftTheme) {
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
                            .padding(top = if (compactExpressive) 34.dp else 48.dp, start = 8.dp)
                            .then(
                                if (isGlassSoftTheme) Modifier
                                    .glassSoftShadow(CircleShape, 4.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                    .border(1.dp, glassStyle.borderColor, CircleShape)
                                else if (isExpressiveGlassTheme) Modifier
                                    .expressiveLiquidGlass(
                                        shape = CircleShape,
                                        accent = expressiveGlassStyle.accent,
                                        intensity = 0.92f,
                                        elevation = 7.dp
                                    )
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
                                tint = when {
                                    isExpressiveGlassTheme -> expressiveGlassStyle.accent
                                    isExpressiveTheme -> MaterialTheme.colorScheme.onPrimaryContainer
                                    else -> MaterialTheme.colorScheme.primary
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
                            .padding(top = if (compactExpressive) 34.dp else 48.dp, end = 8.dp)
                            .then(
                                if (isGlassSoftTheme) Modifier
                                    .glassSoftShadow(CircleShape, 4.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                    .border(1.dp, glassStyle.borderColor, CircleShape)
                                else if (isExpressiveGlassTheme) Modifier
                                    .expressiveLiquidGlass(
                                        shape = CircleShape,
                                        accent = expressiveGlassStyle.accent,
                                        intensity = 0.92f,
                                        elevation = 7.dp
                                    )
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
                                tint = when {
                                    isExpressiveGlassTheme -> expressiveGlassStyle.accent
                                    isExpressiveTheme -> MaterialTheme.colorScheme.onPrimaryContainer
                                    else -> MaterialTheme.colorScheme.primary
                                }
                            )
                        }
                    }
                }
            }
        }
        Spacer(
            modifier = Modifier.height(
                when {
                    compactExpressive -> 10.dp
                    screenProfile.veryCompact -> 8.dp
                    else -> 16.dp
                }
            )
        )

            Column(
            modifier = Modifier.padding(horizontal = screenProfile.horizontalPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val searchFieldShape = when {
                isExpressiveGlassTheme -> {
                    val water = expressiveGlassStyle.fluidity
                    RoundedCornerShape(
                        topStart = (30f + 8f * water).dp,
                        topEnd = (22f + 10f * water).dp,
                        bottomEnd = (32f + 8f * water).dp,
                        bottomStart = (24f + 12f * water).dp
                    )
                }
                isExpressiveTheme -> RoundedCornerShape(30.dp)
                else -> RoundedCornerShape(32.dp)
            }
            TextField(
                value = searchQuery,
                onValueChange = viewModel::updateSearchQuery,
                singleLine = true,
                placeholder = { Text("Pesquisar produto...", style = MaterialTheme.typography.bodyLarge) },
                leadingIcon = {
                    if (isExpressiveGlassTheme) {
                        Box(
                            modifier = Modifier
                                .size(if (compactExpressive) 38.dp else 42.dp)
                                .expressiveLiquidGlass(
                                    shape = CircleShape,
                                    accent = expressiveGlassStyle.accent,
                                    secondaryAccent = expressiveGlassStyle.secondaryAccent,
                                    intensity = 0.92f,
                                    elevation = 5.dp,
                                    waves = false,
                                    bubbleSeed = 41
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Pesquisar",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(if (compactExpressive) 21.dp else 23.dp)
                            )
                        }
                    } else {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Pesquisar",
                            modifier = Modifier.size(if (compactExpressive) 24.dp else 28.dp)
                        )
                    }
                },
                trailingIcon = {
                    Row {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { viewModel.updateSearchQuery("") },
                                modifier = Modifier.then(
                                    if (isExpressiveGlassTheme) {
                                        Modifier.expressiveLiquidGlass(
                                            shape = CircleShape,
                                            accent = expressiveGlassStyle.secondaryAccent,
                                            intensity = 0.88f,
                                            elevation = 4.dp,
                                            waves = false,
                                            bubbleSeed = 43
                                        )
                                    } else Modifier
                                )
                            ) {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = "Limpar",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }

                        } else {
                            if (canQuickAddProduct) {
                                val quickAddInteraction = remember { MutableInteractionSource() }
                                val quickAddPressed by quickAddInteraction.collectIsPressedAsState()
                                val quickAddScale by animateFloatAsState(
                                    targetValue = if (quickAddPressed) 0.90f else 1f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    ),
                                    label = "quick-add-fab-scale"
                                )
                                Box(
                                    modifier = Modifier
                                        .size(if (isExpressiveTheme) 48.dp else 40.dp)
                                        .scale(quickAddScale)
                                        .then(
                                            if (isExpressiveGlassTheme) {
                                                Modifier.expressiveLiquidGlass(
                                                    shape = CircleShape,
                                                    accent = if (expressiveGlassStyle.accentName == "gold") Color(0xFFFFF8E9) else expressiveGlassStyle.accent,
                                                    secondaryAccent = if (expressiveGlassStyle.accentName == "gold") Color(0xFFFFD56A) else expressiveGlassStyle.tertiaryAccent,
                                                    intensity = 1.10f,
                                                    elevation = 8.dp,
                                                    waves = false,
                                                    bubbleSeed = 47
                                                )
                                            } else {
                                                Modifier
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                            }
                                        )
                                        .clickable(
                                            interactionSource = quickAddInteraction,
                                            indication = null
                                        ) { showQuickAddProduct = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = "Adicionar produto",
                                        tint = if (isExpressiveGlassTheme) expressiveGlassStyle.accent else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(if (isExpressiveTheme) 28.dp else 24.dp)
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
                                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Diga o nome ou código do produto")
                                    }
                                    voiceLauncher.launch(intent)
                                },
                                modifier = Modifier
                                    .size(if (isExpressiveTheme) 42.dp else 48.dp)
                                    .then(
                                        if (isExpressiveGlassTheme) {
                                            Modifier.expressiveLiquidGlass(
                                                shape = CircleShape,
                                                accent = if (expressiveGlassStyle.accentName == "gold") Color(0xFFFFF8E9) else expressiveGlassStyle.tertiaryAccent,
                                                secondaryAccent = if (expressiveGlassStyle.accentName == "gold") Color(0xFF9ECFFF) else expressiveGlassStyle.accent,
                                                intensity = 0.86f,
                                                elevation = 4.dp,
                                                waves = false,
                                                bubbleSeed = 53
                                            )
                                        } else Modifier
                                    )
                            ) {
                                Icon(
                                    Icons.Default.Mic,
                                    contentDescription = "Pesquisar por voz",
                                    tint = if (isExpressiveGlassTheme) expressiveGlassStyle.tertiaryAccent else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(
                        min = when {
                            compactExpressive -> 58.dp
                            isExpressiveGlassTheme -> 62.dp
                            isExpressiveTheme -> 60.dp
                            else -> 56.dp
                        }
                    )
                    .then(
                        when {
                            isExpressiveGlassTheme -> Modifier.expressiveLiquidGlass(
                                shape = searchFieldShape,
                                accent = if (expressiveGlassStyle.accentName == "gold") Color(0xFFFFF7F1) else expressiveGlassStyle.accent,
                                secondaryAccent = if (expressiveGlassStyle.accentName == "gold") Color(0xFF8EC9FF) else expressiveGlassStyle.secondaryAccent,
                                intensity = 1.18f,
                                elevation = 10.dp,
                                animated = true,
                                waves = true,
                                bubbleSeed = 59
                            )
                            isGlassSoftTheme -> Modifier.glassSoftShadow(searchFieldShape)
                            isExpressiveTheme -> Modifier.expressiveShadow(searchFieldShape, 8.dp)
                            else -> Modifier
                        }
                    )
                    .clip(searchFieldShape)
                    .border(
                        1.dp,
                        when {
                            isExpressiveGlassTheme -> Color.Transparent
                            isGlassSoftTheme -> glassStyle.borderColor
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
                        isExpressiveGlassTheme -> Color.Transparent
                        isGlassSoftTheme -> MaterialTheme.colorScheme.surface
                        isExpressiveTheme -> MaterialTheme.colorScheme.surface
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    unfocusedContainerColor = when {
                        isExpressiveGlassTheme -> Color.Transparent
                        isGlassSoftTheme -> MaterialTheme.colorScheme.surface
                        isExpressiveTheme -> MaterialTheme.colorScheme.surface
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    disabledContainerColor = when {
                        isExpressiveGlassTheme -> Color.Transparent
                        isGlassSoftTheme -> MaterialTheme.colorScheme.surface
                        isExpressiveTheme -> MaterialTheme.colorScheme.surfaceContainerHigh
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                )
            )
            
            Spacer(modifier = Modifier.height(if (screenProfile.veryCompact) 8.dp else 16.dp))
            
            val searchButtonShape = when {
                isExpressiveGlassTheme -> {
                    val water = expressiveGlassStyle.fluidity
                    RoundedCornerShape(
                        topStart = (30f + 10f * water).dp,
                        topEnd = (20f + 14f * water).dp,
                        bottomEnd = (34f + 8f * water).dp,
                        bottomStart = (22f + 16f * water).dp
                    )
                }
                isExpressiveTheme -> RoundedCornerShape(30.dp)
                else -> RoundedCornerShape(28.dp)
            }
            val openProductSearch = {
                keyboardController?.hide()
                sheetQuery = searchQuery
                showProductSearchSheet = true
            }
            val primaryActionInteraction = remember { MutableInteractionSource() }
            val primaryActionPressed by primaryActionInteraction.collectIsPressedAsState()
            val primaryActionScale by animateFloatAsState(
                targetValue = if (primaryActionPressed) 0.965f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "expressive-primary-action"
            )
            if (isGlassSoftTheme) {
                val glassSearchButtonHeight = when {
                    compactExpressive -> 54.dp
                    isExpressiveTheme -> 58.dp
                    else -> 56.dp
                }
                Surface(
                    onClick = openProductSearch,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(glassSearchButtonHeight)
                        .glassSoftShadow(searchButtonShape)
                        .expressiveShadow(searchButtonShape, 9.dp),
                    shape = searchButtonShape,
                    color = Color.Transparent,
                    contentColor = glassStyle.onAccent,
                    border = BorderStroke(1.dp, glassStyle.borderColor)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(glassSearchButtonHeight)
                            .background(glassActionBrush),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .expressiveLiquidGlass(
                                    shape = CircleShape,
                                    accent = expressiveGlassStyle.accent,
                                    intensity = 0.88f,
                                    elevation = 3.dp,
                                    waves = false,
                                    bubbleSeed = 67
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Pesquisar", fontWeight = FontWeight.Black, fontSize = 17.sp)
                    }
                }
            } else if (isExpressiveGlassTheme) {
                val expressiveGlassSearchButtonHeight = when {
                    compactExpressive -> 58.dp
                    else -> 64.dp
                }
                Surface(
                    onClick = openProductSearch,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(expressiveGlassSearchButtonHeight)
                        .scale(primaryActionScale)
                        .expressiveLiquidGlass(
                            shape = searchButtonShape,
                            accent = Color(0xFFF5AA00),
                            secondaryAccent = Color(0xFFFFE27A),
                            intensity = 1.40f,
                            elevation = 13.dp,
                            animated = true,
                            waves = true,
                            bubbleSeed = 61
                        ),
                    shape = searchButtonShape,
                    color = Color.Transparent,
                    contentColor = expressiveGlassStyle.onAccent,
                    border = null,
                    interactionSource = primaryActionInteraction
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(expressiveGlassSearchButtonHeight),
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
                        .heightIn(
                            min = when {
                                compactExpressive -> 54.dp
                                isExpressiveTheme -> 58.dp
                                else -> 56.dp
                            }
                        )
                        .expressiveShadow(searchButtonShape, 9.dp),
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
                homeSettings.showFavorites
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

                if (homeSettings.showFavorites) {
                    item {
                        SectionHeader("Meus Favoritos", textPreferences)
                        if (favorites.isEmpty()) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                shape = RoundedCornerShape(if (isExpressiveTheme) 22.dp else 16.dp),
                                color = if (isExpressiveTheme) {
                                    MaterialTheme.colorScheme.surfaceContainerLow
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        Icons.Default.FavoriteBorder,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        "Toque no coração de um produto para adicioná-lo aos seus favoritos.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
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

        if (showQuickAddProduct && canQuickAddProduct) {
            QuickAddProductDialog(
                viewModel = viewModel,
                categories = activeCategoryNames,
                onDismiss = { showQuickAddProduct = false },
                onSaved = {
                    Toast.makeText(context, "Produto adicionado com sucesso!", Toast.LENGTH_SHORT).show()
                }
            )
        }

        if (showProductSearchSheet) {
            val sheetResultsFlow = remember(sheetQuery) { viewModel.searchProducts(sheetQuery) }
            val sheetResults by sheetResultsFlow.collectAsState(initial = emptyList())
            ModalBottomSheet(
                onDismissRequest = { showProductSearchSheet = false },
                containerColor = if (isGlassSoftTheme || isExpressiveGlassTheme) MaterialTheme.colorScheme.surfaceContainerHigh
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
                containerColor = if (isGlassSoftTheme || isExpressiveGlassTheme) MaterialTheme.colorScheme.surfaceContainerHigh
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
                containerColor = if (isGlassSoftTheme || isExpressiveGlassTheme) MaterialTheme.colorScheme.surfaceContainerHigh
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
    val expressiveGlass = LocalExpressiveGlassStyle.current
    val isExpressiveGlass = expressiveGlass.enabled
    val profile = rememberNrdScreenProfile()
    val compactExpressive = expressive && profile.compact
    val sectionIcon = when {
        title.contains("Mais Utilizados", ignoreCase = true) -> Icons.Default.BarChart
        title.contains("Últimos", ignoreCase = true) -> Icons.Default.NewReleases
        title.contains("Histórico", ignoreCase = true) -> Icons.Default.History
        title.contains("Favoritos", ignoreCase = true) -> Icons.Default.Favorite
        else -> Icons.Default.Search
    }
    val sectionAccent = when {
        title.contains("Mais Utilizados", ignoreCase = true) -> expressiveGlass.accent
        title.contains("Últimos", ignoreCase = true) -> expressiveGlass.tertiaryAccent
        title.contains("Histórico", ignoreCase = true) -> expressiveGlass.secondaryAccent
        title.contains("Favoritos", ignoreCase = true) -> Color(0xFFEF4E56)
        else -> expressiveGlass.accent
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = if (compactExpressive) 12.dp else 16.dp,
                vertical = if (compactExpressive) 4.dp else if (expressive) 7.dp else 2.dp
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (expressive) {
                val headerIconShape = RoundedCornerShape(if (compactExpressive) 10.dp else 12.dp)
                Box(
                    modifier = Modifier
                        .size(if (compactExpressive) 30.dp else 34.dp)
                        .then(
                            if (isExpressiveGlass) {
                                Modifier.expressiveLiquidGlass(
                                    shape = headerIconShape,
                                    accent = sectionAccent,
                                    secondaryAccent = expressiveGlass.secondaryAccent,
                                    intensity = 0.92f,
                                    elevation = 5.dp,
                                    waves = true,
                                    bubbleSeed = title.hashCode()
                                )
                            } else {
                                Modifier
                                    .clip(headerIconShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        sectionIcon,
                        contentDescription = null,
                        tint = if (isExpressiveGlass) sectionAccent else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(if (compactExpressive) 18.dp else 20.dp)
                    )
                }
                Spacer(Modifier.width(if (compactExpressive) 7.dp else 10.dp))
            }
            StylizedText(
                text = title,
                baseStyle = if (expressive) {
                    MaterialTheme.typography.titleLarge.copy(fontSize = if (compactExpressive) 17.sp else 19.sp)
                } else {
                    MaterialTheme.typography.labelMedium
                },
                boldOutline = textPreferences.boldOutline,
                uppercaseBold = textPreferences.uppercaseBold,
                color = if (expressive) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (actionLabel != null && onAction != null) {
            val actionShape = RoundedCornerShape(18.dp)
            TextButton(
                onClick = onAction,
                modifier = Modifier.then(
                    if (isExpressiveGlass) {
                        Modifier.expressiveLiquidGlass(
                            shape = actionShape,
                            accent = sectionAccent,
                            intensity = 0.72f,
                            elevation = 3.dp
                        )
                    } else Modifier
                ),
                shape = actionShape,
                contentPadding = PaddingValues(
                    horizontal = if (compactExpressive) 5.dp else if (expressive) 8.dp else 12.dp,
                    vertical = if (compactExpressive) 4.dp else 6.dp
                ),
                colors = ButtonDefaults.textButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                StylizedText(
                    text = actionLabel,
                    baseStyle = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = if (expressive) FontWeight.ExtraBold else FontWeight.Normal
                    ),
                    boldOutline = textPreferences.boldOutline,
                    uppercaseBold = textPreferences.uppercaseBold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (expressive) {
                    Spacer(Modifier.width(2.dp))
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(if (compactExpressive) 15.dp else 17.dp)
                    )
                }
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
    val expressiveGlass = LocalExpressiveGlassStyle.current
    val isExpressiveGlass = expressiveGlass.enabled
    val profile = rememberNrdScreenProfile()
    val compactExpressive = expressive && profile.compact
    val categoryColors = listOf(
        MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer,
        MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer,
        MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer,
        MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    )

    LazyRow(
        contentPadding = PaddingValues(horizontal = if (compactExpressive) 12.dp else 16.dp),
        horizontalArrangement = Arrangement.spacedBy(if (compactExpressive) 6.dp else 8.dp),
        modifier = Modifier.padding(bottom = 2.dp)
    ) {
        itemsIndexed(categories) { index, category ->
            val colors = categoryColors[index % categoryColors.size]
            val dynamicColors = homeDynamicColors(index, appTheme, colors.first, colors.second)
            val strongColors = homeStrongColors(index)
            val liquidAccent = expressiveGlassCardAccent(expressiveGlass, index)
            val categoryGlassFill = when {
                glass.enabled -> glass.fill.copy(alpha = glass.alpha)
                isExpressiveGlass -> expressiveGlass.surfaceBase.copy(alpha = expressiveGlass.surfaceAlpha)
                expressive -> strongColors.first
                else -> dynamicColors.first
            }
            val categoryGlassBorder = when {
                glass.enabled -> glass.border
                isExpressiveGlass -> expressiveGlass.borderColor
                else -> Color.Transparent
            }
            val categoryShape = when {
                isExpressiveGlass -> {
                    val water = expressiveGlass.fluidity
                    RoundedCornerShape(
                        topStart = (18f + 8f * water).dp,
                        topEnd = (14f + 12f * water).dp,
                        bottomEnd = (22f + 8f * water).dp,
                        bottomStart = (15f + 10f * water).dp
                    )
                }
                expressive -> RoundedCornerShape(if (compactExpressive) 18.dp else 22.dp)
                else -> RoundedCornerShape(16.dp)
            }
            Box(
                modifier = Modifier
                    .glassSoftShadow(categoryShape)
                    .expressiveShadow(categoryShape, 6.dp)
                    .clip(categoryShape)
                    .then(
                        if (isExpressiveGlass) {
                            Modifier
                                .background(
                                    Brush.verticalGradient(
                                        listOf(liquidAccent.first.copy(alpha = 0.96f), liquidAccent.first)
                                    )
                                )
                                .expressiveLiquidGlass(
                                    shape = categoryShape,
                                    accent = liquidAccent.first,
                                    secondaryAccent = expressiveGlassCardSecondary(expressiveGlass, index),
                                    intensity = 1.08f,
                                    elevation = 8.dp,
                                    animated = true,
                                    waves = true,
                                    bubbleSeed = index
                                )
                        } else {
                            Modifier
                                .background(categoryGlassFill)
                                .border(1.dp, categoryGlassBorder, categoryShape)
                        }
                    )
                    .clickable { onCategoryClick(category) }
                    .padding(
                        horizontal = when {
                            compactExpressive -> 13.dp
                            expressive -> 18.dp
                            else -> 16.dp
                        },
                        vertical = when {
                            compactExpressive -> 8.dp
                            expressive -> 11.dp
                            else -> 10.dp
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (expressive) {
                        Icon(
                            painter = painterResource(id = expressiveCategoryIconRes(category)),
                            contentDescription = category,
                            tint = when {
                                isExpressiveGlass -> Color.White
                                glass.enabled -> strongColors.first
                                else -> strongColors.second
                            },
                            modifier = Modifier.size(if (compactExpressive) 18.dp else 20.dp)
                        )
                        Spacer(Modifier.width(if (compactExpressive) 6.dp else 8.dp))
                    }
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
                            isExpressiveGlass -> Color.White
                            glass.enabled -> MaterialTheme.colorScheme.onSurface
                            expressive -> strongColors.second
                            else -> dynamicColors.second
                        }
                    )
                }
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
private fun FavoriteToggleButton(
    product: Product,
    viewModel: MainViewModel,
    compact: Boolean = false
) {
    val expressiveGlass = LocalExpressiveGlassStyle.current
    val heartScale by animateFloatAsState(
        targetValue = if (product.isFavorite) 1.10f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "favorite-liquid-scale"
    )
    val heartAccent = if (product.isFavorite) Color(0xFFEF4E56) else expressiveGlass.accent
    IconButton(
        onClick = { viewModel.toggleFavorite(product) },
        modifier = Modifier
            .size(if (compact) 34.dp else 38.dp)
            .then(
                if (expressiveGlass.enabled) {
                    Modifier.expressiveLiquidGlass(
                        shape = CircleShape,
                        accent = heartAccent,
                        secondaryAccent = expressiveGlass.secondaryAccent,
                        intensity = if (product.isFavorite) 0.94f else 0.72f,
                        elevation = if (product.isFavorite) 5.dp else 3.dp,
                        waves = false,
                        bubbleSeed = product.code.hashCode()
                    )
                } else Modifier
            )
    ) {
        Icon(
            imageVector = if (product.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            contentDescription = if (product.isFavorite) "Remover dos favoritos" else "Adicionar aos favoritos",
            tint = if (product.isFavorite) Color(0xFFEF4E56) else MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(if (compact) 19.dp else 21.dp)
                .scale(heartScale)
        )
    }
}

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
    val expressiveGlass = LocalExpressiveGlassStyle.current
    val isExpressiveGlass = expressiveGlass.enabled
    val profile = rememberNrdScreenProfile()
    val compactExpressive = expressive && profile.compact
    val cardShape = when {
        isExpressiveGlass -> {
            val water = expressiveGlass.fluidity
            RoundedCornerShape(
                topStart = (24f + 12f * water).dp,
                topEnd = (18f + 14f * water).dp,
                bottomEnd = (28f + 10f * water).dp,
                bottomStart = (20f + 16f * water).dp
            )
        }
        expressive -> RoundedCornerShape(
            topStart = if (compactExpressive) 26.dp else 34.dp,
            topEnd = if (compactExpressive) 18.dp else 22.dp,
            bottomEnd = if (compactExpressive) 24.dp else 30.dp,
            bottomStart = if (compactExpressive) 20.dp else 26.dp
        )
        else -> RoundedCornerShape(24.dp)
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val shareLayer = rememberGraphicsLayer()
    val cardAccent = if (isExpressiveGlass) {
        expressiveGlassCardAccent(expressiveGlass, index)
    } else {
        homeDynamicColors(
            index,
            appTheme,
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
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
            .then(
                if (isExpressiveGlass) {
                    Modifier.expressiveLiquidGlass(
                        shape = cardShape,
                        accent = cardAccent.first,
                        secondaryAccent = expressiveGlassCardSecondary(expressiveGlass, index),
                        intensity = 1.30f,
                        elevation = 8.dp,
                        waves = true,
                        bubbleSeed = index
                    )
                } else {
                    Modifier
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
                }
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
            .padding(if (compactExpressive) 12.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (product.imageUrl != null) {
            AsyncImage(
                model = product.imageUrl,
                contentDescription = product.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(if (compactExpressive) 42.dp else 48.dp)
                    .clip(CircleShape)
            )
        } else {
            val dynColors = cardAccent
            Box(
                modifier = Modifier
                    .size(if (compactExpressive) 42.dp else 48.dp)
                    .clip(
                        if (expressive) RoundedCornerShape(if (compactExpressive) 14.dp else 16.dp)
                        else CircleShape
                    )
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

        Spacer(modifier = Modifier.width(if (compactExpressive) 11.dp else 16.dp))

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

        Spacer(modifier = Modifier.width(if (compactExpressive) 8.dp else 12.dp))

        Column(horizontalAlignment = Alignment.End) {
            FavoriteToggleButton(product, viewModel, compact = compactExpressive)
            val codeShape = if (expressive) RoundedCornerShape(20.dp) else RoundedCornerShape(16.dp)
            Box(
                modifier = Modifier
                    .then(
                        if (isExpressiveGlass) {
                            Modifier.expressiveLiquidGlass(
                                shape = codeShape,
                                accent = cardAccent.first,
                                secondaryAccent = expressiveGlass.secondaryAccent,
                                intensity = 0.90f,
                                elevation = 4.dp,
                                waves = false,
                                bubbleSeed = index + 101
                            )
                        } else {
                            Modifier
                                .clip(codeShape)
                                .background(if (expressive) cardAccent.first else MaterialTheme.colorScheme.primaryContainer)
                        }
                    )
                    .padding(
                        horizontal = when {
                            compactExpressive -> 12.dp
                            expressive -> 18.dp
                            else -> 16.dp
                        },
                        vertical = when {
                            compactExpressive -> 7.dp
                            expressive -> 10.dp
                            else -> 8.dp
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = product.code,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, fontSize = 16.sp),
                        color = if (isExpressiveGlass) cardAccent.first else if (expressive) cardAccent.second else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = product.unit.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Black),
                        color = if (isExpressiveGlass) MaterialTheme.colorScheme.onSurfaceVariant else if (expressive) cardAccent.second.copy(alpha = 0.78f) else MaterialTheme.colorScheme.primary
                    )
                }
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
    val expressiveGlass = LocalExpressiveGlassStyle.current
    val isExpressiveGlass = expressiveGlass.enabled
    val profile = rememberNrdScreenProfile()
    val compactExpressive = expressive && profile.compact
    val cardShape = when {
        isExpressiveGlass -> {
            val water = expressiveGlass.fluidity
            RoundedCornerShape(
                topStart = (22f + 10f * water).dp,
                topEnd = (16f + 13f * water).dp,
                bottomEnd = (26f + 10f * water).dp,
                bottomStart = (18f + 14f * water).dp
            )
        }
        expressive -> RoundedCornerShape(
            topStart = if (compactExpressive) 24.dp else 30.dp,
            topEnd = if (compactExpressive) 15.dp else 18.dp,
            bottomEnd = if (compactExpressive) 28.dp else 34.dp,
            bottomStart = if (compactExpressive) 18.dp else 22.dp
        )
        else -> RoundedCornerShape(24.dp)
    }
    val cardAccent = if (isExpressiveGlass) {
        expressiveGlassCardAccent(expressiveGlass, index)
    } else {
        homeDynamicColors(
            index,
            appTheme,
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
    val strongAccent = if (isExpressiveGlass) cardAccent else homeStrongColors(index)
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
            .widthIn(
                min = when {
                    compactExpressive -> 138.dp
                    expressive -> 154.dp
                    else -> 144.dp
                },
                max = when {
                    compactExpressive -> 164.dp
                    expressive -> 184.dp
                    else -> 176.dp
                }
            )
            .heightIn(
                min = when {
                    compactExpressive -> 154.dp
                    expressive -> 176.dp
                    textPreferences.largeText -> 168.dp
                    else -> 132.dp
                }
            )
            .glassSoftShadow(cardShape)
            .expressiveShadow(cardShape, 7.dp)
            .clip(cardShape)
            .then(
                if (isExpressiveGlass) {
                    Modifier.expressiveLiquidGlass(
                        shape = cardShape,
                        accent = cardAccent.first,
                        secondaryAccent = expressiveGlassCardSecondary(expressiveGlass, index),
                        intensity = 1.26f,
                        elevation = 7.dp,
                        waves = true,
                        bubbleSeed = index + 13
                    )
                } else {
                    Modifier
                        .background(
                            when {
                                glass.enabled -> glass.fill.copy(alpha = glass.alpha)
                                expressive -> MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
                                else -> MaterialTheme.colorScheme.surface
                            }
                        )
                        .border(
                            1.dp,
                            when {
                                glass.enabled -> glass.border
                                expressive -> MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)
                                else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                            },
                            cardShape
                        )
                }
            )
            .vibrateClickable(viewModel) {
                if (onProductClick != null) {
                    onProductClick(product)
                } else {
                    viewModel.onProductSearched(product)
                    showDialog = true
                }
            }
            .padding(
                when {
                    compactExpressive -> 9.dp
                    expressive -> 12.dp
                    else -> 10.dp
                }
            ),
        verticalArrangement = Arrangement.spacedBy(
            when {
                compactExpressive -> 5.dp
                expressive -> 7.dp
                else -> 5.dp
            }
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
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
                        .size(
                            when {
                                compactExpressive -> 46.dp
                                expressive -> 54.dp
                                else -> 32.dp
                            }
                        )
                        .clip(
                            if (expressive) RoundedCornerShape(if (compactExpressive) 14.dp else 17.dp)
                            else CircleShape
                        )
                        .background(cardAccent.first)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(
                            when {
                                compactExpressive -> 46.dp
                                expressive -> 54.dp
                                else -> 32.dp
                            }
                        )
                        .clip(
                            if (expressive) RoundedCornerShape(if (compactExpressive) 14.dp else 17.dp)
                            else CircleShape
                        )
                        .background(cardAccent.first),
                    contentAlignment = Alignment.Center
                ) {
                    StylizedText(
                        text = product.name.take(1),
                        baseStyle = MaterialTheme.typography.titleMedium.copy(fontSize = if (expressive) 18.sp else 14.sp),
                        boldOutline = textPreferences.boldOutline,
                        uppercaseBold = true,
                        color = if (expressive) strongAccent.first else cardAccent.second
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                FavoriteToggleButton(product, viewModel, compact = compactExpressive)
                if (expressive) {
                    val unitShape = RoundedCornerShape(14.dp)
                    Box(
                        modifier = Modifier
                            .then(
                                if (isExpressiveGlass) {
                                    Modifier.expressiveLiquidGlass(
                                        shape = unitShape,
                                        accent = cardAccent.first,
                                        intensity = 0.76f,
                                        elevation = 3.dp
                                    )
                                } else {
                                    Modifier
                                        .clip(unitShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                }
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = product.unit.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                            color = if (isExpressiveGlass) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                } else {
                    Text(
                        text = product.unit.uppercase(),
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        StylizedText(
            text = product.name,
            baseStyle = MaterialTheme.typography.titleMedium.copy(
                fontSize = when {
                    compactExpressive -> 14.sp
                    expressive -> 15.sp
                    else -> 14.sp
                },
                fontWeight = if (expressive) FontWeight.ExtraBold else FontWeight.Bold
            ),
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
                baseStyle = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                ),
                boldOutline = textPreferences.boldOutline,
                uppercaseBold = true,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = product.code,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Black,
                    fontSize = when {
                        compactExpressive -> 16.sp
                        expressive -> 18.sp
                        else -> 16.sp
                    }
                ),
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (expressive) {
                Box(
                    modifier = Modifier
                        .then(
                            if (isExpressiveGlass) {
                                Modifier.expressiveLiquidGlass(
                                    shape = CircleShape,
                                    accent = cardAccent.first,
                                    intensity = 0.78f,
                                    elevation = 3.dp
                                )
                            } else {
                                Modifier
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                            }
                        )
                        .padding(if (compactExpressive) 5.dp else 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = "Abrir produto",
                        tint = if (isExpressiveGlass) cardAccent.first else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(if (compactExpressive) 16.dp else 18.dp)
                    )
                }
            }
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
    val expressiveGlass = LocalExpressiveGlassStyle.current
    val isExpressiveGlass = expressiveGlass.enabled
    val profile = rememberNrdScreenProfile()
    val compactExpressive = expressive && profile.compact
    val itemShape = when {
        isExpressiveGlass -> {
            val water = expressiveGlass.fluidity
            RoundedCornerShape(
                topStart = (20f + 10f * water).dp,
                topEnd = (15f + 13f * water).dp,
                bottomEnd = (24f + 11f * water).dp,
                bottomStart = (17f + 14f * water).dp
            )
        }
        expressive -> RoundedCornerShape(
            topStart = if (compactExpressive) 22.dp else 28.dp,
            topEnd = if (compactExpressive) 14.dp else 18.dp,
            bottomEnd = if (compactExpressive) 26.dp else 32.dp,
            bottomStart = if (compactExpressive) 17.dp else 21.dp
        )
        else -> RoundedCornerShape(16.dp)
    }
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
    val dynColors = if (isExpressiveGlass) {
        expressiveGlassCardAccent(expressiveGlass, index)
    } else {
        homeDynamicColors(
            index,
            appTheme,
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
    val strongColors = if (isExpressiveGlass) dynColors else homeStrongColors(index)

    if (expressive) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = if (compactExpressive) 72.dp else 84.dp)
                .glassSoftShadow(itemShape)
                .expressiveShadow(itemShape, 6.dp)
                .clip(itemShape)
                .then(
                    when {
                        isExpressiveGlass -> Modifier.expressiveLiquidGlass(
                            shape = itemShape,
                            accent = dynColors.first,
                            secondaryAccent = expressiveGlassCardSecondary(expressiveGlass, index),
                            intensity = 1.30f,
                            elevation = 7.dp,
                            waves = true,
                            bubbleSeed = index + 29
                        )
                        glass.enabled -> Modifier
                            .background(glass.fill.copy(alpha = glass.alpha))
                            .border(1.dp, strongColors.first.copy(alpha = 0.42f), itemShape)
                        else -> Modifier
                            .background(dynColors.first.copy(alpha = 0.62f))
                            .border(1.dp, strongColors.first.copy(alpha = 0.52f), itemShape)
                    }
                )
                .vibrateClickable(viewModel) {
                    viewModel.onProductSearched(product)
                    showDialog = true
                }
                .padding(
                    horizontal = if (compactExpressive) 8.dp else 10.dp,
                    vertical = if (compactExpressive) 7.dp else 9.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val historyIconShape = RoundedCornerShape(if (compactExpressive) 12.dp else 15.dp)
            Box(
                modifier = Modifier
                    .size(if (compactExpressive) 38.dp else 46.dp)
                    .then(
                        if (isExpressiveGlass) {
                            Modifier
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(Color.White.copy(alpha = 0.40f), dynColors.first, dynColors.first.copy(alpha = 0.92f)),
                                        center = Offset(18f, 14f)
                                    )
                                )
                                .expressiveLiquidGlass(
                                    shape = historyIconShape,
                                    accent = dynColors.first,
                                    secondaryAccent = expressiveGlassCardSecondary(expressiveGlass, index),
                                    intensity = 1.12f,
                                    elevation = 7.dp,
                                    waves = true,
                                    bubbleSeed = index + 149
                                )
                        } else {
                            Modifier
                                .clip(historyIconShape)
                                .background(strongColors.first)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = "Histórico",
                    tint = if (isExpressiveGlass) Color.White else strongColors.second,
                    modifier = Modifier.size(if (compactExpressive) 19.dp else 23.dp)
                )
            }
            Spacer(modifier = Modifier.width(if (compactExpressive) 7.dp else 9.dp))
            if (product.imageUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(product.imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = product.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(if (compactExpressive) 46.dp else 54.dp)
                        .clip(RoundedCornerShape(if (compactExpressive) 13.dp else 16.dp))
                        .background(MaterialTheme.colorScheme.surface)
                )
                Spacer(modifier = Modifier.width(if (compactExpressive) 7.dp else 10.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                StylizedText(
                    text = product.name,
                    baseStyle = MaterialTheme.typography.titleMedium.copy(
                        fontSize = if (compactExpressive) 13.sp else 14.sp,
                        fontWeight = FontWeight.ExtraBold
                    ),
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
                        baseStyle = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        ),
                        boldOutline = textPreferences.boldOutline,
                        uppercaseBold = true,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "Código: ${product.code}",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            FavoriteToggleButton(product, viewModel, compact = compactExpressive)
            Box(
                modifier = Modifier.then(
                    if (isExpressiveGlass) {
                        Modifier.expressiveLiquidGlass(
                            shape = CircleShape,
                            accent = dynColors.first,
                            intensity = 0.70f,
                            elevation = 2.dp
                        )
                    } else Modifier
                ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = "Abrir produto",
                    tint = strongColors.first,
                    modifier = Modifier
                        .padding(if (isExpressiveGlass) 4.dp else 0.dp)
                        .size(if (compactExpressive) 20.dp else 24.dp)
                )
            }
        }
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassSoftShadow(itemShape)
            .clip(itemShape)
            .background(
                if (glass.enabled) glass.fill.copy(alpha = glass.alpha)
                else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
            )
            .border(
                1.dp,
                if (glass.enabled) glass.border else dynColors.first,
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
            Icon(
                imageVector = Icons.Default.History,
                contentDescription = "Histórico",
                tint = dynColors.first,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                StylizedText(
                    text = product.name,
                    baseStyle = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
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
                        baseStyle = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        ),
                        boldOutline = textPreferences.boldOutline,
                        uppercaseBold = true,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "Código: ${product.code}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        FavoriteToggleButton(product, viewModel, compact = true)
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
