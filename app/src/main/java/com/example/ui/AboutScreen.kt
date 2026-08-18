package com.example.ui

import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.ui.Alignment
import android.graphics.Bitmap
import android.content.ClipboardManager
import android.content.ClipData
import android.graphics.Color
import com.google.zxing.MultiFormatWriter
import com.google.zxing.BarcodeFormat


import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Android
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.ClickableText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import java.util.Calendar
import com.example.R

private const val IPHONE_PWA_URL = "https://nrdpwa-i6x25cjv.manus.space/"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onNavigateBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
    val copyrightYear = if (currentYear <= 2026) "2026" else "2026-$currentYear"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sobre") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = "Sobre o Aplicativo\n\nEste aplicativo foi desenvolvido por Alessandro P., Operador de Caixa, com o objetivo de auxiliar os colaboradores da Frente de Loja na consulta rápida de códigos correlatos, contribuindo para mais agilidade, precisão e eficiência no atendimento aos clientes.\n\nEste projeto nasceu da vivência diária na operação de caixa e da necessidade de tornar a rotina de trabalho mais prática, oferecendo uma ferramenta de apoio aos profissionais da equipe.\n\nRegistro meu sincero agradecimento aos Fiscais de Caixa, pela confiança, incentivo e apoio durante o desenvolvimento desta iniciativa, bem como aos colegas de trabalho, que compartilharam sugestões, experiências e conhecimentos que contribuíram para o aprimoramento do aplicativo.\n\nEste aplicativo foi desenvolvido exclusivamente como uma ferramenta de apoio operacional interno e não substitui os procedimentos, normas, orientações ou sistemas oficiais da empresa.\n\nTodas as marcas, nomes, logotipos, códigos, informações e demais conteúdos relacionados ao Supermercado Nordestão pertencem aos seus respectivos proprietários. Todos os direitos são reservados à empresa. O desenvolvedor não reivindica qualquer direito de propriedade sobre essas informações, utilizando-as unicamente para fins de apoio às atividades internas dos colaboradores.\n\n© $copyrightYear Alessandro P. Todos os direitos do aplicativo são reservados ao autor. O conteúdo institucional e as informações pertencentes ao Supermercado Nordestão permanecem de propriedade exclusiva da empresa.\n\nVersão: ${com.example.BuildConfig.VERSION_NAME}\nDesenvolvedor: Alessandro Paulo\n@bichocutela @haydendanex",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(16.dp))
            
            LinkText(
                text = "Site: ",
                linkText = "https://www.nordestaomaisvoce.com.br/",
                url = "https://www.nordestaomaisvoce.com.br/",
                uriHandler = uriHandler
            )
            
            LinkText(
                text = "App Nossa Gente: ",
                linkText = "https://app.nordestao.com.br/",
                url = "https://app.nordestao.com.br/",
                uriHandler = uriHandler
            )
            
            LinkText(
                text = "Nordestão Pra Você: ",
                linkText = "https://pravoce.nordestao.com.br/home",
                url = "https://pravoce.nordestao.com.br/home",
                uriHandler = uriHandler
            )
            
            LinkText(
                text = "Encarte: ",
                linkText = "https://pravoce.nordestao.com.br/tabloids",
                url = "https://pravoce.nordestao.com.br/tabloids",
                uriHandler = uriHandler
            )

            Spacer(modifier = Modifier.height(24.dp))

            val context = androidx.compose.ui.platform.LocalContext.current
            val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
            var updateTag by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
            var updateUrl by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }

            
            var isGeneratingQr by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            var showQrDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            var qrBitmap by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Bitmap?>(null) }
            var qrReleaseTag by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
            var qrReleaseUrl by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
            var qrPlatform by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("android") }
            var showQrErrorDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            var qrErrorMessage by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("Verifique sua conexão e tente novamente.") }
            var updateAvailable by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

            androidx.compose.runtime.LaunchedEffect(Unit) {
                when (val releaseResult = com.example.util.UpdateChecker.checkLatestRelease()) {
                    is com.example.util.ReleaseCheckResult.Success -> {
                        if (isRemoteVersionNewer(com.example.BuildConfig.VERSION_NAME, releaseResult.tagName)) {
                            updateTag = releaseResult.tagName
                            updateUrl = releaseResult.downloadUrl
                            updateAvailable = true
                        }
                    }
                    else -> Unit
                }
            }

            
            if (showQrDialog && qrBitmap != null) {
                AlertDialog(
                    onDismissRequest = { showQrDialog = false },
                    title = { Text(if (qrPlatform == "iphone") "Compartilhar pra iPhone" else "Compartilhar pra Android") },
                    text = {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (qrPlatform == "iphone") "NRD Códigos para iPhone" else "Versão disponível: $qrReleaseTag",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Image(
                                bitmap = qrBitmap!!.asImageBitmap(),
                                contentDescription = if (qrPlatform == "iphone") "QR Code para instalar NRD Códigos no iPhone" else "QR Code para baixar a versão Android",
                                modifier = Modifier.size(200.dp),
                                contentScale = ContentScale.Fit
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                if (qrPlatform == "iphone") {
                                    "Escaneie no iPhone, abra no Safari, toque em Compartilhar e depois em Adicionar à Tela de Início."
                                } else {
                                    "Escaneie para baixar a versão Android mais recente."
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clipData = ClipData.newPlainText("Link de Download", qrReleaseUrl)
                            clipboardManager.setPrimaryClip(clipData)
                            android.widget.Toast.makeText(context, "Link copiado!", android.widget.Toast.LENGTH_SHORT).show()
                        }) {
                            Text("Copiar link")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showQrDialog = false }) {
                            Text("Fechar")
                        }
                    }
                )
            }

            if (showQrErrorDialog) {
                AlertDialog(
                    onDismissRequest = { showQrErrorDialog = false },
                    title = { Text("Não foi possível gerar o QR Code no momento.") },
                    text = { Text(qrErrorMessage) },
                    confirmButton = {
                        TextButton(onClick = {
                            showQrErrorDialog = false
                            isGeneratingQr = true
                            coroutineScope.launch {
                                val releaseResult = com.example.util.UpdateChecker.checkLatestRelease()
                                isGeneratingQr = false
                                when (releaseResult) {
                                    is com.example.util.ReleaseCheckResult.Success -> {
                                        val tag = releaseResult.tagName
                                        val url = releaseResult.downloadUrl
                                        qrReleaseTag = tag
                                        qrReleaseUrl = url
                                        try {
                                            val writer = MultiFormatWriter()
                                            val bitMatrix = writer.encode(url, BarcodeFormat.QR_CODE, 512, 512)
                                            val width = bitMatrix.width
                                            val height = bitMatrix.height
                                            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
                                            for (x in 0 until width) {
                                                for (y in 0 until height) {
                                                    bmp.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
                                                }
                                            }
                                            qrBitmap = bmp
                                            qrPlatform = "android"
                                            showQrDialog = true
                                        } catch (e: Exception) {
                                            qrErrorMessage = "A resposta foi obtida, mas não foi possível gerar o QR Code."
                                            showQrErrorDialog = true
                                        }
                                    }
                                    else -> {
                                        qrErrorMessage = releaseFailureMessage(releaseResult)
                                        showQrErrorDialog = true
                                    }
                                }
                            }
                        }) {
                            Text("Tentar novamente")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showQrErrorDialog = false }) {
                            Text("Fechar")
                        }
                    }
                )
            }

            if (updateAvailable) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Atualização disponível",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "A versão $updateTag está disponível. Toque abaixo para baixar e instalar a atualização.",
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                com.example.util.UpdateChecker.downloadAndInstallApk(
                                    context,
                                    updateUrl,
                                    updateTag
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = updateUrl.isNotBlank()
                        ) {
                            Text("Baixar agora")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Text(
                text = "Escolhe e Compartilhe",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = {
                    isGeneratingQr = true
                    coroutineScope.launch {
                        val releaseResult = com.example.util.UpdateChecker.checkLatestRelease()
                        isGeneratingQr = false
                        when (releaseResult) {
                            is com.example.util.ReleaseCheckResult.Success -> {
                                val tag = releaseResult.tagName
                                val url = releaseResult.downloadUrl
                                qrReleaseTag = tag
                                qrReleaseUrl = url
                                try {
                                    val writer = MultiFormatWriter()
                                    val bitMatrix = writer.encode(url, BarcodeFormat.QR_CODE, 512, 512)
                                    val width = bitMatrix.width
                                    val height = bitMatrix.height
                                    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
                                    for (x in 0 until width) {
                                        for (y in 0 until height) {
                                            bmp.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
                                        }
                                    }
                                    qrBitmap = bmp
                                    qrPlatform = "android"
                                    showQrDialog = true
                                } catch (e: Exception) {
                                    qrErrorMessage = "A resposta foi obtida, mas não foi possível gerar o QR Code."
                                    showQrErrorDialog = true
                                }
                            }
                            else -> {
                                qrErrorMessage = releaseFailureMessage(releaseResult)
                                showQrErrorDialog = true
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isGeneratingQr
            ) {
                if (isGeneratingQr) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Android, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Compartilhar pra Android")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = {
                    try {
                        val writer = MultiFormatWriter()
                        val bitMatrix = writer.encode(IPHONE_PWA_URL, BarcodeFormat.QR_CODE, 512, 512)
                        val bmp = Bitmap.createBitmap(bitMatrix.width, bitMatrix.height, Bitmap.Config.RGB_565)
                        for (x in 0 until bitMatrix.width) {
                            for (y in 0 until bitMatrix.height) {
                                bmp.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
                            }
                        }
                        qrReleaseTag = "PWA NRD Códigos"
                        qrReleaseUrl = IPHONE_PWA_URL
                        qrPlatform = "iphone"
                        qrBitmap = bmp
                        showQrDialog = true
                    } catch (e: Exception) {
                        qrErrorMessage = "Não foi possível gerar o QR Code para iPhone."
                        showQrErrorDialog = true
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_apple_logo),
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Compartilhar pra iPhone")
            }

        }
    }
}

private fun isRemoteVersionNewer(currentVersion: String, remoteTag: String): Boolean {
    val currentParts = currentVersion.split(".").map { it.toIntOrNull() ?: 0 }
    val remoteParts = remoteTag.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
    val maxLength = maxOf(currentParts.size, remoteParts.size)
    for (index in 0 until maxLength) {
        val current = currentParts.getOrElse(index) { 0 }
        val remote = remoteParts.getOrElse(index) { 0 }
        if (remote > current) return true
        if (remote < current) return false
    }
    return false
}

private fun releaseFailureMessage(result: com.example.util.ReleaseCheckResult): String = when (result) {
    is com.example.util.ReleaseCheckResult.NetworkError -> "Sem internet: ${result.message}."
    is com.example.util.ReleaseCheckResult.InvalidResponse -> "Resposta inválida do GitHub: ${result.message}."
    is com.example.util.ReleaseCheckResult.HttpError -> {
        val rateLimitDetails = listOfNotNull(
            result.rateLimitRemaining?.let { "restante: $it" },
            result.rateLimitReset?.let { "reset: $it" },
            result.retryAfter?.let { "tente novamente após: $it" }
        ).joinToString(", ")
        if (result.responseCode == 403 || result.responseCode == 429) {
            "Limite da API do GitHub atingido (HTTP ${result.responseCode}). " +
                "${result.message}" +
                if (rateLimitDetails.isNotBlank()) " [$rateLimitDetails]" else ""
        } else {
            "Erro HTTP ${result.responseCode} no GitHub: ${result.message}"
        }
    }
    is com.example.util.ReleaseCheckResult.Success -> ""
}

@Composable
fun LinkText(text: String, linkText: String, url: String, uriHandler: androidx.compose.ui.platform.UriHandler) {
    val annotatedString = buildAnnotatedString {
        withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.onBackground)) {
            append(text)
        }
        pushStringAnnotation(tag = "URL", annotation = url)
        withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline, fontWeight = FontWeight.Bold)) {
            append(linkText)
        }
        pop()
    }

    ClickableText(
        text = annotatedString,
        style = MaterialTheme.typography.bodyMedium,
        onClick = { offset ->
            annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                .firstOrNull()?.let { annotation ->
                    uriHandler.openUri(annotation.item)
                }
        },
        modifier = Modifier.padding(vertical = 4.dp)
    )
}
