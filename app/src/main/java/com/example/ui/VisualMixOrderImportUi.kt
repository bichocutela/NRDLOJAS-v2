package com.example.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.acp.*
import com.example.data.flyer.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun VisualMixOrderImportButton(onClick: () -> Unit) {
    val isMaster = com.google.firebase.auth.FirebaseAuth.getInstance()
        .currentUser?.email?.trim()?.lowercase() == "mestre@nrdlojas.com"
    if (!isMaster) return

    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)
    ) {
        Icon(Icons.Default.UploadFile, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Importar Ordem", fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun VisualMixOrderImportHost(
    api: AcpApi,
    open: Boolean,
    externalPdfUri: String? = null,
    externalPdfRequestKey: Long = 0L,
    onExternalPdfConsumed: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val isMaster = com.google.firebase.auth.FirebaseAuth.getInstance()
        .currentUser?.email?.trim()?.lowercase() == "mestre@nrdlojas.com"
    if (!isMaster) return

    var sharedOpen by remember { mutableStateOf(false) }
    var pendingSharedPdf by remember { mutableStateOf<Uri?>(null) }
    var pendingSharedPdfRequestKey by remember { mutableLongStateOf(0L) }

    LaunchedEffect(externalPdfUri, externalPdfRequestKey, isMaster) {
        if (isMaster && externalPdfUri != null) {
            pendingSharedPdf = Uri.parse(externalPdfUri)
            pendingSharedPdfRequestKey = externalPdfRequestKey
            sharedOpen = true
            onExternalPdfConsumed()
        }
    }

    if (open || sharedOpen) {
        VisualMixOrderImportDialog(
            api = api,
            initialPdfUri = pendingSharedPdf,
            initialPdfRequestKey = pendingSharedPdfRequestKey,
            onInitialPdfConsumed = { pendingSharedPdf = null },
            onDismiss = {
                sharedOpen = false
                onDismiss()
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VisualMixOrderImportDialog(
    api: AcpApi,
    initialPdfUri: Uri? = null,
    initialPdfRequestKey: Long = 0L,
    onInitialPdfConsumed: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var analysis by remember { mutableStateOf<FlyerAnalysisResult?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var compareOffer by remember { mutableStateOf<FlyerOffer?>(null) }
    var compareProduct by remember { mutableStateOf<AcpProduct?>(null) }
    var compareBusy by remember { mutableStateOf(false) }
    var compareError by remember { mutableStateOf<String?>(null) }
    var openPreviewAfterLoad by remember { mutableStateOf(false) }
    var savingValidity by remember { mutableStateOf(false) }
    var confirmedKeys by remember { mutableStateOf(VisualMixReviewStore.confirmedKeys(context)) }
    var draftAvailable by remember { mutableStateOf(VisualMixReviewStore.hasDraft(context)) }
    var draftMessage by remember { mutableStateOf<String?>(null) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var batchBusy by remember { mutableStateOf(false) }
    var expandedApproved by remember { mutableStateOf<Set<String>>(emptySet()) }

    fun stableKey(offer: FlyerOffer) = VisualMixReviewStore.stableKey(offer)
    fun isVerified(offer: FlyerOffer) = stableKey(offer) in confirmedKeys

    fun startComparison(offer: FlyerOffer, previewAfterLoad: Boolean = false) {
        if (compareBusy || batchBusy) return
        compareOffer = offer
        compareProduct = null
        compareError = null
        openPreviewAfterLoad = previewAfterLoad
        compareBusy = true
        scope.launch {
            try {
                if (!api.restoreSession()) api.confirmAccess()
                compareProduct = findOrderProductInAcp(api, offer)
                if (compareProduct == null) compareError = "O produto não foi localizado no sistema pelos códigos importados. Confira a descrição, código e EAN do Visual Mix."
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                compareError = "Não foi possível consultar o sistema agora."
            } finally {
                compareBusy = false
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !busy) {
            scope.launch {
                busy = true
                error = null
                analysis = null
                selectionMode = false
                selectedKeys = emptySet()
                draftMessage = null
                try {
                    analysis = FlyerImportEngine.analyzeUri(context, uri)
                    confirmedKeys = VisualMixReviewStore.confirmedKeys(context)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    error = failure.message ?: "Não foi possível ler a ordem do Visual Mix."
                } finally {
                    busy = false
                }
            }
        }
    }

    LaunchedEffect(initialPdfRequestKey) {
        val uri = initialPdfUri ?: return@LaunchedEffect
        if (busy) return@LaunchedEffect

        busy = true
        error = null
        analysis = null
        selectionMode = false
        selectedKeys = emptySet()
        draftMessage = "PDF recebido pelo compartilhamento. Importando automaticamente…"
        try {
            analysis = FlyerImportEngine.analyzeUri(context, uri)
            confirmedKeys = VisualMixReviewStore.confirmedKeys(context)
            draftMessage = "PDF recebido e carregado automaticamente."
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure.message ?: "Não foi possível ler a ordem compartilhada."
            draftMessage = null
        } finally {
            busy = false
            onInitialPdfConsumed()
        }
    }

    Dialog(
        onDismissRequest = { if (!busy && !savingValidity && !batchBusy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text("Importar Ordem") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss, enabled = !busy && !savingValidity && !batchBusy) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                        }
                    }
                )

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Text(
                            "Selecione o PDF do Visual Mix. O NRD separa descrição, código, EAN, preço e vigência e cruza os produtos com o sistema.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    item {
                        Button(
                            onClick = { picker.launch(arrayOf("application/pdf")) },
                            enabled = !busy && !batchBusy,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (analysis == null) "Selecionar PDF Visual Mix" else "Importar outro PDF")
                        }
                    }
                    item {
                        OutlinedButton(
                            onClick = {
                                VisualMixReviewStore.loadDraft(context)?.let { saved ->
                                    analysis = saved
                                    confirmedKeys = VisualMixReviewStore.confirmedKeys(context)
                                    selectionMode = false
                                    selectedKeys = emptySet()
                                    error = null
                                    draftMessage = "Rascunho carregado. Continue de onde parou."
                                }
                            },
                            enabled = draftAvailable && !busy && !batchBusy,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Rascunho") }
                    }
                    if (busy || batchBusy) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                                Text(
                                    if (batchBusy) "Confirmando os produtos selecionados…" else "Lendo o PDF e conferindo os produtos no sistema…",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                    draftMessage?.let { item { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) } }
                    error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }

                    analysis?.let { result ->
                        item {
                            OutlinedCard(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(result.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    val from = formatIsoDate(result.validFrom) ?: "não identificada"
                                    val to = formatIsoDate(result.validTo) ?: "não identificada"
                                    Text("Vigência encontrada: $from a $to")
                                    Text("${result.offers.size} oferta(s) extraída(s)", style = MaterialTheme.typography.bodySmall)
                                    val matched = result.offers.count { it.canOpenAcpComparison() }
                                    val done = result.offers.count(::isVerified)
                                    Text("$matched com produto correspondente localizado no sistema", style = MaterialTheme.typography.bodySmall)
                                    if (done > 0) Text("$done já conferida(s)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                        result.warnings.forEach { warning ->
                            item { Text("⚠ $warning", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary) }
                        }
                        if (result.offers.isEmpty()) {
                            item { Text("Nenhuma oferta foi estruturada nesse PDF.") }
                        } else {
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Produtos alterados", style = MaterialTheme.typography.titleMedium)
                                        TextButton(
                                            onClick = {
                                                VisualMixReviewStore.saveDraft(context, result)
                                                draftAvailable = true
                                                draftMessage = "Rascunho salvo."
                                            },
                                            enabled = !busy && !batchBusy
                                        ) { Text("Salvar rascunho") }
                                    }
                                    if (!selectionMode) {
                                        OutlinedButton(
                                            onClick = { selectionMode = true; selectedKeys = emptySet() },
                                            enabled = !busy && !batchBusy,
                                            modifier = Modifier.fillMaxWidth()
                                        ) { Text("Selecionar produtos") }
                                    } else {
                                        val eligible = result.offers.filter { offer ->
                                            offer.canOpenAcpComparison() && !isVerified(offer) && validityForOrderOffer(result, offer) != null
                                        }
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            TextButton(
                                                onClick = { selectedKeys = eligible.map(::stableKey).toSet() },
                                                modifier = Modifier.weight(1f)
                                            ) { Text("Selecionar todos") }
                                            TextButton(
                                                onClick = { selectionMode = false; selectedKeys = emptySet() },
                                                modifier = Modifier.weight(1f)
                                            ) { Text("Cancelar") }
                                        }
                                        Button(
                                            onClick = {
                                                val selectedOffers = eligible.filter { stableKey(it) in selectedKeys }
                                                if (selectedOffers.isEmpty()) return@Button
                                                batchBusy = true
                                                error = null
                                                scope.launch {
                                                    val successes = mutableListOf<FlyerOffer>()
                                                    var failures = 0
                                                    try {
                                                        if (!api.restoreSession()) api.confirmAccess()
                                                        for (offer in selectedOffers) {
                                                            val validity = validityForOrderOffer(result, offer)
                                                            val product = runCatching { findOrderProductInAcp(api, offer) }.getOrNull()
                                                            if (validity == null || product == null) {
                                                                failures++
                                                                continue
                                                            }
                                                            val saved = runCatching {
                                                                AcpOfferValidityStore.save(
                                                                    productName = product.description,
                                                                    family = offer.orderAcpFamily(),
                                                                    startDate = formatIsoDate(validity.first) ?: validity.first,
                                                                    endDate = formatIsoDate(validity.second) ?: validity.second
                                                                )
                                                            }.isSuccess
                                                            if (saved) successes += offer else failures++
                                                        }
                                                        VisualMixReviewStore.markConfirmed(context, successes)
                                                        confirmedKeys = VisualMixReviewStore.confirmedKeys(context)
                                                        selectedKeys = emptySet()
                                                        selectionMode = false
                                                        draftMessage = if (failures == 0) {
                                                            "${successes.size} produto(s) confirmado(s)."
                                                        } else {
                                                            "${successes.size} confirmado(s) e $failures pendente(s) para revisão individual."
                                                        }
                                                    } catch (_: Exception) {
                                                        error = "Não foi possível concluir a confirmação em lote. Tente novamente."
                                                    } finally {
                                                        batchBusy = false
                                                    }
                                                }
                                            },
                                            enabled = selectedKeys.isNotEmpty() && !batchBusy,
                                            modifier = Modifier.fillMaxWidth()
                                        ) { Text("Dar OK e salvar selecionados (${selectedKeys.size})") }
                                    }
                                }
                            }

                            items(result.offers, key = { it.id }) { offer ->
                                val key = stableKey(offer)
                                VisualMixOrderOfferCard(
                                    offer = offer,
                                    result = result,
                                    verified = key in confirmedKeys,
                                    selectionMode = selectionMode,
                                    selected = key in selectedKeys,
                                    expandedApproved = key in expandedApproved,
                                    onSelectedChange = { checked ->
                                        selectedKeys = if (checked) selectedKeys + key else selectedKeys - key
                                    },
                                    onToggleApproved = {
                                        expandedApproved = if (key in expandedApproved) expandedApproved - key else expandedApproved + key
                                    },
                                    onCompare = { startComparison(offer) },
                                    onPreview = { startComparison(offer, previewAfterLoad = true) }
                                )
                            }
                        }
                    }
                    item { Spacer(Modifier.height(20.dp)) }
                }
            }
        }
    }

    compareOffer?.let { offer ->
        VisualMixAcpComparisonDialog(
            offer = offer,
            result = analysis,
            product = compareProduct,
            busy = compareBusy,
            error = compareError,
            saving = savingValidity,
            applied = isVerified(offer),
            openPreviewInitially = openPreviewAfterLoad,
            onDismiss = {
                if (!savingValidity) {
                    compareOffer = null
                    compareProduct = null
                    compareError = null
                    openPreviewAfterLoad = false
                }
            },
            onConfirm = {
                val result = analysis
                val product = compareProduct
                if (result == null || product == null || savingValidity) return@VisualMixAcpComparisonDialog
                val validity = validityForOrderOffer(result, offer)
                if (validity == null) {
                    compareError = "Não foi possível confirmar a data inicial e final dessa oferta no PDF."
                    return@VisualMixAcpComparisonDialog
                }
                savingValidity = true
                compareError = null
                scope.launch {
                    try {
                        AcpOfferValidityStore.save(
                            productName = product.description,
                            family = offer.orderAcpFamily(),
                            startDate = formatIsoDate(validity.first) ?: validity.first,
                            endDate = formatIsoDate(validity.second) ?: validity.second
                        )
                        VisualMixReviewStore.markConfirmed(context, offer)
                        confirmedKeys = VisualMixReviewStore.confirmedKeys(context)
                    } catch (_: SecurityException) {
                        compareError = "Somente o Mestre pode confirmar a validade."
                    } catch (_: Exception) {
                        compareError = "Não foi possível salvar a validade agora."
                    } finally {
                        savingValidity = false
                    }
                }
            }
        )
    }
}

@Composable
private fun VisualMixOrderOfferCard(
    offer: FlyerOffer,
    result: FlyerAnalysisResult,
    verified: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    expandedApproved: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    onToggleApproved: () -> Unit,
    onCompare: () -> Unit,
    onPreview: () -> Unit
) {
    val validity = validityForOrderOffer(result, offer)
    val canCompare = offer.canOpenAcpComparison()
    val hasLookupKey = offer.productCodes.any { it.isNotBlank() } || offer.barcodes.any { it.isNotBlank() }
    val selectable = canCompare && !verified && validity != null

    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            if (selectionMode && selectable) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = selected, onCheckedChange = onSelectedChange)
                    Text("Selecionar para confirmar")
                }
            }

            if (verified && !expandedApproved) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(offer.orderLabel(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(offer.sourceDescription, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                    Text("OK", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    TextButton(onClick = onPreview, enabled = hasLookupKey) { Text("Prévia") }
                    TextButton(onClick = onToggleApproved) { Text("Ver") }
                }
                return@Column
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(offer.orderLabel(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                when {
                    verified -> Text("VALIDADE APLICADA", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    canCompare -> Text("sistema ENCONTRADO", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    else -> TextButton(onClick = onCompare, enabled = hasLookupKey, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                        Text("REVISAR", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                    }
                }
            }
            Text(offer.sourceDescription, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            offer.regularPrice?.let { Text("Preço normal: ${formatMoney(it)}", style = MaterialTheme.typography.bodySmall) }
            offer.flyerPrice?.let { Text("Preço da oferta: ${formatMoney(it)}", style = MaterialTheme.typography.bodySmall) }
            validity?.let { (from, to) ->
                Text("Validade: ${formatIsoDate(from) ?: from} até ${formatIsoDate(to) ?: to}", style = MaterialTheme.typography.bodySmall)
            }
            if (offer.detail.isNotBlank()) Text(offer.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val code = offer.productCodes.firstOrNull().orEmpty()
                val ean = offer.barcodes.firstOrNull().orEmpty()
                if (canCompare) {
                    if (code.isNotBlank()) TextButton(onClick = onCompare, modifier = Modifier.weight(1f)) { Text("Código $code") }
                    if (ean.isNotBlank()) TextButton(onClick = onCompare, modifier = Modifier.weight(1f)) { Text("EAN $ean") }
                } else {
                    Column(Modifier.weight(1f)) {
                        Text("Código: ${code.ifBlank { "não identificado" }}", style = MaterialTheme.typography.bodySmall)
                        Text("EAN: ${ean.ifBlank { "não identificado" }}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onPreview, enabled = hasLookupKey) { Text("Prévia") }
                if (!canCompare && !verified) {
                    TextButton(onClick = onCompare, enabled = hasLookupKey) { Text("Revisar") }
                }
                if (verified) {
                    TextButton(onClick = onToggleApproved) { Text("Minimizar") }
                }
            }
        }
    }
}

@Composable
private fun VisualMixAcpComparisonDialog(
    offer: FlyerOffer,
    result: FlyerAnalysisResult?,
    product: AcpProduct?,
    busy: Boolean,
    error: String?,
    saving: Boolean,
    applied: Boolean,
    openPreviewInitially: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val validity = result?.let { validityForOrderOffer(it, offer) }
    var previewOpen by remember(offer.id, openPreviewInitially) { mutableStateOf(openPreviewInitially) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Visual Mix × sistema") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("VISUAL MIX", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                CopyableReviewField("Descrição", offer.sourceDescription, strong = true)
                Text("Tipo: ${offer.orderLabel()}")
                CopyableReviewField("Código", offer.productCodes.firstOrNull().orEmpty().ifBlank { "não informado" })
                CopyableReviewField("EAN", offer.barcodes.firstOrNull().orEmpty().ifBlank { "não informado" })
                offer.regularPrice?.let { Text("Preço normal: ${formatMoney(it)}") }
                offer.flyerPrice?.let { Text("Preço oferta: ${formatMoney(it)}") }
                validity?.let { (from, to) -> Text("Validade: ${formatIsoDate(from) ?: from} até ${formatIsoDate(to) ?: to}") }
                if (offer.detail.isNotBlank()) Text(offer.detail, style = MaterialTheme.typography.bodySmall)

                HorizontalDivider()
                Text("sistema", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                if (busy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Carregando o produto…")
                }
                product?.let { acp ->
                    CopyableReviewField("Descrição", acp.description, strong = true)
                    CopyableReviewField("Código", acp.code.ifBlank { "não informado" })
                    CopyableReviewField("EAN", acp.barcode.ifBlank { "não informado" })
                    Text("Preço principal: ${acp.value?.brl() ?: "não informado"}")
                    val expectedFamily = offer.orderAcpFamily()
                    val allOffers = acp.offers()
                    val relevant = allOffers.filter { it.family == expectedFamily }
                    Text("Condição esperada: ${offer.orderLabel()}", style = MaterialTheme.typography.bodySmall)
                    if (relevant.isEmpty()) {
                        Text("O sistema não retornou uma oferta dessa família neste momento. Confira antes de marcar como correto.", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall)
                    } else {
                        relevant.forEach { acpOffer ->
                            OutlinedCard(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(acpOffer.title, fontWeight = FontWeight.Bold)
                                    acpOffer.referencePrice?.let { Text("Normal: ${it.brl()}") }
                                    acpOffer.price?.let { Text("Oferta: ${it.brl()}") }
                                    Text(acpOffer.detail, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { previewOpen = true }) { Text("Prévia") }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = applied,
                        onCheckedChange = { checked -> if (checked && !applied && !saving && product != null) onConfirm() },
                        enabled = !busy && !saving && product != null && validity != null && !applied
                    )
                    Column {
                        Text(if (applied) "Tudo conferido" else "Conferi: produto e oferta estão corretos", fontWeight = FontWeight.Bold)
                        Text(
                            if (applied) "A validade já foi preenchida automaticamente no Consultar Preços."
                            else "Ao marcar, a data inicial e final do Visual Mix serão aplicadas à validade desta oferta.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("Fechar") } }
    )

    if (previewOpen && product != null) {
        VisualMixUserPreviewDialog(offer, product, validity, applied) { previewOpen = false }
    }
}

@Suppress("DEPRECATION")
@Composable
private fun CopyableReviewField(label: String, value: String, strong: Boolean = false) {
    val clipboard = LocalClipboardManager.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "$label: $value",
            modifier = Modifier.weight(1f),
            style = if (strong) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal
        )
        TextButton(
            onClick = { if (value.isNotBlank() && value != "não informado") clipboard.setText(AnnotatedString(value)) },
            enabled = value.isNotBlank() && value != "não informado",
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
        ) { Text("Copiar", style = MaterialTheme.typography.labelSmall) }
    }
}

@Composable
private fun VisualMixUserPreviewDialog(
    offer: FlyerOffer,
    product: AcpProduct,
    validity: Pair<String, String>?,
    applied: Boolean,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Prévia para o usuário") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (applied) "Abaixo está o antes e o resultado que já foi aplicado."
                    else "Abaixo está o antes e uma simulação de como ficará depois que você confirmar.",
                    style = MaterialTheme.typography.bodySmall
                )
                UserSearchPreviewCard("ANTES DA REVISÃO", product, offer, null)
                UserProductDetailPreview(product, offer, null)
                HorizontalDivider()
                UserSearchPreviewCard(if (applied) "DEPOIS DA REVISÃO" else "DEPOIS DA REVISÃO • SIMULAÇÃO", product, offer, validity?.second)
                UserProductDetailPreview(product, offer, validity?.second)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } }
    )
}

@Composable
private fun UserSearchPreviewCard(title: String, product: AcpProduct, offer: FlyerOffer, validityEnd: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text("Na busca", style = MaterialTheme.typography.labelMedium)
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(product.description, style = MaterialTheme.typography.titleMedium)
                Text("Código: ${product.code.ifBlank { "não informado" }} • EAN: ${product.barcode.ifBlank { "não informado" }}", style = MaterialTheme.typography.bodySmall)
                Text("Preço: ${product.value?.brl() ?: "não informado"}", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    AssistChip(onClick = {}, label = { Text(offer.orderLabel()) })
                    shortValidityLabel(validityEnd)?.let { end -> AssistChip(onClick = {}, label = { Text("Válido até $end") }) }
                }
                Text("Ver ficha completa", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun UserProductDetailPreview(product: AcpProduct, offer: FlyerOffer, validityEnd: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("Ao abrir o produto", style = MaterialTheme.typography.labelMedium)
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(product.description, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Código: ${product.code.ifBlank { "não informado" }}")
                Text("Cód. barras: ${product.barcode.ifBlank { "não informado" }}")
                Text("Preço principal: ${product.value?.brl() ?: "não informado"}", style = MaterialTheme.typography.titleMedium)
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(offer.orderLabel(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    shortValidityLabel(validityEnd)?.let { end -> Text("Válido até $end", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
                }
                offer.regularPrice?.let { Text("Normal: ${formatMoney(it)}") }
                offer.flyerPrice?.let { Text("Oferta: ${formatMoney(it)}") }
            }
        }
    }
}

private fun shortValidityLabel(value: String?): String? = formatIsoDate(value)?.takeIf { it.length >= 5 }?.take(5)

private suspend fun findOrderProductInAcp(api: AcpApi, offer: FlyerOffer): AcpProduct? {
    offer.barcodes.filter { it.isNotBlank() }.forEach { ean ->
        val clean = ean.filter(Char::isDigit)
        val page = api.searchProducts(AcpSearchField.BARCODE, clean, null, 0)
        page.items.firstOrNull { it.barcode.filter(Char::isDigit) == clean }?.let { return it }
    }
    offer.productCodes.filter { it.isNotBlank() }.forEach { code ->
        val clean = code.trim()
        val page = api.searchProducts(AcpSearchField.CODE, clean, null, 0)
        page.items.firstOrNull { it.code.trim() == clean }?.let { return it }
    }
    return null
}

private fun FlyerOffer.canOpenAcpComparison(): Boolean =
    matchStatus == FlyerMatchStatus.CONFIRMED && !matchedProductName.isNullOrBlank() &&
        (productCodes.any { it.isNotBlank() } || barcodes.any { it.isNotBlank() })

private fun FlyerOffer.orderAcpFamily(): AcpOfferFamily = when {
    clubCondition == FlyerClubCondition.REQUIRED -> AcpOfferFamily.CLUB
    type == FlyerOfferType.DE_POR -> AcpOfferFamily.DE_POR
    type == FlyerOfferType.SECOND_UNIT_PERCENT -> AcpOfferFamily.SECOND_UNIT
    type == FlyerOfferType.TAKE_PAY_QUANTITY || type == FlyerOfferType.TAKE_PAY_MEASURE -> AcpOfferFamily.TAKE_PAY
    type == FlyerOfferType.CASHBACK -> AcpOfferFamily.CASHBACK
    else -> AcpOfferFamily.PRICE
}

private fun FlyerOffer.orderLabel(): String = when {
    clubCondition == FlyerClubCondition.REQUIRED -> "PREÇO CLUBE"
    type == FlyerOfferType.DE_POR -> "DE / POR"
    type == FlyerOfferType.SECOND_UNIT_PERCENT -> "2ª UNIDADE"
    type == FlyerOfferType.TAKE_PAY_QUANTITY || type == FlyerOfferType.TAKE_PAY_MEASURE -> "LEVE / PAGUE"
    type == FlyerOfferType.CASHBACK -> "CASHBACK"
    else -> "PREÇO OFERTA"
}

private fun validityForOrderOffer(result: FlyerAnalysisResult, offer: FlyerOffer): Pair<String, String>? {
    val individualDates = Regex("\\d{4}-\\d{2}-\\d{2}")
        .findAll(offer.detail)
        .map { it.value }
        .filter { parseIsoDate(it) != null }
        .toList()
    val start = if (individualDates.size >= 2) individualDates.first() else result.validFrom
    val end = when {
        individualDates.size >= 2 -> individualDates.last()
        individualDates.size == 1 -> individualDates.first()
        else -> result.validTo
    }
    if (parseIsoDate(start) == null || parseIsoDate(end) == null) return null
    val startDate = parseIsoDate(start) ?: return null
    val endDate = parseIsoDate(end) ?: return null
    if (endDate.before(startDate)) return null
    return start!! to end!!
}
