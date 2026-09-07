package com.example.data

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class DynamicPageScheduleTest {
    private lateinit var originalTimeZone: TimeZone

    @Before
    fun setUp() {
        originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalTimeZone)
    }

    @Test
    fun `data inicial fica visivel desde o inicio do dia local`() {
        val startAt = utcDate(2026, Calendar.SEPTEMBER, 7)
        val document = DynamicPageDocument(startAt = startAt)

        assertFalse(document.isVisibleAt(localDateTime(2026, Calendar.SEPTEMBER, 6, 23, 59)))
        assertTrue(document.isVisibleAt(localDateTime(2026, Calendar.SEPTEMBER, 7, 0, 0)))
    }

    @Test
    fun `data final permanece visivel ate o fim do dia local`() {
        val endAt = utcDate(2026, Calendar.SEPTEMBER, 7) + 86_399_999L
        val document = DynamicPageDocument(endAt = endAt)

        assertTrue(document.isVisibleAt(localDateTime(2026, Calendar.SEPTEMBER, 7, 23, 59)))
        assertFalse(document.isVisibleAt(localDateTime(2026, Calendar.SEPTEMBER, 8, 0, 0)))
    }

    @Test
    fun `conteudo desativado nunca fica visivel`() {
        val document = DynamicPageDocument(enabled = false)
        assertFalse(document.isVisibleAt(localDateTime(2026, Calendar.SEPTEMBER, 7, 12, 0)))
    }

    private fun utcDate(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(year, month, day, 0, 0, 0)
        }.timeInMillis

    private fun localDateTime(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month, day, hour, minute, 0)
        }.timeInMillis
}
