package com.example.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.util.ImageUrlHelper
import kotlinx.coroutines.launch

@Composable
fun QuickAddProductDialog(
    viewModel: MainViewModel,
    categories: List<String>,
    onDismiss: () -> Unit,
    onSaved: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val expressive = com.example.ui.theme.LocalExpressiveStyle.current.enabled
    val profile = rememberNrdScreenProfile()

    var productName by remember { mutableStateOf("") }
    var productCode by remember { mutableStateOf("") }
    var productCategory by remember { mutableStateOf("") }
    var productImageUrl by remember { mutableStateOf("") }
    var productImageMessage by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isValidatingImage by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    val photoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            productImageUrl = it.toString()
            productImageMessage = "Foto selecionada. Toque em Salvar Produto para confirmar."
            statusMessage = null
        }
    }

    Dialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val shape = RoundedCornerShape(
            when {
                profile.veryCompact -> 22.dp
                profile.compact -> 26.dp
                expressive -> 30.dp
                else -> 24.dp
            }
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth(if (profile.compact) 0.94f else 0.90f)
                .heightIn(max = 720.dp),
            shape = shape,
            color = if (expressive) {
                MaterialTheme.colorScheme.surfaceContainerLow
            } else {
                MaterialTheme.colorScheme.surface
            },
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(
                        horizontal = if (profile.compact) 14.dp else 18.dp,
                        vertical = if (profile.compact) 12.dp else 16.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Novo produto",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                    IconButton(onClick = onDismiss, enabled = !isSaving) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar")
                    }
                }

                Text(
                    "Cadastro rápido do catálogo NRD",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = productName,
                    onValueChange = {
                        productName = it
                        statusMessage = null
                    },
                    label = { Text("Nome do Produto") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false
                )

                OutlinedTextField(
                    value = productCode,
                    onValueChange = {
                        productCode = it
                        statusMessage = null
                    },
                    label = { Text("Código EAN / Interno") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OfficialCategoryDropdown(
                    selectedCategory = productCategory,
                    onCategorySelected = {
                        productCategory = it
                        statusMessage = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    categories = categories,
                    enabled = !isSaving
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            photoLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isValidatingImage && !isSaving
                    ) {
                        Icon(Icons.Default.AddAPhoto, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text("Escolher Foto")
                    }

                    OutlinedButton(
                        onClick = {
                            val copiedUrl = adminClipboardHttpUrl(context)
                            when {
                                copiedUrl == null -> {
                                    productImageMessage = "Nenhum link de imagem foi encontrado."
                                }
                                adminIsGoogleSearchPage(copiedUrl) -> {
                                    productImageMessage = "Abra a imagem e copie o endereço direto da foto."
                                }
                                else -> {
                                    isValidatingImage = true
                                    productImageMessage = "Validando o link da imagem…"
                                    scope.launch {
                                        try {
                                            val request = ImageRequest.Builder(context)
                                                .data(copiedUrl)
                                                .allowHardware(false)
                                                .build()
                                            val result = context.imageLoader.execute(request)
                                            if (result is SuccessResult) {
                                                productImageUrl = copiedUrl
                                                productImageMessage = "Prévia carregada. Toque em Salvar Produto para confirmar."
                                            } else {
                                                productImageMessage = "O link não carregou como imagem."
                                            }
                                        } catch (_: Exception) {
                                            productImageMessage = "Não foi possível validar esse link."
                                        } finally {
                                            isValidatingImage = false
                                        }
                                    }
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isValidatingImage && !isSaving
                    ) {
                        if (isValidatingImage) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Colar Link")
                        }
                    }
                }

                if (productImageUrl.isNotBlank()) {
                    AsyncImage(
                        model = productImageUrl,
                        contentDescription = "Prévia da foto do produto",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 110.dp, max = 210.dp)
                    )
                }

                productImageMessage?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                statusMessage?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(Modifier.height(2.dp))

                Button(
                    onClick = {
                        if (productName.isBlank() || productCode.isBlank() || productCategory !in categories) {
                            statusMessage = "Preencha nome, código e selecione uma categoria oficial."
                            return@Button
                        }
                        scope.launch {
                            isSaving = true
                            statusMessage = null
                            val success = viewModel.addProductSuspend(
                                name = productName,
                                code = productCode,
                                category = productCategory,
                                unit = "un",
                                imageUrl = productImageUrl
                                    .ifBlank { null }
                                    ?.let(ImageUrlHelper::normalizeUrl)
                            )
                            isSaving = false
                            if (success) {
                                onSaved()
                                onDismiss()
                            } else {
                                statusMessage = "Não foi possível salvar. Confira os dados ou tente novamente."
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSaving && !isValidatingImage
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Save, contentDescription = null)
                    }
                    Spacer(Modifier.size(8.dp))
                    Text(if (isSaving) "SALVANDO..." else "SALVAR PRODUTO")
                }
            }
        }
    }
}
