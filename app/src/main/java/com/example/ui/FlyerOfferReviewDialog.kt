package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.acp.*
import com.example.data.flyer.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.Normalizer

@Composable
internal fun FlyerOfferReviewDialog(initial: FlyerOffer, onDismiss: () -> Unit, onSave: (FlyerOffer) -> Unit) {
    val context = LocalContext.current
    val api = remember { AcpApi(context) }
    val scope = rememberCoroutineScope()
    var type by remember { mutableStateOf(initial.type) }
    var description by remember { mutableStateOf(initial.sourceDescription) }
    var conditions by remember { mutableStateOf(initial.detail) }
    var club by remember { mutableStateOf(initial.clubCondition) }
    var linked by remember { mutableStateOf(initial) }
    var results by remember { mutableStateOf<AcpProductPage?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var searchHint by remember { mutableStateOf<String?>(null) }
    val numbers = remember { mutableStateMapOf(
        "regular" to formatQuantity(initial.regularPrice), "price" to formatQuantity(initial.flyerPrice),
        "percent" to formatQuantity(initial.secondUnitDiscountPercent),
        "take" to formatQuantity(initial.takeQuantity), "pay" to formatQuantity(initial.payQuantity),
        "cashPercent" to formatQuantity(initial.cashbackPercent), "cashValue" to formatQuantity(initial.cashbackValue)
    ) }
    var takeUnit by remember { mutableStateOf(initial.takeUnit.orEmpty()) }
    var payUnit by remember { mutableStateOf(initial.payUnit.orEmpty()) }
    fun number(key: String) = numbers[key]?.trim()?.replace(',', '.')?.toDoubleOrNull()

    fun selectProduct(product: AcpProduct) {
        linked = linked.copy(
            productCodes = listOf(product.code).filter { it.isNotBlank() },
            barcodes = listOf(product.barcode).filter { it.isNotBlank() },
            matchedProductName = product.description
        )
    }

    suspend fun loadDescriptionCandidates(cleanQuery: String): List<AcpProduct> {
        val candidates = linkedMapOf<String, AcpProduct>()
        val direct = api.searchProducts(AcpSearchField.DESCRIPTION, cleanQuery, null, 0)
        direct.items.forEach { product ->
            candidates["${product.code}|${product.barcode}|${product.id}"] = product
        }
        if (candidates.isEmpty()) {
            for (fallback in smartDescriptionQueries(cleanQuery)) {
                if (fallback.equals(cleanQuery, ignoreCase = true)) continue
                val pageResult = api.searchProducts(AcpSearchField.DESCRIPTION, fallback, null, 0)
                pageResult.items.forEach { product ->
                    candidates["${product.code}|${product.barcode}|${product.id}"] = product
                }
                if (candidates.size >= 20) break
            }
        }
        return candidates.values
            .sortedByDescending { reviewMatchScore(cleanQuery, it.description) }
            .take(20)
    }

    fun searchDescriptionInAcp() {
        val cleanQuery = description.trim()
        if (busy || cleanQuery.isBlank()) return
        busy = true
        error = null
        results = null
        searchHint = "Buscando pela descrição diretamente na ACP…"
        scope.launch {
            try {
                if (!api.restoreSession()) api.confirmAccess()
                val ranked = loadDescriptionCandidates(cleanQuery)
                results = AcpProductPage(ranked, 0, if (ranked.isEmpty()) 0 else 1, ranked.size)
                searchHint = if (ranked.isEmpty()) {
                    "A ACP não encontrou produto por essa descrição."
                } else {
                    "Confira a descrição e toque no produto ACP correto."
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                error = "Não foi possível consultar a ACP. Confira o acesso em Consultar Produtos e tente novamente."
                results = null
            } finally {
                busy = false
            }
        }
    }

    fun searchEanInAcp(ean: String, webName: String?) {
        val cleanEan = ean.filter(Char::isDigit)
        if (busy || cleanEan.isBlank()) return
        busy = true
        error = null
        results = null
        searchHint = "EAN $cleanEan encontrado no Google. Conferindo automaticamente na ACP…"
        scope.launch {
            try {
                if (!api.restoreSession()) api.confirmAccess()
                val eanPage = api.searchProducts(AcpSearchField.BARCODE, cleanEan, null, 0)
                val exact = eanPage.items.filter { product ->
                    product.barcode.filter(Char::isDigit) == cleanEan
                }
                if (exact.isNotEmpty()) {
                    results = AcpProductPage(exact, 0, 1, exact.size)
                    searchHint = buildString {
                        append("EAN $cleanEan encontrado no Google e confirmado na ACP")
                        webName?.takeIf { it.isNotBlank() }?.let { append(" para $it") }
                        append(". Compare as descrições e toque em CONFIRMAR ESTE PRODUTO.")
                    }
                } else {
                    val ranked = loadDescriptionCandidates(description.trim())
                    results = AcpProductPage(ranked, 0, if (ranked.isEmpty()) 0 else 1, ranked.size)
                    searchHint = if (ranked.isEmpty()) {
                        "O EAN $cleanEan encontrado no Google não apareceu na ACP e também não encontrei candidato pela descrição."
                    } else {
                        "O EAN $cleanEan encontrado no Google não apareceu na ACP. Mostrando candidatos pela descrição para você conferir."
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                error = "Não foi possível consultar a ACP. Confira o acesso em Consultar Produtos e tente novamente."
                results = null
            } finally {
                busy = false
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.9f),
            shape = MaterialTheme.shapes.large
        ) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Revisar oferta do encarte", style = MaterialTheme.typography.titleLarge)
                Text("Confira a oferta e localize o produto ACP. O vínculo só é salvo depois da sua confirmação.")
                if (initial.sourceText.isNotBlank()) Text("Texto reconhecido: ${initial.sourceText}", style = MaterialTheme.typography.bodySmall)
                var typesOpen by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { typesOpen = true }) { Text(type.reviewLabel()) }
                    DropdownMenu(expanded = typesOpen, onDismissRequest = { typesOpen = false }) {
                        FlyerOfferType.entries.forEach { option ->
                            DropdownMenuItem(text = { Text(option.reviewLabel()) }, onClick = { type = option; typesOpen = false })
                        }
                    }
                }
                OutlinedTextField(description, { description = it.take(300) }, label = { Text("Descrição no encarte") })
                @Composable fun amount(key: String, label: String) {
                    OutlinedTextField(numbers[key].orEmpty(), { numbers[key] = it.take(20) }, label = { Text(label) }, singleLine = true)
                }
                amount("regular", "Preço normal do encarte (R$)")
                when (type) {
                    FlyerOfferType.SECOND_UNIT_PERCENT -> amount("percent", "Desconto na segunda unidade (%)")
                    FlyerOfferType.TAKE_PAY_QUANTITY, FlyerOfferType.TAKE_PAY_MEASURE -> {
                        amount("take", "Leve — quantidade")
                        amount("pay", "Pague — quantidade")
                        if (type == FlyerOfferType.TAKE_PAY_MEASURE) {
                            OutlinedTextField(takeUnit, { takeUnit = it.take(10) }, label = { Text("Unidade leve: L, ml, kg ou g") })
                            OutlinedTextField(payUnit, { payUnit = it.take(10) }, label = { Text("Unidade pague: L, ml, kg ou g") })
                        }
                    }
                    FlyerOfferType.CASHBACK -> {
                        amount("price", "Preço anunciado (R$, opcional)")
                        amount("cashPercent", "Cashback (%, opcional)")
                        amount("cashValue", "Cashback (R$, opcional)")
                        Text("Cashback é retorno posterior, não desconto imediato. Informe apenas o que consta no encarte.")
                    }
                    else -> amount("price", "Preço da oferta (R$)")
                }
                OutlinedTextField(conditions, { conditions = it.take(1500) }, label = { Text("Condições, limites e ativação") })
                var clubOpen by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { clubOpen = true }) { Text(club.reviewLabel()) }
                    DropdownMenu(clubOpen, { clubOpen = false }) {
                        FlyerClubCondition.entries.forEach { option ->
                            DropdownMenuItem(text = { Text(option.reviewLabel()) }, onClick = { club = option; clubOpen = false })
                        }
                    }
                }
                HorizontalDivider()
                Text("Produto ACP", style = MaterialTheme.typography.titleMedium)
                if (linked.matchedProductName.isNullOrBlank()) {
                    Text("Nenhum produto ACP confirmado", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(linked.matchedProductName.orEmpty(), style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Código ${linked.productCodes.joinToString()} • EAN ${linked.barcodes.joinToString()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                FlyerEanLookupSection(
                    description = description,
                    enabled = !busy,
                    onEanFound = { ean, webName -> searchEanInAcp(ean, webName) }
                )

                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                searchHint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }

                results?.let { page ->
                    if (page.items.isEmpty()) {
                        Text("Nenhum produto ACP encontrado.", color = MaterialTheme.colorScheme.tertiary)
                    }
                    page.items.forEach { product ->
                        OutlinedCard(
                            onClick = {
                                selectProduct(product)
                                results = null
                                error = null
                                searchHint = "Produto ACP confirmado. Agora confira a oferta e salve."
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("ACP", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Text(product.description, style = MaterialTheme.typography.titleSmall)
                                Text("Código ${product.code} • EAN ${product.barcode}", style = MaterialTheme.typography.bodySmall)
                                product.value?.let { Text("Preço ACP: R$ ${it.toPlainString().replace('.', ',')}") }
                                Text("Encarte: $description", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("CONFIRMAR ESTE PRODUTO", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }

                TextButton(
                    onClick = { searchDescriptionInAcp() },
                    enabled = !busy && description.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Não achou? Buscar pela descrição na ACP")
                }

                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(onClick = {
                    val relevant = mutableListOf("regular")
                    when (type) {
                        FlyerOfferType.SECOND_UNIT_PERCENT -> relevant += "percent"
                        FlyerOfferType.TAKE_PAY_QUANTITY, FlyerOfferType.TAKE_PAY_MEASURE -> relevant += listOf("take", "pay")
                        FlyerOfferType.CASHBACK -> relevant += listOf("price", "cashPercent", "cashValue")
                        else -> relevant += "price"
                    }
                    if (relevant.any { !numbers[it].isNullOrBlank() && number(it) == null }) {
                        error = "Confira os números. Use, por exemplo, 45,49."
                    } else {
                        val draft = linked.copy(
                            type = type,
                            sourceDescription = description.trim(),
                            detail = conditions.trim(),
                            scope = FlyerOfferScope.PRODUCT,
                            clubCondition = club,
                            regularPrice = number("regular"),
                            flyerPrice = if ("price" in relevant) number("price") else null,
                            secondUnitDiscountPercent = if ("percent" in relevant) number("percent") else null,
                            takeQuantity = if ("take" in relevant) number("take") else null,
                            payQuantity = if ("pay" in relevant) number("pay") else null,
                            takeUnit = takeUnit,
                            payUnit = payUnit,
                            cashbackPercent = if ("cashPercent" in relevant) number("cashPercent") else null,
                            cashbackValue = if ("cashValue" in relevant) number("cashValue") else null
                        )
                        val confirmed = draft.confirmedForPublication()
                        if (confirmed == null) error = draft.reviewError() else onSave(confirmed)
                    }
                }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Conferi a oferta e o produto") }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        }
    }
}

private val OCR_SEARCH_STOP_WORDS = setOf(
    "de", "da", "do", "das", "dos", "em", "ou", "com", "sem", "para", "cada",
    "pct", "pt", "cx", "lta", "vd", "tb", "bd", "gfa", "un", "und"
)

private fun normalizeForSearch(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "")
    .lowercase()
    .replace(Regex("[^a-z0-9]+"), " ")
    .trim()

private fun reviewTokens(value: String): List<String> = normalizeForSearch(value)
    .split(' ')
    .filter { token -> token.length >= 3 && token !in OCR_SEARCH_STOP_WORDS }

private fun smartDescriptionQueries(value: String): List<String> {
    val tokens = reviewTokens(value)
    val words = tokens.filterNot { it.matches(Regex("\\d+(?:g|kg|ml|l)?")) }
    val measures = tokens.filter { it.matches(Regex("\\d+(?:g|kg|ml|l)")) }
    return buildList {
        if (words.size >= 2) add(words.take(2).joinToString(" "))
        words.firstOrNull()?.let(::add)
        measures.firstOrNull()?.let(::add)
    }.distinct().filter { it.isNotBlank() }.take(3)
}

private fun reviewMatchScore(source: String, candidate: String): Double {
    val sourceTokens = reviewTokens(source).toSet()
    val candidateTokens = reviewTokens(candidate).toSet()
    if (sourceTokens.isEmpty() || candidateTokens.isEmpty()) return 0.0
    val exact = sourceTokens.intersect(candidateTokens).size.toDouble()
    val coverage = exact / sourceTokens.size
    val sourceMeasures = sourceTokens.filter { it.any(Char::isDigit) }.toSet()
    val candidateMeasures = candidateTokens.filter { it.any(Char::isDigit) }.toSet()
    val measureBonus = when {
        sourceMeasures.isEmpty() -> 0.0
        sourceMeasures.any { it in candidateMeasures } -> 0.25
        else -> -0.20
    }
    return (coverage + measureBonus).coerceIn(0.0, 1.25)
}

private fun FlyerOfferType.reviewLabel() = when (this) {
    FlyerOfferType.SECOND_UNIT_PERCENT -> "Desconto na segunda unidade"
    FlyerOfferType.TAKE_PAY_QUANTITY -> "Leve / Pague — unidades"
    FlyerOfferType.TAKE_PAY_MEASURE -> "Leve / Pague — medida"
    FlyerOfferType.DE_POR -> "De / Por"
    FlyerOfferType.CASHBACK -> "Cashback"
    FlyerOfferType.FLYER_PRICE -> "Preço do encarte"
}

internal fun FlyerClubCondition.reviewLabel() = when (this) {
    FlyerClubCondition.NOT_INFORMED -> "Clube: não informado no encarte"
    FlyerClubCondition.REQUIRED -> "Exclusivo Clube, conforme encarte"
    FlyerClubCondition.NOT_REQUIRED -> "Sem exigência de Clube, conforme encarte"
}
