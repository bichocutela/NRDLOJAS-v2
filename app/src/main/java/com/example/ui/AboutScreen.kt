package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onNavigateBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current

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
                text = "Sobre o Aplicativo\n\nEste aplicativo foi desenvolvido por Alessandro P., Operador de Caixa, com o objetivo de auxiliar os colaboradores da Frente de Loja na consulta rápida de códigos correlatos, contribuindo para mais agilidade, precisão e eficiência no atendimento aos clientes.\n\nEste projeto nasceu da vivência diária na operação de caixa e da necessidade de tornar a rotina de trabalho mais prática, oferecendo uma ferramenta de apoio aos profissionais da equipe.\n\nRegistro meu sincero agradecimento aos Fiscais de Caixa, pela confiança, incentivo e apoio durante o desenvolvimento desta iniciativa, bem como aos colegas de trabalho, que compartilharam sugestões, experiências e conhecimentos que contribuíram para o aprimoramento do aplicativo.\n\nEste aplicativo foi desenvolvido exclusivamente como uma ferramenta de apoio operacional interno e não substitui os procedimentos, normas, orientações ou sistemas oficiais da empresa.\n\nTodas as marcas, nomes, logotipos, códigos, informações e demais conteúdos relacionados ao Supermercado Nordestão pertencem aos seus respectivos proprietários. Todos os direitos são reservados à empresa. O desenvolvedor não reivindica qualquer direito de propriedade sobre essas informações, utilizando-as unicamente para fins de apoio às atividades internas dos colaboradores.\n\n© 2026 Alessandro P. Todos os direitos do aplicativo são reservados ao autor. O conteúdo institucional e as informações pertencentes ao Supermercado Nordestão permanecem de propriedade exclusiva da empresa.\n\nVersão: ${com.example.BuildConfig.VERSION_NAME}\nDesenvolvedor: Alessandro Paulo\n@bichocutela @haydendanex",
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
            var isChecking by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            var showUpdateDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            var updateTag by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
            var updateUrl by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }

            if (showUpdateDialog) {
                AlertDialog(
                    onDismissRequest = { showUpdateDialog = false },
                    title = { Text("Atualização Disponível") },
                    text = { Text("Uma nova versão ($updateTag) está disponível. Deseja baixar e instalar agora?") },
                    confirmButton = {
                        TextButton(onClick = {
                            showUpdateDialog = false
                            com.example.util.UpdateChecker.downloadAndInstallApk(context, updateUrl, updateTag)
                        }) {
                            Text("Sim")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showUpdateDialog = false }) {
                            Text("Não")
                        }
                    }
                )
            }

            Button(
                onClick = {
                    isChecking = true
                    coroutineScope.launch {
                        val release = com.example.util.UpdateChecker.checkLatestRelease()
                        isChecking = false
                        if (release != null) {
                            val (tag, url) = release
                            val currentVersion = com.example.BuildConfig.VERSION_NAME
                            
                            // Numeric version check
                            val remoteTag = tag.removePrefix("v")
                            val currentParts = currentVersion.split(".").map { it.toIntOrNull() ?: 0 }
                            val remoteParts = remoteTag.split(".").map { it.toIntOrNull() ?: 0 }
                            
                            var isNewer = false
                            val maxLength = maxOf(currentParts.size, remoteParts.size)
                            for (i in 0 until maxLength) {
                                val c = currentParts.getOrElse(i) { 0 }
                                val r = remoteParts.getOrElse(i) { 0 }
                                if (r > c) {
                                    isNewer = true
                                    break
                                } else if (r < c) {
                                    break
                                }
                            }
                            
                            if (isNewer) {
                                updateTag = tag
                                updateUrl = url
                                showUpdateDialog = true
                            } else {
                                android.widget.Toast.makeText(context, "Você já possui a última versão.", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            android.widget.Toast.makeText(context, "Erro ao verificar atualizações.", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isChecking
            ) {
                if (isChecking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Verificar Atualizações")
                }
            }
        }
    }
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
