package com.example.ui

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.acp.*
import com.example.data.flyer.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Fluxo simples e exclusivo do Mestre para revisar a ordem do Visual Mix.
 * Reaproveita o parser de PDF/OCR, a busca ACP e a validade manual já existentes.
 */
@Composable
internal fun VisualMixOrderImportButton(api: AcpApi) {
    val isMaster = com.google.firebase.auth.FirebaseAuth.getInstance()
        .currentUser?.email?.trim()?.lowercase() == "mestre@nrdlojas.com"
    if (!isMaster) return

    var open by remember { mutableStateOf(false) }
    OutlinedButton(
        onClick = { open = true },
        modifier = Modifier.fillMaxWidth().height(50.dp)
    ) {
        Icon(Icons.Default.UploadFile, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Importar Ordem", fontWeight = FontWeight.Bold)
    }

    if (open) {
        VisualMixOrderImportDialog(api = api, onDismiss = { open = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VisualMixOrderImportDialog(
    api: AcpApi,
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
    var savingValidity by remember { mutableStateOf(false) }
    val verified = remember { mutableStateListOf<String>() }

    fun startComparison(offer: FlyerOffer) {
        if (compareBusy) return
        compareOffer = offer
        compareProduct = null
        compareError = null
        compareBusy = true
        scope.launch {
            try {
                if (!api.restoreSession()) api.confirmAccess()
                compareProduct = findOrderProductInAcp(api, offer)
                if (compareProduct == null) {
                    compareError = "O produto deixou de aparecer na ACP. Faça uma nova importação ou confira os códigos."
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                compareError = "Não foi possível consultar a ACP agora."
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
                verified.clear()
                try {
                    analysis = FlyerImportEngine.analyzeUri(context, uri)
                } catch (failure: Exception) {
                    error = failure.message ?: "Não foi possível ler a ordem do Visual Mix."
                } finally {
                    busy = false
                }
            }
        }
    }

    Dialog(
        onDismissRequest = { if (!busy && !savingValidity) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text("Importar Ordem") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss, enabled = !busy && !savingValidity) {
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
                            "Selecione o PDF do Visual Mix. O NRD reaproveita a leitura já existente, separa descrição, código, EAN, preço e vigência e cruza os produtos com a ACP.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    item {
                        Button(
                            onClick = { picker.launch(arrayOf("application/pdf")) },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (analysis == null) "Selecionar PDF Visual Mix" else "Importar outro PDF")
                        }
                    }
                    if (busy) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                                Text("Lendo o PDF e conferindo os produtos na ACP…", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    error?.let { message ->
                        item { Text(message, color = MaterialTheme.colorScheme.error) }
                    }

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
                                    Text("$matched com produto correspondente localizado na ACP", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        result.warnings.forEach { warning ->
                            item { Text("⚠ $warning", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary) }
                        }
                        if (result.offers.isEmpty()) {
                            item { Text("Nenhuma oferta foi estruturada nesse PDF.") }
                        } else {
                            item { Text("Produtos alterados", style = MaterialTheme.typography.titleMedium) }
                            items(result.offers, key = { it.id }) { offer ->
                                VisualMixOrderOfferCard(
                                    offer = offer,
                                    result = result,
                                    verified = offer.id in verified,
                                    onCompare = { startComparison(offer) }
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
            applied = offer.id in verified,
            onDismiss = {
                if (!savingValidity) {
                    compareOffer = null
                    compareProduct = null
                    compareError = null
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
                        if (offer.id !in verified) verified += offer.id
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
    onCompare: () -> Unit
) {
    val validity = validityForOrderOffer(result, offer)
    val canCompare = offer.canOpenAcpComparison()
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(offer.orderLabel(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                if (verified) {
                    Text("VALIDADE APLICADA", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                } else if (canCompare) {
                    Text("ACP ENCONTRADO", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                } else {
                    Text("REVISAR", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
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
                    if (code.isNotBlank()) {
                        TextButton(onClick = onCompare, modifier = Modifier.weight(1f)) { Text("Código $code") }
                    }
                    if (ean.isNotBlank()) {
                        TextButton(onClick = onCompare, modifier = Modifier.weight(1f)) { Text("EAN $ean") }
                    }
                } else {
                    Column {
                        Text("Código: ${code.ifBlank { "não identificado" }}", style = MaterialTheme.typography.bodySmall)
                        Text("EAN: ${ean.ifBlank { "não identificado" }}", style = MaterialTheme.typography.bodySmall)
                        Text("O código/EAN fica clicável quando o produto correspondente é confirmado na ACP.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
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
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val validity = result?.let { validityForOrderOffer(it, offer) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Visual Mix × ACP") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("VISUAL MIX", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(offer.sourceDescription, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("Tipo: ${offer.orderLabel()}")
                Text("Código: ${offer.productCodes.firstOrNull().orEmpty().ifBlank { "não informado" }}")
                Text("EAN: ${offer.barcodes.firstOrNull().orEmpty().ifBlank { "não informado" }}")
                offer.regularPrice?.let { Text("Preço normal: ${formatMoney(it)}") }
                offer.flyerPrice?.let { Text("Preço oferta: ${formatMoney(it)}") }
                validity?.let { (from, to) ->
                    Text("Validade: ${formatIsoDate(from) ?: from} até ${formatIsoDate(to) ?: to}")
                }
                if (offer.detail.isNotBlank()) Text(offer.detail, style = MaterialTheme.typography.bodySmall)

                HorizontalDivider()
                Text("ACP", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                if (busy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Carregando o produto ACP…")
                }
                product?.let { acp ->
                    Text(acp.description, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("Código: ${acp.code.ifBlank { "não informado" }} • EAN: ${acp.barcode.ifBlank { "não informado" }}")
                    Text("Preço principal: ${acp.value?.brl() ?: "não informado"}")
                    val expectedFamily = offer.orderAcpFamily()
                    val allOffers = acp.offers()
                    val relevant = allOffers.filter { it.family == expectedFamily }
                    Text("Condição esperada: ${offer.orderLabel()}", style = MaterialTheme.typography.bodySmall)
                    if (relevant.isEmpty()) {
                        Text(
                            "A ACP não retornou uma oferta dessa família neste momento. Confira antes de marcar como correto.",
                            color = MaterialTheme.colorScheme.tertiary,
                            style = MaterialTheme.typography.bodySmall
                        )
                        allOffers.take(3).forEach { acpOffer ->
                            Text("• ${acpOffer.title}: ${acpOffer.detail}", style = MaterialTheme.typography.bodySmall)
                        }
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
}

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

    val start = when {
        individualDates.size >= 2 -> individualDates.first()
        else -> result.validFrom
    }
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
