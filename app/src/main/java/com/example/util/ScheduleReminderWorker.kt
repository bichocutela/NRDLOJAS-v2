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
                val previous = store.getInt("revision_${schedule.monthKey}", 0)
                if (previous > 0 && schedule.revision > previous) {
                    NotificationHelper.showNotification(applicationContext, "SCHEDULE_CHANGED", "Escala Alterada", "Confira as folgas de ${schedule.month}/${schedule.year} no Meu Perfil.")
                } else if (previous == 0 && schedule.monthKey > "%04d-%02d".format(Calendar.getInstance().get(Calendar.YEAR), Calendar.getInstance().get(Calendar.MONTH) + 1)) {
                    NotificationHelper.showNotification(applicationContext, "SCHEDULE_NEW", "Escala de ${monthName(schedule.month)} Inserida", "Confira suas folgas no Meu Perfil.")
                }
                store.edit().putInt("revision_${schedule.monthKey}", schedule.revision).apply()
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
            val dedupeKey = "${type}_${date.get(Calendar.YEAR)}_${key}_$day"
            if (store.getBoolean(dedupeKey, false)) continue
            val title = if (type == "TODAY_OFF") "Hoje é Sua Folga!" else "Amanhã você estará de folga"
            NotificationHelper.showNotification(applicationContext, type, title, "A escala publicada marca o dia $day como sua folga.")
            store.edit().putBoolean(dedupeKey, true).apply()
        }
        return Result.success()
    }

    companion object {
        private const val PREFS = "profile_schedule_reminders"
        private const val KEY_REGISTRATION = "registration"
        private const val KEY_SCHEDULES = "schedules"
        private const val UNIQUE_WORK = "profile_schedule_reminders"

        fun cacheSchedules(context: Context, schedules: List<WorkSchedule>, registration: String) {
            if (registration.isBlank()) return
            val json = JSONArray().apply {
                schedules.forEach { schedule ->
                    put(JSONObject().apply {
                        put("monthKey", schedule.monthKey)
                        put("employees", JSONArray().apply {
                            schedule.employees.forEach { row ->
                                if (row.registration.filter(Char::isDigit) == registration) {
                                    put(JSONObject().put("registration", row.registration).put("daysOff", JSONArray(row.daysOff)))
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

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ScheduleReminderWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(UNIQUE_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun cancel(context: Context) { WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_WORK) }
    }
}
