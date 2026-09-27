package com.example.data

/** Schedule data extracted from the photographed monthly roster. Dates are day-of-month values. */
data class WorkScheduleEmployee(
    val registration: String,
    val name: String,
    val shift: String = "",
    val daysOff: List<Int> = emptyList(),
    val vacationDays: List<Int> = emptyList(),
    val verified: Boolean = false
)

data class WorkSchedule(
    val monthKey: String,
    val year: Int,
    val month: Int,
    val employees: List<WorkScheduleEmployee>,
    val updatedAt: Long = System.currentTimeMillis(),
    val revision: Int = 1
)

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
