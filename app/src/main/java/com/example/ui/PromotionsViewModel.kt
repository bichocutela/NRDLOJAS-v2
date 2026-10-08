package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.Promotion
import com.example.data.promotions.PromotionSyncCoordinator
import com.example.data.promotions.PromotionSyncState
import kotlinx.coroutines.flow.*

internal data class PromotionsUiState(
    val offers: List<Promotion> = emptyList(),
    val latestAddedIds: Set<String> = emptySet(),
    val initialized: Boolean = false,
    val sync: PromotionSyncState = PromotionSyncState()
) {
    val loading: Boolean get() = !initialized && (sync.running || !sync.attempted)
    /** Falhas do gateway não geram mensagem, toast, snackbar nem estado de erro para não-Mestre. */
    fun visibleSyncError(showDiagnostics: Boolean): String? =
        sync.error?.takeIf { showDiagnostics && !initialized }
}

internal class PromotionsViewModel(application: Application) : AndroidViewModel(application) {
    private val coordinator = PromotionSyncCoordinator.get(application)
    val state: StateFlow<PromotionsUiState> = combine(
        coordinator.repository.observeOffers(),
        coordinator.repository.observeLatestAdded(),
        coordinator.repository.dao.observeInitialized(),
        coordinator.state
    ) { offers, added, initialized, sync ->
        PromotionsUiState(offers, added, initialized.firstOrNull()?.value == "1", sync)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PromotionsUiState())

    fun screenOpened() { coordinator.requestSync(interactive = true) }
    fun refresh() { coordinator.requestSync(interactive = true) }
    fun storesChanged() { coordinator.requestSync() }
}
