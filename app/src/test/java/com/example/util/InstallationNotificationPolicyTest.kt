package com.example.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallationNotificationPolicyTest {
    private val event = "a".repeat(64) + "_708"
    @Test fun loggedOutAndCommonUsersNeverReceiveMasterAlerts() {
        assertFalse(InstallationNotificationPolicy.shouldDisplay(false, true, true, event))
    }
    @Test fun masterOptOutAndGeneralOptOutAreRespected() {
        assertFalse(InstallationNotificationPolicy.shouldDisplay(true, true, false, event))
        assertFalse(InstallationNotificationPolicy.shouldDisplay(true, false, true, event))
    }
    @Test fun authenticatedMasterReceivesValidInstallationOrVersionEvent() {
        assertTrue(InstallationNotificationPolicy.shouldDisplay(true, true, true, event))
        assertTrue(InstallationNotificationPolicy.shouldDisplay(true, true, true, "b".repeat(64) + "_709"))
    }
    @Test fun missingOrMalformedEventCannotBypassDeduplication() {
        assertFalse(InstallationNotificationPolicy.shouldDisplay(true, true, true, null))
        assertFalse(InstallationNotificationPolicy.shouldDisplay(true, true, true, "invalid"))
    }
}
