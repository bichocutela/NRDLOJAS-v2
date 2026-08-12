package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.ui.theme.getDynamicThemeColor
import androidx.compose.ui.unit.dp
import com.example.data.Product
import kotlinx.coroutines.launch


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageProductsScreen(viewModel: MainViewModel, onNavigateBack: () -> Unit) {
    val products by viewModel.allProducts.collectAsState()
    val appTheme by viewModel.userPreferences.appTheme.collectAsState(initial = "multicolor")
    val coroutineScope = rememberCoroutineScope()
    var showDialog by remember { mutableStateOf(false) }
    var editingProduct by remember { mutableStateOf<Product?>(null) }
    var duplicateErrorProduct by remember { mutableStateOf<Product?>(null) }
    var showDuplicateErrorDialog by remember { mutableStateOf(false) }
    
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var imageUrl by remember { mutableStateOf("") }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gerenciar Produtos") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding).fillMaxSize()
        ) {
            itemsIndexed(products) { index, product ->
                val dynColors = getDynamicThemeColor(index, appTheme, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, dynColors.first)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(product.name, style = MaterialTheme.typography.titleMedium)
                            Text("Código: ${product.code} | Categoria: ${product.category}", style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = {
                            name = product.name
                            code = product.code
                            category = product.category
                            imageUrl = product.imageUrl ?: ""
                            editingProduct = product
                            showDialog = true
                        }) {
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
                onDismissRequest = { showDialog = false },
                title = { Text("Editar Produto") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Nome do Produto") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = code,
                            onValueChange = { code = it },
                            label = { Text("Código") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = category,
                            onValueChange = { category = it },
                            label = { Text("Categoria") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = imageUrl,
                            onValueChange = { imageUrl = it },
                            label = { Text("URL da Imagem (Opcional)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (name.isNotBlank() && code.isNotBlank()) {
                            coroutineScope.launch {
                                val existingProduct = viewModel.checkDuplicateCode(code, editingProduct!!.id)
                                if (existingProduct != null) {
                                    duplicateErrorProduct = existingProduct
                                    showDuplicateErrorDialog = true
                                } else {
                                    val newProduct = editingProduct!!.copy(
                                        name = name,
                                        code = code,
                                        category = category,
                                        imageUrl = imageUrl.takeIf { it.isNotBlank() }
                                    )
                                    viewModel.updateProduct(editingProduct!!, newProduct)
                                    showDialog = false
                                }
                            }
                        }
                    }) {
                        Text("Salvar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDialog = false }) {
                        Text("Cancelar")
                    }
                }
            )
        }
    }
}
