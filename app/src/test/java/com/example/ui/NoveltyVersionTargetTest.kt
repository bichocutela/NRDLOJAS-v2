package com.example.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoveltyVersionTargetTest {
    @Test fun allAudienceAcceptsAnyVersion() {
        assertTrue(isNoveltyVersionEligible("1.0.680", "all", "1.0.685"))
    }

    @Test fun newAudienceRequiresExactVersion() {
        assertTrue(isNoveltyVersionEligible("1.0.685", "new", "1.0.685"))
        assertFalse(isNoveltyVersionEligible("1.0.684", "new", "1.0.685"))
        assertFalse(isNoveltyVersionEligible("1.0.686", "new", "1.0.685"))
    }

    @Test fun previousAudienceAcceptsOnlyLowerNumericVersions() {
        assertTrue(isNoveltyVersionEligible("1.0.684", "previous", "1.0.685"))
        assertFalse(isNoveltyVersionEligible("1.0.685", "previous", "1.0.685"))
        assertFalse(isNoveltyVersionEligible("1.0.686", "previous", "1.0.685"))
    }
}
