from pathlib import Path

p = Path("app/src/main/java/com/example/ui/ProductBarcodeDialog.kt")
s = p.read_text(encoding="utf-8")

state_start = s.index("    val photoUrl = remember(product.imageUrl) {")
state_end = s.index("    var scannerProfile by remember", state_start)

new_state = '''    var photoUrl by remember(product.code, product.imageUrl) {
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
                            photoUrl = uploadedUrl
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

'''
s = s[:state_start] + new_state + s[state_end:]

photo_anchor = s.index("        if (showPhotoDialog && photoUrl != null) {")
text_start = s.index("                text = {", photo_anchor)
alert_end = s.index("            )\n        }\n    }\n}", text_start)

new_actions = '''                text = {
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
                            if (isPhotoLoading || isPhotoSaving) {
                                CircularProgressIndicator()
                            }
                            if (photoLoadFailed && !isPhotoSaving) {
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
                                color = if (message.startsWith("Foto atualizada")) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
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
                                Text(if (isPhotoSaving) "Salvando…" else "Editar Foto")
                            }
                        }
                        TextButton(
                            onClick = { showPhotoDialog = false },
                            enabled = !isPhotoSaving
                        ) {
                            Text("Fechar")
                        }
                    }
                }
'''
s = s[:text_start] + new_actions + s[alert_end:]

if s.count('"Editar Foto"') != 1:
    raise SystemExit("Botão Editar Foto não foi inserido uma única vez")
if s.count("managementRoleForEmail(") < 1:
    raise SystemExit("Proteção Mestre ausente")
if s.count("uploadImageToStorage(") < 1:
    raise SystemExit("Upload de foto ausente")

p.write_text(s, encoding="utf-8")
