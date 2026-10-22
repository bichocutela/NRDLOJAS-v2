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
        val row = day("22:00", "02:00", "03:00", "07:00").copy(expectedMinutes = 480, scheduledStart = 1320, scheduledBreak = 60)
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

    @Test fun automaticallySeparatesEarlyWorkAndEarlyExitFromFourPunches() {
        val row = day("12:22", "17:36", "18:46", "22:27")
        assertEquals(83, row.automaticExtraMinutes)
        assertEquals(83, row.creditMinutes); assertEquals(13, row.debitMinutes)
        assertEquals(70, row.balanceMinutes)
    }

    @Test fun suppliedJulyReportKeepsShorterLunchCreditSeparateFromLateEntryDebit() {
        // 14/07, schedule 00710: 09:45–19:20, 01:50 meal.
        val row = day("10:00", "13:30", "15:06", "19:10")
            .copy(scheduledStart = 585, scheduledBreak = 110)
        assertEquals(14, row.intervalCreditMinutes)
        assertEquals(14, row.creditMinutes); assertEquals(25, row.debitMinutes)
        assertEquals(-11, row.balanceMinutes)
    }


    @Test fun liveSummaryStopsDuringLunch() {
        val row = Workday("2026-10-01", listOf(825, 1020, null, null))
        assertEquals(195, ClockJourney.workedSoFar(row, 1050))
    }
    @Test fun liveSummaryAddsSecondWorkInterval() {
        val row = Workday("2026-10-01", listOf(825, 1020, 1090, null))
        assertEquals(305, ClockJourney.workedSoFar(row, 1200))
        assertEquals(1360, ClockJourney.predictedExit(row))
    }
    @Test fun pendingIncludesOnlyPastIncompletePunches() {
        val rows = listOf(Workday("2026-10-01", listOf(825, null, null, null)), Workday("2026-10-02"), Workday("2026-10-03", listOf(825, null, null, null)))
        assertEquals(listOf("2026-10-01"), ClockJourney.pending(rows, "2026-10-03").map { it.date })
    }
    @Test fun compensationDoesNotChangePunchBalance() {
        val row = day("13:45", "17:00", "18:10", "22:40")
        val adjustment = ClockAdjustment("test", "2026-10-01", -60, "Compensação de folga")
        adjustment.validate()
        assertEquals(0, row.balanceMinutes)
        assertEquals(-60, (row.balanceMinutes ?: 0) + adjustment.minutes)
    }

    private fun reminders(minute: Int, rows: List<Workday> = emptyList(), folgas: Set<String> = emptySet(), punches: Boolean = true, pending: Boolean = false, off: Boolean = false) =
        ClockReminderPlan.events("2026-10-11", minute, WorkHours(), 1020, rows, folgas, punches, pending, off)
    @Test fun punchReminderUsesOnlyTheNextMissingStamp() {
        val row = Workday("2026-10-11", listOf(825, 1020, null, null))
        assertTrue(reminders(1089, listOf(row)).isEmpty())
        assertEquals("2026-10-11:2", reminders(1090, listOf(row)).single().key)
    }
    @Test fun manualDayOffDisablesPunchReminder() {
        assertTrue(reminders(825, folgas = setOf("2026-10-11")).isEmpty())
        assertTrue(reminders(825, rows = listOf(Workday("2026-10-11", dayOff = true))).isEmpty())
    }
    @Test fun dayOffReminderIncludesFallbackDatesAndRespectsEveningWindow() {
        val rows = listOf(Workday("2026-10-12", dayOff = true))
        assertTrue(reminders(1079, rows, punches = false, off = true).isEmpty())
        assertEquals("CLOCK_TOMORROW_OFF", reminders(1080, rows, punches = false, off = true).single().type)
    }
    @Test fun pendingReminderDoesNotTreatOvernightShiftAsForgotten() {
        val row = Workday("2026-10-10", listOf(1320, null, null, null), scheduledStart = 1320)
        assertTrue(reminders(600, listOf(row), punches = false, pending = true).isEmpty())
        assertEquals("CLOCK_PENDING", reminders(1400, listOf(row), punches = false, pending = true).single().type)
    }
    @Test fun overnightReturnReminderUsesPreviousWorkDate() {
        val row = Workday("2026-10-10", listOf(1320, 1440, null, null), scheduledStart = 1320, scheduledBreak = 60)
        assertEquals("2026-10-10:2", reminders(60, listOf(row)).single().key)
    }
    @Test fun disabledReminderOptionsProduceNoEvents() {
        assertTrue(reminders(1080, listOf(Workday("2026-10-10", listOf(825, null, null, null))), setOf("2026-10-12"), punches = false, pending = false, off = false).isEmpty())
    }
    @Test fun forgottenDaytimeShiftIsPendingTheNextMorning() {
        val row = Workday("2026-10-10", listOf(825, null, null, null))
        assertEquals("CLOCK_PENDING", reminders(480, listOf(row), punches = false, pending = true).single().type)
    }
}
