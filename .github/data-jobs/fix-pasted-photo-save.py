from pathlib import Path

# 1) ProductBarcodeDialog: preview first, explicit Save, notify local catalog after persist.
p = Path("app/src/main/java/com/example/ui/ProductBarcodeDialog.kt")
s = p.read_text(encoding="utf-8")

old_sig = '''fun ProductBarcodeDialog(
    product: Product,
    onDismiss: () -> Unit,
    highlightedFromNotification: Boolean = false
) {'''
new_sig = '''fun ProductBarcodeDialog(
    product: Product,
    onDismiss: () -> Unit,
    highlightedFromNotification: Boolean = false,
    onProductUpdated: (Product) -> Unit = {}
) {'''
if old_sig not in s:
    raise SystemExit("Assinatura de ProductBarcodeDialog não encontrada")
s = s.replace(old_sig, new_sig, 1)

old_state = '''    var isPhotoSaving by remember { mutableStateOf(false) }
    var photoEditMessage by remember { mutableStateOf<String?>(null) }

    fun clipboardHttpUrl(): String? {'''
new_state = '''    var isPhotoSaving by remember { mutableStateOf(false) }
    var photoEditMessage by remember { mutableStateOf<String?>(null) }
    var pendingPhotoUrl by remember(product.code) { mutableStateOf<String?>(null) }

    fun clipboardHttpUrl(): String? {'''
if old_state not in s:
    raise SystemExit("Estado da foto não encontrado")
s = s.replace(old_state, new_state, 1)

old_upload_success = '''                        if (saved) {
                            photoUrl = uploadedUrl
                            photoEditMessage = "Foto atualizada com sucesso."
                        } else {'''
new_upload_success = '''                        if (saved) {
                            val updatedProduct = product.copy(imageUrl = uploadedUrl)
                            photoUrl = uploadedUrl
                            pendingPhotoUrl = null
                            onProductUpdated(updatedProduct)
                            photoEditMessage = "Foto atualizada com sucesso."
                        } else {'''
if old_upload_success not in s:
    raise SystemExit("Sucesso de upload não encontrado")
s = s.replace(old_upload_success, new_upload_success, 1)

old_dialog_state = '''        if (showPhotoDialog && photoUrl != null) {
            var isPhotoLoading by remember(photoUrl) { mutableStateOf(true) }
            var photoLoadFailed by remember(photoUrl) { mutableStateOf(false) }'''
new_dialog_state = '''        if (showPhotoDialog && photoUrl != null) {
            var isPhotoLoading by remember(photoUrl, pendingPhotoUrl) { mutableStateOf(true) }
            var photoLoadFailed by remember(photoUrl, pendingPhotoUrl) { mutableStateOf(false) }'''
if old_dialog_state not in s:
    raise SystemExit("Estado do diálogo de foto não encontrado")
s = s.replace(old_dialog_state, new_dialog_state, 1)

old_model = '''                                model = photoUrl,
                                contentDescription = product.name,'''
new_model = '''                                model = pendingPhotoUrl ?: photoUrl,
                                contentDescription = product.name,'''
if old_model not in s:
    raise SystemExit("Modelo da AsyncImage não encontrado")
s = s.replace(old_model, new_model, 1)

old_color = '''                                color = when {
                                    message.startsWith("Foto atualizada") -> MaterialTheme.colorScheme.primary
                                    message.startsWith("Validando") -> MaterialTheme.colorScheme.onSurfaceVariant
                                    else -> MaterialTheme.colorScheme.error
                                }'''
new_color = '''                                color = when {
                                    message.startsWith("Foto atualizada") -> MaterialTheme.colorScheme.primary
                                    message.startsWith("Prévia") -> MaterialTheme.colorScheme.onSurfaceVariant
                                    message.startsWith("Validando") -> MaterialTheme.colorScheme.onSurfaceVariant
                                    message.startsWith("Salvando") -> MaterialTheme.colorScheme.onSurfaceVariant
                                    else -> MaterialTheme.colorScheme.error
                                }'''
if old_color not in s:
    raise SystemExit("Cores de mensagem não encontradas")
s = s.replace(old_color, new_color, 1)

old_paste_save = '''                                                    if (imageResult !is coil.request.SuccessResult) {
                                                        photoEditMessage = "O link não carregou como imagem. Abra a imagem em nova guia e copie o endereço direto dela."
                                                    } else {
                                                        val saved = com.example.data.FirebaseService.saveProduct(
                                                            product.copy(imageUrl = copiedUrl)
                                                        )
                                                        if (saved) {
                                                            photoUrl = copiedUrl
                                                            photoEditMessage = "Foto atualizada por link. Nenhum upload foi feito."
                                                        } else {
                                                            photoEditMessage = "A imagem é válida, mas não foi possível atualizar o produto."
                                                        }
                                                    }'''
