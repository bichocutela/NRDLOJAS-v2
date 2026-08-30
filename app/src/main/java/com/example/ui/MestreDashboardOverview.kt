package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun MestreDashboardOverview(
    pendingSuggestions: Int,
    productCount: Int,
    activeCategoryCount: Int,
    categoryCount: Int,
    latestBackupAt: Long?,
    importEnabled: Boolean,
    onOpenCatalog: () -> Unit,
    onOpenCategories: () -> Unit,
    onManageTabs: () -> Unit,
    onImportProducts: () -> Unit
) {
    Text("Visão geral", style = MaterialTheme.typography.titleLarge)
    Text(
        "Acompanhe o aplicativo e acesse as tarefas mais usadas.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(12.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        DashboardMetricCard(
            title = "Pendências",
            value = pendingSuggestions.toString(),
            icon = Icons.Default.PendingActions,
            modifier = Modifier.weight(1f)
        )
        DashboardMetricCard(
            title = "Produtos",
            value = productCount.toString(),
            icon = Icons.Default.Inventory,
            modifier = Modifier.weight(1f)
        )
    }
    Spacer(modifier = Modifier.height(10.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        DashboardMetricCard(
            title = "Categorias ativas",
            value = "$activeCategoryCount de $categoryCount",
            icon = Icons.Default.Category,
            modifier = Modifier.weight(1f)
        )
        DashboardMetricCard(
            title = "Último backup",
            value = latestBackupAt?.let(::formatDashboardDate) ?: "Nenhum",
            icon = Icons.Default.Backup,
            modifier = Modifier.weight(1f)
        )
    }

    Spacer(modifier = Modifier.height(20.dp))
    Text("Ações rápidas", style = MaterialTheme.typography.titleMedium)
    Spacer(modifier = Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        DashboardQuickAction(
            title = "Produtos",
            description = "Gerenciar catálogo",
            icon = Icons.Default.Inventory,
            onClick = onOpenCatalog,
            modifier = Modifier.weight(1f)
        )
        DashboardQuickAction(
            title = "Categorias",
            description = "Organizar grupos",
            icon = Icons.Default.Category,
            onClick = onOpenCategories,
            modifier = Modifier.weight(1f)
        )
    }
    Spacer(modifier = Modifier.height(10.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        DashboardQuickAction(
            title = "Abas",
            description = "Organizar conteúdo",
            icon = Icons.Default.ViewCarousel,
            onClick = onManageTabs,
            modifier = Modifier.weight(1f)
        )
        DashboardQuickAction(
            title = "Importar",
            description = if (importEnabled) "CSV ou TSV" else "Aguarde...",
            icon = Icons.Default.UploadFile,
            onClick = onImportProducts,
            enabled = importEnabled,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
internal fun MestrePanelAreaNavigation(
    onOpenCatalog: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAdvanced: () -> Unit
) {
    Text("Áreas do painel", style = MaterialTheme.typography.titleMedium)
    Spacer(modifier = Modifier.height(8.dp))
    PanelAreaCard(
        title = "Conteúdo e catálogo",
        description = "Produtos, categorias, abas e importação",
        icon = Icons.Default.Inventory,
        onClick = onOpenCatalog
    )
    Spacer(modifier = Modifier.height(10.dp))
    PanelAreaCard(
        title = "Configuração do aplicativo",
        description = "Home, aparência e notificações globais",
        icon = Icons.Default.Settings,
        onClick = onOpenSettings
    )
    Spacer(modifier = Modifier.height(10.dp))
    PanelAreaCard(
        title = "Ferramentas avançadas",
        description = "Diagnóstico, sincronização e backups",
        icon = Icons.Default.CloudSync,
        onClick = onOpenAdvanced
    )
}

@Composable
internal fun MestreContentHub(
    importEnabled: Boolean,
    onManageProducts: () -> Unit,
    onAddProduct: () -> Unit,
    onOpenCategories: () -> Unit,
    onManageTabs: () -> Unit,
    onImportProducts: () -> Unit
) {
    Text("Produtos", style = MaterialTheme.typography.titleMedium)
    Text(
        "Edite o catálogo existente ou cadastre um novo item.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Inventory,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("Gerenciar produtos", style = MaterialTheme.typography.titleSmall)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(onClick = onManageProducts, modifier = Modifier.weight(1f)) {
                    Text("Editar")
                }
                OutlinedButton(onClick = onAddProduct, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.AddBox, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Adicionar")
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
    Text("Outras ferramentas", style = MaterialTheme.typography.titleMedium)
    Spacer(modifier = Modifier.height(8.dp))
    PanelAreaCard(
        title = "Categorias",
        description = "Criar, ordenar, renomear ou ocultar grupos",
        icon = Icons.Default.Category,
        onClick = onOpenCategories
    )
    Spacer(modifier = Modifier.height(10.dp))
    PanelAreaCard(
        title = "Abas do aplicativo",
        description = "Criar e organizar conteúdo adicional",
        icon = Icons.Default.ViewCarousel,
        onClick = onManageTabs
    )
    Spacer(modifier = Modifier.height(10.dp))
    PanelAreaCard(
        title = "Importar planilha",
        description = if (importEnabled) "Adicionar produtos por CSV ou TSV" else "Importação em andamento...",
        icon = Icons.Default.UploadFile,
        onClick = onImportProducts,
        enabled = importEnabled
    )
}

@Composable
internal fun MestreSettingsHub(
    onOpenHome: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenNotifications: () -> Unit
) {
    Text("Escolha o que deseja configurar", style = MaterialTheme.typography.titleMedium)
    Text(
        "Cada alteração global é publicada separadamente para todos os usuários.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(12.dp))
    PanelAreaCard(
        title = "Tela Home",
        description = "Seções, quantidade de produtos e carrossel",
        icon = Icons.Default.Home,
        onClick = onOpenHome
    )
    Spacer(modifier = Modifier.height(10.dp))
    PanelAreaCard(
        title = "Aparência global",
        description = "Tema, modo visual e fundos programados",
        icon = Icons.Default.Palette,
        onClick = onOpenAppearance
    )
    Spacer(modifier = Modifier.height(10.dp))
    PanelAreaCard(
        title = "Notificações globais",
        description = "Políticas aplicadas aos aparelhos dos usuários",
        icon = Icons.Default.Notifications,
        onClick = onOpenNotifications
    )
}

@Composable
private fun DashboardMetricCard(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    ElevatedCard(modifier = modifier.heightIn(min = 96.dp)) {
        Column(modifier = Modifier.padding(14.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DashboardQuickAction(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedCard(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 88.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PanelAreaCard(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    OutlinedCard(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatDashboardDate(timestamp: Long): String =
    SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR")).format(Date(timestamp))