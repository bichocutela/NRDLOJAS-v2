package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Test

class GlobalProductUsageTest {
    @Test
    fun recentRoutineCanOvertakeStaleHistoricTotal() {
        val now = 1_800_000_000_000L
        val staleLeader = usage("old", count = 1_000, lastViewedAt = now - 70L * DAY)
        val routineLeader = usage("recent", count = 12, lastViewedAt = now)

        val ranked = rankGloballyMostUsedProducts(listOf(staleLeader, routineLeader), now)

        assertEquals(listOf("recent", "old"), ranked.map { it.code })
    }

    @Test
    fun totalUsageBreaksTieWhenRecencyMatches() {
        val now = 1_800_000_000_000L
        val ranked = rankGloballyMostUsedProducts(
            listOf(
                usage("less", count = 4, lastViewedAt = now),
                usage("more", count = 9, lastViewedAt = now)
            ),
            now
        )

        assertEquals(listOf("more", "less"), ranked.map { it.code })
    }

    @Test
    fun productsNeverOpenedAreNotShown() {
        val ranked = rankGloballyMostUsedProducts(
            listOf(usage("unused", count = 0, lastViewedAt = null)),
            nowMillis = 1_800_000_000_000L
        )

        assertEquals(emptyList<Product>(), ranked)
    }

    private fun usage(code: String, count: Int, lastViewedAt: Long?) = GlobalProductUsage(
        product = Product(
            code = code,
            name = code,
            searchName = code,
            category = "Mercearia",
            searchCount = count
        ),
        lastViewedAt = lastViewedAt
    )

    private companion object {
        const val DAY = 24L * 60L * 60L * 1000L
    }
}