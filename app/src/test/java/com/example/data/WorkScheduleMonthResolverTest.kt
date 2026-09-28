package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkScheduleMonthResolverTest {
    private fun schedule(key: String, month: Int, vararg days: Int) = WorkSchedule(
        monthKey = key,
        year = key.substring(0, 4).toInt(),
        month = month,
        employees = listOf(WorkScheduleEmployee("10293613", "Alessandro", daysOff = days.toList()))
    )

    @Test
    fun keepsCurrentMonthWhileAnyRestDayHasNotPassed() {
        assertEquals(
            "2026-10",
            resolveProfileScheduleMonth(listOf(schedule("2026-10", 10, 7, 14, 18)), "10293613", 2026, 10, 14)
        )
    }

    @Test
    fun advancesToNearestMonthAfterCurrentMonthsLastDayOff() {
        assertEquals(
            "2026-11",
            resolveProfileScheduleMonth(
                listOf(schedule("2026-11", 11, 4, 11), schedule("2026-10", 10, 7, 14)),
                "10293613", 2026, 10, 15
            )
        )
    }

    @Test
    fun keepsChangedPreviousMonthVisibleForReview() {
        assertEquals(
            "2026-09",
            resolveProfileScheduleMonth(
                listOf(schedule("2026-11", 11, 4), schedule("2026-09", 9, 3)),
                "10293613", 2026, 10, 28, changedPreviousMonthKey = "2026-09"
            )
        )
    }
}
