package com.example.data.promotions
import com.example.ui.PromotionsUiState
import org.junit.Assert.*
import org.junit.Test
class PromotionsUiStateTest {
    @Test fun entryWaitsForFreshScanEvenWithSavedOffersAndAlwaysFinishesOnFailure() {
        val entering = PromotionsUiState(initialized = true, opening = true,
            sync = PromotionSyncState(running = true))
        assertTrue(entering.loading)
        assertFalse(entering.copy(opening = false,
            sync = PromotionSyncState(attempted = true)).loading)
        val failed = entering.copy(opening = false,
            sync = PromotionSyncState(attempted = true, error = "offline"))
        assertFalse(failed.loading)
        assertNull(failed.visibleSyncError(false))
    }

    @Test fun cachedEmptyListIsReadyAndSilentChecksNeverShowSpinner() {
        assertFalse(PromotionsUiState(initialized = true, sync = PromotionSyncState(running = true)).loading)
        assertTrue(PromotionsUiState(sync = PromotionSyncState(running = true)).loading)
        assertFalse(PromotionsUiState(sync = PromotionSyncState(attempted = true, error = "offline")).loading)
        assertFalse(PromotionSyncState(running = true).visibleNetwork)
    }
    @Test fun favoritesOnlyReceiveAlertsForTheirOwnStore() {
        assertFalse(matchesFavoriteStore(null, "0012"))
        assertFalse(matchesFavoriteStore("", "0012"))
        assertFalse(matchesFavoriteStore("0012", null))
        assertFalse(matchesFavoriteStore("0012", "0021"))
        assertTrue(matchesFavoriteStore("0012", "0012"))
    }
    @Test fun quotaAndNetworkErrorsAreCompletelyHiddenFromOrdinaryUsers() {
        for (message in listOf(
            "O serviço atingiu o limite temporário de consultas. Tente novamente mais tarde.",
            "O serviço de consulta está temporariamente indisponível.",
            "Não foi possível sincronizar agora."
        )) {
            val noCache = PromotionsUiState(initialized = false,
                sync = PromotionSyncState(attempted = true, error = message))
            assertNull(noCache.visibleSyncError(showDiagnostics = false))
            assertEquals(message, noCache.visibleSyncError(showDiagnostics = true))

            val cached = noCache.copy(initialized = true)
            assertNull(cached.visibleSyncError(showDiagnostics = false))
            assertNull(cached.visibleSyncError(showDiagnostics = true))
        }
    }
    @Test fun onlyAddedIdentitiesBecomeNewOffersAndReturningProductIsNewAgain() {
        assertTrue(addedOfferIds(setOf("0012|1"), setOf("0012|1"), true).isEmpty())
        assertTrue(addedOfferIds(setOf("0012|1"), emptySet(), true).isEmpty())
        assertEquals(setOf("0012|1"), addedOfferIds(emptySet(), setOf("0012|1"), true))
    }
    @Test fun unchangedScansPreserveTheNewBatchAndNeverCreateAdditions() {
        val old = setOf("0012|1", "0012|2")
        val batch = setOf("0012|2")
        val unchanged = addedOfferIds(old, old, true)
        assertTrue(unchanged.isEmpty())
        assertEquals(batch, latestAddedOfferIds(batch, unchanged, old, true))
        val next = old + "0012|3"
        val added = addedOfferIds(old, next, true)
        assertEquals(setOf("0012|3"), added)
        assertEquals(added, latestAddedOfferIds(batch, added, next, true))
        assertEquals(added, latestAddedOfferIds(added, emptySet(), next, true))
    }
}
