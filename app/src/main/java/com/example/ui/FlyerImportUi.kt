package com.example.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.acp.AcpProduct
import com.example.data.Product
import com.example.data.flyer.*
import com.example.ui.theme.glassSoftShadow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun FlyerImportEntryCard() {
    var open by remember { mutableStateOf(false) }
    OutlinedCard(
        onClick = { open = true },
        modifier = Modifier.fillMaxWidth().glassSoftShadow(MaterialTheme.shapes.medium)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.PictureAsPdf,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Importar Encarte", style = MaterialTheme.typography.titleSmall)
                Text(
                    "PDF ou imagem • vigência e ofertas automáticas",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    if (open) FlyerImportDialog(onDismiss = { open = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FlyerImportDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val campaignsFlow = remember { FlyerRepository.observeCampaigns() }
    val campaigns by campaignsFlow.collectAsState(initial = emptyList())
    var analysis by remember { mutableStateOf<FlyerAnalysisResult?>(null) }
    var busy by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<String?>(null) }
    var driveUrl by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var validFrom by remember { mutableStateOf("") }
    var validTo by remember { mutableStateOf("") }
    var editingCampaign by remember { mutableStateOf<FlyerCampaign?>(null) }
    var draftCampaignId by remember { mutableStateOf<String?>(null) }
    var editingOffer by remember { mutableStateOf<FlyerOffer?>(null) }
    var deleteTarget by remember { mutableStateOf<FlyerCampaign?>(null) }
    var showConfirmed by remember { mutableStateOf(false) }
    var selectedOfferIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    fun acceptResult(result: FlyerAnalysisResult, campaign: FlyerCampaign? = null) {
        editingCampaign = campaign
        draftCampaignId = campaign?.id ?: java.util.UUID.randomUUID().toString()
        analysis = result
        name = result.name
        validFrom = result.validFrom.orEmpty()
        validTo = result.validTo.orEmpty()
        showConfirmed = false
        selectedOfferIds = emptySet()
        error = null
        success = null
    }

    fun persistProgress(result: FlyerAnalysisResult, message: String) {
        val startDate = parseIsoDate(validFrom)
        val endDate = parseIsoDate(validTo)
        if (startDate == null || endDate == null || endDate.before(startDate)) {
            error = "Confira as datas de início e fim antes de salvar."
            return
        }
        if (saving) return
        val campaign = FlyerCampaign(
            id = editingCampaign?.id ?: draftCampaignId ?: java.util.UUID.randomUUID().toString(),
            createdAt = editingCampaign?.createdAt ?: System.currentTimeMillis(),
            enabled = editingCampaign?.enabled ?: true,
            name = name.trim().ifBlank { "Encarte" },
            sourceType = result.sourceType,
            sourceLabel = result.sourceLabel,
            validFrom = validFrom,
            validTo = validTo,
            offers = result.offers
        )
        saving = true
        error = null
        scope.launch {
            val saved = FlyerRepository.saveCampaign(campaign)
            if (saved) {
                editingCampaign = campaign
                draftCampaignId = campaign.id
                success = message
            } else {
                error = FlyerRepository.lastError ?: "Não foi possível salvar o encarte."
            }
            saving = false
        }
    }

    fun analyze(block: suspend () -> FlyerAnalysisResult) {
        if (busy) return
        scope.launch {
            busy = true
            error = null
            success = null
            analysis = null
            try {
                acceptResult(block())
            } catch (failure: Exception) {
                error = failure.message ?: "Não foi possível analisar o encarte."
            } finally {
                busy = false
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) analyze { FlyerImportEngine.analyzeUri(context, uri) }
    }

    LaunchedEffect(Unit) { FlyerRepository.disableExpiredCampaigns() }

    Dialog(onDismissRequest = { if (!busy && !saving) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text("Importar Encarte") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss, enabled = !busy && !saving) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                        }
                    }
                )
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Importe o arquivo, confira as datas e revise cada oferta. Cada confirmação é salva como progresso; as restantes ficam como rascunho para continuar depois.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Button(
                        onClick = { picker.launch(arrayOf("application/pdf", "image/*")) },
                        enabled = !busy && !saving,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Selecionar PDF ou imagem")
                    }

                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text("Link do Google Drive", style = MaterialTheme.typography.titleSmall)
                            }
                            OutlinedTextField(
                                value = driveUrl,
                                onValueChange = { driveUrl = it.take(1000) },
                                label = { Text("Link compartilhado") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !busy && !saving
                            )
                            OutlinedButton(
                                onClick = { analyze { FlyerImportEngine.analyzeDriveLink(context, driveUrl) } },
                                enabled = !busy && !saving && driveUrl.startsWith("https://"),
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Analisar link") }
                        }
                    }

                    if (busy) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("Lendo páginas, reconhecendo ofertas e cruzando produtos com a ACP…", style = MaterialTheme.typography.bodySmall)
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    success?.let { Text(it, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }

                    analysis?.let { result ->
                        HorizontalDivider()
                        Text("Resultado da análise", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it.take(120) },
                            label = { Text("Nome do encarte") },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !saving
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = validFrom,
                                onValueChange = { validFrom = it.take(10) },
                                label = { Text("Início geral") },
                                supportingText = { Text("AAAA-MM-DD") },
                                modifier = Modifier.weight(1f),
                                enabled = !saving
                            )
                            OutlinedTextField(
                                value = validTo,
                                onValueChange = { validTo = it.take(10) },
                                label = { Text("Fim geral") },
                                supportingText = { Text("AAAA-MM-DD") },
                                modifier = Modifier.weight(1f),
                                enabled = !saving
                            )
                        }
                        val startDate = parseIsoDate(validFrom)
                        val endDate = parseIsoDate(validTo)
                        val datesValid = startDate != null && endDate != null && !endDate.before(startDate)

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SummaryMetric("Confirmadas", result.confirmedCount, Modifier.weight(1f))
                            SummaryMetric("Revisar", result.reviewCount, Modifier.weight(1f))
                            SummaryMetric("Não resolvidas", result.unresolvedCount, Modifier.weight(1f))
                        }
                        result.warnings.forEach { warning ->
                            Text("⚠ $warning", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                        }

                        val confirmedOffers = result.offers.filter { it.reviewed && it.matchStatus == FlyerMatchStatus.CONFIRMED }
                        val pendingOffers = result.offers.filterNot { it.reviewed && it.matchStatus == FlyerMatchStatus.CONFIRMED }
                        val visibleOffers = if (showConfirmed) result.offers else pendingOffers
                        val readyToConfirm = pendingOffers.filter { it.matchStatus == FlyerMatchStatus.CONFIRMED && it.reviewError() == null }

                        if (confirmedOffers.isNotEmpty()) {
                            OutlinedCard(Modifier.fillMaxWidth()) {
                                Row(
                                    Modifier.fillMaxWidth().padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("${confirmedOffers.size} confirmada(s) e salva(s)", fontWeight = FontWeight.Bold)
                                    TextButton(onClick = { showConfirmed = !showConfirmed }) {
                                        Text(if (showConfirmed) "Ocultar" else "Mostrar")
                                    }
                                }
                            }
                        }

                        if (readyToConfirm.isNotEmpty()) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { selectedOfferIds = readyToConfirm.map { it.id }.toSet() },
                                    enabled = !saving,
                                    modifier = Modifier.weight(1f)
                                ) { Text("Selecionar prontos") }
                                OutlinedButton(
                                    onClick = { selectedOfferIds = emptySet() },
                                    enabled = !saving && selectedOfferIds.isNotEmpty(),
                                    modifier = Modifier.weight(1f)
                                ) { Text("Limpar seleção") }
                            }
                            Button(
                                onClick = {
                                    val updated = result.copy(offers = result.offers.map { offer ->
                                        if (offer.id in selectedOfferIds) offer.confirmedForPublication() ?: offer else offer
                                    })
                                    analysis = updated
                                    selectedOfferIds = emptySet()
                                    persistProgress(updated, "Selecionadas confirmadas e salvas. As restantes continuam como rascunho.")
                                },
                                enabled = !saving && selectedOfferIds.isNotEmpty(),
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Confirmar e salvar selecionados") }
                        }

                        if (visibleOffers.isEmpty()) {
                            Text(if (result.offers.isEmpty()) "Nenhuma oferta adicional foi reconhecida." else "Todas as ofertas visíveis já foram confirmadas.")
                        } else {
                            Text("Ofertas pendentes", style = MaterialTheme.typography.titleSmall)
                            visibleOffers.forEach { offer ->
                                val canBulkConfirm = !offer.reviewed && offer.matchStatus == FlyerMatchStatus.CONFIRMED && offer.reviewError() == null
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = offer.id in selectedOfferIds,
                                        onCheckedChange = { checked ->
                                            selectedOfferIds = if (checked) selectedOfferIds + offer.id else selectedOfferIds - offer.id
                                        },
                                        enabled = canBulkConfirm && !saving
                                    )
                                    Column(Modifier.weight(1f)) {
                                        DetectedOfferCard(offer)
                                    }
                                }
                                Row {
                                    TextButton(onClick = { editingOffer = offer }, enabled = !saving) { Text("Revisar / comparar ACP") }
                                    TextButton(onClick = {
                                        val updated = result.copy(offers = result.offers.filterNot { it.id == offer.id })
                                        analysis = updated
                                        selectedOfferIds = selectedOfferIds - offer.id
                                        if (editingCampaign != null) persistProgress(updated, "Rascunho atualizado.")
                                    }, enabled = !saving) { Text("Remover") }
                                }
                            }
                        }

                        OutlinedButton(onClick = {
                            editingOffer = FlyerOffer(type = FlyerOfferType.FLYER_PRICE, sourceDescription = "")
                        }, enabled = !saving) { Text("Adicionar oferta não reconhecida") }
                        Text("Confirmadas ficam ocultas para não atrapalhar. Pendentes permanecem no rascunho e podem ser retomadas sem reenviar o PDF.")
                        Button(
                            onClick = { persistProgress(result, "Progresso salvo. Confirmadas já podem aparecer em Consultar Preços durante a validade; pendentes ficaram em rascunho.") },
                            enabled = !saving && !busy && name.isNotBlank() && datesValid,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (saving) "Salvando…" else "Salvar progresso") }
                    }

                    HorizontalDivider()
                    Text("Encartes e rascunhos", style = MaterialTheme.typography.titleMedium)
                    if (campaigns.isEmpty()) {
                        Text("Nenhum encarte cadastrado.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        campaigns.forEach { campaign ->
                            CampaignManagementCard(
                                campaign = campaign,
                                onToggle = { enabled ->
                                    scope.launch {
                                        if (!FlyerRepository.setEnabled(campaign.id, enabled)) {
                                            error = FlyerRepository.lastError ?: "Não foi possível alterar o encarte."
                                        }
                                    }
                                },
                                onEdit = {
                                    acceptResult(
                                        FlyerAnalysisResult(
                                            campaign.name,
                                            campaign.validFrom,
                                            campaign.validTo,
                                            campaign.offers,
                                            emptyList(),
                                            campaign.sourceType,
                                            campaign.sourceLabel
                                        ),
                                        campaign
                                    )
                                },
                                onDelete = { deleteTarget = campaign }
                            )
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    editingOffer?.let { offer ->
        key(offer.id) {
            FlyerOfferReviewDialog(offer, onDismiss = { editingOffer = null }, onSave = { reviewed ->
                val updated = analysis?.let { result ->
                    result.copy(offers = if (result.offers.any { it.id == reviewed.id })
                        result.offers.map { if (it.id == reviewed.id) reviewed else it }
                        else result.offers + reviewed)
                }
                if (updated != null) {
                    analysis = updated
                    selectedOfferIds = selectedOfferIds - reviewed.id
                    persistProgress(updated, "Oferta confirmada e salva. Ela foi ocultada; continue revisando as restantes quando quiser.")
                }
                editingOffer = null
            })
        }
    }

    deleteTarget?.let { campaign ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Excluir encarte?") },
            text = { Text("${campaign.name} e suas ofertas deixarão de aparecer no NRD.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    scope.launch {
                        if (!FlyerRepository.deleteCampaign(campaign.id)) {
                            error = FlyerRepository.lastError ?: "Não foi possível excluir o encarte."
                        }
                    }
                }) { Text("Excluir") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun SummaryMetric(label: String, count: Int, modifier: Modifier = Modifier) {
    ElevatedCard(modifier) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(count.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun DetectedOfferCard(offer: FlyerOffer) {
    val status = when (offer.matchStatus) {
        FlyerMatchStatus.CONFIRMED -> if (offer.reviewed) "SALVA" else "ACP ENCONTRADO"
        FlyerMatchStatus.REVIEW -> "REVISAR"
        FlyerMatchStatus.UNRESOLVED -> "NÃO RESOLVIDA"
    }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(offer.displayTitle(), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(status, style = MaterialTheme.typography.labelSmall, color = when (offer.matchStatus) {
                    FlyerMatchStatus.CONFIRMED -> MaterialTheme.colorScheme.primary
                    FlyerMatchStatus.REVIEW -> MaterialTheme.colorScheme.tertiary
                    FlyerMatchStatus.UNRESOLVED -> MaterialTheme.colorScheme.error
                })
            }
            Text(offer.matchedProductName ?: offer.sourceDescription, style = MaterialTheme.typography.bodyMedium)
            if (offer.detail.isNotBlank()) Text(offer.detail, style = MaterialTheme.typography.bodySmall)
            if (!offer.validFrom.isNullOrBlank() || !offer.validTo.isNullOrBlank()) {
                Text(
                    "Validade: ${offer.validFrom?.let(::dateLabel).orEmpty()} até ${offer.validTo?.let(::dateLabel).orEmpty()}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Text("Página ${offer.page} • confiança ${(offer.confidence * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CampaignManagementCard(
    campaign: FlyerCampaign,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    val status = campaign.statusAt()
    val statusLabel = when (status) {
        FlyerCampaignStatus.ACTIVE -> "EM VIGOR"
        FlyerCampaignStatus.SCHEDULED -> "AGENDADO"
        FlyerCampaignStatus.EXPIRED -> "ENCERRADO"
        FlyerCampaignStatus.DISABLED -> "DESATIVADO"
    }
    val confirmed = campaign.offers.count { it.reviewed && it.matchStatus == FlyerMatchStatus.CONFIRMED }
    val pending = campaign.offers.count { !it.reviewed }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(campaign.name, style = MaterialTheme.typography.titleSmall)
                    Text("${dateLabel(campaign.validFrom)} → ${dateLabel(campaign.validTo)}", style = MaterialTheme.typography.bodySmall)
                }
                Text(statusLabel, style = MaterialTheme.typography.labelSmall, color = if (status == FlyerCampaignStatus.ACTIVE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "$confirmed confirmada(s) • $pending pendente(s) em rascunho",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onEdit) { Text(if (pending > 0) "Continuar revisão" else "Revisar ofertas") }
                Text("Ativado", style = MaterialTheme.typography.labelMedium)
                Switch(
                    checked = campaign.enabled && status != FlyerCampaignStatus.EXPIRED,
                    onCheckedChange = onToggle,
                    enabled = status != FlyerCampaignStatus.EXPIRED
                )
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Excluir encarte") }
            }
        }
    }
}

/** Flyer conditions require explicit administrator review. */
@Composable
internal fun ActiveFlyerOffersForProduct(product: Product) {
    val campaignsFlow = remember { FlyerRepository.observeCampaigns() }
    val campaigns by campaignsFlow.collectAsState(initial = emptyList())
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            now = System.currentTimeMillis()
        }
    }
    val matches = remember(product.code, campaigns, now) {
        campaigns.filter { it.isActiveAt(now) }
            .flatMap { campaign ->
                campaign.offers
                    .filter { it.isActiveAt(campaign, now) && it.matches(product) }
                    .map { campaign to it }
            }
            .sortedWith(compareBy<Pair<FlyerCampaign, FlyerOffer>> { offerPriority(it.second.type) }
                .thenBy { it.second.effectiveValidTo(it.first) })
    }
    if (matches.isEmpty()) return

    HorizontalDivider(Modifier.padding(vertical = 6.dp))
    Text("OFERTAS DE ENCARTE", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    Text(
        if (matches.size == 1) "1 oferta ativa encontrada" else "${matches.size} ofertas ativas encontradas",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(6.dp))
    matches.forEach { (campaign, offer) ->
        FlyerOfferDisplayCard(campaign, offer)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
internal fun ActiveFlyerOffersForAcpProduct(product: AcpProduct) {
    val campaignsFlow = remember { FlyerRepository.observeCampaigns() }
    val campaigns by campaignsFlow.collectAsState(initial = emptyList())
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            now = System.currentTimeMillis()
        }
    }
    val matches = remember(product.code, product.barcode, campaigns, now) {
        campaigns.filter { it.isActiveAt(now) }.flatMap { campaign ->
            campaign.offers
                .filter { it.isActiveAt(campaign, now) && it.matchesAcp(product.code, product.barcode) }
                .map { campaign to it }
        }.sortedBy { it.second.effectiveValidTo(it.first) }
    }
    if (matches.isEmpty()) return
    HorizontalDivider()
    Text("OFERTAS DO ENCARTE", style = MaterialTheme.typography.titleMedium)
    Text("Condições revisadas no NRD. Não substituem o preço ACP e não são somadas automaticamente a outras promoções.", style = MaterialTheme.typography.bodySmall)
    matches.forEach { (campaign, offer) ->
        FlyerOfferDisplayCard(campaign, offer)
        val advertised = offer.flyerPrice ?: offer.regularPrice
        val acpPrice = product.value
        if (advertised != null && acpPrice != null && java.math.BigDecimal.valueOf(advertised).compareTo(acpPrice) != 0) {
            Text("Valores diferentes: ACP ${formatMoney(acpPrice.toDouble())}; encarte ${formatMoney(advertised)}. Confira as condições antes de aplicar a oferta.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun FlyerOfferDisplayCard(campaign: FlyerCampaign, offer: FlyerOffer) {
    val shape = RoundedCornerShape(16.dp)
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = shape, colors = CardDefaults.elevatedCardColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
        Column(
            Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Color(0xFFFFEB3B)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(offer.displayTitle(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = androidx.compose.ui.graphics.Color(0xFFB90016))
            when (offer.type) {
                FlyerOfferType.SECOND_UNIT_PERCENT -> {
                    offer.regularPrice?.let { Text("1ª unidade: ${formatMoney(it)}") }
                    offer.secondUnitPrice?.let { Text("2ª unidade: ${formatMoney(it)}") }
                    offer.equivalentUnitPrice?.let {
                        Text("Média comprando 2: ${formatMoney(it)} cada", fontWeight = FontWeight.Bold)
                    }
                }
                FlyerOfferType.TAKE_PAY_QUANTITY -> {
                    offer.equivalentUnitPrice?.let { Text("Média equivalente: ${formatMoney(it)} por unidade") }
                }
                FlyerOfferType.TAKE_PAY_MEASURE -> Unit
                FlyerOfferType.DE_POR -> {
                    offer.regularPrice?.let { Text("De ${formatMoney(it)}") }
                    offer.flyerPrice?.let { Text("Por ${formatMoney(it)}", fontWeight = FontWeight.Bold) }
                }
                FlyerOfferType.CASHBACK -> {
                    offer.flyerPrice?.let { Text("Preço anunciado: ${formatMoney(it)}", fontWeight = FontWeight.Bold) }
                    offer.cashbackPercent?.let { Text("Retorno: ${formatQuantity(it)}%") }
                    offer.cashbackValue?.let { Text("Retorno informado: ${formatMoney(it)}") }
                    Text("Cashback é retorno posterior; não foi descontado do preço.")
                }
                FlyerOfferType.FLYER_PRICE -> offer.flyerPrice?.let { Text(formatMoney(it), fontWeight = FontWeight.Bold) }
            }
            if (offer.detail.isNotBlank()) Text(offer.detail, style = MaterialTheme.typography.bodySmall)
            Text(offer.clubCondition.reviewLabel(), fontWeight = FontWeight.Bold)
            Text("Fonte: encarte • página ${offer.page}", style = MaterialTheme.typography.labelSmall)
            Text(
                "${campaign.name} • válido de ${dateLabel(offer.effectiveValidFrom(campaign))} até ${dateLabel(offer.effectiveValidTo(campaign))}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private fun offerPriority(type: FlyerOfferType): Int = when (type) {
    FlyerOfferType.SECOND_UNIT_PERCENT -> 0
    FlyerOfferType.TAKE_PAY_QUANTITY, FlyerOfferType.TAKE_PAY_MEASURE -> 1
    FlyerOfferType.CASHBACK -> 2
    FlyerOfferType.DE_POR -> 3
    FlyerOfferType.FLYER_PRICE -> 4
}

private fun dateLabel(value: String): String = formatIsoDate(value) ?: value
