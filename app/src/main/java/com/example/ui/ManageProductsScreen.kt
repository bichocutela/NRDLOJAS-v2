package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.ui.theme.getDynamicThemeColor
import com.example.ui.theme.LocalGlassSoftStyle
import com.example.ui.theme.glassSoftShadow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.data.Product
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageProductsScreen(viewModel: MainViewModel, onNavigateBack: () -> Unit) {
    val products by viewModel.allProducts.collectAsState()
    val activeCategoryNames by viewModel.activeCategoryNames.collectAsState()
    val appTheme by viewModel.userPreferences.appTheme.collectAsState(initial = "multicolor")
    val glassStyle = LocalGlassSoftStyle.current
    val coroutineScope = rememberCoroutineScope()
    var showDialog by remember { mutableStateOf(false) }
    var editingProduct by remember { mutableStateOf<Product?>(null) }
    var duplicateErrorProduct by remember { mutableStateOf<Product?>(null) }
    var showDuplicateErrorDialog by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var imageUrl by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Gerenciar produtos", style = MaterialTheme.typography.titleLarge)
                        Text("Gerencie produtos e inventário", style = MaterialTheme.typography.labelMedium)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            itemsIndexed(products) { index, product ->
                val dynColors = getDynamicThemeColor(
                    index,
                    appTheme,
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.onPrimaryContainer
                )
                val productCardShape = RoundedCornerShape(18.dp)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassSoftShadow(productCardShape),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (glassStyle.enabled) glassStyle.borderColor else dynColors.first
                    ),
                    shape = productCardShape,
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(product.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Código ${product.code} • ${product.category}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = {
                                if (!isSaving) {
                                    name = product.name
                                    code = product.code
                                    category = product.category.takeIf { it in activeCategoryNames }.orEmpty()
                                    imageUrl = product.imageUrl ?: ""
                                    editingProduct = product
                                    showDialog = true
                                }
                            },
                            enabled = !isSaving
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Editar")
                        }
                    }
                }
            }
        }

        if (showDuplicateErrorDialog && duplicateErrorProduct != null) {
            AlertDialog(
                onDismissRequest = { showDuplicateErrorDialog = false },
                title = { Text("Código já cadastrado") },
                text = { Text("O código ${code.trim()} já pertence ao produto:\n${duplicateErrorProduct!!.name}") },
                confirmButton = {
                    TextButton(onClick = { showDuplicateErrorDialog = false }) {
                        Text("OK")
                    }
                }
            )
        }

        if (showDialog && editingProduct != null) {
            AlertDialog(
                onDismissRequest = { if (!isSaving) showDialog = false },
                title = { Text("Editar Produto") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Nome do Produto") },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isSaving
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = code,
                            onValueChange = { code = it },
                            label = { Text("Código") },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isSaving
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        if (category.isBlank() && editingProduct!!.category !in activeCategoryNames) {
                            Text(
                                text = "Categoria atual (legado): ${editingProduct!!.category}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        OfficialCategoryDropdown(
                            selectedCategory = category,
                            onCategorySelected = { category = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = if (category.isBlank()) "Nova categoria (opcional)" else "Categoria",
                            categories = activeCategoryNames,
                            enabled = !isSaving
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = imageUrl,
                            onValueChange = { imageUrl = it },
                            label = { Text("URL da Imagem (Opcional)") },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isSaving
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (name.isNotBlank() && code.isNotBlank() && !isSaving) {
                                coroutineScope.launch {
                                    val currentProduct = editingProduct ?: return@launch
                                    val existingProduct = viewModel.checkDuplicateCode(code, currentProduct.id)
                                    if (existingProduct != null) {
                                        duplicateErrorProduct = existingProduct
                                        showDuplicateErrorDialog = true
                                        return@launch
                                    }

                                    isSaving = true
                                    try {
                                        val newProduct = currentProduct.copy(
                                            name = name,
                                            code = code,
                                            category = category.ifBlank { currentProduct.category },
                                            imageUrl = imageUrl.takeIf { it.isNotBlank() }
                                        )
                                        val success = viewModel.updateProductSuspend(currentProduct, newProduct)
                                        if (success) {
                                            showDialog = false
                                            editingProduct = null
                                        }
                                    } finally {
                                        isSaving = false
                                    }
                                }
                            }
                        },
                        enabled = !isSaving && name.isNotBlank() && code.isNotBlank()
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Salvando...")
                        } else {
                            Text("Salvar alterações")
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDialog = false }, enabled = !isSaving) {
                        Text("Cancelar")
                    }
                }
            )
        }
    }
}
