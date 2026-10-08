package com.example.data.promotions
import com.example.ui.PromotionsUiState
import org.junit.Assert.*
import org.junit.Test
class PromotionsUiStateTest {
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
    @Test fun onlyAddedIdentitiesBecomeNewOffersAndReturningProductIsNewAgain() {
        assertTrue(addedOfferIds(setOf("0012|1"), setOf("0012|1"), true).isEmpty())
        assertTrue(addedOfferIds(setOf("0012|1"), emptySet(), true).isEmpty())
        assertEquals(setOf("0012|1"), addedOfferIds(emptySet(), setOf("0012|1"), true))
    }
}
