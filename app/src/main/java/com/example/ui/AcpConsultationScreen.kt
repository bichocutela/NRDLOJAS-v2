package com.example.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.data.acp.AcpHistoryExport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
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
    val screenProfile = rememberNrdScreenProfile()
    val historyExport = remember(context) { AcpHistoryExport(context.applicationContext) }
    var historyExportBusy by rememberSaveable { mutableStateOf(false) }
    var historyExportMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val saveHistory = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) {
            historyExportBusy = false
            historyExportMessage = "Salvamento cancelado."
        } else scope.launch {
            try {
                val bytes = withContext(Dispatchers.IO + NonCancellable) { historyExport.save(uri) }
                historyExportMessage = "Histórico salvo e verificado ($bytes bytes). Anexe o arquivo JSON aqui no ChatGPT."
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { historyExportMessage = "Falha ao gravar ou verificar o JSON. Salve novamente em Downloads." }
            finally { historyExportBusy = false }
        }
    }
    val exportHistory: (String, Int) -> Unit = { payload, index ->
        historyExportBusy = true
        historyExportMessage = "Preparando arquivo…"
        scope.launch {
            try {
                withContext(Dispatchers.IO) { historyExport.stage(payload) }
                saveHistory.launch(if (index < 0) "acp-investigacao-produto.json" else "acp-historico-pagina-$index.json")
            } catch (cancelled: CancellationException) {
                historyExportBusy = false
                throw cancelled
            } catch (_: Exception) {
                historyExportBusy = false
                historyExportMessage = "Não foi possível preparar o JSON. Tente novamente."
            }
        }
    }

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
                api.enableBackgroundSync()
                api.warmFeaturedCatalog()
                authenticated = true
            } else authenticated = false
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = acpErrorMessage(failure) }
        finally { checking = false }
    }

    Box(Modifier.fillMaxSize().background(Color.Transparent)) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .then(
                                if (screenProfile.tablet) Modifier.widthIn(max = screenProfile.contentMaxWidth)
                                else Modifier
                            )
                            .aspectRatio(3f)
                            .clip(
                                RoundedCornerShape(
                                    bottomStart = if (screenProfile.veryCompact) 16.dp else 22.dp,
                                    bottomEnd = if (screenProfile.veryCompact) 16.dp else 22.dp
                                )
                            )
                            .background(Color.Transparent)
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

                        Surface(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = if (screenProfile.veryCompact) 4.dp else 10.dp),
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                            tonalElevation = 0.dp
                        ) {
                            IconButton(onClick = onNavigateBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar")
                            }
                        }
                    }
                }
            }
        ) { padding ->
            val verticalPadding = if (authenticated) 8.dp else 20.dp
            Box(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth()
                        .then(
                            if (screenProfile.tablet) {
                                Modifier.widthIn(
                                    max = if (authenticated) screenProfile.contentMaxWidth else screenProfile.dialogMaxWidth
                                )
                            } else Modifier
                        )
                        .padding(horizontal = screenProfile.horizontalPadding, vertical = verticalPadding)
                        .then(if (authenticated) Modifier else Modifier.verticalScroll(rememberScrollState())),
                    verticalArrangement = Arrangement.spacedBy(if (authenticated) 8.dp else 16.dp)
                ) {
                    if (authenticated) {
                        AcpProductsExperience(
                            api = api,
                            canAddToNrd = canConfigure,
                            appearance = appearanceSettings,
                            historyExportBusy = historyExportBusy,
                            historyExportMessage = historyExportMessage,
                            onExportHistory = exportHistory,
                            onSessionExpired = {
                                authenticated = false
                                error = "Não foi possível renovar a sessão automaticamente. Tente novamente."
                            }
                        )
                    } else {
                        Text("Acesso ACP", style = MaterialTheme.typography.headlineSmall)
                        Text(if (configured) "A sessão é renovada automaticamente. Tente novamente apenas se a ACP não responder." else "Acesse a consulta de preços do Nordestão.")
                        OutlinedTextField(if (configured) "********" else "", {}, readOnly = true, label = { Text("Login") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(if (configured) "********" else "", {}, readOnly = true, label = { Text("Senha") }, modifier = Modifier.fillMaxWidth())
                        if (checking || busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        Button(onClick = {
                            busy = true; error = null
                            scope.launch {
                                try { api.confirmAccess(); api.enableBackgroundSync(); api.warmFeaturedCatalog(); authenticated = true }
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
                OutlinedTextField(password, { password = it }, label = { Text("Senha") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), enabled = !saving)
                saveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }, confirmButton = {
            TextButton(enabled = !saving && login.isNotBlank() && password.isNotBlank(), onClick = {
                saving = true
                scope.launch {
                    try {
                        api.configure(login, password); password = ""; login = ""; configured = true; error = null; configure = false
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
