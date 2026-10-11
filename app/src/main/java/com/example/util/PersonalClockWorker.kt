package com.example.util

import android.content.Context
import androidx.work.*
import com.example.data.ManualDaysOffRepository
import com.example.data.timeclock.*
import java.util.Calendar
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

/** Inexact reminders are intentionally battery-friendly and keep working when the UI closes. */
class PersonalClockWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = TimeClockRepository(applicationContext)
        NotificationHelper.createNotificationChannel(applicationContext)
        if (com.example.data.RestrictedAccessRepository.current().profile && com.example.data.UserPreferences(applicationContext).notificationsEnabled.first()) {
            val folgas = ManualDaysOffRepository(applicationContext).schedules().flatMap { s -> s.employees.flatMap { e -> e.daysOff.map { day -> "${s.monthKey}-${day.toString().padStart(2, '0')}" } } }.toSet()
            val minute = Calendar.getInstance().let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }
            val events = ClockReminderPlan.events(TimeClock.today(), minute, repo.hours(), repo.lunchAt(), repo.days(), folgas, repo.reminders(), repo.pendingReminders(), repo.dayOffReminders())
            val prefs = applicationContext.getSharedPreferences("personal_clock_reminders", Context.MODE_PRIVATE)
            val account = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.takeUnless { it.isAnonymous }?.uid ?: "guest"
            reminderLock.withLock {
                events.forEach { event ->
                    val key = "$account:${event.type}:${event.key}"
                    val manager = androidx.core.app.NotificationManagerCompat.from(applicationContext)
                    val permitted = android.os.Build.VERSION.SDK_INT < 33 || androidx.core.content.ContextCompat.checkSelfPermission(applicationContext, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    val channel = if (android.os.Build.VERSION.SDK_INT >= 26) manager.getNotificationChannel(NotificationHelper.channelIdForType(event.type)!!) else null
                    if (permitted && manager.areNotificationsEnabled() && (channel == null || channel.importance != android.app.NotificationManager.IMPORTANCE_NONE) && !prefs.getBoolean(key, false)) {
                        NotificationHelper.showNotification(applicationContext, event.type, event.title, event.body, notificationTag = "clock_$key")
                        val now = System.currentTimeMillis()
                        val historyId = maxOf(now, prefs.getLong("lastHistoryId", 0L) + 1)
                        com.example.data.UserPreferences(applicationContext).addNotification(com.example.data.AppNotification(historyId, event.type, event.title, event.body, false, now))
                        prefs.edit().putLong("lastHistoryId", historyId).apply()
                        prefs.edit().putBoolean(key, true).apply()
                    }
                }
            }
        }
        val synced = runCatching { kotlinx.coroutines.withTimeout(20_000) { repo.sync() } }.isSuccess
        return if (synced) Result.success() else Result.retry()
    }
    companion object {
        private val reminderLock = kotlinx.coroutines.sync.Mutex()
        fun schedule(context: Context) {
            val wm = WorkManager.getInstance(context.applicationContext)
            wm.enqueueUniquePeriodicWork("personal_clock_periodic", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<PersonalClockWorker>(15, TimeUnit.MINUTES).build())
            wm.enqueueUniqueWork("personal_clock_backup", ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<PersonalClockWorker>().setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
        }
    }
}
