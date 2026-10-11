package com.example.data.timeclock

import org.junit.Assert.*
import org.junit.Test

class TimeClockTest {
    private fun day(vararg times: String) = Workday("2026-10-11", TimeClock.normalize(times.map(TimeClock::parseTime)))
    @Test fun exampleDeductsLunchAndHasZeroBalance() {
        assertEquals(465, WorkHours().expectedMinutes)
        val row = day("13:45", "17:00", "18:10", "22:40")
        assertEquals(465, row.workedMinutes)
        assertEquals(0, row.balanceMinutes)
    }
    @Test fun longerLunchAndEarlyExitProduceDebit() {
        val row = day("13:45", "17:00", "18:20", "22:20")
        assertEquals(-30, row.balanceMinutes)
    }
    @Test fun creditAndDebitRemainSeparateInMonthlyTotals() {
        val credit = day("13:45", "17:00", "18:10", "23:00")
        val debit = day("13:45", "17:00", "18:10", "22:10")
        val totals = TimeClock.totals(listOf(credit, debit))
        assertEquals(20, totals.credit); assertEquals(30, totals.debit); assertEquals(-10, totals.balance)
    }
    @Test fun incompleteDayDoesNotBecomeANegativeBalance() {
        val row = Workday("2026-10-11", listOf(825, 1020, null, null))
        assertNull(row.balanceMinutes)
        assertEquals(0, TimeClock.totals(listOf(row)).balance)
        assertEquals(1, TimeClock.totals(listOf(row)).pending)
    }
    @Test fun explicitAbsenceDebitsButDayOffDoesNot() {
        assertEquals(-465, Workday("2026-10-11", absent = true).balanceMinutes)
        assertNull(Workday("2026-10-11", dayOff = true).balanceMinutes)
        assertEquals(465, day("13:45", "17:00", "18:10", "22:40").copy(dayOff = true).balanceMinutes)
    }
    @Test fun overnightWorkDeductsOnlyActualLunch() {
        val row = day("22:00", "02:00", "03:00", "07:00").copy(expectedMinutes = 480)
        assertEquals(listOf(1320, 1560, 1620, 1860), row.punches)
        assertEquals(480, row.workedMinutes); assertEquals(0, row.balanceMinutes)
        assertEquals(480, WorkHours(1320, 420, 60).expectedMinutes)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsMultipleMidnightCrossings() {
        TimeClock.normalize(listOf(1320, 60, 1300, 50))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsSkippedPunches() {
        TimeClock.normalize(listOf(825, null, 1090, 1360))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsMealLongerThanShift() {
        WorkHours(825, 1360, 600).validate()
    }
    @Test fun leapYearReportContainsFebruary29() {
        assertEquals(29, TimeClock.monthDates("2028-02").size)
        assertEquals(28, TimeClock.monthDates("2026-02").size)
        assertEquals("2027-01", TimeClock.shiftMonth("2026-12", 1))
    }
    @Test fun formatsLargeBalancesWithoutWrappingAfter24Hours() {
        assertEquals("+30:05", TimeClock.duration(1805, true))
        assertEquals("−30:05", TimeClock.duration(-1805, true))
        assertNull(TimeClock.parseTime("24:00")); assertNull(TimeClock.parseTime("17:70"))
    }
    @Test fun changingCurrentHoursDoesNotRewriteOldDay() {
        val saved = day("13:45", "17:00", "18:10", "22:40")
        val newHours = WorkHours(600, 1080, 60)
        assertEquals(420, newHours.expectedMinutes)
        assertEquals(465, saved.expectedMinutes); assertEquals(0, saved.balanceMinutes)
    }
    @Test fun reportSeparatesEarlyExitAndExtraPeriodOnSameDay() {
        // 27/07 in the supplied August report: debit 00:13 plus credit 01:23.
        val row = day("13:45", "17:36", "18:46", "22:27").copy(extras = listOf(742, 825))
        TimeClock.validate(row)
        assertEquals(13, row.debitMinutes); assertEquals(83, row.creditMinutes)
        assertEquals(70, row.balanceMinutes)
        val totals = TimeClock.totals(listOf(row))
        assertEquals(83, totals.credit); assertEquals(13, totals.debit)
    }
    @Test fun reportsFollow25To24ClosingCycle() {
        val dates = TimeClock.periodDates("2026-09")
        assertEquals("2026-08-25", dates.first()); assertEquals("2026-09-24", dates.last())
        assertEquals("2026-09", TimeClock.competence("2026-08-25", 24))
        assertEquals("2026-08", TimeClock.competence("2026-08-24", 24))
        assertEquals("2026-09-01", TimeClock.periodDates("2026-09", 0).first())
    }
    @Test(expected = IllegalArgumentException::class) fun cannotCountOverlappingExtraTwice() {
        TimeClock.validate(day("13:45", "17:00", "18:10", "22:40").copy(extras = listOf(1320, 1380)))
    }
    @Test fun occurrencesIdentifyIncompleteAndEarlyExit() {
        assertTrue(day("13:45", "17:00", "18:10", "22:20").occurrences().any { it.startsWith("Saída antecipada") })
        assertTrue(Workday("2026-10-11", listOf(825, null, null, null)).occurrences().any { it.contains("incompleta") })
    }

}
