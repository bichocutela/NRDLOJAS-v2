Warning: truncated output (original token count: 33850)
Total output lines: 3038

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
                                    .glassSoftShadow(CircleShape, 4…21850 tokens truncated…             Modifier.expressiveLiquidGlass(
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
