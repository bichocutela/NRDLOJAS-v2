package com.example.ui

import android.content.Intent
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import com.example.data.Product
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
                        val result = analyzeImage(selectedBitmap!!)
                        if (result != null) {
                            productName = result.name
                            productCode = result.code
                            productCategory = result.category
                            statusMessage = "Análise concluída. Verifique as informações."
                        } else {
                            statusMessage = "Erro na análise. Preencha manualmente."
                        }
                    } catch (e: Exception) {
                        statusMessage = "Erro: ${e.message}"
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
                title = { Text("Painel Administrativo") },
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
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
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Exportar Inventário (PDF)")
                }
                Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                text = "Adicionar Novo Produto",
                style = MaterialTheme.typography.titleLarge
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
                    }
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
                    }
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
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
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
                        
                        OutlinedTextField(
                            value = productCategory,
                            onValueChange = { productCategory = it },
                            label = { Text("Categoria") },
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
                        if (productName.isNotBlank() && productCode.isNotBlank()) {
                            scope.launch {
                                isAdding = true
                                val success = viewModel.addProductSuspend(
                                    name = productName,
                                    code = productCode,
                                    category = if (productCategory.isNotBlank()) productCategory else "Geral",
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
                                snackbarHostState.showSnackbar("Preencha o nome e código.")
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

suspend fun analyzeImage(bitmap: Bitmap): ProductAnalysisResult? = withContext(Dispatchers.IO) {
    try {
        val apiKey = BuildConfig.GEMINI_API_KEY
        val prompt = "Analise a imagem deste produto. Identifique o nome do produto, seu código (EAN ou número em destaque) e a categoria mais provável (ex: Açougue, Padaria, Hortifruti, Bebidas, etc). Retorne apenas um JSON com as chaves: 'nome', 'codigo', 'categoria'."
        
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
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

@Composable
fun AdminProductList(products: List<Product>, viewModel: MainViewModel) {
    var searchQuery by remember { mutableStateOf("") }
    
    Text("Gerenciar Produtos", style = MaterialTheme.typography.titleLarge)
    Spacer(modifier = Modifier.height(16.dp))
    
    OutlinedTextField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        label = { Text("Pesquisar produto ou categoria") },
        modifier = Modifier.fillMaxWidth(),
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Pesquisar") }
    )
    
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
    var editCategory by remember(product.category) { mutableStateOf(product.category) }
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
                Text(text = product.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
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
                OutlinedTextField(
                    value = editCategory,
                    onValueChange = { editCategory = it },
                    label = { Text("Categoria") },
                    modifier = Modifier.fillMaxWidth()
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
                                category = editCategory.ifBlank { "Geral" },
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
