package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.Promotion
import com.example.data.promotions.PromotionSyncCoordinator
import com.example.data.promotions.PromotionSyncState
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

internal data class PromotionsUiState(
    val offers: List<Promotion> = emptyList(),
    val latestAddedIds: Set<String> = emptySet(),
    val initialized: Boolean = false,
    val sync: PromotionSyncState = PromotionSyncState(),
    val opening: Boolean = false
) {
    val loading: Boolean get() = opening || (!initialized && (sync.running || !sync.attempted))
    /** Falhas do gateway não geram mensagem, toast, snackbar nem estado de erro para não-Mestre. */
    fun visibleSyncError(showDiagnostics: Boolean): String? =
        sync.error?.takeIf { showDiagnostics && !initialized }
}

internal class PromotionsViewModel(application: Application) : AndroidViewModel(application) {
    private val coordinator = PromotionSyncCoordinator.get(application)
    private val opening = MutableStateFlow(true)
    private var openingJob: kotlinx.coroutines.Job? = null
    val state: StateFlow<PromotionsUiState> = combine(
        coordinator.repository.observeOffers(),
        coordinator.repository.observeLatestAdded(),
        coordinator.repository.dao.observeInitialized(),
        coordinator.state,
        opening
    ) { offers, added, initialized, sync, opening ->
        PromotionsUiState(offers, added, initialized.firstOrNull()?.value == "1", sync, opening)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PromotionsUiState(opening = true))

    fun screenOpened() {
        if (openingJob?.isActive == true) return
        opening.value = true
        val refresh = coordinator.requestSync(interactive = true)
        openingJob = viewModelScope.launch {
            try { refresh.join() }
            finally { opening.value = false }
        }
    }
    fun refresh() { coordinator.requestSync(interactive = true) }
    fun storesChanged() { coordinator.requestSync() }
}
