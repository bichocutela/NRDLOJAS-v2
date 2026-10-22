package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RestrictedAccessLoginTest {
    @Test fun managementAccountsKeepExistingAddress() {
        assertEquals("mestre@nrdlojas.com", RestrictedAccessRepository.loginEmail(" Mestre "))
        assertEquals("admin@nrdlojas.com", RestrictedAccessRepository.loginEmail("ADMIN"))
    }
    @Test fun createdUsersUseSeparateAddressNamespace() {
        assertEquals("ana.silva@usuarios.nrdlojas.com", RestrictedAccessRepository.loginEmail(" Ana.Silva "))
        assertEquals("mestre-2@usuarios.nrdlojas.com", RestrictedAccessRepository.loginEmail("mestre-2"))
    }
    @Test fun invalidLoginsCannotSupplyAnotherEmailOrEmptyAccount() {
        listOf("", "ab", "ana silva", "mestre@nrdlojas.com", "áéí", "x".repeat(65)).forEach {
            assertThrows(IllegalArgumentException::class.java) { RestrictedAccessRepository.loginEmail(it) }
        }
    }
}
