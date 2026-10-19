package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.Product
import com.example.data.acp.*
import org.json.JSONArray
import org.json.JSONObject

/** Uses the same commercial snapshot/parser and barcode renderer as Consultar Preços. */
@Composable
internal fun PromotionProductDetailsContent(raw: String?, ean: String?, code: String, name: String) {
    val product = remember(raw) {
        runCatching {
            raw?.let { AcpProductParser.page(JSONObject().put("items", JSONArray().put(JSONObject(it))), 0).items.single() }
        }.getOrNull()
    }
    var showBarcode by remember { mutableStateOf(false) }
    val barcode = product?.barcode?.takeIf { it.isNotBlank() } ?: ean.orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(enabled = barcode.isNotBlank(), onClick = { showBarcode = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Ver código de barras")
        }
        if (product == null) Text("Código: $code • EAN: ${barcode.ifBlank { "não informado" }}", style = MaterialTheme.typography.bodySmall)
        product?.let { AcpCommercialProductContent(it) }
    }
    if (showBarcode) ProductBarcodeDialog(
        product = Product(code = barcode, name = name, searchName = name.lowercase(), category = "Promoções", unit = product?.unit ?: "un"),
        onDismiss = { showBarcode = false }
    )
}

@Composable
internal fun AcpCommercialProductContent(
    item: AcpProduct,
    additionalOffers: List<AcpOffer> = emptyList(),
    bannerFor: (AcpOffer) -> com.example.data.ThemeBackground? = { null }
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Código: ${item.code.ifBlank { "não informado" }} • EAN: ${item.barcode.ifBlank { "não informado" }}")
        Text("Preço principal: ${item.value?.brl() ?: "não informado"}${item.unit?.let { " / $it" } ?: ""}", style = MaterialTheme.typography.titleMedium)
            Text("Informações do produto", style = MaterialTheme.typography.titleMedium)
            item.unit?.let { Text("Unidade: $it") }
            item.stockQuantity?.let { Text("Estoque: ${it.quantity()}") }
            item.unitLimitPerCPF?.takeIf { it.signum() > 0 }?.let { Text("Limite por CPF: ${it.quantity()}") }
            item.auxDescriptions.forEach { Text(it) }
            item.characteristic?.let { Text("Característica: $it") }
            if (item.categories.isNotEmpty()) Text("Categorias: ${item.categories.joinToString()}")
            item.productFamily?.let { Text("Família: $it") }
            item.packageQuantity?.let { Text("Embalagem: ${it.quantity()} ${item.packageType.orEmpty()}") }
            item.dueDate?.let { Text("Validade do produto: ${acpDateLabel(it)}") }
            item.contentQuantity?.let { Text("Conteúdo: ${it.quantity()} ${item.contentUnit.orEmpty()}") }
            HorizontalDivider()
            Text("Preços e condições", style = MaterialTheme.typography.titleMedium)
            (item.offers() + additionalOffers).forAutomaticDisplay().forEach { offer -> AcpOfferLandscapePoster(item.description, offer, bannerFor(offer)) }
    }
}
