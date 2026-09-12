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
import com.example.data.acp.family

/** Poster styling only; prices and eligibility are never inferred from the artwork. */
@Composable
internal fun AcpOfferPoster(offer: AcpOffer, compact: Boolean) {
    val yellow = Color(0xFFFFEB27)
    val red = Color(0xFFB90012)
    val blue = Color(0xFF005A9C)
    val club = offer.title == "Clube de Vantagens"
    val headerHorizontal = if (compact) 10.dp else 12.dp
    val headerVertical = if (compact) 7.dp else 12.dp
    val bodyHorizontal = if (compact) 10.dp else 14.dp
    val bodyVertical = if (compact) 8.dp else 14.dp
    val bodySpacing = if (compact) 4.dp else 8.dp

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = yellow,
        contentColor = Color.Black
    ) {
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
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (club) blue else red)
                    .padding(horizontal = headerHorizontal, vertical = headerVertical),
                color = Color.White,
                fontWeight = FontWeight.Black,
                style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium
            )
            Column(
                Modifier.padding(horizontal = bodyHorizontal, vertical = bodyVertical),
                verticalArrangement = Arrangement.spacedBy(bodySpacing)
            ) {
                offer.referencePrice?.let { reference ->
                    val referenceLabel = when (offer.title) {
                        "De/Por" -> "DE"
                        "Atacado" -> "PREÇO VAREJO"
                        "Leve/Pague" -> "PREÇO UNITÁRIO NORMAL"
                        "Cashback", "Cashback em valor" -> "PREÇO PRINCIPAL"
                        else -> "PREÇO NORMAL CADASTRADO"
                    }
                    Text(
                        referenceLabel,
                        style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        reference.brl(),
                        style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textDecoration = if (offer.title == "De/Por") TextDecoration.LineThrough else TextDecoration.None
                    )
                }
                offer.headline?.let {
                    Text(
                        it,
                        style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = red
                    )
                }
                if (offer.price != null) {
                    val priceLabel = when (offer.title) {
                        "De/Por" -> "POR"
                        "Atacado" -> "PREÇO ATACADO POR UNIDADE"
                        "Leve/Pague" -> "MÉDIA POR UNIDADE"
                        else -> null
                    }
                    priceLabel?.let {
                        Text(
                            it,
                            fontWeight = FontWeight.Black,
                            color = red,
                            style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium
                        )
                    }
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = if (club) red else yellow,
                        contentColor = if (club) Color.White else red
                    ) {
                        Text(
                            offer.price.brl(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(if (club) if (compact) 9.dp else 12.dp else 0.dp),
                            style = if (compact) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
                if (club) {
                    Text(
                        "Exclusivo Clube de Vantagens",
                        fontWeight = FontWeight.Bold,
                        style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium
                    )
                }
                Text(
                    offer.detail,
                    style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall
                )
            }
            Row(Modifier.fillMaxWidth().height(if (compact) 3.dp else 4.dp)) {
                listOf(red, Color(0xFF00863D), Color(0xFFFFA000), blue).forEach { color ->
                    Box(Modifier.weight(1f).fillMaxHeight().background(color))
                }
            }
        }
    }
}

@Composable
internal fun AcpOfferLandscapePoster(productName: String, offer: AcpOffer) {
    val yellow = Color(0xFFFFEB27)
    val red = Color(0xFFB90012)
    val blue = Color(0xFF005A9C)
    val club = offer.family == com.example.data.acp.AcpOfferFamily.CLUB
    val headerColor = if (club) blue else red
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = yellow,
        contentColor = Color.Black
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1.15f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = when (offer.family) {
                        com.example.data.acp.AcpOfferFamily.DE_POR -> "DE / POR"
                        com.example.data.acp.AcpOfferFamily.CLUB -> "PREÇO CLUBE"
                        com.example.data.acp.AcpOfferFamily.TAKE_PAY -> "LEVE / PAGUE"
                        com.example.data.acp.AcpOfferFamily.SECOND_UNIT -> "NA SEGUNDA UNIDADE"
                        com.example.data.acp.AcpOfferFamily.CASHBACK,
                        com.example.data.acp.AcpOfferFamily.CASHBACK_VALUE -> "CASHBACK"
                        else -> offer.title.uppercase()
                    },
                    modifier = Modifier.background(headerColor).padding(horizontal = 10.dp, vertical = 5.dp),
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(productName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(offer.detail, style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = androidx.compose.ui.Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                offer.referencePrice?.let {
                    Text(it.brl(), style = MaterialTheme.typography.titleMedium, textDecoration = if (offer.family == com.example.data.acp.AcpOfferFamily.DE_POR) TextDecoration.LineThrough else TextDecoration.None)
                }
                offer.headline?.let { Text(it, color = red, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium) }
                offer.price?.let {
                    Text(it.brl(), color = if (club) Color.White else red, fontWeight = FontWeight.Black, style = MaterialTheme.typography.displaySmall,
                        modifier = Modifier.background(if (club) red else yellow).padding(horizontal = 8.dp, vertical = 2.dp))
                }
            }
        }
    }
}
