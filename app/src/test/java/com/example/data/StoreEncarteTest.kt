package com.example.data

import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class StoreEncarteTest {
    private val encarte = StoreEncarte(1, "Encarte", "2026-10-21T00:00:00", "2026-10-22T23:59:59", "", "")
    private fun instant(value: String) = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.parse(value)!!.time
    @Test fun validityUsesNatalTimezone() {
        assertTrue(encarte.isActive(instant("2026-10-23T02:59:59Z")))
        assertFalse(encarte.isActive(instant("2026-10-23T03:00:00Z")))
        assertFalse(encarte.isActive(instant("2026-10-21T02:59:59Z")))
        assertEquals("21/10 a 22/10", encarte.validity)
    }
    @Test fun invalidDatesFailClosed() {
        assertFalse(encarte.copy(endDate = "invalid").isActive())
        assertFalse(encarte.copy(endDate = "2026-02-31T23:59:59").isActive())
    }
}
