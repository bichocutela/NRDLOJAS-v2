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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
fun ProductBarcodeDialog(product: Product, onDismiss: () -> Unit) {
    val showDialog = remember { mutableStateOf(true) }
    val animateIn = remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val userPreferences = remember { UserPreferences(context) }
    val barcodeNumberScale by userPreferences.barcodeNumberScale.collectAsState(initial = 1.0f)
    val barcodeTitleScale by userPreferences.barcodeTitleScale.collectAsState(initial = 1.0f)
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
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth(0.9f).padding(vertical = 24.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp)
                    ) {
                        Text(
                            text = product.name,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Black,
                                fontSize = 28.sp * barcodeTitleScale
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
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
                        Text(
                            text = product.category.uppercase(),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.secondary,
                            textAlign = TextAlign.Center
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