new_paste_save = '''                                                    if (imageResult !is coil.request.SuccessResult) {
                                                        photoEditMessage = "O link não carregou como imagem. Abra a imagem em nova guia e copie o endereço direto dela."
                                                    } else {
                                                        pendingPhotoUrl = copiedUrl
                                                        photoEditMessage = "Prévia carregada. Toque em Salvar para confirmar esta foto no produto."
                                                    }'''
if old_paste_save not in s:
    raise SystemExit("Salvamento automático do link não encontrado")
s = s.replace(old_paste_save, new_paste_save, 1)

old_after_edit = '''                            ) {
                                Text(if (isPhotoSaving) "Salvando…" else "Editar Foto")
                            }
                            OutlinedButton(
                                onClick = {
                                    val copiedUrl = clipboardHttpUrl()'''
new_after_edit = '''                            ) {
                                Text(if (isPhotoSaving) "Salvando…" else "Editar Foto")
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
                                    val copiedUrl = clipboardHttpUrl()'''
if old_after_edit not in s:
    raise SystemExit("Ponto para inserir botão Salvar não encontrado")
s = s.replace(old_after_edit, new_after_edit, 1)

old_close = '''                        TextButton(
                            onClick = { showPhotoDialog = false },
                            enabled = !isPhotoSaving
                        ) {'''
new_close = '''                        TextButton(
                            onClick = {
                                pendingPhotoUrl = null
                                photoEditMessage = null
                                showPhotoDialog = false
                            },
                            enabled = !isPhotoSaving
                        ) {'''
if old_close not in s:
    raise SystemExit("Botão Fechar do diálogo não encontrado")
s = s.replace(old_close, new_close, 1)

# Dismiss outside also discards an unconfirmed preview.
old_dismiss = '''            AlertDialog(
                onDismissRequest = { showPhotoDialog = false },
                title = { Text("Foto do Produto") },'''
new_dismiss = '''            AlertDialog(
                onDismissRequest = {
                    pendingPhotoUrl = null
                    photoEditMessage = null
                    showPhotoDialog = false
                },
                title = { Text("Foto do Produto") },'''
if old_dismiss not in s:
    raise SystemExit("onDismiss do diálogo não encontrado")
s = s.replace(old_dismiss, new_dismiss, 1)

p.write_text(s, encoding="utf-8")

# 2) MainViewModel: update Room/local catalog after a remote save from the dialog.
p = Path("app/src/main/java/com/example/ui/MainViewModel.kt")
s = p.read_text(encoding="utf-8")
anchor = '''    fun updateProduct(oldProduct: Product, newProduct: Product) {
        viewModelScope.launch { updateProductSuspend(oldProduct, newProduct) }
    }

    suspend fun addProductSuspend('''
replacement = '''    fun updateProduct(oldProduct: Product, newProduct: Product) {
        viewModelScope.launch { updateProductSuspend(oldProduct, newProduct) }
    }

    fun updateProductLocally(product: Product) {
        viewModelScope.launch { repository.updateProduct(product) }
    }

    suspend fun addProductSuspend('''
if anchor not in s:
    raise SystemExit("Âncora para updateProductLocally não encontrada")
s = s.replace(anchor, replacement, 1)
p.write_text(s, encoding="utf-8")

# 3) SearchScreen: keep visible/local product list in sync after photo save.
p = Path("app/src/main/java/com/example/ui/SearchScreen.kt")
s = p.read_text(encoding="utf-8")
old_notification = '''            ProductBarcodeDialog(
                product = product,
                onDismiss = { selectedNotificationProduct = null },
                highlightedFromNotification = true
            )'''
new_notification = '''            ProductBarcodeDialog(
                product = product,
                onDismiss = { selectedNotificationProduct = null },
                highlightedFromNotification = true,
                onProductUpdated = { updated ->
                    selectedNotificationProduct = updated
                    viewModel.updateProductLocally(updated)
                }
            )'''
if old_notification not in s:
    raise SystemExit("Diálogo de notificação não encontrado")
s = s.replace(old_notification, new_notification, 1)

old_most_used = '''            ProductBarcodeDialog(
                product = product,
                onDismiss = { selectedMostUsedProduct = null }
            )'''
new_most_used = '''            ProductBarcodeDialog(
                product = product,
                onDismiss = { selectedMostUsedProduct = null },
                onProductUpdated = { updated ->
                    selectedMostUsedProduct = updated
                    viewModel.updateProductLocally(updated)
                }
            )'''
if old_most_used not in s:
    raise SystemExit("Diálogo de mais utilizados não encontrado")
s = s.replace(old_most_used, new_most_used, 1)
p.write_text(s, encoding="utf-8")
