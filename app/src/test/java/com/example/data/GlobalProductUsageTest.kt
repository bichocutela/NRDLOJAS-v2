package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Test

class GlobalProductUsageTest {
    @Test
    fun largerGlobalTotalAlwaysRanksFirst() {
        val now = 1_800_000_000_000L
        val staleLeader = usage("old", count = 1_000, lastViewedAt = now - 70L * DAY)
        val routineLeader = usage("recent", count = 12, lastViewedAt = now)

        val ranked = rankGloballyMostUsedProducts(listOf(staleLeader, routineLeader))

        assertEquals(listOf("old", "recent"), ranked.map { it.code })
    }

    @Test
    fun totalUsageBreaksTieWhenRecencyMatches() {
        val now = 1_800_000_000_000L
        val ranked = rankGloballyMostUsedProducts(
            listOf(
                usage("less", count = 4, lastViewedAt = now),
                usage("more", count = 9, lastViewedAt = now)
            )
        )

        assertEquals(listOf("more", "less"), ranked.map { it.code })
    }

    @Test
    fun productsNeverOpenedAreNotShown() {
        val ranked = rankGloballyMostUsedProducts(
            listOf(usage("unused", count = 0, lastViewedAt = null))
        )

        assertEquals(emptyList<Product>(), ranked)
    }

    @Test
    fun latestAddedUsesCreationDateAndSkipsUnknownDates() {
        val newest = usage("newest", count = 0, lastViewedAt = null, createdAt = 300L)
        val older = usage("older", count = 0, lastViewedAt = null, createdAt = 100L)
        val unknown = usage("unknown", count = 0, lastViewedAt = null)

        val ranked = rankLatestAddedProducts(listOf(older, unknown, newest))

        assertEquals(listOf("newest", "older"), ranked.map { it.code })
    }

    private fun usage(
        code: String,
        count: Int,
        lastViewedAt: Long?,
        createdAt: Long? = null
    ) = GlobalProductUsage(
        product = Product(
            code = code,
            name = code,
            searchName = code,
            category = "Mercearia",
            searchCount = count
        ),
        lastViewedAt = lastViewedAt,
        createdAt = createdAt
    )

    private companion object {
        const val DAY = 24L * 60L * 60L * 1000L
    }
}
