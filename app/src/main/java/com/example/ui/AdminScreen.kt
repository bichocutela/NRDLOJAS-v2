package com.example.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Sync
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
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(viewModel: MainViewModel, onNavigateBack: () -> Unit) {
    var showManualForm by remember { mutableStateOf(false) }
    var productName by remember { mutableStateOf("") }
    var productCode by remember { mutableStateOf("") }
    var productCategory by remember { mutableStateOf("") }
    var productImageUrl by remember { mutableStateOf("") }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val adminScrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    val allProducts by viewModel.allProducts.collectAsStateWithLifecycle()
    val exportProducts: () -> Unit = {
        scope.launch {
            val path = com.example.util.PdfExporter.exportProductsToPdf(context, allProducts)
            snackbarHostState.showSnackbar(
                if (path != null) "PDF salvo na pasta Downloads."
                else "Não foi possível exportar o PDF."
            )
        }
    }
    val legacyStoragePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) exportProducts()
        else scope.launch { snackbarHostState.showSnackbar("Não foi possível exportar o PDF.") }
    }

    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val appTheme by viewModel.userPreferences.appTheme.collectAsStateWithLifecycle(initialValue = "multicolor")


    LaunchedEffect(Unit) {
        viewModel.syncMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
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
                .verticalScroll(adminScrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
                AdminPanelSectionHeader(
                    title = "Ações rápidas",
                    description = "Exportações e ferramentas de administração"
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    colors = ButtonDefaults.buttonColors(
                        containerColor = getDynamicThemeColor(0, appTheme, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary).first,
                        contentColor = getDynamicThemeColor(0, appTheme, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary).second
                    ),
                    onClick = {
                        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                        ) {
                            legacyStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        } else {
                            exportProducts()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Exportar Produtos em PDF")
                }
                Spacer(modifier = Modifier.height(24.dp))
            
            AdminPanelSectionHeader(
                title = "Produtos",
                description = "Cadastre um novo produto ou edite o catálogo existente"
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    colors = ButtonDefaults.buttonColors(
                        containerColor = getDynamicThemeColor(1, appTheme, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary).first,
                        contentColor = getDynamicThemeColor(1, appTheme, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary).second
                    ),
                    onClick = {
                        productName = ""
                        productCode = ""
                        productCategory = ""
                        productImageUrl = ""
                        showManualForm = true
                    },
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Adicionar Produto")
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
            
            Spacer(modifier = Modifier.height(32.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))
            AdminPanelSectionHeader(
                title = "Catálogo de produtos",
                description = "Pesquise, edite ou remova itens do inventário"
            )
            Spacer(modifier = Modifier.height(8.dp))
            AdminProductList(
                products = allProducts,
                viewModel = viewModel,
                onScrollToTop = {
                    scope.launch { adminScrollState.animateScrollTo(0) }
                }
            )

        }
    }
}

@Composable
private fun AdminPanelSectionHeader(
    title: String,
    description: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun AdminProductList(
    products: List<Product>,
    viewModel: MainViewModel,
    onScrollToTop: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var pageIndex by remember { mutableStateOf(0) }
    val pageSize = 50
    LaunchedEffect(searchQuery, products.size) {
        pageIndex = 0
    }
    
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
    val pageCount = if (filteredProducts.isEmpty()) {
        0
    } else {
        (filteredProducts.size + pageSize - 1) / pageSize
    }
    val currentPage = pageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
    val productsToRender = if (searchQuery.isBlank()) {
        emptyList()
    } else {
        filteredProducts
            .drop(currentPage * pageSize)
            .take(pageSize)
    }

    if (searchQuery.isBlank()) {
        Text(
            text = "Digite o nome, código ou selecione uma categoria para carregar os produtos.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 16.dp)
        )
    } else if (filteredProducts.isEmpty()) {
        Text(
            text = "Nenhum produto encontrado.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 16.dp)
        )
    } else {
        val groupedProducts = productsToRender.groupBy { it.category }
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
        if (pageCount > 0) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Página atual: ${currentPage + 1} | Total de páginas: $pageCount",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { pageIndex = (currentPage - 1).coerceAtLeast(0) },
                        enabled = currentPage > 0,
                        modifier = Modifier.width(140.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text("Anterior", maxLines = 1)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Button(
                        onClick = { pageIndex = (currentPage + 1).coerceAtMost(pageCount - 1) },
                        enabled = currentPage < pageCount - 1,
                        modifier = Modifier.width(140.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text("Próxima", maxLines = 1)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onScrollToTop,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary
                    ),
                    modifier = Modifier.width(220.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Voltar pro Topo", maxLines = 1)
                }
            }
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
