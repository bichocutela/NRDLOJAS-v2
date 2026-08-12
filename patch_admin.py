import sys

with open("app/src/main/java/com/example/ui/AdminScreen.kt", "r") as f:
    content = f.read()

target = """                        var isAdding by remember { mutableStateOf(false) }
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
                            scope.launch { snackbarHostState.showSnackbar("Nome e código são obrigatórios.") }
                        }
                    },"""

replacement = """                        var isAdding by remember { mutableStateOf(false) }
                        var duplicateErrorProduct by remember { mutableStateOf<com.example.data.Product?>(null) }
                        var showDuplicateErrorDialog by remember { mutableStateOf(false) }

                        if (showDuplicateErrorDialog && duplicateErrorProduct != null) {
                            androidx.compose.material3.AlertDialog(
                                onDismissRequest = { showDuplicateErrorDialog = false },
                                title = { Text("Código já cadastrado") },
                                text = { Text("O código ${productCode.trim()} já pertence ao produto:\\n${duplicateErrorProduct!!.name}") },
                                confirmButton = {
                                    androidx.compose.material3.TextButton(onClick = { showDuplicateErrorDialog = false }) {
                                        Text("OK")
                                    }
                                }
                            )
                        }

                Button(
                    onClick = {
                        if (productName.isNotBlank() && productCode.isNotBlank()) {
                            scope.launch {
                                isAdding = true
                                val existingProduct = viewModel.checkDuplicateCode(productCode)
                                if (existingProduct != null) {
                                    duplicateErrorProduct = existingProduct
                                    showDuplicateErrorDialog = true
                                    isAdding = false
                                } else {
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
                            }
                        } else {
                            scope.launch { snackbarHostState.showSnackbar("Nome e código são obrigatórios.") }
                        }
                    },"""

content = content.replace(target, replacement)

with open("app/src/main/java/com/example/ui/AdminScreen.kt", "w") as f:
    f.write(content)
