package com.example.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.data.acp.AcpOffer
import com.example.data.acp.AcpOfferFamily
import com.example.data.acp.AcpOfferValidity
import com.example.data.acp.AcpOfferValidityStore
import com.example.data.acp.brl
import com.example.data.acp.family
import com.example.data.ThemeBackground
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private val validityDateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).apply { isLenient = false }

private fun validDate(value: String): Boolean = runCatching { validityDateFormat.parse(value) }.getOrNull() != null

private fun dateMillis(value: String): Long? = runCatching { validityDateFormat.parse(value)?.time }.getOrNull()

private fun shortValidity(value: String): String {
    val parts = value.split("/")
    return if (parts.size == 3) "${parts[0]}/${parts[1]}" else value
}

@Composable
private fun rememberOfferValidity(productName: String?, offer: AcpOffer): State<AcpOfferValidity?> {
    val name = productName.orEmpty()
    return remember(name, offer.family) {
        if (name.isBlank()) kotlinx.coroutines.flow.flowOf(null)
        else AcpOfferValidityStore.observe(name, offer.family)
    }.collectAsState(initial = null)
}

@Composable
private fun OfferHeader(
    label: String,
    validity: AcpOfferValidity?,
    background: Color,
    compact: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth().background(background).padding(horizontal = if (compact) 10.dp else 12.dp, vertical = if (compact) 7.dp else 12.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color.White,
            fontWeight = FontWeight.Black,
            style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium
        )
        validity?.endDate?.takeIf { it.isNotBlank() }?.let { end ->
            Surface(shape = MaterialTheme.shapes.small, color = Color(0xFF0D6FB8), contentColor = Color.White) {
                Text(
                    "Válido até ${shortValidity(end)}",
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

/** Poster styling only; prices and eligibility are never inferred from the artwork. */
@Composable
internal fun AcpOfferPoster(
    offer: AcpOffer,
    compact: Boolean,
    productName: String? = null,
    banner: ThemeBackground? = null,
    validityOverride: AcpOfferValidity? = null
) {
    // Compact result cards receive validity from one shared list subscription.
    val validity = validityOverride ?: if (compact) null else rememberOfferValidity(productName, offer).value
    if (banner != null) {
        PersonalizedOfferBanner(productName, offer, banner, compact, validity)
        return
    }
    val yellow = Color(0xFFFFEB27)
    val red = Color(0xFFB90012)
    val blue = Color(0xFF005A9C)
    val club = offer.title == "Clube de Vantagens"
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
            OfferHeader(
                label = when (offer.title) {
                    "Clube de Vantagens" -> "PREÇO CLUBE"
                    "De/Por" -> "DE / POR"
                    "Segunda unidade" -> "NA SEGUNDA UNIDADE"
                    "Leve/Pague" -> "LEVE / PAGUE"
                    "Cashback", "Cashback em valor" -> "CASHBACK"
                    else -> offer.title.uppercase()
                },
                validity = validity,
                background = if (club) blue else red,
                compact = compact
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
                    Text(referenceLabel, style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text(reference.brl(), style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textDecoration = if (offer.title == "De/Por") TextDecoration.LineThrough else TextDecoration.None)
                }
                offer.headline?.let { Text(it, style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = red) }
                if (offer.price != null) {
                    val priceLabel = when (offer.title) {
                        "De/Por" -> "POR"
                        "Atacado" -> "PREÇO ATACADO POR UNIDADE"
                        "Leve/Pague" -> "MÉDIA POR UNIDADE"
                        else -> null
                    }
                    priceLabel?.let { Text(it, fontWeight = FontWeight.Black, color = red, style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium) }
                    Surface(shape = MaterialTheme.shapes.small, color = if (club) red else yellow, contentColor = if (club) Color.White else red) {
                        Text(offer.price.brl(), modifier = Modifier.fillMaxWidth().padding(if (club) if (compact) 9.dp else 12.dp else 0.dp), style = if (compact) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black)
                    }
                }
                if (club) Text("Exclusivo Clube de Vantagens", fontWeight = FontWeight.Bold, style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium)
                Text(offer.detail, style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall)
            }
            Row(Modifier.fillMaxWidth().height(if (compact) 3.dp else 4.dp)) {
                listOf(red, Color(0xFF00863D), Color(0xFFFFA000), blue).forEach { color -> Box(Modifier.weight(1f).fillMaxHeight().background(color)) }
            }
        }
    }
}

@Composable
internal fun AcpOfferLandscapePoster(
    productName: String,
    offer: AcpOffer,
    banner: ThemeBackground? = null
) {
    val validity by rememberOfferValidity(productName, offer)
    val isMaster = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email?.trim()?.lowercase() == "mestre@nrdlojas.com"
    val narrowPhone = LocalConfiguration.current.screenWidthDp < 430
    var editValidity by remember { mutableStateOf(false) }

    if (banner != null) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PersonalizedOfferBanner(productName, offer, banner, compact = narrowPhone, validity = validity)
            if (isMaster) TextButton(onClick = { editValidity = true }) { Text("Validade Oferta") }
        }
    } else {
        val yellow = Color(0xFFFFEB27)
        val red = Color(0xFFB90012)
        val blue = Color(0xFF005A9C)
        val club = offer.family == AcpOfferFamily.CLUB
        val headerColor = if (club) blue else red
        val headerLabel = when (offer.family) {
            AcpOfferFamily.DE_POR -> "DE / POR"
            AcpOfferFamily.CLUB -> "PREÇO CLUBE"
            AcpOfferFamily.TAKE_PAY -> "LEVE / PAGUE"
            AcpOfferFamily.SECOND_UNIT -> "NA SEGUNDA UNIDADE"
            AcpOfferFamily.CASHBACK, AcpOfferFamily.CASHBACK_VALUE -> "CASHBACK"
            else -> offer.title.uppercase()
        }
        Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = yellow, contentColor = Color.Black) {
            if (narrowPhone) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OfferHeader(
                        label = headerLabel,
                        validity = validity,
                        background = headerColor,
                        compact = true
                    )
                    Text(productName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(offer.detail, style = MaterialTheme.typography.bodySmall)
                    HorizontalDivider(color = Color.Black.copy(alpha = 0.12f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            offer.referencePrice?.let {
                                Text(
                                    it.brl(),
                                    style = MaterialTheme.typography.titleSmall,
                                    textDecoration = if (offer.family == AcpOfferFamily.DE_POR) TextDecoration.LineThrough else TextDecoration.None
                                )
                            }
                            offer.headline?.let {
                                Text(it, color = red, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleSmall)
                            }
                        }
                        offer.price?.let {
                            Text(
                                it.brl(),
                                color = if (club) Color.White else red,
                                fontWeight = FontWeight.Black,
                                style = MaterialTheme.typography.headlineMedium,
                                modifier = Modifier
                                    .background(if (club) red else yellow)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (isMaster) {
                        TextButton(
                            onClick = { editValidity = true },
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
                        ) { Text("Validade Oferta") }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1.15f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        OfferHeader(
                            label = headerLabel,
                            validity = validity,
                            background = headerColor,
                            compact = true
                        )
                        Text(productName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(offer.detail, style = MaterialTheme.typography.bodySmall)
                        if (isMaster) TextButton(onClick = { editValidity = true }, contentPadding = PaddingValues(0.dp)) { Text("Validade Oferta") }
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        offer.referencePrice?.let { Text(it.brl(), style = MaterialTheme.typography.titleMedium, textDecoration = if (offer.family == AcpOfferFamily.DE_POR) TextDecoration.LineThrough else TextDecoration.None) }
                        offer.headline?.let { Text(it, color = red, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium) }
                        offer.price?.let { Text(it.brl(), color = if (club) Color.White else red, fontWeight = FontWeight.Black, style = MaterialTheme.typography.displaySmall, modifier = Modifier.background(if (club) red else yellow).padding(horizontal = 8.dp, vertical = 2.dp)) }
                    }
                }
            }
        }
    }

    if (editValidity) OfferValidityDialog(productName, offer, validity, onDismiss = { editValidity = false })
}

@Composable
private fun ValidityDateField(
    label: String,
    value: String,
    minDate: String? = null,
    enabled: Boolean,
    onDateSelected: (String) -> Unit
) {
    val context = LocalContext.current

    fun openCalendar() {
        val calendar = Calendar.getInstance()
        dateMillis(value)?.let { calendar.timeInMillis = it }

        val picker = DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                onDateSelected(String.format(Locale("pt", "BR"), "%02d/%02d/%04d", dayOfMonth, month + 1, year))
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )

        minDate?.let { dateMillis(it) }?.let { picker.datePicker.minDate = it }
        picker.show()
    }

    OutlinedTextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        enabled = enabled,
        label = { Text(label) },
        placeholder = { Text("Selecione no calendário") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        trailingIcon = {
            TextButton(enabled = enabled, onClick = { openCalendar() }) {
                Text("📅", style = MaterialTheme.typography.titleMedium)
            }
        }
    )
}

@Composable
private fun OfferValidityDialog(
    productName: String,
    offer: AcpOffer,
    current: AcpOfferValidity?,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var start by remember(current) { mutableStateOf(current?.startDate.orEmpty()) }
    var end by remember(current) { mutableStateOf(current?.endDate.orEmpty()) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val startMillis = dateMillis(start)
    val endMillis = dateMillis(end)
    val invalidRange = startMillis != null && endMillis != null && endMillis < startMillis
    val canSave = validDate(start) && validDate(end) && !invalidRange

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Validade Oferta") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(productName, style = MaterialTheme.typography.titleSmall)
                Text("Selecione a data inicial e final da oferta nos calendários.", style = MaterialTheme.typography.bodySmall)
                ValidityDateField(
                    label = "Validade inicial",
                    value = start,
                    enabled = !saving,
                    onDateSelected = {
                        start = it
                        if (dateMillis(end)?.let { currentEnd -> dateMillis(it)?.let { selectedStart -> currentEnd < selectedStart } } == true) end = ""
                        error = null
                    }
                )
                ValidityDateField(
                    label = "Validade final",
                    value = end,
                    minDate = start.takeIf { validDate(it) },
                    enabled = !saving,
                    onDateSelected = {
                        end = it
                        error = null
                    }
                )
                if (invalidRange) Text("A validade final não pode ser anterior à data inicial.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = canSave && !saving, onClick = {
                saving = true
                scope.launch {
                    try {
                        AcpOfferValidityStore.save(productName, offer.family, start, end)
                        onDismiss()
                    } catch (_: Exception) {
                        error = "Não foi possível salvar a validade. Tente novamente."
                    } finally { saving = false }
                }
            }) { Text(if (saving) "Salvando…" else "Salvar validade") }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun PersonalizedOfferBanner(
    productName: String?,
    offer: AcpOffer,
    banner: ThemeBackground,
    compact: Boolean,
    validity: AcpOfferValidity? = null
) {
    Surface(modifier = Modifier.fillMaxWidth().aspectRatio(3f), shape = MaterialTheme.shapes.medium, tonalElevation = 1.dp) {
        Box(contentAlignment = Alignment.Center) {
            AsyncImage(
                model = banner.url,
                contentDescription = banner.label,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = banner.imageScale.coerceIn(0.5f, 3f) * banner.imageStretchX.coerceIn(0.5f, 2.5f)
                    scaleY = banner.imageScale.coerceIn(0.5f, 3f) * banner.imageStretchY.coerceIn(0.5f, 2.5f)
                    translationX = size.width * banner.imageOffsetX.coerceIn(-1f, 1f)
                    translationY = size.height * banner.imageOffsetY.coerceIn(-1f, 1f)
                },
                contentScale = ContentScale.Fit
            )
            Surface(color = Color.White.copy(alpha = 0.90f), contentColor = Color.Black, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth(if (compact) 0.64f else 0.58f)) {
                Column(modifier = Modifier.padding(horizontal = if (compact) 8.dp else 14.dp, vertical = if (compact) 5.dp else 9.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(if (compact) 1.dp else 3.dp)) {
                    validity?.endDate?.takeIf { it.isNotBlank() }?.let { Text("Válido até ${shortValidity(it)}", color = Color(0xFF005A9C), fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall) }
                    productName?.takeIf { it.isNotBlank() }?.let { Text(it, style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 2) }
                    offer.headline?.let { Text(it, color = Color(0xFFB90012), fontWeight = FontWeight.Black, style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleLarge) }
                    offer.price?.let { Text(it.brl(), color = Color(0xFFB90012), fontWeight = FontWeight.Black, style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium) }
                    if (offer.price == null && offer.headline == null) Text(offer.title.uppercase(), fontWeight = FontWeight.Black, style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium)
                    Text(offer.detail, style = MaterialTheme.typography.labelSmall, maxLines = if (compact) 1 else 2)
                }
            }
        }
    }
}
