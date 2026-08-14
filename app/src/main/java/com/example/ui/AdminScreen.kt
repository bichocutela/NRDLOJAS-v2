package com.example.ui

import android.content.Intent
import android.util.Log
import androidx.compose.material.icons.filled.Sync
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import com.example.data.Product
import com.example.data.ProductStandards
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.ui.theme.getDynamicThemeColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import retrofit2.HttpException
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.put

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(viewModel: MainViewModel, onNavigateBack: () -> Unit) {
    var showManualForm by remember { mutableStateOf(false) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    
    var productName by remember { mutableStateOf("") }
    var productCode by remember { mutableStateOf("") }
    var productCategory by remember { mutableStateOf("") }
    var productImageUrl by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
    val allProducts by viewModel.allProducts.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val appTheme by viewModel.userPreferences.appTheme.collectAsStateWithLifecycle(initialValue = "multicolor")


    LaunchedEffect(Unit) {
        viewModel.syncMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            showManualForm = true // Show form to verify
            productName = ""
            productCode = ""
            productCategory = ""
                        productImageUrl = ""
            
            // Convert URI to Bitmap
            try {
                selectedBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = ImageDecoder.createSource(context.contentResolver, it)
                    ImageDecoder.decodeBitmap(source)
                } else {
                    MediaStore.Images.Media.getBitmap(context.contentResolver, it)
                }
                
                // Process image with Gemini
                scope.launch {
                    isProcessing = true
                    statusMessage = "A IA está analisando a imagem..."
                    
                    try {
                        val detectedCode = detectBarcodeFromBitmap(selectedBitmap!!)
                        if (!detectedCode.isNullOrBlank()) {
                            productCode = detectedCode
                            statusMessage = "Código detectado pelo leitor local."
                        }

                        val apiKey = BuildConfig.GEMINI_API_KEY.trim()
                        if (apiKey.isEmpty() || apiKey.equals("dummy", ignoreCase = true)) {
                            statusMessage = "IA não configurada nesta versão."
                            return@launch
                        }

                        val result = analyzeImage(selectedBitmap!!, detectedCode)
                        if (result != null) {
                            productName = result.name
                            if (detectedCode.isNullOrBlank()) productCode = result.code
                            productCategory = ProductStandards.categoryFromSuggestion(result.category).orEmpty()
                            statusMessage = "Análise concluída. Verifique as informações."
                        } else {
                            statusMessage = "Não foi possível identificar todos os dados com IA. Complete manualmente."
                        }
                    } catch (e: HttpException) {
                        statusMessage = when (e.code()) {
                            429 -> "Limite gratuito da IA atingido. Complete manualmente."
                            401, 403 -> "Serviço de IA não autorizado. Complete manualmente."
                            else -> "Não foi possível identificar todos os dados com IA. Complete manualmente."
                        }
                    } catch (_: Exception) {
                        statusMessage = "Não foi possível identificar todos os dados com IA. Complete manualmente."
                    } finally {
                        isProcessing = false
                    }
                }
            } catch (e: Exception) {
                statusMessage = "Erro ao carregar a imagem."
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                title = {
                    Column {
                        Text("Painel Administrativo", style = MaterialTheme.typography.titleLarge)
                        Text("Gerencie produtos e inventário", style = MaterialTheme.typography.labelMedium)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
                Text(
                    "Ações rápidas",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    colors = ButtonDefaults.buttonColors(
                        containerColor = getDynamicThemeColor(0, appTheme, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary).first,
                        contentColor = getDynamicThemeColor(0, appTheme, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary).second
                    ),
                    onClick = {
                        scope.launch {
                            val path = com.example.util.PdfExporter.exportProductsToPdf(context, allProducts)
                            if (path != null) {
                                snackbarHostState.showSnackbar("Inventário exportado para PDF: $path")
                            } else {
                                snackbarHostState.showSnackbar("Erro ao exportar PDF.")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Exportar PDF")
                }
                Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Adicionar produto",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    colors = ButtonDefaults.buttonColors(
                        containerColor = getDynamicThemeColor(1, appTheme, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary).first,
                        contentColor = getDynamicThemeColor(1, appTheme, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary).second
                    ),
                    onClick = {
                        launcher.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.AddAPhoto, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Por Foto (IA)")
                }
                
                Button(
                    colors = ButtonDefaults.buttonColors(
                        containerColor = getDynamicThemeColor(2, appTheme, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary).first,
                        contentColor = getDynamicThemeColor(2, appTheme, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary).second
                    ),
                    onClick = {
                        selectedImageUri = null
                        selectedBitmap = null
                        productName = ""
                        productCode = ""
                        productCategory = ""
                        productImageUrl = ""
                        showManualForm = true
                        statusMessage = null
                    },
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Manualmente")
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            if (showManualForm) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Text("Novo produto", style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(12.dp))
                        if (selectedBitmap != null) {
                            Image(
                                bitmap = selectedBitmap!!.asImageBitmap(),
                                contentDescription = "Imagem do produto",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .padding(bottom = 16.dp)
                            )
                        }
                        
                        if (isProcessing) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                        
                        OutlinedTextField(
                            value = productName,
                            onValueChange = { productName = it },
                            label = { Text("Nome do Produto") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        OutlinedTextField(
                            value = productCode,
                            onValueChange = { productCode = it },
                            label = { Text("Código EAN / Interno") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        OfficialCategoryDropdown(
                            selectedCategory = productCategory,
                            onCategorySelected = { productCategory = it },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        val manualLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                            contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
                        ) { uri ->
                            uri?.let {
                                try {
                                    val flag = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    context.contentResolver.takePersistableUriPermission(it, flag)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                                productImageUrl = it.toString()
                            }
                        }
                        Button(
                            onClick = {
                                manualLauncher.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.AddAPhoto, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("ADICIONAR FOTO (OPCIONAL)", color = MaterialTheme.colorScheme.primary)
                        }
                        if (productImageUrl.isNotBlank()) {
                            Text(text = "Foto selecionada", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(top = 4.dp))
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        var isAdding by remember { mutableStateOf(false) }
                Button(
                    onClick = {
                        if (productName.isNotBlank() && productCode.isNotBlank() && ProductStandards.isOfficialCategory(productCategory)) {
                            scope.launch {
                                isAdding = true
                                val success = viewModel.addProductSuspend(
                                    name = productName,
                                    code = productCode,
                                    category = productCategory,
                                    unit = "un",
                                    imageUrl = productImageUrl.ifBlank { null }?.let { com.example.util.ImageUrlHelper.normalizeUrl(it) }
                                )
                                isAdding = false
                                if (success) {
                                    showManualForm = false
                                    snackbarHostState.showSnackbar("Produto adicionado com sucesso!")
                                }
                            }
                        } else {
                            scope.launch {
                                snackbarHostState.showSnackbar("Preencha nome, código e selecione uma categoria oficial.")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isAdding
                ) {
                    if (isAdding) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("SALVANDO...")
                    } else {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("SALVAR PRODUTO")
                    }
                }
                    }
                }
            }
            
            if (statusMessage != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = statusMessage!!,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))
            AdminProductList(allProducts, viewModel)

        }
    }
}

data class ProductAnalysisResult(val name: String, val code: String, val category: String)

suspend fun analyzeImage(bitmap: Bitmap, detectedCode: String? = null): ProductAnalysisResult? = withContext(Dispatchers.IO) {
    val apiKey = BuildConfig.GEMINI_API_KEY.trim()
    if (apiKey.isEmpty() || apiKey.equals("dummy", ignoreCase = true)) return@withContext null
    try {
        val codeContext = detectedCode?.let { " O código de barras detectado com confiança pelo ML Kit é $it; não o substitua." }.orEmpty()
        val prompt = "Analise a imagem deste produto. Identifique principalmente o nome e escolha somente uma categoria entre: Açougue, Cafeteria, Frios, Hortifruti, Mercearia ou Padaria. O código deve ser informado apenas como apoio quando não houver código detectado localmente.$codeContext Retorne apenas um JSON com as chaves: 'nome', 'codigo', 'categoria'."
        
        val requestBody = GenerateContentRequest(
            contents = listOf(Content(
                parts = listOf(
                    Part(text = prompt),
                    Part(inlineData = InlineData(mimeType = "image/jpeg", data = bitmap.toBase64()))
                )
            )),
            generationConfig = GenerationConfig(
                responseFormat = ResponseFormat(
                    text = ResponseFormatText(
                        mimeType = "application/json",
                        schema = buildJsonObject {
                            put("type", "OBJECT")
                            putJsonObject("properties") {
                                putJsonObject("nome") {
                                    put("type", "STRING")
                                    put("description", "O nome do produto.")
                                }
                                putJsonObject("codigo") {
                                    put("type", "STRING")
                                    put("description", "O código EAN ou numérico do produto.")
                                }
                                putJsonObject("categoria") {
                                    put("type", "STRING")
                                    put("description", "A categoria do produto.")
                                }
                            }
                        }
                    )
                )
            )
        )
        
        val response = RetrofitClient.service.generateContent(apiKey, requestBody)
        val responseText = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
        
        if (responseText != null) {
            val jsonResponse = Json.parseToJsonElement(responseText).jsonObject
            val name = jsonResponse["nome"]?.jsonPrimitive?.content ?: ""
            val code = jsonResponse["codigo"]?.jsonPrimitive?.content ?: ""
            val category = jsonResponse["categoria"]?.jsonPrimitive?.content ?: ""
            return@withContext ProductAnalysisResult(name, code, category)
        }
        null
    } catch (e: HttpException) {
        throw e
    } catch (e: Exception) {
        Log.e("AdminScreen", "Falha na análise da imagem", e)
        null
    }
}

private suspend fun detectBarcodeFromBitmap(bitmap: Bitmap): String? = suspendCancellableCoroutine { continuation ->
    val options = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
        .build()
    val scanner = BarcodeScanning.getClient(options)
    scanner.process(InputImage.fromBitmap(bitmap, 0))
        .addOnSuccessListener { barcodes ->
            continuation.resume(barcodes.firstNotNullOfOrNull { it.rawValue })
        }
        .addOnFailureListener { error ->
            Log.w("AdminScreen", "ML Kit não detectou código na imagem", error)
            continuation.resume(null)
        }
        .addOnCompleteListener { scanner.close() }
}

@Composable
fun AdminProductList(products: List<Product>, viewModel: MainViewModel) {
    var searchQuery by remember { mutableStateOf("") }
    
    Text("Gerenciar produtos", style = MaterialTheme.typography.headlineSmall)
    Text(
        "Pesquise e edite o inventário",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(12.dp))
    
    OutlinedTextField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        label = { Text("Pesquisar produto ou categoria") },
        modifier = Modifier.fillMaxWidth(),
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Pesquisar") },
        placeholder = { Text("Pesquisar por nome, código ou categoria") },
        shape = RoundedCornerShape(16.dp)
    )
    Spacer(modifier = Modifier.height(12.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = searchQuery.isBlank(),
            onClick = { searchQuery = "" },
            label = { Text("Todos") }
        )
        ProductStandards.officialCategories.forEach { category ->
            FilterChip(
                selected = searchQuery == category,
                onClick = { searchQuery = category },
                label = { Text(category) }
            )
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
    
    val filteredProducts = products.filter {
        it.name.contains(searchQuery, ignoreCase = true) ||
        it.category.contains(searchQuery, ignoreCase = true) ||
        it.code.contains(searchQuery, ignoreCase = true)
    }
    
    val groupedProducts = filteredProducts.groupBy { it.category }
    
    groupedProducts.forEach { (category, categoryProducts) ->
        Text(
            text = category,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        categoryProducts.forEach { product ->
            AdminProductItem(product, viewModel)
        }
    }
}

@Composable
fun AdminProductItem(product: Product, viewModel: MainViewModel) {
    var isEditing by remember { mutableStateOf(false) }
    var editCode by remember(product.code) { mutableStateOf(product.code) }
    var editName by remember(product.name) { mutableStateOf(product.name) }
    var editCategory by remember(product.category) {
        mutableStateOf(product.category.takeIf { ProductStandards.isOfficialCategory(it) }.orEmpty())
    }
    var editImageUrl by remember(product.imageUrl) { mutableStateOf(product.imageUrl ?: "") }
    val context = LocalContext.current
    
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val flag = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(it, flag)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            editImageUrl = it.toString()
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (product.imageUrl != null) {
                    AsyncImage(
                        model = product.imageUrl,
                        contentDescription = product.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = product.name.take(1).uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = product.name, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Código ${product.code} • ${product.category}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { isEditing = !isEditing }) {
                    Icon(if (isEditing) Icons.Default.Close else Icons.Default.Edit, contentDescription = "Editar")
                }
            }
            if (isEditing) {
                var isSaving by remember { mutableStateOf(false) }
                var isDeleting by remember { mutableStateOf(false) }
                val coroutineScope = rememberCoroutineScope()
                OutlinedTextField(
                    value = editName,
                    onValueChange = { editName = it },
                    label = { Text("Nome do Produto") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = editCode,
                    onValueChange = { editCode = it },
                    label = { Text("Novo Código") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (editCategory.isBlank() && !ProductStandards.isOfficialCategory(product.category)) {
                    Text(
                        text = "Categoria atual (legado): ${product.category}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                OfficialCategoryDropdown(
                    selectedCategory = editCategory,
                    onCategorySelected = { editCategory = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = if (editCategory.isBlank()) "Nova categoria (opcional)" else "Categoria"
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        launcher.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    enabled = !isSaving && !isDeleting
                ) {
                    Icon(Icons.Default.AddAPhoto, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("ADICIONAR/TROCAR FOTO", color = MaterialTheme.colorScheme.primary)
                }
                if (editImageUrl.isNotBlank()) {
                    Text(text = "Foto selecionada", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(top = 4.dp))
                }
                Spacer(modifier = Modifier.height(8.dp))

                
                var showRemovePhotoDialog by remember { mutableStateOf(false) }
                if (product.imageUrl != null) {
                    Button(
                        onClick = { showRemovePhotoDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        enabled = !isSaving && !isDeleting
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("EXCLUIR FOTO", color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (showRemovePhotoDialog) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { showRemovePhotoDialog = false },
                        title = { Text("Remover a foto deste produto?") },
                        text = { Text("A foto será excluída para todos os usuários.") },
                        confirmButton = {
                            var isRemovingPhoto by remember { mutableStateOf(false) }
                            androidx.compose.material3.TextButton(
                                onClick = {
                                    coroutineScope.launch {
                                        isRemovingPhoto = true
                                        val success = viewModel.removeProductImage(product)
                                        isRemovingPhoto = false
                                        if (success) {
                                            showRemovePhotoDialog = false
                                            isEditing = false
                                        }
                                    }
                                },
                                enabled = !isRemovingPhoto
                            ) {
                                Text(if (isRemovingPhoto) "Removendo..." else "Remover Foto", color = MaterialTheme.colorScheme.error)
                            }
                        },
                        dismissButton = {
                            androidx.compose.material3.TextButton(onClick = { showRemovePhotoDialog = false }) {
                                Text("Cancelar")
                            }
                        }
                    )
                }

                Button(
                    onClick = {
                        coroutineScope.launch {
                            isSaving = true
                            val searchName = editName.lowercase().replace(Regex("[áàâã]"), "a").replace(Regex("[éèê]"), "e").replace(Regex("[íìî]"), "i").replace(Regex("[óòôõ]"), "o").replace(Regex("[úùû]"), "u").replace(Regex("[ç]"), "c")
                            val newProduct = product.copy(
                                code = editCode, 
                                name = editName, 
                                category = editCategory.ifBlank { product.category },
                                searchName = searchName,
                                imageUrl = editImageUrl.ifBlank { null }?.let { com.example.util.ImageUrlHelper.normalizeUrl(it) }
                            )
                            val success = viewModel.updateProductSuspend(product, newProduct)
                            isSaving = false
                            if (success) {
                                isEditing = false
                            }
                        }
                    },
                    modifier = Modifier.align(Alignment.End),
                    enabled = !isSaving && !isDeleting
                ) {
                    if (isSaving) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Salvando...")
                    } else {
                        Text("Salvar")
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))
                
                var showDeleteProductDialog by remember { mutableStateOf(false) }
                Button(
                    onClick = { showDeleteProductDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    enabled = !isSaving && !isDeleting
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.onError)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("EXCLUIR PRODUTO", color = MaterialTheme.colorScheme.onError)
                }

                if (showDeleteProductDialog) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { showDeleteProductDialog = false },
                        title = { Text("Excluir este produto?") },
                        text = { Text("Esta ação removerá o produto para todos os usuários.") },
                        confirmButton = {
                            androidx.compose.material3.TextButton(
                                onClick = {
                                    coroutineScope.launch {
                                        isDeleting = true
                                        val success = viewModel.deleteProductSuspend(product)
                                        isDeleting = false
                                        if (success) {
                                            showDeleteProductDialog = false
                                            isEditing = false
                                        }
                                    }
                                },
                                enabled = !isDeleting
                            ) {
                                Text(if (isDeleting) "Excluindo..." else "Excluir", color = MaterialTheme.colorScheme.error)
                            }
                        },
                        dismissButton = {
                            androidx.compose.material3.TextButton(onClick = { showDeleteProductDialog = false }) {
                                Text("Cancelar")
                            }
                        }
                    )
                }
            } else {
                Text(text = "Código: ${product.code}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}
