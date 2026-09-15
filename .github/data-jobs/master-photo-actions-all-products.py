from pathlib import Path

path = Path('app/src/main/java/com/example/ui/ProductBarcodeDialog.kt')
s = path.read_text(encoding='utf-8')

old = '''                            if (photoUrl != null) {
                                Spacer(modifier = Modifier.weight(1f))
                                TextButton(
                                    onClick = { showPhotoDialog = true },
                                    contentPadding = PaddingValues(horizontal = 4.dp)
                                ) {
                                    Text("Ver Foto do Produto", maxLines = 2, textAlign = TextAlign.End)
                                }
                            }
'''
new = '''                            if (photoUrl != null || isMaster) {
                                Spacer(modifier = Modifier.weight(1f))
                                TextButton(
                                    onClick = { showPhotoDialog = true },
                                    contentPadding = PaddingValues(horizontal = 4.dp)
                                ) {
                                    Text(
                                        if (photoUrl != null) "Ver Foto do Produto" else "Adicionar Foto",
                                        maxLines = 2,
                                        textAlign = TextAlign.End
                                    )
                                }
                            }
'''
if old not in s:
    raise SystemExit('Botão de foto no leitor não encontrado')
s = s.replace(old, new, 1)

old = '''        if (showPhotoDialog && photoUrl != null) {
            var isPhotoLoading by remember(photoUrl, pendingPhotoUrl) { mutableStateOf(true) }
            var photoLoadFailed by remember(photoUrl, pendingPhotoUrl) { mutableStateOf(false) }
'''
new = '''        if (showPhotoDialog && (photoUrl != null || isMaster)) {
            var isPhotoLoading by remember(photoUrl, pendingPhotoUrl) {
                mutableStateOf((pendingPhotoUrl ?: photoUrl) != null)
            }
            var photoLoadFailed by remember(photoUrl, pendingPhotoUrl) { mutableStateOf(false) }
'''
if old not in s:
    raise SystemExit('Condição do diálogo de foto não encontrada')
s = s.replace(old, new, 1)

old = '''                            AsyncImage(
                                model = pendingPhotoUrl ?: photoUrl,
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
'''
new = '''                            val displayedPhotoUrl = pendingPhotoUrl ?: photoUrl
                            if (displayedPhotoUrl != null) {
                                AsyncImage(
                                    model = displayedPhotoUrl,
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
                            } else if (!isPhotoSaving) {
                                Text(
                                    "Este produto ainda não tem foto. Escolha uma imagem ou cole um link.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isPhotoSaving || (displayedPhotoUrl != null && isPhotoLoading)) {
                                CircularProgressIndicator()
                            }
                            if (displayedPhotoUrl != null && photoLoadFailed && !isPhotoSaving) {
                                Text(
                                    "Não foi possível carregar a foto do produto.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
'''
if old not in s:
    raise SystemExit('Bloco de prévia da foto não encontrado')
s = s.replace(old, new, 1)

old = '''                                Text(if (isPhotoSaving) "Salvando…" else "Editar Foto")
'''
new = '''                                Text(
                                    when {
                                        isPhotoSaving -> "Salvando…"
                                        photoUrl == null -> "Adicionar Foto"
                                        else -> "Editar Foto"
                                    }
                                )
'''
if old not in s:
    raise SystemExit('Rótulo Editar Foto não encontrado')
s = s.replace(old, new, 1)

path.write_text(s, encoding='utf-8')
