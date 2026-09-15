from pathlib import Path

p = Path("app/src/main/java/com/example/ui/ProductBarcodeDialog.kt")
s = p.read_text(encoding="utf-8")

old_import = "import coil.compose.AsyncImage\n"
new_import = "import coil.compose.AsyncImage\nimport coil.imageLoader\n"
if old_import not in s:
    raise SystemExit("Import do Coil não encontrado")
if "import coil.imageLoader" not in s:
    s = s.replace(old_import, new_import, 1)

old_state = '''    var isPhotoSaving by remember { mutableStateOf(false) }
    var photoEditMessage by remember { mutableStateOf<String?>(null) }
    val photoPicker = androidx.activity.compose.rememberLauncherForActivityResult(
'''
new_state = '''    var isPhotoSaving by remember { mutableStateOf(false) }
    var photoEditMessage by remember { mutableStateOf<String?>(null) }

    fun clipboardHttpUrl(): String? {
        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
            as? android.content.ClipboardManager ?: return null
        val clip = clipboard.primaryClip ?: return null
        val urlRegex = Regex("https?://[^\\\\s<>\\\"']+")
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
'''
if old_state not in s:
    raise SystemExit("Bloco de estado da foto não encontrado")
s = s.replace(old_state, new_state, 1)

old_color = '''                                color = if (message.startsWith("Foto atualizada")) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                }
'''
new_color = '''                                color = when {
                                    message.startsWith("Foto atualizada") -> MaterialTheme.colorScheme.primary
                                    message.startsWith("Validando") -> MaterialTheme.colorScheme.onSurfaceVariant
                                    else -> MaterialTheme.colorScheme.error
                                }
'''
if old_color not in s:
    raise SystemExit("Bloco de cor da mensagem não encontrado")
s = s.replace(old_color, new_color, 1)

old_buttons = '''                        if (isMaster) {
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
'''
new_buttons = '''                        if (isMaster) {
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
                                                        val saved = com.example.data.FirebaseService.saveProduct(
                                                            product.copy(imageUrl = copiedUrl)
                                                        )
                                                        if (saved) {
                                                            photoUrl = copiedUrl
                                                            photoEditMessage = "Foto atualizada por link. Nenhum upload foi feito."
                                                        } else {
                                                            photoEditMessage = "A imagem é válida, mas não foi possível atualizar o produto."
                                                        }
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
'''
if old_buttons not in s:
    raise SystemExit("Bloco dos botões da foto não encontrado")
s = s.replace(old_buttons, new_buttons, 1)

if s.count('Text("Colar Link")') != 1:
    raise SystemExit("Botão Colar Link não ficou único")
if "context.imageLoader.execute(request)" not in s:
    raise SystemExit("Validação real da imagem não foi inserida")
if "product.copy(imageUrl = copiedUrl)" not in s:
    raise SystemExit("Persistência do link não foi inserida")

p.write_text(s, encoding="utf-8")
