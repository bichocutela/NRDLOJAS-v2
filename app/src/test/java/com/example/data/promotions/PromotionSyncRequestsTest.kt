package com.example.data.promotions

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PromotionSyncRequestsTest {
    @Test fun repeatedPullsQueueOnlyOneFreshScanAfterSilentCheck() = runTest {
        val release = CompletableDeferred<Unit>()
        val calls = mutableListOf<Boolean>()
        val requests = PromotionSyncRequests(this) { interactive ->
            calls += interactive
            if (!interactive) release.await()
        }
        val silent = requests.request()
        runCurrent()
        val manual = requests.request(true)
        assertNotSame(silent, manual)
        repeat(10) { assertSame(manual, requests.request(true)) }
        assertSame(manual, requests.request())
        runCurrent()
        assertEquals(listOf(false), calls)
        release.complete(Unit)
        manual.join()
        assertEquals(listOf(false, true), calls)
        requests.request(true).join()
        assertEquals(listOf(false, true, true), calls)
    }
}
