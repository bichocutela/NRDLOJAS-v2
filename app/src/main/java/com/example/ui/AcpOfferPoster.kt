package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.data.acp.AcpOffer
import com.example.data.acp.brl

/** Poster styling only; prices and eligibility are never inferred from the artwork. */
@Composable
internal fun AcpOfferPoster(offer: AcpOffer, compact: Boolean) {
    val yellow = Color(0xFFFFEB27)
    val red = Color(0xFFB90012)
    val blue = Color(0xFF005A9C)
    val club = offer.title == "Clube de Vantagens"
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = yellow, contentColor = Color.Black) {
        Column {
            Text(
                text = when (offer.title) {
                    "Clube de Vantagens" -> "PREÇO CLUBE"
                    "De/Por" -> "DE / POR"
                    "Segunda unidade" -> "NA SEGUNDA UNIDADE"
                    "Leve/Pague" -> "LEVE / PAGUE"
                    "Cashback", "Cashback em valor" -> "CASHBACK"
                    else -> offer.title.uppercase()
                },
                modifier = Modifier.fillMaxWidth().background(if (club) blue else red).padding(12.dp),
                color = Color.White, fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.titleMedium
            )
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                offer.referencePrice?.let { reference ->
                    val referenceLabel = when (offer.title) {
                        "De/Por" -> "DE"
                        "Atacado" -> "PREÇO VAREJO"
                        "Leve/Pague" -> "PREÇO UNITÁRIO NORMAL"
                        "Cashback", "Cashback em valor" -> "PREÇO PRINCIPAL"
                        else -> "PREÇO NORMAL CADASTRADO"
                    }
                    Text(referenceLabel, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text(reference.brl(), style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textDecoration = if (offer.title == "De/Por") TextDecoration.LineThrough else TextDecoration.None)
                }
                offer.headline?.let {
                    Text(it, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = red)
                }
                if (offer.price != null) {
                    val priceLabel = when (offer.title) {
                        "De/Por" -> "POR"
                        "Atacado" -> "PREÇO ATACADO POR UNIDADE"
                        "Leve/Pague" -> "MÉDIA POR UNIDADE"
                        else -> null
                    }
                    priceLabel?.let { Text(it, fontWeight = FontWeight.Black, color = red) }
                    Surface(shape = MaterialTheme.shapes.small, color = if (club) red else yellow,
                        contentColor = if (club) Color.White else red) {
                        Text(offer.price.brl(), modifier = Modifier.fillMaxWidth().padding(if (club) 12.dp else 0.dp),
                            style = if (compact) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Black)
                    }
                }
                if (club) Text("Exclusivo Clube de Vantagens", fontWeight = FontWeight.Bold)
                // Keep quantities, reference-price context and cashback conditions visible even in search results.
                Text(offer.detail, style = MaterialTheme.typography.bodySmall)
            }
            Row(Modifier.fillMaxWidth().height(4.dp)) {
                listOf(red, Color(0xFF00863D), Color(0xFFFFA000), blue).forEach { color ->
                    Box(Modifier.weight(1f).fillMaxHeight().background(color))
                }
            }
        }
    }
}
