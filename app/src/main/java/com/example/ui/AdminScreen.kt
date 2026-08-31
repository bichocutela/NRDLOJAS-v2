package com.example.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.ui.theme.getDynamicThemeColor
import com.example.ui.theme.LocalGlassSoftStyle
import com.example.ui.theme.glassSoftShadow
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
    val activeCategoryNames by viewModel.activeCategoryNames.collectAsStateWithLifecycle()
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
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .verticalScroll(adminScrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
                AdminPanelSectionHeader(
                    title = "Ações rápidas",
                    description = "Exportações e ferramentas de administração"
                )
                Spacer(modifier = Modifier.height(8.dp))
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
                Spacer(modifier = Modifier.height(14.dp))
            
            AdminPanelSectionHeader(
                title = "Produtos",
                description = "Cadastre um novo produto ou edite o catálogo existente"
            )
            
            Spacer(modifier = Modifier.height(14.dp))
            
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
                        if (showManualForm) {
                            showManualForm = false
                        } else {
                            productName = ""
                            productCode = ""
                            productCategory = ""
                            productImageUrl = ""
                            showManualForm = true
                        }
                    },
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = if (showManualForm) Icons.Default.Close else Icons.Default.Edit,
                        contentDescription = if (showManualForm) "Recolher formulário" else "Adicionar produto"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (showManualForm) "Recolher formulário" else "Adicionar Produto")
                }
            }
            
            Spacer(modifier = Modifier.height(14.dp))
            
            AnimatedVisibility(
                visible = showManualForm,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                val formCardShape = RoundedCornerShape(20.dp)
                Card(
                    modifier = Modifier.fillMaxWidth().glassSoftShadow(formCardShape),
                    shape = formCardShape,
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text("Novo produto", style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(8.dp))
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
                            modifier = Modifier.fillMaxWidth(),
                            categories = activeCategoryNames
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
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        var isAdding by remember { mutableStateOf(false) }
                Button(
                    onClick = {
                        if (productName.isNotBlank() && productCode.isNotBlank() && productCategory in activeCategoryNames) {
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
            
            Spacer(modifier = Modifier.height(18.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(10.dp))
            AdminPanelSectionHeader(
                title = "Catálogo de produtos",
                description = "Pesquise, edite ou remova itens do inventário"
            )
            Spacer(modifier = Modifier.height(6.dp))
            AdminProductList(
                products = allProducts,
                viewModel = viewModel,
                categories = activeCategoryNames,
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
            .padding(vertical = 3.dp)
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
    categories: List<String>,
    onScrollToTop: () -> Unit
) {
    val glassStyle = LocalGlassSoftStyle.current
    var searchQuery by remember { mutableStateOf("") }
    var pageIndex by remember { mutableStateOf(0) }
    var selectedCodes by remember { mutableStateOf(emptySet<String>()) }
    var showBulkCategoryDialog by remember { mutableStateOf(false) }
    var showBulkDeleteDialog by remember { mutableStateOf(false) }
    var bulkCategory by remember { mutableStateOf(categories.firstOrNull().orEmpty()) }
    var isBulkWorking by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val pageSize = 50
    LaunchedEffect(searchQuery, products.size) {
        pageIndex = 0
    }
    LaunchedEffect(products) {
        val availableCodes = products.map { it.code }.toSet()
        selectedCodes = selectedCodes.intersect(availableCodes)
    }
    
    Text("Gerenciar produtos", style = MaterialTheme.typography.headlineSmall)
    Text(
        "Pesquise e edite o inventário",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(8.dp))
    
    OutlinedTextField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        label = { Text("Pesquisar produto ou categoria") },
        modifier = Modifier.fillMaxWidth(),
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Pesquisar") },
        placeholder = { Text("Pesquisar por nome, código ou categoria") },
        shape = RoundedCornerShape(16.dp)
    )
    Spacer(modifier = Modifier.height(8.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        FilterChip(
            selected = searchQuery.isBlank(),
            onClick = { searchQuery = "" },
            label = { Text("Todos") }
        )
        categories.forEach { category ->
            FilterChip(
                selected = searchQuery == category,
                onClick = { searchQuery = category },
                label = { Text(category) }
            )
        }
    }
    Spacer(modifier = Modifier.height(10.dp))
    
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
    val selectedProducts = products.filter { it.code in selectedCodes }

    if (searchQuery.isBlank()) {
        Text(
            text = "Digite o nome, código ou selecione uma categoria para carregar os produtos.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 10.dp)
        )
    } else if (filteredProducts.isEmpty()) {
        Text(
            text = "Nenhum produto encontrado.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 10.dp)
        )
    } else {
        val selectionContentColor = if (glassStyle.enabled) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            Color.White
        }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .glassSoftShadow(MaterialTheme.shapes.medium),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "${selectedProducts.size} selecionado(s)",
                        style = MaterialTheme.typography.titleSmall,
                        color = selectionContentColor
                    )
                    TextButton(
                        onClick = {
                            val pageCodes = productsToRender.map { it.code }.toSet()
                            selectedCodes = if (pageCodes.isNotEmpty() && pageCodes.all { it in selectedCodes }) {
                                selectedCodes - pageCodes
                            } else {
                                selectedCodes + pageCodes
                            }
                        }
                    ) {
                        Text(
                            if (productsToRender.isNotEmpty() && productsToRender.all { it.code in selectedCodes }) {
                                "Limpar página"
                            } else {
                                "Selecionar página"
                            },
                            color = selectionContentColor
                        )
                    }
                }
                if (selectedProducts.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = {
                                bulkCategory = categories.firstOrNull().orEmpty()
                                showBulkCategoryDialog = true
                            },
                            enabled = categories.isNotEmpty() && !isBulkWorking,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = selectionContentColor),
                            border = androidx.compose.foundation.BorderStroke(1.dp, selectionContentColor),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Alterar categoria", maxLines = 1, color = selectionContentColor)
                        }
                        Button(
                            onClick = { showBulkDeleteDialog = true },
                            enabled = !isBulkWorking,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Excluir", maxLines = 1, color = Color.White)
                        }
                    }
                }
            }
        }

        val groupedProducts = productsToRender.groupBy { it.category }
        groupedProducts.forEach { (category, categoryProducts) ->
            Text(
                text = category,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 6.dp)
            )
            categoryProducts.forEach { product ->
                AdminProductItem(
                    product = product,
                    viewModel = viewModel,
                    categories = categories,
                    isSelected = product.code in selectedCodes,
                    onSelectionChanged = { selected ->
                        selectedCodes = if (selected) selectedCodes + product.code else selectedCodes - product.code
                    }
                )
            }
        }
        if (pageCount > 0) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Página atual: ${currentPage + 1} | Total de páginas: $pageCount",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp)
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
                    Spacer(modifier = Modifier.width(8.dp))
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
                Spacer(modifier = Modifier.height(8.dp))
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

    if (showBulkCategoryDialog) {
        AlertDialog(
            onDismissRequest = { if (!isBulkWorking) showBulkCategoryDialog = false },
            title = { Text("Alterar categoria") },
            text = {
                Column {
                    Text("Escolha a categoria para ${selectedProducts.size} produto(s).")
                    Spacer(modifier = Modifier.height(8.dp))
                    OfficialCategoryDropdown(
                        selectedCategory = bulkCategory,
                        onCategorySelected = { bulkCategory = it },
                        modifier = Modifier.fillMaxWidth(),
                        categories = categories
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            isBulkWorking = true
                            val success = viewModel.updateSelectedProductsCategory(selectedProducts, bulkCategory)
                            isBulkWorking = false
                            if (success) {
                                selectedCodes = emptySet()
                                showBulkCategoryDialog = false
                            }
                        }
                    },
                    enabled = !isBulkWorking && bulkCategory.isNotBlank()
                ) {
                    Text(if (isBulkWorking) "Salvando..." else "Salvar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showBulkCategoryDialog = false },
                    enabled = !isBulkWorking
                ) { Text("Cancelar") }
            }
        )
    }

    if (showBulkDeleteDialog) {
        AlertDialog(
            onDismissRequest = { if (!isBulkWorking) showBulkDeleteDialog = false },
            title = { Text("Excluir produtos selecionados?") },
            text = {
                Text("Esta ação removerá ${selectedProducts.size} produto(s) para todos os usuários e não poderá ser desfeita.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            isBulkWorking = true
                            val success = viewModel.deleteSelectedProducts(selectedProducts)
                            isBulkWorking = false
                            if (success) {
                                selectedCodes = emptySet()
                                showBulkDeleteDialog = false
                            }
                        }
                    },
                    enabled = !isBulkWorking && selectedProducts.isNotEmpty()
                ) {
                    Text(if (isBulkWorking) "Excluindo..." else "Excluir", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showBulkDeleteDialog = false },
                    enabled = !isBulkWorking
                ) { Text("Cancelar") }
            }
        )
    }
}

@Composable
fun AdminProductItem(
    product: Product,
    viewModel: MainViewModel,
    categories: List<String>,
    isSelected: Boolean,
    onSelectionChanged: (Boolean) -> Unit
) {
    val productCardShape = RoundedCornerShape(18.dp)
    var isEditing by remember { mutableStateOf(false) }
    var editCode by remember(product.code) { mutableStateOf(product.code) }
    var editName by remember(product.name) { mutableStateOf(product.name) }
    var editCategory by remember(product.category, categories) {
        mutableStateOf(product.category.takeIf { it in categories }.orEmpty())
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
            .padding(vertical = 3.dp)
            .glassSoftShadow(productCardShape),
        shape = productCardShape,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = onSelectionChanged
                )
                if (product.imageUrl != null) {
                    AsyncImage(
                        model = product.imageUrl,
                        contentDescription = product.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
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
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = product.name, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(2.dp))
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
                if (editCategory.isBlank() && product.category !in categories) {
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
                    label = if (editCategory.isBlank()) "Nova categoria (opcional)" else "Categoria",
                    categories = categories
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
                
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(10.dp))
                
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
