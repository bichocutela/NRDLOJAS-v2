package com.example.data.promotions
import com.example.ui.PromotionsUiState
import org.junit.Assert.*
import org.junit.Test
class PromotionsUiStateTest {
    @Test fun partialStoreFailureNeverClaimsACompleteFreshCatalogAndRecoveryClearsTheWarning() {
        val previous=PromotionSyncState(lastCompleteSyncAt=100L)
        val partial=previous.afterStoreSync(setOf("0012"),200L)
        assertEquals(100L,partial.lastCompleteSyncAt)
        assertTrue(partial.error!!.contains("Cidade Jardim"))
        assertTrue(partial.error!!.contains("automaticamente"))
        val recovered=partial.afterStoreSync(emptySet(),300L)
        assertEquals(300L,recovered.lastCompleteSyncAt)
        assertNull(recovered.error)
    }
    @Test fun entryShowsSavedOffersDuringBackgroundScanAndFinishesOnFailure() {
        val entering = PromotionsUiState(initialized = true, opening = true,
            sync = PromotionSyncState(running = true))
        assertFalse(entering.loading)
        assertFalse(entering.copy(opening = false,
            sync = PromotionSyncState(attempted = true)).loading)
        val failed = entering.copy(opening = false,
            sync = PromotionSyncState(attempted = true, error = "offline"))
        assertFalse(failed.loading)
        assertNotNull(failed.visibleSyncError(false))
    }

    @Test fun cachedEmptyListIsReadyAndSilentChecksNeverShowSpinner() {
        assertFalse(PromotionsUiState(initialized = true, sync = PromotionSyncState(running = true)).loading)
        assertTrue(PromotionsUiState(sync = PromotionSyncState(running = true)).loading)
        assertFalse(PromotionsUiState(sync = PromotionSyncState(attempted = true, error = "offline")).loading)
        assertFalse(PromotionSyncState(running = true).visibleNetwork)
    }
    @Test fun diskOffersRequireRecentAcpConfirmationAndFailuresRevokeIt() {
        assertFalse(acpOffersConfirmed(0L, 100_000L))
        assertTrue(acpOffersConfirmed(100_000L, 159_999L))
        assertFalse(acpOffersConfirmed(100_000L, 160_000L))
        assertFalse(acpOffersConfirmed(100_000L, 99_999L))
        val confirmed=PromotionSyncState().afterStoreSync(emptySet(),100_000L)
        assertEquals(100_000L,confirmed.acpConfirmedAt)
        assertEquals(0L,confirmed.afterStoreSync(setOf("0012"),120_000L).acpConfirmedAt)
        assertEquals(120_000L,confirmed.afterStoreSync(setOf("0005"),120_000L).acpConfirmedAt)
    }
    @Test fun acpRevisionCheckRunsOnceAfterFourAmAndManualRefreshCanForceIt() {
        val format = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.ROOT).apply {
            timeZone = java.util.TimeZone.getTimeZone("America/Fortaleza")
        }
        val beforeWindow = format.parse("2026-10-26 03:59")!!.time
        val afterWindow = format.parse("2026-10-26 04:00")!!.time
        assertFalse(shouldCheckAcpStatus("2026-10-25", beforeWindow))
        assertTrue(shouldCheckAcpStatus("2026-10-25", afterWindow))
        assertFalse(shouldCheckAcpStatus("2026-10-26", afterWindow))
        assertTrue(shouldCheckAcpStatus("2026-10-26", afterWindow, force = true))
        assertTrue(shouldCheckAcpStatus(null, beforeWindow))
    }

    @Test fun favoritesOnlyReceiveAlertsForTheirOwnStore() {
        assertFalse(matchesFavoriteStore(null, "0012"))
        assertFalse(matchesFavoriteStore("", "0012"))
        assertFalse(matchesFavoriteStore("0012", null))
        assertFalse(matchesFavoriteStore("0012", "0021"))
        assertTrue(matchesFavoriteStore("0012", "0012"))
    }
    @Test fun syncFailuresAreVisibleWithoutExposingTechnicalDetails() {
        for (message in listOf(
            "O serviço atingiu o limite temporário de consultas. Tente novamente mais tarde.",
            "O serviço de consulta está temporariamente indisponível.",
            "Não foi possível sincronizar agora."
        )) {
            val noCache = PromotionsUiState(initialized = false,
                sync = PromotionSyncState(attempted = true, error = message))
            assertTrue(noCache.visibleSyncError(showDiagnostics = false)!!.contains("confirmar"))
            assertEquals(message, noCache.visibleSyncError(showDiagnostics = true))

            val cached = noCache.copy(initialized = true)
            assertTrue(cached.visibleSyncError(showDiagnostics = false)!!.contains("confirmar"))
            assertEquals(message,cached.visibleSyncError(showDiagnostics = true))
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
