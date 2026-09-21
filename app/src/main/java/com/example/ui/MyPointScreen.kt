package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.NossaGenteApi
import com.example.data.NossaGentePointResult
import com.example.data.PointEntry
import com.example.data.PointSummary
import kotlinx.coroutines.launch
import androidx.compose.material3.ExperimentalMaterial3Api

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyPointScreen(
    api: NossaGenteApi,
    onNavigateBack: () -> Unit,
    onRequireLogin: () -> Unit
) {
    var point by remember { mutableStateOf<PointSummary?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun load() {
        if (loading) return
        if (!api.hasSession()) {
            onRequireLogin()
            return
        }
        loading = true
        error = null
        scope.launch {
            when (val result = api.fetchPoint()) {
                is NossaGentePointResult.Success -> point = result.point
                NossaGentePointResult.Unauthorized -> onRequireLogin()
                is NossaGentePointResult.Error -> error = result.message
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        if (!api.hasSession()) {
            loading = false
            onRequireLogin()
        } else {
            when (val result = api.fetchPoint()) {
                is NossaGentePointResult.Success -> point = result.point
                NossaGentePointResult.Unauthorized -> onRequireLogin()
                is NossaGentePointResult.Error -> error = result.message
            }
            loading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Meu Ponto") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, "Voltar") }
                },
                actions = {
                    TextButton(onClick = onRequireLogin) { Text(if (api.hasSession()) "Trocar acesso" else "Entrar") }
                    IconButton(onClick = ::load, enabled = !loading) { Icon(Icons.Default.Refresh, "Atualizar") }
                }
            )
        }
    ) { padding ->
        if (loading && point == null) {
            Column(Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.Center) {
                CircularProgressIndicator(Modifier.padding(24.dp))
            }
        } else if (error != null && point == null) {
            Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) { Text(error!!, color = MaterialTheme.colorScheme.error) }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    point?.let { summary ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                Text(summary.period ?: "Período atual", style = MaterialTheme.typography.titleLarge)
                                Spacer(Modifier.height(8.dp))
                                summary.status?.let { Text("Status: $it") }
                                summary.worked?.let { Text("Horas trabalhadas: $it") }
                                summary.balance?.let { Text("Saldo: $it") }
                            }
                        }
                    }
                }
                if (point?.records.isNullOrEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Não foi possível apresentar os dias e horários com os dados recebidos. Isso não confirma ausência de registros. Tente atualizar; se persistir, a integração do ponto precisa ser verificada.")
                            androidx.compose.material3.Button(onClick = onRequireLogin) { Text(if (api.hasSession()) "Trocar acesso" else "Entrar no Nossa Gente") }
                        }
                    }
                } else {
                    items(point!!.records) { entry -> PointEntryCard(entry) }
                }
            }
        }
    }
}

@Composable
private fun PointEntryCard(entry: PointEntry) {
    Card {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row {
                Icon(Icons.Default.AccessTime, contentDescription = null)
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text(entry.date ?: "Dia não informado", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(5.dp))
            Text("Entrada: ${entry.entry ?: "—"}   Saída: ${entry.exit ?: "—"}")
            entry.interval?.let { Text("Intervalo: $it") }
            entry.status?.let { Text("Status: $it", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
