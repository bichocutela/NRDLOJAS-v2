package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.data.acp.*
import com.example.data.flyer.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

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
    var query by remember { mutableStateOf(initial.sourceDescription) }
    var field by remember { mutableStateOf(AcpSearchField.DESCRIPTION) }
    var results by remember { mutableStateOf<AcpProductPage?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val numbers = remember { mutableStateMapOf(
        "regular" to formatQuantity(initial.regularPrice), "price" to formatQuantity(initial.flyerPrice),
        "percent" to formatQuantity(initial.secondUnitDiscountPercent),
        "take" to formatQuantity(initial.takeQuantity), "pay" to formatQuantity(initial.payQuantity),
        "cashPercent" to formatQuantity(initial.cashbackPercent), "cashValue" to formatQuantity(initial.cashbackValue)
    ) }
    var takeUnit by remember { mutableStateOf(initial.takeUnit.orEmpty()) }
    var payUnit by remember { mutableStateOf(initial.payUnit.orEmpty()) }
    fun number(key: String) = numbers[key]?.trim()?.replace(',', '.')?.toDoubleOrNull()
    fun search(page: Int) {
        if (busy || query.isBlank()) return
        busy = true
        error = null
        scope.launch {
            try {
                if (!api.restoreSession()) api.confirmAccess()
                results = api.searchProducts(field, query.trim(), null, page)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                error = "Não foi possível consultar a ACP. Confira o acesso em Consultar Produtos e tente novamente."
                results = null
            } finally { busy = false }
        }
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Revisar oferta do encarte", style = MaterialTheme.typography.titleLarge)
                Text("Confira a página ${initial.page} do arquivo original. A busca na ACP confirma a identidade do produto; a regra abaixo vem do encarte.")
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
                Text("Vincular ao produto ACP", style = MaterialTheme.typography.titleMedium)
                Text(linked.matchedProductName ?: "Nenhum produto selecionado")
                Text("Código: ${linked.productCodes.joinToString()} • EAN: ${linked.barcodes.joinToString()}", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    AcpSearchField.entries.forEach { option ->
                        FilterChip(selected = field == option, onClick = { field = option; results = null }, label = { Text(option.label) }, enabled = !busy)
                    }
                }
                OutlinedTextField(query, { query = it.take(300); results = null }, label = { Text(field.label) }, enabled = !busy)
                Button(onClick = { search(0) }, enabled = !busy && query.isNotBlank()) { Text(if (busy) "Buscando…" else "Buscar na ACP") }
                results?.let { page ->
                    if (page.items.isEmpty()) Text("Nenhum produto encontrado. Tente o EAN ou código.")
                    page.items.forEach { product ->
                        OutlinedCard(onClick = {
                            linked = linked.copy(productCodes = listOf(product.code).filter { it.isNotBlank() },
                                barcodes = listOf(product.barcode).filter { it.isNotBlank() }, matchedProductName = product.description)
                            results = null
                        }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(10.dp)) {
                                Text(product.description)
                                Text("${product.code} • ${product.barcode}", style = MaterialTheme.typography.bodySmall)
                                product.value?.let { Text("Preço ACP: R$ ${it.toPlainString().replace('.', ',')}") }
                                Text("Selecionar este produto", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    Row {
                        TextButton(onClick = { search(page.pageIndex - 1) }, enabled = !busy && page.pageIndex > 0) { Text("Anterior") }
                        TextButton(onClick = { search(page.pageIndex + 1) }, enabled = !busy && page.pageIndex + 1 < page.totalPages) { Text("Próxima") }
                    }
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
                        val draft = linked.copy(type = type, sourceDescription = description.trim(), detail = conditions.trim(),
                            scope = FlyerOfferScope.PRODUCT, clubCondition = club, regularPrice = number("regular"),
                            flyerPrice = if ("price" in relevant) number("price") else null,
                            secondUnitDiscountPercent = if ("percent" in relevant) number("percent") else null,
                            takeQuantity = if ("take" in relevant) number("take") else null,
                            payQuantity = if ("pay" in relevant) number("pay") else null,
                            takeUnit = takeUnit, payUnit = payUnit,
                            cashbackPercent = if ("cashPercent" in relevant) number("cashPercent") else null,
                            cashbackValue = if ("cashValue" in relevant) number("cashValue") else null)
                        val confirmed = draft.confirmedForPublication()
                        if (confirmed == null) error = draft.reviewError() else onSave(confirmed)
                    }
                }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Conferi a oferta e o produto") }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        }
    }
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
