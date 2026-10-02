package com.example.data

import org.junit.Assert.*
import org.junit.Test

class UpdatePolicyTest {
    @Test fun disabledPolicyNeverBlocks() { assertFalse(UpdatePolicy(false, "v1.0.800").requiresUpdate("1.0.705")) }
    @Test fun onlyVersionsBelowMinimumAreBlocked() {
        val policy = UpdatePolicy(true, "v1.0.705")
        assertTrue(policy.requiresUpdate("1.0.704"))
        assertFalse(policy.requiresUpdate("1.0.705"))
        assertFalse(policy.requiresUpdate("1.0.706"))
    }
    @Test fun versionComparisonIsNumeric() { assertTrue(UpdatePolicy(true, "v1.0.10").requiresUpdate("1.0.9")) }
    @Test fun missingOrInvalidPolicyCannotLockUsers() {
        listOf("", "banana", "1.0", "1.0.999999999999999999", "1.0.-1").forEach {
            assertFalse(UpdatePolicy(true, it).requiresUpdate("1.0.705"))
        }
    }
}
