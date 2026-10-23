package com.example.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NordestaoStoresScreen(onBack: () -> Unit, loadStores: suspend () -> NordestaoStoresSnapshot = { NordestaoStoresRepository.load() }) {
    var snapshot by remember { mutableStateOf<NordestaoStoresSnapshot?>(null) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences("nrd_store_selection", android.content.Context.MODE_PRIVATE) }
    var favorite by remember { mutableLongStateOf(preferences.getLong("favorite", -1)) }
    var mapMode by rememberSaveable { mutableStateOf(false) }
    var favoriteOnly by rememberSaveable { mutableStateOf(false) }
    var nearest by rememberSaveable { mutableStateOf(false) }
    var location by remember { mutableStateOf<Pair<Double,Double>?>(null) }
    var locationRequest by remember { mutableIntStateOf(0) }
    var locating by remember { mutableStateOf(false) }
    var locationMessage by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    fun locate() { scope.launch {
        locating = true
        try {
            val point = storeLocation(context)
            if(point != null) { location = point; nearest = true; locationRequest++ }
            locationMessage = if(point == null) "Não foi possível obter sua localização. Confira se a localização do aparelho está ligada e tente novamente." else "Sua localização aproximada está marcada em azul no mapa. Distâncias em linha reta."
        } finally { locating = false }
    } }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if(granted) locate() else { locating = false; locationMessage = "Localização não permitida. Você pode consultar as lojas pela lista ou mapa." }
    }
    fun favoriteStore(id: Long) { favorite = if(favorite == id) -1 else id; preferences.edit().putLong("favorite",favorite).apply() }

    LaunchedEffect(lifecycle, refresh) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while(true) {
                loading = true
                try {
                    val next = loadStores()
                    snapshot = next
                    if(next.stores.none { it.id == selectedId }) selectedId = null
                    failed = false
                } catch(cancelled: CancellationException) { throw cancelled }
                catch(_: Exception) { failed = true }
                finally { loading = false }
                delay(60_000)
            }
        }
    }
    val selected = snapshot?.stores?.find { it.id == selectedId }
    BackHandler(enabled = selected != null) { selectedId = null }
    fun open(intent: Intent) {
        try { context.startActivity(intent) }
        catch(_: ActivityNotFoundException) { Toast.makeText(context, "Nenhum aplicativo disponível para esta ação.", Toast.LENGTH_SHORT).show() }
        catch(_: SecurityException) { Toast.makeText(context, "Não foi possível abrir esta ação.", Toast.LENGTH_SHORT).show() }
    }
    Scaffold(topBar = { TopAppBar(title = { Text("Nossas Lojas") }, navigationIcon = {
        IconButton(onClick = { if(selectedId != null) selectedId = null else onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
    }, actions = { IconButton(onClick = { refresh++ }, enabled = !loading) { Icon(Icons.Default.Refresh, "Atualizar lojas") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if(loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text("Informações do Nordestão Pra Você", style = MaterialTheme.typography.labelMedium)
            snapshot?.let { Text("Última consulta à fonte: ${storeUpdateLabel(it.fetchedAt)}", style = MaterialTheme.typography.bodySmall) }
            if(failed) {
                Text(if(snapshot == null) "Não foi possível carregar as lojas. Tente novamente." else "Não foi possível atualizar. As informações abaixo são da última consulta e podem ter mudado.", color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { refresh++ }, enabled = !loading) { Text("Tentar novamente") }
            }
            if(selected != null) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row {
                        Text(selected.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                        IconButton(onClick = { favoriteStore(selected.id) }) { Icon(if(favorite == selected.id) Icons.Default.Star else Icons.Default.StarBorder, "Loja preferida no NRD") }
                    }
                    if(favorite == selected.id) Text("Sua loja preferida no NRD")
                    if(selected.identifier.isNotBlank()) Text("Identificação da unidade: ${selected.identifier}")
                    StoreInfo("Endereço", selected.address.ifBlank { "Não informado pela fonte" })
                    StoreInfo("Horário de funcionamento", selected.displayHours.ifBlank { "Não informado pela fonte. Consulte a unidade antes de se deslocar." })
                    if(selected.hoursFromLeaflet) Text("Horário de referência informado em 23/10/2026. Atualizações de horário da fonte oficial têm prioridade.", style = MaterialTheme.typography.bodySmall)
                    selected.isOpen?.let { StoreInfo("Situação informada na última consulta", if(it) "Aberta" else "Fechada") }
                    selected.capacity?.let { StoreInfo("Capacidade informada", it.toString()) }
                    selected.mapUrl?.let { url -> OutlinedButton(onClick = { open(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }) { Text("Ver no mapa") } }
                    Text("Telefones", fontWeight = FontWeight.Bold)
                    if(selected.phones.isEmpty()) Text("Não informados pela fonte")
                    selected.phones.forEach { phone ->
                        OutlinedButton(onClick = { open(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + if(phone.length <= 11) "+55$phone" else "+$phone"))) }) { Text("Ligar: $phone") }
                    }
                    Text("Horários especiais e feriados podem variar. Os contatos são exibidos conforme o cadastro oficial.", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(12.dp))
                }
            } else {
                OutlinedTextField(query, { query = it }, label = { Text("Buscar loja, bairro ou cidade") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !mapMode, onClick = { mapMode = false }, label = { Text("Lista") })
                    FilterChip(selected = mapMode, onClick = { mapMode = true }, label = { Text("Mapa") })
                    FilterChip(selected = favoriteOnly, onClick = { favoriteOnly = !favoriteOnly }, label = { Text("Preferida") })
                }
                OutlinedButton(onClick = {
                    if(!mapMode && nearest) nearest = false
                    else if(androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) locate()
                    else permission.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION)
                }, enabled = !locating) { Text(if(locating) "Obtendo localização…" else if(mapMode) "Minha localização" else if(nearest) "Ordenar por nome" else "Mais próxima") }
                if(locationMessage.isNotBlank()) Text(locationMessage, style = MaterialTheme.typography.bodySmall)
                val stores = snapshot?.stores.orEmpty().filter { it.matches(query) && (!favoriteOnly || it.id == favorite) }.let { list ->
                    val point = location
                    if(nearest && point != null) list.sortedBy { if(it.latitude != null && it.longitude != null) storeDistance(point.first,point.second,it.latitude,it.longitude) else Double.MAX_VALUE } else list
                }
                if(snapshot != null) Text("${stores.size} unidade(s)", style = MaterialTheme.typography.labelMedium)
                if(snapshot != null && stores.isEmpty()) Text(when { favoriteOnly -> "Nenhuma loja preferida encontrada. Use a estrela para escolher sua unidade."; query.isBlank() -> "Nenhuma loja disponível na fonte."; else -> "Nenhuma unidade encontrada." })
                if(mapMode && stores.isNotEmpty()) {
                    Box(Modifier.weight(1f)) { NordestaoStoresMap(stores, location, locationRequest, { selectedId = it }, { open(Intent(Intent.ACTION_VIEW,it)) }) }
                } else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(stores, key = { it.id }) { store ->
                        OutlinedCard(onClick = { selectedId = store.id }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Row {
                                    Text(store.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                    IconButton(onClick = { favoriteStore(store.id) }) { Icon(if(favorite == store.id) Icons.Default.Star else Icons.Default.StarBorder, "Loja preferida no NRD: ${store.name}") }
                                }
                                val point = location
                                if(nearest && point != null && store.latitude != null && store.longitude != null) Text(String.format(java.util.Locale.forLanguageTag("pt-BR"), "Aproximadamente %.1f km", storeDistance(point.first,point.second,store.latitude,store.longitude)))
                                Text(listOf(store.neighborhood, store.city, store.state).filter { it.isNotBlank() }.joinToString(" • "))
                                Text(if(store.displayHours.isBlank()) "Horário não informado pela fonte" else store.displayHours)
                                Text("Ver endereço e contatos", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}
@Composable private fun StoreInfo(title: String, value: String) {
    Text(title, fontWeight = FontWeight.Bold)
    Text(value)
}
private fun storeUpdateLabel(raw: String): String = runCatching {
    val parser = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.ROOT).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
    java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.forLanguageTag("pt-BR")).apply {
        timeZone = java.util.TimeZone.getTimeZone("America/Fortaleza")
    }.format(parser.parse(raw)!!)
}.getOrDefault("Não informada")
