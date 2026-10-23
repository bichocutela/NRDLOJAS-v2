package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import coil.compose.AsyncImage
import com.example.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.util.Locale

@Composable
fun NordestaoStoreOffersContent(store: NordestaoStore?, refresh: Int, onChooseStore: () -> Unit,
    modifier: Modifier = Modifier,
    loadOffers: suspend (Long) -> NordestaoStoreOffersSnapshot = { NordestaoStoreOffersRepository.load(it) }) {
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
    var sortName by rememberSaveable { mutableStateOf(OfferSortOption.NAME.name) }
    val sortOption = OfferSortOption.values().firstOrNull { it.name == sortName } ?: OfferSortOption.NAME
    var detail by remember(store.id) { mutableStateOf<NordestaoStoreOffer?>(null) }
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
                OutlinedCard(onClick = { detail = offer }) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if(offer.tag.isNotBlank()) SuggestionChip(onClick = { detail = offer }, label = { Text(offer.tag) })
                        if(offer.image.isNotBlank()) AsyncImage(model = offer.image, contentDescription = offer.description, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().height(130.dp))
                        else Box(Modifier.height(130.dp)) { Text("Imagem não disponível", style = MaterialTheme.typography.bodySmall) }
                        OfferPrice(offer)
                        Text(offer.description, style = MaterialTheme.typography.bodyMedium)
                        if(offer.maxAmount > 0) Text("Máx. ${NumberFormat.getNumberInstance(Locale.forLanguageTag("pt-BR")).format(offer.maxAmount)} un.", style = MaterialTheme.typography.bodySmall)
                        Text(offer.validity, style = MaterialTheme.typography.bodySmall)
                        Text("Ver condições", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
    detail?.let { offer ->
        AlertDialog(onDismissRequest = { detail = null }, title = { Text(offer.description) }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if(offer.image.isNotBlank()) AsyncImage(model = offer.image, contentDescription = offer.description, modifier = Modifier.fillMaxWidth().height(180.dp), contentScale = ContentScale.Fit)
                OfferPrice(offer)
                Text(offer.validity)
                if(offer.maxAmount > 0) Text("Quantidade máxima: ${NumberFormat.getNumberInstance(Locale.forLanguageTag("pt-BR")).format(offer.maxAmount)} un.")
                if(offer.conditions.isNotBlank()) Text(offer.conditions)
                if(offer.activationRequired) Text("Ative esta oferta no aplicativo Nordestão Pra Você antes da compra.")
                Text("Loja: ${store.name}")
                if(offer.code.isNotBlank()) Text("Código: ${offer.code}")
            }
        }, confirmButton = { TextButton(onClick = { detail = null }) { Text("Fechar") } })
    }
}
@Composable private fun OfferPrice(offer: NordestaoStoreOffer) {
    val currency = remember { NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")) }
    if(offer.showPrice && offer.priceCents > 0) {
        if(offer.originalPriceCents > offer.priceCents) Text(currency.format(offer.originalPriceCents / 100.0), textDecoration = TextDecoration.LineThrough, style = MaterialTheme.typography.bodySmall)
        Text(currency.format(offer.priceCents / 100.0), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if(offer.discount > 0) Text("${offer.discount}% de desconto", color = MaterialTheme.colorScheme.primary)
    } else Text("Condição especial", fontWeight = FontWeight.Bold)
    if(offer.cashback.isNotBlank()) Text(offer.cashback, fontWeight = FontWeight.Bold)
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
