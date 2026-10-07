package com.example.data.promotions
import org.junit.Assert.*
import org.junit.Test
class PromotionDeltaTest {
    @Test fun replacementWithSameCountUpdatesAndRemovesAtomically() {
        val old = listOf(CachedAcpRow("1", "a", "{}"), CachedAcpRow("2", "b", "{}"))
        val delta = PromotionDelta("revision", 2, listOf(CachedAcpRow("1", "changed", "{}"), CachedAcpRow("3", "new", "{}")), listOf("2"))
        assertEquals(setOf("1", "3"), delta.applyTo(old).map { it.id }.toSet())
        assertEquals("changed", delta.applyTo(old).first { it.id == "1" }.hash)
    }
    @Test fun rejectsPartialSnapshotAndFirstInstallIsBaseline() {
        assertThrows(IllegalArgumentException::class.java) { PromotionDelta("r", 2, emptyList(), emptyList()).applyTo(emptyList()) }
        assertTrue(addedOfferIds(emptySet(), setOf("0012|9"), initialized = false).isEmpty())
        assertEquals(setOf("0012|10"), addedOfferIds(setOf("0012|9"), setOf("0012|9", "0012|10"), true))
        assertEquals("0012|9", offerIdentity("0012", "9"))
    }
}
