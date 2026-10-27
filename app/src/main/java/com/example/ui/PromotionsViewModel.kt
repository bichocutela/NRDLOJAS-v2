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
    val sync: PromotionSyncState = PromotionSyncState(),
    val opening: Boolean = false
) {
    val loading: Boolean get() = !initialized && (opening || sync.running || !sync.attempted)
    /** Everyone sees an availability warning; technical diagnostics remain Master-only. */
    fun visibleSyncError(showDiagnostics: Boolean): String? =
        sync.error?.let { raw ->
            val message = if (showDiagnostics) raw
                else "Não foi possível confirmar as ofertas no Sistema. Atualize para consultar as ofertas atuais."
            Regex("(?i)\\bACP\\b").replace(message, "Sistema")
        }
}

internal class PromotionsViewModel(application: Application) : AndroidViewModel(application) {
    private val coordinator = PromotionSyncCoordinator.get(application)
    private val opening = MutableStateFlow(true)
    val state: StateFlow<PromotionsUiState> = combine(
        coordinator.repository.observeOffers(),
        coordinator.repository.observeLatestAdded(),
        coordinator.repository.dao.observeInitialized(),
        coordinator.state,
        opening
    ) { offers, added, initialized, sync, opening ->
        // Recheck validity on each sync transition, even when the saved rows did not change.
        val currentOffers = offers.filter {
            com.example.data.promotions.AcpPromotionsRepository.active(it.validFrom, it.validTo)
        }
        PromotionsUiState(currentOffers, added, initialized.firstOrNull()?.value == "1", sync, opening)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PromotionsUiState(opening = true))

    /** The application monitor refreshes silently before/while users browse; opening the
     * screen must render the last confirmed City Jardim snapshot without triggering a fetch. */
    fun screenOpened() {
        opening.value = false
    }
    fun refresh() { coordinator.requestSync(interactive = true) }
    fun storesChanged() { coordinator.requestSync() }
}
