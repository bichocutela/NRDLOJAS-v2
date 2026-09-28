package com.example.data

/** Schedule data extracted from the photographed monthly roster. Dates are day-of-month values. */
data class WorkScheduleEmployee(
    val registration: String,
    val name: String,
    val shift: String = "",
    val daysOff: List<Int> = emptyList(),
    val vacationDays: List<Int> = emptyList(),
    val verified: Boolean = false,
    val rosterPhotoUrl: String = ""
)

data class WorkSchedule(
    val monthKey: String,
    val year: Int,
    val month: Int,
    val employees: List<WorkScheduleEmployee>,
    val updatedAt: Long = System.currentTimeMillis(),
    val revision: Int = 1
)

/** Keeps the profile on the current month's remaining days, then advances to the
 * nearest future month with a published day off. A recently changed previous
 * month takes precedence so employees can review that correction. */
fun resolveProfileScheduleMonth(
    schedules: List<WorkSchedule>,
    registration: String,
    year: Int,
    month: Int,
    dayOfMonth: Int,
    changedPreviousMonthKey: String? = null
): String {
    val reg = registration.filter(Char::isDigit)
    val currentKey = "%04d-%02d".format(year, month)
    val previousCalendar = java.util.Calendar.getInstance().apply {
        clear(); set(year, month - 1, 1); add(java.util.Calendar.MONTH, -1)
    }
    val previousKey = "%04d-%02d".format(
        previousCalendar.get(java.util.Calendar.YEAR),
        previousCalendar.get(java.util.Calendar.MONTH) + 1
    )
    if (changedPreviousMonthKey == previousKey && schedules.any { it.monthKey == previousKey }) return previousKey

    fun hasUpcomingDays(schedule: WorkSchedule): Boolean {
        val row = schedule.employees.firstOrNull { it.registration.filter(Char::isDigit) == reg } ?: return false
        val lastDay = java.util.GregorianCalendar(schedule.year, schedule.month - 1, 1)
            .getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
        return if (schedule.monthKey == currentKey) row.daysOff.any { it in dayOfMonth..lastDay }
        else row.daysOff.any { it in 1..lastDay }
    }

    return schedules.asSequence()
        .filter { it.monthKey >= currentKey && hasUpcomingDays(it) }
        .minByOrNull { it.monthKey }
        ?.monthKey ?: currentKey
}

data class NossaGenteDirectoryEmployee(
    val registration: String,
    val name: String,
    val employeeId: String? = null
)

sealed interface NossaGenteDirectoryResult {
    data class Success(val employees: List<NossaGenteDirectoryEmployee>) : NossaGenteDirectoryResult
    data object Unauthorized : NossaGenteDirectoryResult
    data class Error(val message: String) : NossaGenteDirectoryResult
}
