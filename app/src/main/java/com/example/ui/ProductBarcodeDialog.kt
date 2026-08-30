package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import coil.compose.AsyncImage
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.Product
import com.example.data.UserPreferences
import com.example.util.ImageUrlHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ProductBarcodeDialog(
    product: Product,
    onDismiss: () -> Unit,
    highlightedFromNotification: Boolean = false
) {
    val showDialog = remember { mutableStateOf(true) }
    val animateIn = remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val userPreferences = remember { UserPreferences(context) }
    val barcodeNumberScale by userPreferences.barcodeNumberScale.collectAsState(initial = 1.0f)
    val barcodeTitleScale by userPreferences.barcodeTitleScale.collectAsState(initial = 1.0f)
    val boldOutline by userPreferences.boldOutline.collectAsState(initial = false)
    val uppercaseBold by userPreferences.uppercaseBold.collectAsState(initial = false)
    val appTheme by userPreferences.appTheme.collectAsState(initial = "multicolor")
    val glassTransparency by userPreferences.glassTransparency.collectAsState(initial = 0.55f)
    val glassType by userPreferences.glassType.collectAsState(initial = "soft")
    val glassAccentName by userPreferences.glassAccentColor.collectAsState(initial = "multicolor")
    val isGlassTheme = appTheme == "glass"
    val darkGlass = MaterialTheme.colorScheme.background.luminance() < 0.35f
    val glassAccent = remember(glassAccentName) {
        when (glassAccentName) {
            "red" -> Color(0xFFE5252A)
            "green" -> Color(0xFF2E9D44)
            "orange" -> Color(0xFFF28C18)
            "blue" -> Color(0xFF2474D2)
            "gold" -> Color(0xFFC99A14)
            else -> listOf(Color(0xFFE5252A), Color(0xFF2E9D44), Color(0xFFF28C18), Color(0xFF2474D2), Color(0xFFC99A14)).shuffled().first()
        }
    }
    val glassBaseAlpha = (1f - glassTransparency).coerceIn(0.10f, 0.80f)
    val glassDialogAlpha = when (glassType) {
        "frosted" -> (glassBaseAlpha + 0.18f).coerceIn(0.22f, 0.86f)
        "crystal" -> (glassBaseAlpha - 0.12f).coerceIn(0.08f, 0.62f)
        else -> glassBaseAlpha
    }
    val glassDialogColor = if (darkGlass) {
        glassAccent.copy(alpha = (glassDialogAlpha * 0.62f).coerceAtLeast(0.14f))
    } else {
        glassAccent.copy(alpha = (glassDialogAlpha * 0.28f).coerceAtLeast(0.08f))
    }
    val glassDialogBorder = when (glassType) {
        "crystal" -> Color.White.copy(alpha = 0.96f)
        "frosted" -> Color.White.copy(alpha = 0.84f)
        else -> Color.White.copy(alpha = 0.76f)
    }
    val photoUrl = remember(product.imageUrl) {
        product.imageUrl
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let(ImageUrlHelper::normalizeUrl)
            ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
    }
    var showPhotoDialog by remember { mutableStateOf(false) }
    
    var scannerProfile by remember { mutableStateOf("Padrão") }
    var zoomPercent by remember { mutableIntStateOf(100) }

    fun closeDialog() {
        animateIn.value = false
        coroutineScope.launch {
            delay(200)
            showDialog.value = false
            onDismiss()
        }
    }

    if (showDialog.value) {
        LaunchedEffect(Unit) {
            animateIn.value = true
        }
        Dialog(
            onDismissRequest = { closeDialog() },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            AnimatedVisibility(
                visible = animateIn.value,
                enter = fadeIn() + scaleIn(initialScale = 0.8f, animationSpec = tween(300, easing = EaseOutBack)),
                exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.8f, animationSpec = tween(200, easing = EaseIn))
            ) {
                Surface(
                    shape = RoundedCornerShape(32.dp),
                    color = when {
                        highlightedFromNotification -> MaterialTheme.colorScheme.primaryContainer
                        isGlassTheme -> glassDialogColor
                        else -> MaterialTheme.colorScheme.surface
                    },
                    border = when {
                        highlightedFromNotification -> BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
                        isGlassTheme -> BorderStroke(1.dp, glassDialogBorder)
                        else -> null
                    },
                    modifier = Modifier.fillMaxWidth(0.9f).padding(vertical = 24.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp)
                    ) {
                        if (highlightedFromNotification) {
                            Text(
                                text = "ABERTO PELA NOTIFICAÇÃO",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        StylizedText(
                            text = product.name,
                            baseStyle = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Black,
                                fontSize = 28.sp * barcodeTitleScale
                            ),
                            boldOutline = boldOutline,
                            uppercaseBold = uppercaseBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = product.code,
                            style = MaterialTheme.typography.displayMedium.copy(
                                fontWeight = FontWeight.Black,
                                fontSize = 42.sp * barcodeNumberScale,
                                color = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        StylizedText(
                            text = product.category,
                            baseStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            boldOutline = boldOutline,
                            uppercaseBold = true,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        val barcodeBitmap = generateBarcodeBitmap(product.code, scannerProfile)
                        if (barcodeBitmap != null) {
                            val targetHeight = when (scannerProfile) {
                                "Symbol" -> 130.dp
                                "Datalogic" -> 140.dp
                                else -> 110.dp
                            }
                            val widthFraction = (0.9f * (zoomPercent / 100f)).coerceAtMost(1.0f)
                            
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = barcodeBitmap,
                                    contentDescription = "Código de barras",
                                    contentScale = ContentScale.Fit,
                                    filterQuality = FilterQuality.None,
                                    modifier = Modifier
                                        .fillMaxWidth(widthFraction)
                                        .height(targetHeight)
                                        .background(Color.White)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        Text(
                            text = "Código de barras / Referência",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        Divider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = "Scanner",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Perfil do Leitor",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (photoUrl != null) {
                                Spacer(modifier = Modifier.weight(1f))
                                TextButton(
                                    onClick = { showPhotoDialog = true },
                                    contentPadding = PaddingValues(horizontal = 4.dp)
                                ) {
                                    Text("Ver Foto do Produto", maxLines = 2, textAlign = TextAlign.End)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("Padrão", "Symbol", "Datalogic").forEach { profile ->
                                if (scannerProfile == profile) {
                                    Button(
                                        onClick = { scannerProfile = profile },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text(profile, fontSize = 12.sp, maxLines = 1)
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = { scannerProfile = profile },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text(profile, fontSize = 12.sp, maxLines = 1)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Ajuste de leitura", style = MaterialTheme.typography.titleSmall)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            IconButton(
                                onClick = { 
                                    if (zoomPercent > 80) zoomPercent -= 10 
                                },
                                enabled = zoomPercent > 80
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Menos")
                            }
                            Text(
                                text = "${zoomPercent}%",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            IconButton(
                                onClick = { 
                                    if (zoomPercent < 120) zoomPercent += 10 
                                },
                                enabled = zoomPercent < 120
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Mais")
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { closeDialog() },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("FECHAR", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            }
        }

        if (showPhotoDialog && photoUrl != null) {
            var isPhotoLoading by remember(photoUrl) { mutableStateOf(true) }
            var photoLoadFailed by remember(photoUrl) { mutableStateOf(false) }
            AlertDialog(
                onDismissRequest = { showPhotoDialog = false },
                containerColor = if (isGlassTheme) glassDialogColor else MaterialTheme.colorScheme.surfaceContainerHigh,
                title = { Text("Foto do Produto") },
                text = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 180.dp, max = 360.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = photoUrl,
                            contentDescription = product.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                            onLoading = {
                                isPhotoLoading = true
                                photoLoadFailed = false
                            },
                            onSuccess = { isPhotoLoading = false },
                            onError = {
                                isPhotoLoading = false
                                photoLoadFailed = true
                            }
                        )
                        if (isPhotoLoading) {
                            CircularProgressIndicator()
                        }
                        if (photoLoadFailed) {
                            Text(
                                "Não foi possível carregar a foto do produto.",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showPhotoDialog = false }) {
                        Text("Fechar")
                    }
                }
            )
        }
    }
}
