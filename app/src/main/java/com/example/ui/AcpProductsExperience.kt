package com.example.ui

import androidx.compose.runtime.Composable
import com.example.data.AppearanceSettings
import com.example.data.acp.AcpApi

/**
 * Tela pública de consulta ACP. O catálogo experimental por categorias fica
 * preservado no projeto, mas não é exibido nesta experiência.
 */
@Composable
internal fun AcpProductsExperience(
    api: AcpApi,
    canAddToNrd: Boolean,
    appearance: AppearanceSettings,
    historyExportBusy: Boolean,
    historyExportMessage: String?,
    onExportHistory: (String, Int) -> Unit,
    externalPdfUri: String? = null,
    externalPdfRequestKey: Long = 0L,
    onExternalPdfConsumed: () -> Unit = {},
    onSessionExpired: () -> Unit
) {
    AcpProductsPanel(
        api = api,
        canAddToNrd = canAddToNrd,
        appearance = appearance,
        historyExportBusy = historyExportBusy,
        historyExportMessage = historyExportMessage,
        onExportHistory = onExportHistory,
        externalPdfUri = externalPdfUri,
        externalPdfRequestKey = externalPdfRequestKey,
        onExternalPdfConsumed = onExternalPdfConsumed,
        onSessionExpired = onSessionExpired
    )
}
