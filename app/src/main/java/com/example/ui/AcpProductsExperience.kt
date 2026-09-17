package com.example.ui

import androidx.compose.runtime.Composable
import com.example.data.AppearanceSettings
import com.example.data.acp.AcpApi

/**
 * Experiência pública de Consultar Preços.
 *
 * O catálogo experimental por tipo de oferta continua preservado nos arquivos
 * AcpOfferCatalog.kt e data/acp/AcpOfferCatalog.kt para retomada futura, mas fica
 * deliberadamente oculto da interface até a implementação ser concluída.
 */
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
    AcpProductsPanel(
        api = api,
        canAddToNrd = canAddToNrd,
        appearance = appearance,
        historyExportBusy = historyExportBusy,
        historyExportMessage = historyExportMessage,
        onExportHistory = onExportHistory,
        onSessionExpired = onSessionExpired
    )
}
