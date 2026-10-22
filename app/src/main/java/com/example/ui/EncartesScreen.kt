package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import coil.compose.AsyncImage
import com.example.data.EncartesRepository
import com.example.data.StoreEncarte
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EncartesScreen(onNavigateBack: () -> Unit) {
    var encartes by remember { mutableStateOf<List<StoreEncarte>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, refreshKey) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                loading = true
                try {
                    // Replace the entire snapshot: removed source IDs must disappear locally.
                    encartes = EncartesRepository.load()
                    error = false
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    encartes = emptyList()
                    error = true
                } finally { loading = false }
                if (encartes.none { it.id == selectedId }) selectedId = null
                delay(60_000)
            }
        }
    }
    val selected = encartes.firstOrNull { it.id == selectedId && it.isActive() }
    val goBack = { if (selectedId != null) selectedId = null else onNavigateBack() }
    BackHandler(onBack = goBack)
    Scaffold(topBar = {
        TopAppBar(title = { Text(if (selected != null) selected.name else "Encartes") },
            navigationIcon = { IconButton(onClick = goBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } },
            actions = { IconButton(onClick = { refreshKey++ }, enabled = !loading) { Icon(Icons.Default.Refresh, "Atualizar encartes") } })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (selected != null) {
                DynamicPdfBlock(url = selected.pdf, title = "Natal e região • ${selected.validity}", modifier = Modifier.fillMaxSize())
            } else {
                Text("Natal e região", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
                when {
                    error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Não foi possível atualizar os encartes. Tente novamente.")
                            Button(onClick = { refreshKey++ }) { Text("Tentar novamente") }
                        }
                    }
                    encartes.isEmpty() && !loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Nenhum encarte disponível para Natal no momento.", modifier = Modifier.padding(24.dp))
                    }
                    else -> LazyVerticalGrid(columns = GridCells.Adaptive(150.dp),
                        contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(encartes.filter { it.isActive() }, key = { it.id }) { encarte ->
                            Card(onClick = { selectedId = encarte.id }) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(encarte.name, style = MaterialTheme.typography.titleSmall)
                                    Spacer(Modifier.height(8.dp))
                                    AsyncImage(model = encarte.image, contentDescription = "Capa de ${encarte.name}",
                                        modifier = Modifier.fillMaxWidth().height(190.dp), contentScale = ContentScale.Fit)
                                    Spacer(Modifier.height(8.dp))
                                    Text("Validade: ${encarte.validity}", style = MaterialTheme.typography.bodySmall)
                                    Text("Abrir encarte", color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
