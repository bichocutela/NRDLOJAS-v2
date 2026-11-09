package com.example.data.acp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AcpFeaturedBudgetTest {
    @Test fun deviceWarmupHasHourlyBudget() {
        val now=10_000_000L
        assertTrue(shouldRefreshFeaturedCatalog(null,now))
        assertTrue(shouldRefreshFeaturedCatalog("broken",now))
        assertTrue(shouldRefreshFeaturedCatalog("0",now))
        assertFalse(shouldRefreshFeaturedCatalog((now-59*60_000L).toString(),now))
        assertTrue(shouldRefreshFeaturedCatalog((now-60*60_000L).toString(),now))
        assertTrue(shouldRefreshFeaturedCatalog((now+1000L).toString(),now))
    }
}
