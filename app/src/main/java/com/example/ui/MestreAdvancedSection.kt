package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.example.data.CatalogSnapshot
import com.example.data.CategoryCount
import com.example.data.MaintenanceSummary
import com.example.ui.theme.glassSoftShadow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun MestreAdvancedSection(
    maintenanceSummary: MaintenanceSummary?,
    isLoadingMaintenance: Boolean,
    isLoadingCatalogHistory: Boolean,
    isSyncing: Boolean,
    catalogSnapshots: List<CatalogSnapshot>,
    showAllCatalogBackups: Boolean,
    onShowAllCatalogBackupsChange: (Boolean) -> Unit,
    onUpdateMaintenance: () -> Unit,
    onCreateCatalogSnapshot: () -> Unit,
    onRefreshCatalogHistory: () -> Unit,
    onRestoreSnapshot: (CatalogSnapshot) -> Unit
) {
    MestreSectionHeader(
        title = "Manutenção e diagnóstico",
        description = "Confira o estado do catálogo local e remoto sem alterar dados"
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedCard(modifier = Modifier.fillMaxWidth().glassSoftShadow(MaterialTheme.shapes.medium)) {
        Column(modifier = Modifier.padding(16.dp)) {
            val summary = maintenanceSummary
            if (summary == null) {
                Text(
                    "Nenhum diagnóstico realizado nesta sessão.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    if (summary.remoteAvailable) "Conexão remota disponível" else "Não foi possível consultar a nuvem",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (summary.remoteAvailable) advancedSuccessColor() else MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(8.dp))
                AdvancedMaintenanceMetricRow("Produtos locais", summary.localProductCount.toString())
                AdvancedMaintenanceMetricRow(
                    "Produtos na nuvem",
                    if (summary.remoteAvailable) summary.remoteProductCount.toString() else "Não disponível"
                )
                val productDifference = summary.remoteProductCount - summary.localProductCount
                val differenceLabel = if (!summary.remoteAvailable) "Não calculada" else when {
                    productDifference == 0 -> "Nenhuma diferença"
                    productDifference > 0 -> "+$productDifference na nuvem"
                    else -> "$productDifference na nuvem"
                }
                AdvancedMaintenanceMetricRow("Diferença de produtos", differenceLabel)
                AdvancedMaintenanceMetricRow(
                    "Abas dinâmicas",
                    if (summary.remoteAvailable) summary.dynamicTabCount.toString() else "Não disponível"
                )
                AdvancedMaintenanceMetricRow(
                    "Sugestões pendentes",
                    if (summary.remoteAvailable) summary.pendingSuggestionCount.toString() else "Não disponível"
                )
                val lastUpdate = summary.lastRemoteProductUpdate?.let {
                    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(Date(it))
                } ?: "Não informado"
                AdvancedMaintenanceMetricRow("Última atualização de produto", lastUpdate)
                AdvancedMaintenanceMetricRow(
                    "Diagnóstico verificado em",
                    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(Date(summary.checkedAt))
                )
                if (summary.localCategoryCounts.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Categorias locais", style = MaterialTheme.typography.titleSmall)
                    summary.localCategoryCounts.take(4).forEach { count ->
                        AdvancedMaintenanceMetricRow(count.category, count.count.toString())
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onUpdateMaintenance,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoadingMaintenance && !isLoadingCatalogHistory && !isSyncing
            ) {
                if (isLoadingMaintenance) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Consultando...")
                } else {
                    androidx.compose.material3.Icon(Icons.Default.Sync, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Atualizar diagnóstico")
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(16.dp))

    MestreSectionHeader(
        title = "Segurança operacional",
        description = "Crie pontos de retorno do catálogo antes de mudanças importantes"
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedCard(modifier = Modifier.fillMaxWidth().glassSoftShadow(MaterialTheme.shapes.medium)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "O histórico mantém até 20 backups remotos. Restaurar uma versão cria primeiro um backup automático do catálogo atual.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onCreateCatalogSnapshot,
                    enabled = !isLoadingCatalogHistory && !isSyncing,
                    modifier = Modifier.weight(1f)
                ) {
                    androidx.compose.material3.Icon(Icons.Default.Backup, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Criar backup")
                }
                OutlinedButton(
                    onClick = onRefreshCatalogHistory,
                    enabled = !isLoadingCatalogHistory && !isSyncing
                ) {
                    androidx.compose.material3.Icon(Icons.Default.Sync, contentDescription = "Atualizar histórico")
                }
            }
            if (isLoadingCatalogHistory) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Consultando histórico...", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (!isLoadingCatalogHistory && catalogSnapshots.isEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    "Nenhum backup disponível ou a nuvem não está acessível.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            val orderedSnapshots = catalogSnapshots.sortedByDescending { it.createdAt }
            val visibleSnapshots = if (showAllCatalogBackups) orderedSnapshots else orderedSnapshots.take(3)
            visibleSnapshots.forEach { snapshot ->
                Spacer(modifier = Modifier.height(10.dp))
                AdvancedCatalogSnapshotItem(
                    snapshot = snapshot,
                    enabled = !isLoadingCatalogHistory,
                    onRestore = onRestoreSnapshot
                )
            }
            if (orderedSnapshots.size > 3) {
                Spacer(modifier = Modifier.height(6.dp))
                TextButton(
                    onClick = { onShowAllCatalogBackupsChange(!showAllCatalogBackups) },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(
                        if (showAllCatalogBackups) "Mostrar apenas recentes"
                        else "Ver todos os ${orderedSnapshots.size} backups"
                    )
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
private fun AdvancedMaintenanceMetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
        )
    }
}

@Composable
private fun AdvancedCatalogSnapshotItem(
    snapshot: CatalogSnapshot,
    enabled: Boolean,
    onRestore: (CatalogSnapshot) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().glassSoftShadow(MaterialTheme.shapes.medium),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    advancedFormatCatalogHistoryDate(snapshot.createdAt),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                )
                Text(
                    "${snapshot.productCount} produto(s) · ${advancedCatalogHistoryReason(snapshot.reason)}${if (snapshot.restoredAt != null) " · restaurado" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (snapshot.createdBy.isNotBlank()) {
                    Text(
                        "Por: ${snapshot.createdBy}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            TextButton(onClick = { onRestore(snapshot) }, enabled = enabled) {
                Text("Restaurar")
            }
        }
    }
}

private fun advancedFormatCatalogHistoryDate(timestamp: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(Date(timestamp))

private fun advancedCatalogHistoryReason(reason: String): String = when (reason) {
    "pre_restoration" -> "backup automático"
    else -> "manual"
}

@Composable
private fun advancedSuccessColor(): Color = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
    Color(0xFF81C784)
} else {
    Color(0xFF2E7D32)
}
