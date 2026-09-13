package com.example.ui

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AppearanceSettings
import com.example.data.FirebaseService
import com.example.data.acp.AcpApi
import com.example.data.acp.AcpFailure
import com.example.data.acp.AcpUnauthorized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcpConsultationScreen(canConfigure: Boolean, onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val api = remember { AcpApi(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val appearanceSettings by FirebaseService.observeAppearanceSettings()
        .collectAsStateWithLifecycle(initialValue = AppearanceSettings())
    val activeConsultationBackground = appearanceSettings.activeConsultationBackground()
    val bannerBitmap = remember(context) {
        runCatching {
            val encoded = (0..6).joinToString(separator = "") { part ->
                val fileName = "acp_banner/banner_${part.toString().padStart(2, '0')}.b64"
                context.assets.open(fileName).bufferedReader().use { it.readText() }
            }
            val bytes = Base64.decode(encoded, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    }
    var configured by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var authenticated by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var configure by remember { mutableStateOf(false) }

    LaunchedEffect(api) {
        try {
            configured = api.hasCredentials()
            if (configured) {
                api.confirmAccess()
                authenticated = true
            } else {
                authenticated = false
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = acpErrorMessage(failure) }
        finally { checking = false }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Surface(tonalElevation = 2.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            // A janela já entrega esta área abaixo da status bar. Não aplicar
                            // statusBars de novo evita o "vazio" extra que aparecia acima do banner.
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar")
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(3f)
                                .clip(RoundedCornerShape(22.dp))
                                .background(MaterialTheme.colorScheme.surface),
                            contentAlignment = Alignment.Center
                        ) {
                            if (activeConsultationBackground != null) {
                                MaskedThemeBanner(
                                    appTheme = "multicolor",
                                    backgroundUrl = activeConsultationBackground.url,
                                    imageScale = activeConsultationBackground.imageScale,
                                    imageOffsetX = activeConsultationBackground.imageOffsetX,
                                    imageOffsetY = activeConsultationBackground.imageOffsetY,
                                    imageStretchX = activeConsultationBackground.imageStretchX,
                                    imageStretchY = activeConsultationBackground.imageStretchY,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else if (bannerBitmap != null) {
                                Image(
                                    bitmap = bannerBitmap,
                                    contentDescription = "Consultar Produtos",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                    }
                }
            }
        ) { padding ->
            val horizontalPadding = if (authenticated) 12.dp else 20.dp
            val verticalPadding = if (authenticated) 8.dp else 20.dp
            Column(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(horizontal = horizontalPadding, vertical = verticalPadding)
                    .then(if (authenticated) Modifier else Modifier.verticalScroll(rememberScrollState())),
                verticalArrangement = Arrangement.spacedBy(if (authenticated) 8.dp else 16.dp)
            ) {
                if (authenticated) {
                    AcpProductsPanel(api, canAddToNrd = canConfigure, appearance = appearanceSettings, onSessionExpired = {
                        authenticated = false
                        error = "Não foi possível renovar a sessão automaticamente. Tente novamente."
                    })
                } else {
                    Text("Acesso ACP", style = MaterialTheme.typography.headlineSmall)
                    Text(if (configured) "A sessão é renovada automaticamente. Tente novamente apenas se a ACP não responder." else "Acesse a consulta de preços do Nordestão.")
                    OutlinedTextField(value = if (configured) "********" else "", onValueChange = {},
                        readOnly = true, label = { Text("Login") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = if (configured) "********" else "", onValueChange = {},
                        readOnly = true, label = { Text("Senha") }, modifier = Modifier.fillMaxWidth())
                    if (checking || busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Button(onClick = {
                        busy = true
                        error = null
                        scope.launch {
                            try { api.confirmAccess(); authenticated = true }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (failure: Exception) { error = acpErrorMessage(failure) }
                            finally { busy = false }
                        }
                    }, enabled = configured && !checking && !busy, modifier = Modifier.fillMaxWidth()) {
                        Text(if (busy) "Reconectando…" else "Tentar novamente")
                    }
                    if (!configured && !checking) Text("O administrador precisa configurar o acesso neste aparelho uma única vez.")
                    if (canConfigure && !api.hasBundledAccess()) TextButton(onClick = { configure = true }, enabled = !busy && !checking) {
                        Text(if (configured) "Atualizar acesso neste aparelho" else "Configurar acesso neste aparelho")
                    }
                }
            }
        }
    }

    if (configure && canConfigure) {
        var login by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var saving by remember { mutableStateOf(false) }
        var saveError by remember { mutableStateOf<String?>(null) }
        AlertDialog(onDismissRequest = { if (!saving) configure = false }, title = { Text("Configurar acesso ACP") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("O acesso ficará protegido neste aparelho. Na consulta, os campos ficam ocultos e bloqueados.")
                OutlinedTextField(login, { login = it }, label = { Text("Login") }, singleLine = true, enabled = !saving)
                OutlinedTextField(password, { password = it }, label = { Text("Senha") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), enabled = !saving)
                saveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }, confirmButton = {
            TextButton(enabled = !saving && login.isNotBlank() && password.isNotBlank(), onClick = {
                saving = true
                scope.launch {
                    try {
                        api.configure(login, password)
                        password = ""
                        login = ""
                        configured = true
                        error = null
                        configure = false
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { saveError = "Não foi possível proteger o acesso neste aparelho." }
                    finally { saving = false }
                }
            }) { Text(if (saving) "Salvando…" else "Salvar") }
        }, dismissButton = { TextButton(onClick = { configure = false }, enabled = !saving) { Text("Cancelar") } })
    }
}

internal fun acpErrorMessage(error: Exception): String = when (error) {
    is AcpUnauthorized -> "Sua sessão terminou. Não foi possível renová-la automaticamente."
    is AcpFailure -> error.message ?: "Não foi possível consultar a ACP."
    else -> "Não foi possível conectar à ACP. Verifique a internet e tente novamente."
}
