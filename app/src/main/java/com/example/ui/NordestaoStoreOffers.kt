package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import coil.compose.AsyncImage
import com.example.data.*
import com.example.data.promotions.StoreProductImageRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NordestaoStoreOffersContent(store: NordestaoStore?, refresh: Int, onChooseStore: () -> Unit,
    modifier: Modifier = Modifier,
    loadOffers: suspend (Long) -> NordestaoStoreOffersSnapshot = { NordestaoStoreOffersRepository.load(it) },
    resolveEan: suspend (String, String) -> String? = { code, name ->
        StoreProductImageRepository.shared.lookup(code, name).ean
    }) {
    if(store == null) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Escolha sua loja preferida para consultar as ofertas.")
            Button(onClick = onChooseStore) { Text("Escolher loja") }
        }
        return
    }
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("nrd_store_offers", android.content.Context.MODE_PRIVATE) }
    var snapshot by remember(store.id) { mutableStateOf(runCatching {
        NordestaoStoreOffersCodec.decode(preferences.getString("store_${store.id}", null) ?: error("Sem cache"), store.id)
    }.getOrNull()) }
    var loading by remember(store.id) { mutableStateOf(true) }
    var failed by remember(store.id) { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    var query by remember(store.id) { mutableStateOf("") }
    var category by remember(store.id) { mutableStateOf("Todas") }
    var sortName by rememberSaveable { mutableStateOf(OfferSortOption.DISCOUNT_DESC.name) }
    val sortOption = OfferSortOption.values().firstOrNull { it.name == sortName } ?: OfferSortOption.DISCOUNT_DESC
    var detail by remember(store.id) { mutableStateOf<NordestaoStoreOffer?>(null) }
    var detailLayer by remember(store.id) { mutableStateOf<GraphicsLayer?>(null) }
    val scope = rememberCoroutineScope()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(store.id, refresh, retry, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while(true) {
                loading = true
                try {
                    val next = loadOffers(store.id)
                    require(next.storeId == store.id)
                    snapshot = next; failed = false
                    preferences.edit().putString("store_${store.id}", NordestaoStoreOffersCodec.encode(next)).apply()
                } catch(cancelled: CancellationException) { throw cancelled }
                catch(_: Exception) { failed = true }
                finally { loading = false; now = System.currentTimeMillis() }
                delay(60_000)
            }
        }
    }
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while(true) { now = System.currentTimeMillis(); delay(1000) }
        }
    }
    val active = snapshot?.offers.orEmpty().filter { it.active(now) }
    val categories = listOf("Todas") + active.map { it.category }.distinct().sorted()
    val shown = active.filter { it.matches(query) && (category == "Todas" || it.category == category) }
        .let { sortStoreOffers(it, sortOption) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("${store.name} ★", fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onChooseStore) { Text("Alterar loja") }
            Spacer(Modifier.width(8.dp))
            OfferSortSelector(selected = sortOption, onSelected = { sortName = it.name }, options = listOf(
                OfferSortOption.NAME, OfferSortOption.DISCOUNT_DESC, OfferSortOption.DISCOUNT_ASC,
                OfferSortOption.PRICE_ASC, OfferSortOption.PRICE_DESC))
        }
        OutlinedTextField(query, { query = it }, label = { Text("Buscar produto") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if(loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if(failed || snapshot?.stale == true) {
            Text(if(snapshot == null) "Não foi possível carregar as ofertas desta loja." else "Não foi possível atualizar. Ofertas da última consulta; confira as condições e a validade.", color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { retry++ }, enabled = !loading) { Text("Tentar novamente") }
        }
        snapshot?.let { Text("Consulta: ${it.fetchedAt.replace('T', ' ').take(16)} UTC", style = MaterialTheme.typography.bodySmall) }
        Text("Ofertas que exigem ativação devem ser ativadas no aplicativo Nordestão Pra Você.", style = MaterialTheme.typography.bodySmall)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(categories) { value -> FilterChip(selected = category == value, onClick = { category = value }, label = { Text(value) }) }
        }
        if(snapshot != null && shown.isEmpty()) Text(if(active.isEmpty()) "Nenhuma oferta vigente disponível para esta loja." else "Nenhum produto encontrado.")
        LazyVerticalGrid(columns = GridCells.Adaptive(150.dp), modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(shown, key = { it.id }) { offer ->
                // Only composed/visible cards perform lookup; the existing official cache is shared with detail.
                val ean by rememberVerifiedStoreOfferEan(offer, resolveEan)
                val shareLayer = rememberGraphicsLayer()
                fun openDetail() { detailLayer = shareLayer; detail = offer }
                OutlinedCard(modifier = Modifier
                    .drawWithContent {
                        shareLayer.record { this@drawWithContent.drawContent() }
                        drawLayer(shareLayer)
                    }
                    .combinedClickable(
                        onClick = { openDetail() },
                        onLongClick = {
                            scope.launch {
                                val copied = copyProductCardToClipboard(context, shareLayer, offer.description)
                                Toast.makeText(context, if(copied) "Quadradinho copiado"
                                    else "Não foi possível copiar a oferta.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if(offer.tag.isNotBlank()) SuggestionChip(onClick = { openDetail() }, label = { Text(offer.tag) })
                        OfficialOfferPhoto(offer,Modifier.fillMaxWidth().height(130.dp))
                        OfferPrice(offer)
                        Text(offer.description, style = MaterialTheme.typography.bodyMedium)
                        if(offer.maxAmount > 0) Text("Máx. ${NumberFormat.getNumberInstance(Locale.forLanguageTag("pt-BR")).format(offer.maxAmount)} un.", style = MaterialTheme.typography.bodySmall)
                        Text(offer.validity, style = MaterialTheme.typography.bodySmall)
                        if(offer.code.isNotBlank()) Text("Código: ${offer.code}", style = MaterialTheme.typography.labelSmall)
                        Text("EAN: ${ean ?: "não disponível"}", style = MaterialTheme.typography.labelSmall,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Ver condições", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
    detail?.let { offer ->
        val verifiedEan by rememberVerifiedStoreOfferEan(offer, resolveEan)
        val offerText = remember(offer, verifiedEan, store.name) {
            storeOfferSharingText(offer, store.name, verifiedEan)
        }
        // A custom dialog keeps all actions visible instead of letting the default
        // AlertDialog footer overflow when font/display size is increased.
        Dialog(onDismissRequest = { detail = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.92f).widthIn(max = 560.dp)
                    .heightIn(max = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.88f).dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp
            ) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    SelectionContainer {
                        Text(offer.description, style = MaterialTheme.typography.headlineSmall)
                    }
                    Spacer(Modifier.height(12.dp))
                    SelectionContainer {
                        Column(
                            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OfficialOfferPhoto(offer, Modifier.fillMaxWidth().height(180.dp))
                            OfferPrice(offer)
                            Text(offer.validity)
                            if(offer.maxAmount > 0) Text("Quantidade máxima: ${NumberFormat.getNumberInstance(Locale.forLanguageTag("pt-BR")).format(offer.maxAmount)} un.")
                            if(offer.conditions.isNotBlank()) Text(offer.conditions)
                            if(offer.activationRequired) Text("Ative esta oferta no aplicativo Nordestão Pra Você antes da compra.")
                            Text("Loja: ${store.name}")
                            if(offer.code.isNotBlank()) Text("Código: ${offer.code}")
                            Text("EAN: ${verifiedEan ?: "não disponível"}")
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    // Three always-visible actions, split into two rows only on narrow screens.
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val narrow = maxWidth < 330.dp
                        val copyAction: () -> Unit = {
                            copyStoreOfferText(context, offerText)
                            Toast.makeText(context, "Dados da oferta copiados", Toast.LENGTH_SHORT).show()
                        }
                        val shareAction: () -> Unit = {
                            scope.launch {
                                val shared = shareStoreOfferCard(context, detailLayer, offer.description, offerText)
                                if(!shared) Toast.makeText(context, "Não foi possível compartilhar a oferta.", Toast.LENGTH_SHORT).show()
                            }
                        }
                        if(narrow) {
                            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                                Row {
                                    TextButton(onClick = copyAction) { Text("Copiar") }
                                    TextButton(onClick = shareAction) { Text("Compartilhar") }
                                }
                                TextButton(onClick = { detail = null }) { Text("Fechar") }
                            }
                        } else {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = copyAction) { Text("Copiar") }
                                TextButton(onClick = shareAction) { Text("Compartilhar") }
                                TextButton(onClick = { detail = null }) { Text("Fechar") }
                            }
                        }
                    }
                }
            }
        }
    }
}
@Composable
private fun rememberVerifiedStoreOfferEan(
    offer: NordestaoStoreOffer,
    resolveEan: suspend (String, String) -> String?
): State<String?> = produceState<String?>(initialValue = offer.barcode.takeIf { it.isNotBlank() }, offer.code, offer.description, offer.barcode) {
        if(offer.barcode.matches(Regex("[0-9]{8,14}"))) return@produceState
        if(offer.code.isBlank()) return@produceState
        try {
            value = resolveEan(offer.code, offer.description)
        } catch(cancelled: CancellationException) {
            throw cancelled
        } catch(_: Exception) {
            value = null
        }
    }

internal fun storeOfferSharingText(offer: NordestaoStoreOffer, storeName: String, ean: String?): String =
    buildString {
        val currency = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"))
        appendLine(offer.description)
        appendLine("Loja: $storeName")
        if(offer.showPrice && offer.priceCents > 0) {
            if(offer.originalPriceCents > offer.priceCents)
                appendLine("De: ${currency.format(offer.originalPriceCents / 100.0)}")
            appendLine("Preço: ${currency.format(offer.priceCents / 100.0)}")
            if(offer.discount > 0) appendLine("${offer.discount}% de desconto")
        } else appendLine("Condição especial")
        if(offer.cashback.isNotBlank()) appendLine(offer.cashback)
        if(offer.validity.isNotBlank()) appendLine(offer.validity)
        if(offer.maxAmount > 0) appendLine("Máximo: ${NumberFormat.getNumberInstance(Locale.forLanguageTag("pt-BR")).format(offer.maxAmount)} un.")
        if(offer.code.isNotBlank()) appendLine("Código: ${offer.code}")
        if(!ean.isNullOrBlank()) appendLine("EAN: $ean")
        if(offer.conditions.isNotBlank()) appendLine(offer.conditions)
        if(offer.activationRequired) appendLine("Ative a oferta no Nordestão Pra Você antes da compra.")
    }.trim()

private fun copyStoreOfferText(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Oferta NRD", text))
}

/** Shares the same PNG captured for long-press copying; falls back to full text if unavailable. */
private suspend fun shareStoreOfferCard(
    context: Context,
    layer: GraphicsLayer?,
    name: String,
    text: String
): Boolean = runCatching {
    val imageCopied = layer != null && copyProductCardToClipboard(context, layer, name)
    val uri = if(imageCopied) {
        val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
        clip?.getItemAt(0)?.uri
    } else null
    val intent = if(uri != null) Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, text)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    } else Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Compartilhar oferta"))
    true
}.getOrDefault(false)

@Composable private fun OfferPrice(offer: NordestaoStoreOffer) {
    val currency = remember { NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")) }
    if(offer.showPrice && offer.priceCents > 0) {
        if(offer.originalPriceCents > offer.priceCents) Text(currency.format(offer.originalPriceCents / 100.0), textDecoration = TextDecoration.LineThrough, style = MaterialTheme.typography.bodySmall)
        Text(currency.format(offer.priceCents / 100.0), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if(offer.discount > 0) Text("${offer.discount}% de desconto", color = MaterialTheme.colorScheme.primary)
    } else Text("Condição especial", fontWeight = FontWeight.Bold)
    if(offer.cashback.isNotBlank()) Text(offer.cashback, fontWeight = FontWeight.Bold)
}

@Composable
private fun OfficialOfferPhoto(offer: NordestaoStoreOffer, modifier: Modifier) {
    val urls=remember(offer.image,offer.images) {offer.imageCandidates()}
    var position by remember(offer.id,urls) {mutableIntStateOf(0)}
    Box(modifier,contentAlignment=Alignment.Center) {
        if(position < urls.size) AsyncImage(model=urls[position],contentDescription=offer.description,
            contentScale=ContentScale.Fit,modifier=Modifier.fillMaxSize(),onError={position++})
        else Text("Imagem não disponível",style=MaterialTheme.typography.bodySmall)
    }
}

internal fun sortStoreOffers(offers: List<NordestaoStoreOffer>, option: OfferSortOption): List<NordestaoStoreOffer> {
    val byName = compareBy<NordestaoStoreOffer> { it.description.lowercase(Locale.forLanguageTag("pt-BR")) }.thenBy { it.id }
    val comparator = when(option) {
        OfferSortOption.DISCOUNT_DESC -> compareByDescending<NordestaoStoreOffer> { it.discount }.then(byName)
        OfferSortOption.DISCOUNT_ASC -> compareBy<NordestaoStoreOffer> { it.discount }.then(byName)
        OfferSortOption.PRICE_ASC -> compareBy<NordestaoStoreOffer> { !it.showPrice || it.priceCents <= 0 }
            .thenBy { it.priceCents }.then(byName)
        OfferSortOption.PRICE_DESC -> compareBy<NordestaoStoreOffer> { !it.showPrice || it.priceCents <= 0 }
            .thenByDescending { it.priceCents }.then(byName)
        else -> byName
    }
    return offers.sortedWith(comparator)
}
