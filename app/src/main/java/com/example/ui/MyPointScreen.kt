package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.data.BenefitSummary
import com.example.data.HoursSummary
import com.example.data.NossaGenteApi
import com.example.data.NossaGenteBenefitResult
import com.example.data.NossaGenteHoursResult
import com.example.data.NossaGentePointResult
import com.example.data.PointEntry
import com.example.data.PointSummary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyPointScreen(api: NossaGenteApi, onNavigateBack: () -> Unit, onSignOut: () -> Unit) {
    var point by remember { mutableStateOf<PointSummary?>(null) }
    var hours by remember { mutableStateOf<HoursSummary?>(null) }
    // O card permanece visível mesmo quando o endpoint ainda não devolveu
    // compras, para o usuário sempre ter acesso à área de convênio.
    var benefit by remember { mutableStateOf<BenefitSummary?>(BenefitSummary()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showBenefitDetails by remember { mutableStateOf(false) }
    var benefitNotifications by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val credentialStore = remember(context) { com.example.data.NossaGenteCredentialStore(context.applicationContext) }

    fun load() {
        if (loading) return
        if (!api.hasSession()) { onSignOut(); return }
        loading = true
        error = null
        scope.launch {
            when (val result = api.fetchHours()) {
                is NossaGenteHoursResult.Success -> hours = result.hours
                NossaGenteHoursResult.Unauthorized -> onSignOut()
                is NossaGenteHoursResult.Error -> error = result.message
            }
            when (val result = api.fetchPoint()) {
                is NossaGentePointResult.Success -> point = result.point
                NossaGentePointResult.Unauthorized -> onSignOut()
                is NossaGentePointResult.Error -> error = result.message
            }
            when (val result = api.fetchBenefit()) {
                is NossaGenteBenefitResult.Success -> benefit = result.benefit
                NossaGenteBenefitResult.Unauthorized -> onSignOut()
                is NossaGenteBenefitResult.Error -> if (error == null) error = result.message
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        benefitNotifications = credentialStore.isBenefitNotificationsEnabled()
        loading = false
        load()
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Meu Perfil") },
            navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, "Voltar") } },
            actions = {
                TextButton(onClick = onSignOut) { Text("Sair") }
                IconButton(onClick = ::load, enabled = !loading) { Icon(Icons.Default.Refresh, "Atualizar") }
            }
        )
    }) { padding ->
        if (loading && hours == null && point == null) {
            Column(Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator() }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    hours?.let { summary ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                Text("Banco de horas", style = MaterialTheme.typography.titleLarge)
                                Spacer(Modifier.height(8.dp)); Text("Saldo atual: ${summary.total}", style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(10.dp)); Text("Saldos a vencer", style = MaterialTheme.typography.titleMedium)
                                summary.months.forEach { month -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("${month.month}/${month.year}"); Text(month.balance) } }
                            }
                        }
                    }
                    point?.takeIf { summary ->
                        !summary.period.isNullOrBlank() || !summary.status.isNullOrBlank() ||
                            !summary.worked.isNullOrBlank() || !summary.balance.isNullOrBlank() || summary.records.isNotEmpty()
                    }?.let { summary ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                Text(summary.period ?: "Período atual", style = MaterialTheme.typography.titleLarge)
                                summary.status?.let { Text("Status: $it") }; summary.worked?.let { Text("Horas trabalhadas: $it") }; summary.balance?.let { Text("Saldo: $it") }
                            }
                        }
                    }
                    benefit?.let { summary ->
                        Card(onClick = { showBenefitDetails = true }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                Text("Convênio", style = MaterialTheme.typography.titleLarge)
                                summary.period?.let { Text("Período: $it") }
                                summary.updatedAt?.let { Text("Atualizado em: $it", style = MaterialTheme.typography.bodySmall) }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Limite: ${summary.limit ?: "—"}"); Text("Gasto: ${summary.spent ?: "—"}"); Text("Saldo: ${summary.balance ?: "—"}") }
                                Text("Toque para ver as compras", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
                }
                if (point?.records.isNullOrEmpty()) item { Text("Nenhum registro de ponto disponível para o período informado.") }
                else items(point!!.records) { PointEntryCard(it) }
            }
        }
    }
    if (showBenefitDetails) {
        AlertDialog(
            onDismissRequest = { showBenefitDetails = false },
            title = { Text("Convênio") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Ative as notificações", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Quando o saldo for atualizado, o app notificará sobre Convênio Liberado e Compras no Convênio.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Switch(
                            checked = benefitNotifications,
                            onCheckedChange = { enabled ->
                                benefitNotifications = enabled
                                credentialStore.setBenefitNotificationsEnabled(enabled)
                                if (enabled) {
                                    com.example.util.BenefitNotificationWorker.schedule(context, resetSnapshot = true)
                                } else {
                                    com.example.util.BenefitNotificationWorker.cancel(context)
                                }
                            }
                        )
                    }
                    androidx.compose.material3.HorizontalDivider()
                    Text("Compras", style = MaterialTheme.typography.titleMedium)
                    Column(
                        modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (benefit?.purchases.isNullOrEmpty()) Text("Nenhuma compra informada pela API.")
                        else benefit!!.purchases.forEach { purchase ->
                            Text(listOfNotNull(purchase.date, purchase.time).joinToString(" ").ifBlank { "Data não informada" })
                            Text("${purchase.place ?: "Local não informado"} · ${purchase.amount ?: "Valor não informado"}")
                            purchase.description?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showBenefitDetails = false }) { Text("Fechar") } }
        )
    }
}

@Composable
private fun PointEntryCard(entry: PointEntry) {
    Card { Column(Modifier.fillMaxWidth().padding(14.dp)) {
        Row { Icon(Icons.Default.AccessTime, contentDescription = null); Text(entry.date ?: "Dia não informado", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp)) }
        Spacer(Modifier.height(5.dp)); Text("Entrada: ${entry.entry ?: "—"}   Saída: ${entry.exit ?: "—"}")
        entry.interval?.let { Text("Intervalo: $it") }; entry.status?.let { Text("Status: $it", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    } }
}
