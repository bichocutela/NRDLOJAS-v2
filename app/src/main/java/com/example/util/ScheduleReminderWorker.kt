package com.example.util

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.WorkSchedule
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.concurrent.TimeUnit

/** Local best-effort day-before/day-of reminders from schedules already downloaded in Meu Perfil. */
class ScheduleReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val store = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val credentials = com.example.data.NossaGenteCredentialStore(applicationContext)
        val registration = store.getString(KEY_REGISTRATION, null)?.filter(Char::isDigit).orEmpty()
        if (registration.isBlank()) return Result.success()
        if (credentials.isScheduleNotificationsEnabled()) {
            val fresh = com.example.data.FirebaseService.fetchWorkSchedules()
            for (schedule in fresh) {
                val employee = schedule.employees.firstOrNull { it.registration.filter(Char::isDigit) == registration } ?: continue
                if (employee.shift.isBlank() && employee.daysOff.isEmpty() && employee.vacationDays.isEmpty()) continue
                val fingerprint = "${employee.shift}|${employee.daysOff.sorted()}|${employee.vacationDays.sorted()}"
                val key = "fingerprint_${schedule.monthKey}"
                val previous = store.getString(key, null)
                if (previous != null && previous != fingerprint) {
                    NotificationHelper.showNotification(applicationContext, "SCHEDULE_CHANGED", "Escala Alterada", "Confira as folgas de ${schedule.month}/${schedule.year} no Meu Perfil.")
                    store.edit().putString(KEY_LAST_CHANGED_MONTH, schedule.monthKey)
                        .putString(KEY_LAST_CHANGED_REGISTRATION, registration).apply()
                } else if (previous == null && schedule.monthKey > "%04d-%02d".format(Calendar.getInstance().get(Calendar.YEAR), Calendar.getInstance().get(Calendar.MONTH) + 1)) {
                    NotificationHelper.showNotification(applicationContext, "SCHEDULE_NEW", "Escala de ${monthName(schedule.month)} Inserida", "Confira suas folgas no Meu Perfil.")
                }
                store.edit().putString(key, fingerprint).apply()
            }
            if (fresh.isNotEmpty()) cacheSchedules(applicationContext, fresh, registration)
        }
        if (!credentials.isDayOffNotificationsEnabled()) return Result.success()
        val today = Calendar.getInstance()
        val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
        val candidates = listOf(today to "TODAY_OFF", tomorrow to "TOMORROW_OFF")
        val schedules = runCatching { JSONArray(store.getString(KEY_SCHEDULES, "[]")) }.getOrNull() ?: return Result.success()
        for ((date, type) in candidates) {
            val key = "%04d-%02d".format(date.get(Calendar.YEAR), date.get(Calendar.MONTH) + 1)
            val schedule = (0 until schedules.length()).mapNotNull { schedules.optJSONObject(it) }.firstOrNull { it.optString("monthKey") == key } ?: continue
            val employees = schedule.optJSONArray("employees") ?: continue
            val employee = (0 until employees.length()).mapNotNull { employees.optJSONObject(it) }
                .firstOrNull { it.optString("registration").filter(Char::isDigit) == registration } ?: continue
            val day = date.get(Calendar.DAY_OF_MONTH)
            val daysOff = employee.optJSONArray("daysOff") ?: continue
            if ((0 until daysOff.length()).none { daysOff.optInt(it) == day }) continue
            val dedupeKey = "${type}_${registration.hashCode()}_${date.get(Calendar.YEAR)}_${key}_$day"
            val title = if (type == "TODAY_OFF") "Hoje é Sua Folga!" else "Amanhã você estará de folga"
            // Stable tag replaces an already visible copy if WorkManager ever overlaps
            // a retry, while the process lock makes the SharedPreferences check atomic.
            synchronized(reminderNotificationLock) {
                if (!store.getBoolean(dedupeKey, false)) {
                    NotificationHelper.showNotification(
                        applicationContext,
                        type,
                        title,
                        "A escala publicada marca o dia $day como sua folga.",
                        notificationTag = "profile_dayoff_${registration.hashCode()}_${type}_${date.get(Calendar.YEAR)}_${key}_$day"
                    )
                    store.edit().putBoolean(dedupeKey, true).apply()
                }
            }
        }
        return Result.success()
    }

    companion object {
        private const val PREFS = "profile_schedule_reminders"
        private const val KEY_REGISTRATION = "registration"
        private const val KEY_SCHEDULES = "schedules"
        private const val KEY_LAST_CHANGED_MONTH = "last_changed_month"
        private const val KEY_LAST_CHANGED_REGISTRATION = "last_changed_registration"
        private const val UNIQUE_WORK = "profile_schedule_reminders"
        private val reminderNotificationLock = Any()

        fun markScheduleChanged(context: Context, registration: String, monthKey: String) {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_LAST_CHANGED_MONTH, monthKey)
                .putString(KEY_LAST_CHANGED_REGISTRATION, registration.filter(Char::isDigit))
                .apply()
        }

        fun changedPreviousMonthKey(
            context: Context, schedules: List<WorkSchedule>, registration: String, year: Int, month: Int
        ): String? {
            val store = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val previous = Calendar.getInstance().apply {
                clear(); set(year, month - 1, 1); add(Calendar.MONTH, -1)
            }
            val key = "%04d-%02d".format(previous.get(Calendar.YEAR), previous.get(Calendar.MONTH) + 1)
            val digits = registration.filter(Char::isDigit)
            val changedByWorker = store.getString(KEY_LAST_CHANGED_MONTH, null) == key &&
                store.getString(KEY_LAST_CHANGED_REGISTRATION, null) == digits
            if (changedByWorker) return key

            val cachedSchedules = runCatching { JSONArray(store.getString(KEY_SCHEDULES, "[]")) }.getOrNull() ?: return null
            val cachedMonth = (0 until cachedSchedules.length()).mapNotNull { cachedSchedules.optJSONObject(it) }
                .firstOrNull { it.optString("monthKey") == key } ?: return null
            val cachedEmployees = cachedMonth.optJSONArray("employees") ?: return null
            val cachedEmployee = (0 until cachedEmployees.length()).mapNotNull { cachedEmployees.optJSONObject(it) }
                .firstOrNull { it.optString("registration").filter(Char::isDigit) == digits }
            val latest = schedules.firstOrNull { it.monthKey == key }?.employees
                ?.firstOrNull { it.registration.filter(Char::isDigit) == digits }
            if (cachedEmployee == null) return key.takeIf { latest != null }
            if (latest == null) return key
            val previousFingerprint = cachedEmployee.optString("fingerprint").takeIf { it.isNotBlank() }
                ?: "${cachedEmployee.optString("shift")}|${cachedEmployee.optJSONArray("daysOff")?.toIntList().orEmpty()}|${cachedEmployee.optJSONArray("vacationDays")?.toIntList().orEmpty()}"
            val latestFingerprint = "${latest.shift}|${latest.daysOff.sorted()}|${latest.vacationDays.sorted()}"
            return key.takeIf { previousFingerprint != latestFingerprint }
        }

        fun cacheSchedules(context: Context, schedules: List<WorkSchedule>, registration: String) {
            if (registration.isBlank()) return
            val json = JSONArray().apply {
                schedules.forEach { schedule ->
                    put(JSONObject().apply {
                        put("monthKey", schedule.monthKey)
                        put("employees", JSONArray().apply {
                            schedule.employees.forEach { row ->
                                if (row.registration.filter(Char::isDigit) == registration) {
                                    put(JSONObject().put("registration", row.registration)
                                        .put("daysOff", JSONArray(row.daysOff)).put("shift", row.shift)
                                        .put("vacationDays", JSONArray(row.vacationDays))
                                        .put("fingerprint", "${row.shift}|${row.daysOff.sorted()}|${row.vacationDays.sorted()}"))
                                }
                            }
                        })
                    })
                }
            }
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_REGISTRATION, registration).putString(KEY_SCHEDULES, json.toString()).apply()
        }

        private fun monthName(month: Int): String = Calendar.getInstance().apply { set(Calendar.MONTH, month - 1) }
            .getDisplayName(Calendar.MONTH, Calendar.LONG, java.util.Locale("pt", "BR"))
            ?.replaceFirstChar { it.uppercase() } ?: "mês"

        private fun JSONArray.toIntList(): List<Int> = (0 until length()).map { optInt(it) }

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ScheduleReminderWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(UNIQUE_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun cancel(context: Context) { WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_WORK) }
    }
}
