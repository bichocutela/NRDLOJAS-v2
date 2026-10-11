package com.example.util

import android.content.Context
import androidx.work.*
import com.example.data.ManualDaysOffRepository
import com.example.data.timeclock.*
import java.util.Calendar
import java.util.concurrent.TimeUnit

/** Inexact reminders are intentionally battery-friendly and keep working when the UI closes. */
class PersonalClockWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = TimeClockRepository(applicationContext)
        val synced = runCatching { repo.sync() }.isSuccess
        if (repo.reminders()) {
            val today = TimeClock.today()
            val off = ManualDaysOffRepository(applicationContext).schedules().any { s -> s.monthKey == today.take(7) && s.employees.any { e -> today.takeLast(2).toInt() in e.daysOff } }
            val hours = repo.hours()
            val day = repo.days().firstOrNull { it.date == today } ?: Workday(today, expectedMinutes = hours.expectedMinutes, scheduledStart = hours.start, scheduledBreak = hours.breakMinutes)
            if (!off && !day.dayOff && !day.absent && !day.complete) {
                val next = day.punches.indexOfFirst { it == null }
                val due = when (next) {
                    0 -> day.scheduledStart
                    1 -> repo.lunchAt()
                    2 -> day.punches[1]!! + day.scheduledBreak
                    else -> ClockJourney.predictedExit(day) ?: 2880
                }
                val now = Calendar.getInstance().let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }
                val prefs = applicationContext.getSharedPreferences("personal_clock_reminders", Context.MODE_PRIVATE)
                val account = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "guest"
                val key = "$account:$today:$next"
                if (now >= due && now - due <= 90 && !prefs.getBoolean(key, false)) {
                    NotificationHelper.showNotification(applicationContext, PointNotificationWorker.TYPE_POINT_UPDATED,
                        "Lembrete de ponto", "${TimeClock.labels[next]} · abra Meu Perfil para registrar. Confira o horário real da batida.")
                    prefs.edit().putBoolean(key, true).apply()
                }
            }
        }
        return if (synced) Result.success() else Result.retry()
    }
    companion object {
        fun schedule(context: Context) {
            val wm = WorkManager.getInstance(context.applicationContext)
            wm.enqueueUniquePeriodicWork("personal_clock_periodic", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<PersonalClockWorker>(15, TimeUnit.MINUTES).build())
            wm.enqueueUniqueWork("personal_clock_backup", ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<PersonalClockWorker>().setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
        }
    }
}
