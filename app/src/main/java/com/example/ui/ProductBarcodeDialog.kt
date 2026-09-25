package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import coil.compose.AsyncImage
import coil.imageLoader
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.Product
import com.example.data.UserPreferences
import com.example.ui.theme.LocalGlassSoftStyle
import com.example.ui.theme.LocalExpressiveStyle
import com.example.ui.theme.glassSoftShadow
import com.example.ui.theme.expressiveShadow
import com.example.util.ImageUrlHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val barcodeBitmapCache = android.util.LruCache<String, androidx.compose.ui.graphics.ImageBitmap>(12)

@Composable
fun ProductBarcodeDialog(
    product: Product,
    onDismiss: () -> Unit,
    highlightedFromNotification: Boolean = false,
    onProductUpdated: (Product) -> Unit = {},
    onProductCodeChanged: (suspend (Product, String) -> Boolean)? = null,
    onProductDeleted: (suspend (Product) -> Boolean)? = null
) {
    val showDialog = remember { mutableStateOf(true) }
    val visibilityState = remember {
        MutableTransitionState(false).apply { targetState = true }
    }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val screenProfile = rememberNrdScreenProfile()
    val userPreferences = remember { UserPreferences(context) }
    val barcodeNumberScale by userPreferences.barcodeNumberScale.collectAsState(initial = 1.0f)
    val barcodeTitleScale by userPreferences.barcodeTitleScale.collectAsState(initial = 1.0f)
    val boldOutline by userPreferences.boldOutline.collectAsState(initial = false)
    val uppercaseBold by userPreferences.uppercaseBold.collectAsState(initial = false)
    val glassSoftStyle = LocalGlassSoftStyle.current
    val expressiveStyle = LocalExpressiveStyle.current
    val isExpressive = expressiveStyle.enabled
    var photoUrl by remember(product.code, product.imageUrl) {
        mutableStateOf(
            product.imageUrl
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let(ImageUrlHelper::normalizeUrl)
                ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
        )
    }
    var showPhotoDialog by remember { mutableStateOf(false) }
    val isMaster = managementRoleForEmail(
        com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email
    ) == "mestre"
    var isPhotoSaving by remember { mutableStateOf(false) }
    var photoEditMessage by remember { mutableStateOf<String?>(null) }
    var pendingPhotoUrl by remember(product.code) { mutableStateOf<String?>(null) }
    var showCodeEditDialog by remember { mutableStateOf(false) }
    var codeEditValue by remember(product.code) { mutableStateOf(product.code) }
    var isCodeSaving by remember { mutableStateOf(false) }
    var codeEditMessage by remember(product.code) { mutableStateOf<String?>(null) }
    var showDeleteProductDialog by remember { mutableStateOf(false) }
    var isDeletingProduct by remember { mutableStateOf(false) }
    var deleteProductMessage by remember(product.code) { mutableStateOf<String?>(null) }

    fun clipboardHttpUrl(): String? {
        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
            as? android.content.ClipboardManager ?: return null
        val clip = clipboard.primaryClip ?: return null
        val urlRegex = Regex("https?://[^\\s<>\"']+")
        for (index in 0 until clip.itemCount) {
            val item = clip.getItemAt(index)
            val candidates = listOfNotNull(
                item.text?.toString(),
                item.uri?.toString(),
                item.intent?.dataString,
                item.coerceToText(context)?.toString()
            )
            for (raw in candidates) {
                val candidate = urlRegex.find(raw.trim())?.value
                    ?.trimEnd('.', ',', ';', ')', ']', '}')
                if (!candidate.isNullOrBlank()) return candidate
            }
        }
        return null
    }

    fun isGoogleSearchPage(url: String): Boolean {
        return try {
            val parsed = android.net.Uri.parse(url)
            val host = parsed.host?.lowercase().orEmpty()
            val path = parsed.path?.lowercase().orEmpty()
            val isGoogleHost = host == "google.com" || host == "google.com.br" ||
                host.startsWith("www.google.") || host.startsWith("images.google.")
            isGoogleHost && (
                path.startsWith("/search") ||
                    path.startsWith("/imgres") ||
                    path.startsWith("/lens")
                )
        } catch (_: Exception) {
            false
        }
    }

    val photoPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null && isMaster && !isPhotoSaving) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
                // O seletor já concede acesso temporário; persistência é apenas garantia extra.
            }
            isPhotoSaving = true
            photoEditMessage = null
            coroutineScope.launch {
                try {
                    val uploadedUrl = com.example.data.FirebaseService.uploadImageToStorage(
                        uri,
                        "products/${product.code}_${System.currentTimeMillis()}.jpg"
                    )
                    if (uploadedUrl == null) {
                        photoEditMessage = "Não foi possível enviar a nova foto. Tente novamente."
                    } else {
                        val saved = com.example.data.FirebaseService.saveProduct(
                            product.copy(imageUrl = uploadedUrl)
                        )
                        if (saved) {
                            val updatedProduct = product.copy(imageUrl = uploadedUrl)
                            photoUrl = uploadedUrl
                            pendingPhotoUrl = null
                            onProductUpdated(updatedProduct)
                            photoEditMessage = "Foto atualizada com sucesso."
                        } else {
                            photoEditMessage = "A foto foi enviada, mas não foi possível atualizar o produto."
                        }
                    }
                } catch (_: Exception) {
                    photoEditMessage = "Não foi possível atualizar a foto. Verifique a conexão e tente novamente."
                } finally {
                    isPhotoSaving = false
                }
            }
        }
    }

    var scannerProfile by remember { mutableStateOf("Padrão") }
    var zoomPercent by remember { mutableIntStateOf(100) }

    fun closeDialog() {
        visibilityState.targetState = false
        coroutineScope.launch {
            delay(100)
            showDialog.value = false
            onDismiss()
        }
    }

    if (showDialog.value) {
        Dialog(
            onDismissRequest = { closeDialog() },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            AnimatedVisibility(
                visibleState = visibilityState,
                enter = fadeIn(tween(140)),
                exit = fadeOut(tween(100))
            ) {
                val dialogShape = RoundedCornerShape(
                    when {
                        isExpressive && screenProfile.veryCompact -> 24.dp
                        isExpressive && screenProfile.compact -> 28.dp
                        isExpressive -> 36.dp
                        else -> 32.dp
                    }
                )
                Surface(
                    shape = dialogShape,
                    color = when {
                        glassSoftStyle.enabled -> MaterialTheme.colorScheme.surface
                        isExpressive -> MaterialTheme.colorScheme.surfaceContainerLow
                        else -> MaterialTheme.colorScheme.surface
                    },
                    border = if (highlightedFromNotification) {
                        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                    } else if (glassSoftStyle.enabled) {
                        BorderStroke(1.dp, glassSoftStyle.borderColor)
                    } else {
                        null
                    },
                    modifier = Modifier
                        .fillMaxWidth(
                            when {
                                screenProfile.veryCompact -> 0.98f
                                screenProfile.compact -> 0.95f
                                else -> 0.88f
                            }
                        )
                        .then(
                            if (screenProfile.tablet) Modifier.widthIn(max = screenProfile.dialogMaxWidth)
                            else Modifier
                        )
                        .padding(
                            vertical = when {
                                screenProfile.veryCompact -> 4.dp
                                screenProfile.compact -> 8.dp
                                else -> 14.dp
                            }
                        )
                        .glassSoftShadow(dialogShape)
                        .expressiveShadow(dialogShape, if (screenProfile.compact) 7.dp else 10.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(
                                when {
                                    screenProfile.veryCompact -> 10.dp
                                    screenProfile.compact -> 14.dp
                                    else -> 20.dp
                                }
                            )
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

                        SelectionContainer {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                StylizedText(
                                    text = product.name,
                                    baseStyle = MaterialTheme.typography.headlineMedium.copy(
                                        fontWeight = FontWeight.Black,
                                        fontSize = (if (screenProfile.compact) 23.sp else 28.sp) * barcodeTitleScale
                                    ),
                                    boldOutline = boldOutline,
                                    uppercaseBold = uppercaseBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.fillMaxWidth(),
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(if (screenProfile.compact) 8.dp else 12.dp))
                                Text(
                                    text = product.code,
                                    style = MaterialTheme.typography.displayMedium.copy(
                                        fontWeight = FontWeight.Black,
                                        fontSize = (if (screenProfile.compact) 34.sp else 42.sp) * barcodeNumberScale,
                                        color = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(if (screenProfile.compact) 4.dp else 6.dp))
                                StylizedText(
                                    text = product.category,
                                    baseStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    boldOutline = boldOutline,
                                    uppercaseBold = true,
                                    color = if (isExpressive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(if (screenProfile.compact) 6.dp else 8.dp))

                        ActiveFlyerOffersForProduct(product)

                        Spacer(modifier = Modifier.height(if (screenProfile.compact) 8.dp else 10.dp))

                        val barcodeCacheKey = "${product.code}|$scannerProfile"
                        val barcodeBitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(
                            initialValue = barcodeBitmapCache.get(barcodeCacheKey),
                            barcodeCacheKey
                        ) {
                            if (value == null) {
                                value = withContext(Dispatchers.Default) {
                                    generateBarcodeBitmap(product.code, scannerProfile)
                                }?.also { generated ->
                                    barcodeBitmapCache.put(barcodeCacheKey, generated)
                                }
                            }
                        }
                        val renderedBarcode = barcodeBitmap
                        if (renderedBarcode != null) {
                            val targetHeight = when (scannerProfile) {
                                "Symbol" -> if (screenProfile.compact) 108.dp else 120.dp
                                "Datalogic" -> if (screenProfile.compact) 116.dp else 128.dp
                                else -> if (screenProfile.compact) 94.dp else 106.dp
                            }
                            val widthFraction = (0.9f * (zoomPercent / 100f)).coerceAtMost(1.0f)

                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = renderedBarcode,
                                    contentDescription = "Código de barras",
                                    contentScale = ContentScale.Fit,
                                    filterQuality = FilterQuality.None,
                                    modifier = Modifier
                                        .fillMaxWidth(widthFraction)
                                        .height(targetHeight)
                                        .background(Color.White)
                                )
                            }
                            Spacer(modifier = Modifier.height(if (screenProfile.compact) 8.dp else 10.dp))
                        }

                        SelectionContainer {
                            Text(
                                text = "Código de barras / Referência",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        Spacer(modifier = Modifier.height(if (screenProfile.compact) 10.dp else 14.dp))
                        Divider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(modifier = Modifier.height(if (screenProfile.compact) 8.dp else 10.dp))

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
                            Spacer(modifier = Modifier.weight(1f))
                            if (isMaster && onProductCodeChanged != null) {
                                TextButton(
                                    onClick = {
                                        codeEditValue = product.code
                                        codeEditMessage = null
                                        showCodeEditDialog = true
                                    },
                                    contentPadding = PaddingValues(horizontal = 4.dp)
                                ) {
                                    Text(
                                        "Alterar Código",
                                        maxLines = 2,
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.End
                                    )
                                }
                            }
                            if (isMaster && onProductDeleted != null) {
                                TextButton(
                                    onClick = {
                                        deleteProductMessage = null
                                        showDeleteProductDialog = true
                                    },
                                    contentPadding = PaddingValues(horizontal = 4.dp),
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Text(
                                        "Excluir",
                                        maxLines = 1,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                            if (photoUrl != null || isMaster) {
                                TextButton(
                                    onClick = { showPhotoDialog = true },
                                    contentPadding = PaddingValues(horizontal = 4.dp)
                                ) {
                                    Text(
                                        if (photoUrl != null) "Ver Foto do Produto" else "Adicionar Foto",
                                        maxLines = 2,
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.End
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(if (screenProfile.compact) 4.dp else 6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(if (screenProfile.veryCompact) 4.dp else 8.dp)
                        ) {
                            listOf("Padrão", "Symbol", "Datalogic").forEach { profile ->
                                if (scannerProfile == profile) {
                                    Button(
                                        onClick = { scannerProfile = profile },
                                        modifier = Modifier.weight(1f),
                                        shape = if (isExpressive) RoundedCornerShape(18.dp) else MaterialTheme.shapes.small,
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text(profile, fontSize = if (screenProfile.veryCompact) 10.sp else 12.sp, maxLines = 1)
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = { scannerProfile = profile },
                                        modifier = Modifier.weight(1f),
                                        shape = if (isExpressive) RoundedCornerShape(18.dp) else MaterialTheme.shapes.small,
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text(profile, fontSize = if (screenProfile.veryCompact) 10.sp else 12.sp, maxLines = 1)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(if (screenProfile.compact) 8.dp else 10.dp))
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
                                modifier = Modifier.padding(horizontal = if (screenProfile.compact) 10.dp else 14.dp)
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

                        Spacer(modifier = Modifier.height(if (screenProfile.compact) 10.dp else 14.dp))
                        Button(
                            onClick = { closeDialog() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = if (screenProfile.compact) 50.dp else 54.dp)
                                .expressiveShadow(
                                    if (isExpressive) RoundedCornerShape(24.dp) else RoundedCornerShape(24.dp),
                                    6.dp
                                ),
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("FECHAR", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            }
        }

        if (showCodeEditDialog && isMaster && onProductCodeChanged != null) {
            AlertDialog(
                onDismissRequest = {
                    if (!isCodeSaving) {
                        codeEditMessage = null
                        showCodeEditDialog = false
                    }
                },
                title = { Text("Alterar Código") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            product.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Código atual: ${product.code}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = codeEditValue,
                            onValueChange = { codeEditValue = it },
                            label = { Text("Novo Código") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            enabled = !isCodeSaving,
                            modifier = Modifier.fillMaxWidth()
                        )
                        codeEditMessage?.let { message ->
                            Text(
                                message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val newCode = codeEditValue.trim()
                            when {
                                newCode.isBlank() -> codeEditMessage = "Informe o novo código."
                                newCode == product.code -> codeEditMessage = "Digite um código diferente do atual."
                                else -> coroutineScope.launch {
                                    isCodeSaving = true
                                    codeEditMessage = null
                                    try {
                                        val changed = onProductCodeChanged(product, newCode)
                                        if (changed) {
                                            onProductUpdated(product.copy(code = newCode))
                                            showCodeEditDialog = false
                                        } else {
                                            codeEditMessage = "Não foi possível alterar o código."
                                        }
                                    } catch (_: Exception) {
                                        codeEditMessage = "Não foi possível alterar o código. Tente novamente."
                                    } finally {
                                        isCodeSaving = false
                                    }
                                }
                            }
                        },
                        enabled = !isCodeSaving
                    ) {
                        if (isCodeSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(if (isCodeSaving) "Salvando..." else "Salvar")
                    }
                },
                dismissButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            onClick = {
                                codeEditValue = product.code
                                codeEditMessage = null
                            },
                            enabled = !isCodeSaving && codeEditValue != product.code
                        ) {
                            Text("Reverter")
                        }
                        TextButton(
                            onClick = { showCodeEditDialog = false },
                            enabled = !isCodeSaving
                        ) {
                            Text("Cancelar")
                        }
                    }
                }
            )
        }

        if (showDeleteProductDialog && isMaster && onProductDeleted != null) {
            AlertDialog(
                onDismissRequest = {
                    if (!isDeletingProduct) {
                        deleteProductMessage = null
                        showDeleteProductDialog = false
                    }
                },
                title = { Text("Excluir produto") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Deseja realmente excluir este produto do catálogo?")
                        Text(
                            product.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Código: ${product.code}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "A exclusão será aplicada para todos os usuários.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        deleteProductMessage?.let { message ->
                            Text(
                                message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                isDeletingProduct = true
                                deleteProductMessage = null
                                try {
                                    val deleted = onProductDeleted(product)
                                    if (deleted) {
                                        showDeleteProductDialog = false
                                        closeDialog()
                                    } else {
                                        deleteProductMessage = "Não foi possível excluir o produto."
                                    }
                                } catch (_: Exception) {
                                    deleteProductMessage = "Não foi possível excluir o produto. Tente novamente."
                                } finally {
                                    isDeletingProduct = false
                                }
                            }
                        },
                        enabled = !isDeletingProduct,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        if (isDeletingProduct) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(if (isDeletingProduct) "Excluindo..." else "Excluir produto")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showDeleteProductDialog = false },
                        enabled = !isDeletingProduct
                    ) {
                        Text("Cancelar")
                    }
                }
            )
        }

        if (showPhotoDialog && (photoUrl != null || isMaster)) {
            var isPhotoLoading by remember(photoUrl, pendingPhotoUrl) {
                mutableStateOf((pendingPhotoUrl ?: photoUrl) != null)
            }
            var photoLoadFailed by remember(photoUrl, pendingPhotoUrl) { mutableStateOf(false) }
            AlertDialog(
                onDismissRequest = {
                    pendingPhotoUrl = null
                    photoEditMessage = null
                    showPhotoDialog = false
                },
                title = { Text("Foto do Produto") },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 180.dp, max = 360.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val displayedPhotoUrl = pendingPhotoUrl ?: photoUrl
                            if (displayedPhotoUrl != null) {
                                AsyncImage(
                                    model = displayedPhotoUrl,
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
                            } else if (!isPhotoSaving) {
                                Text(
                                    "Este produto ainda não tem foto. Escolha uma imagem ou cole um link.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isPhotoSaving || (displayedPhotoUrl != null && isPhotoLoading)) {
                                CircularProgressIndicator()
                            }
                            if (displayedPhotoUrl != null && photoLoadFailed && !isPhotoSaving) {
                                Text(
                                    "Não foi possível carregar a foto do produto.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        photoEditMessage?.let { message ->
                            Text(
                                message,
                                style = MaterialTheme.typography.bodySmall,
                                color = when {
                                    message.startsWith("Foto atualizada") -> MaterialTheme.colorScheme.primary
                                    message.startsWith("Prévia") -> MaterialTheme.colorScheme.onSurfaceVariant
                                    message.startsWith("Validando") -> MaterialTheme.colorScheme.onSurfaceVariant
                                    message.startsWith("Salvando") -> MaterialTheme.colorScheme.onSurfaceVariant
                                    else -> MaterialTheme.colorScheme.error
                                }
                            )
                        }
                    }
                },
                confirmButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (isMaster) {
                            OutlinedButton(
                                onClick = {
                                    photoEditMessage = null
                                    photoPicker.launch(
                                        androidx.activity.result.PickVisualMediaRequest(
                                            androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                                        )
                                    )
                                },
                                enabled = !isPhotoSaving
                            ) {
                                Text(
                                    when {
                                        isPhotoSaving -> "Salvando…"
                                        photoUrl == null -> "Adicionar Foto"
                                        else -> "Editar Foto"
                                    }
                                )
                            }
                            if (pendingPhotoUrl != null) {
                                Button(
                                    onClick = {
                                        val urlToSave = pendingPhotoUrl ?: return@Button
                                        isPhotoSaving = true
                                        photoEditMessage = "Salvando foto neste produto…"
                                        coroutineScope.launch {
                                            try {
                                                val updatedProduct = product.copy(imageUrl = urlToSave)
                                                val saved = com.example.data.FirebaseService.saveProduct(updatedProduct)
                                                if (saved) {
                                                    photoUrl = urlToSave
                                                    pendingPhotoUrl = null
                                                    onProductUpdated(updatedProduct)
                                                    photoEditMessage = "Foto atualizada e salva neste produto."
                                                } else {
                                                    photoEditMessage = "Não foi possível salvar a foto neste produto. Tente novamente."
                                                }
                                            } catch (_: Exception) {
                                                photoEditMessage = "Não foi possível salvar a foto. Verifique a conexão e tente novamente."
                                            } finally {
                                                isPhotoSaving = false
                                            }
                                        }
                                    },
                                    enabled = !isPhotoSaving
                                ) {
                                    Text("Salvar")
                                }
                            }
                            OutlinedButton(
                                onClick = {
                                    val copiedUrl = clipboardHttpUrl()
                                    when {
                                        copiedUrl == null -> {
                                            photoEditMessage = "Nenhum link de imagem foi encontrado. Copie o endereço da imagem no navegador e tente novamente."
                                        }
                                        isGoogleSearchPage(copiedUrl) -> {
                                            photoEditMessage = "Esse é um link da pesquisa do Google, não da foto. Abra a imagem em nova guia, copie o endereço da imagem e tente novamente."
                                        }
                                        else -> {
                                            isPhotoSaving = true
                                            photoEditMessage = "Validando o link da imagem…"
                                            coroutineScope.launch {
                                                try {
                                                    val request = coil.request.ImageRequest.Builder(context)
                                                        .data(copiedUrl)
                                                        .allowHardware(false)
                                                        .build()
                                                    val imageResult = context.imageLoader.execute(request)
                                                    if (imageResult !is coil.request.SuccessResult) {
                                                        photoEditMessage = "O link não carregou como imagem. Abra a imagem em nova guia e copie o endereço direto dela."
                                                    } else {
                                                        pendingPhotoUrl = copiedUrl
                                                        photoEditMessage = "Prévia carregada. Toque em Salvar para confirmar esta foto no produto."
                                                    }
                                                } catch (_: Exception) {
                                                    photoEditMessage = "Não foi possível validar esse link. Verifique a conexão ou tente outro endereço de imagem."
                                                } finally {
                                                    isPhotoSaving = false
                                                }
                                            }
                                        }
                                    }
                                },
                                enabled = !isPhotoSaving
                            ) {
                                Text("Colar Link")
                            }
                        }
                        TextButton(
                            onClick = {
                                pendingPhotoUrl = null
                                photoEditMessage = null
                                showPhotoDialog = false
                            },
                            enabled = !isPhotoSaving
                        ) {
                            Text("Fechar")
                        }
                    }
                }
            )
        }
    }
}
