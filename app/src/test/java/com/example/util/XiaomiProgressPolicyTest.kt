package com.example.util

import org.junit.Assert.*
import org.junit.Test

class XiaomiProgressPolicyTest {
    @Test fun firstAndCompletedUpdatesAreImmediate() {
        assertTrue(XiaomiProgressPolicy.shouldPost(-1, 0, 0))
        assertTrue(XiaomiProgressPolicy.shouldPost(95, 100, 50))
    }
    @Test fun unchangedProgressDoesNotRepost() {
        assertFalse(XiaomiProgressPolicy.shouldPost(95, 95, 9000))
    }
    @Test fun fastProgressIsThrottled() {
        assertFalse(XiaomiProgressPolicy.shouldPost(10, 15, 900))
        assertTrue(XiaomiProgressPolicy.shouldPost(10, 15, 1800))
    }
    @Test fun slowProgressEventuallyAppears() {
        assertFalse(XiaomiProgressPolicy.shouldPost(90, 91, 1800))
        assertTrue(XiaomiProgressPolicy.shouldPost(90, 91, 5000))
    }
}
