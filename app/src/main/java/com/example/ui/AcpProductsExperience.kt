package com.example.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.AppearanceSettings
import com.example.data.acp.AcpApi

@Composable
internal fun AcpProductsExperience(
    api: AcpApi,
    canAddToNrd: Boolean,
    appearance: AppearanceSettings,
    historyExportBusy: Boolean,
    historyExportMessage: String?,
    onExportHistory: (String, Int) -> Unit,
    onSessionExpired: () -> Unit
) {
    var catalog by remember { mutableStateOf<AcpCatalogKind?>(null) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AcpCatalogKind.entries.forEach { kind ->
                if (catalog == kind) Button(onClick = { catalog = null }, shape = RoundedCornerShape(22.dp)) { Text(kind.label, fontWeight = FontWeight.Bold) }
                else OutlinedButton(onClick = { catalog = kind }, shape = RoundedCornerShape(22.dp)) { Text(kind.label, fontWeight = FontWeight.SemiBold) }
            }
        }
        val active = catalog
        if (active == null) Box(Modifier.weight(1f)) {
            AcpProductsPanel(api, canAddToNrd, appearance, historyExportBusy, historyExportMessage, onExportHistory, onSessionExpired)
        } else Box(Modifier.weight(1f)) {
            AcpOfferCatalog(api, active, onClose = { catalog = null }, onProduct = { _ -> }, onSessionExpired = onSessionExpired)
        }
    }
}
