package com.example.util

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.AppNotification
import com.example.data.HoursSummary
import com.example.data.NossaGenteApi
import com.example.data.NossaGenteCredentialStore
import com.example.data.NossaGenteHoursResult
import com.example.data.NossaGenteLoginResult
import com.example.data.UserPreferences
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/** Consulta o Banco de Horas autenticado e só avisa depois de ter um estado-base local. */
class HoursNotificationWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val credentialStore = NossaGenteCredentialStore(applicationContext)
        if (!credentialStore.isHoursNotificationsEnabled()) return Result.success()

        val api = NossaGenteApi(applicationContext)
        var result = api.fetchHours()
        if (result == NossaGenteHoursResult.Unauthorized) {
            val credentials = credentialStore.load() ?: return Result.success()
            if (api.login(credentials.cpf, credentials.password) == NossaGenteLoginResult.Success) {
                result = api.fetchHours()
            }
        }

        return when (result) {
            is NossaGenteHoursResult.Success -> {
                processSnapshot(result.hours)
                Result.success()
            }
            NossaGenteHoursResult.Unauthorized -> Result.success()
            is NossaGenteHoursResult.Error -> Result.retry()
        }
    }

    private suspend fun processSnapshot(hours: HoursSummary) {
        val snapshot = applicationContext.getSharedPreferences(SNAPSHOT_PREFERENCES, Context.MODE_PRIVATE)
        val current = signature(hours)
        val previous = snapshot.getString(KEY_SIGNATURE, null)
        snapshot.edit().putString(KEY_SIGNATURE, current).apply()
        if (previous == null || previous == current) return

        val preferences = UserPreferences(applicationContext)
        if (!preferences.notificationsEnabled.first()) return

        val title = "Banco de horas atualizado"
        val body = "Saldo atual: ${hours.total}."
        val now = System.currentTimeMillis()
        preferences.addNotification(AppNotification(now, TYPE_HOURS_UPDATED, title, body, false, now))
        NotificationHelper.showNotification(applicationContext, TYPE_HOURS_UPDATED, title, body)
    }

    private fun signature(hours: HoursSummary): String = buildString {
        append(hours.total)
        hours.months.forEach { month -> append('|').append(month.year).append(':').append(month.month).append(':').append(month.balance) }
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "nrdlojas_hours_notifications"
        private const val SNAPSHOT_PREFERENCES = "nossa_gente_hours_snapshot"
        private const val KEY_SIGNATURE = "hours_signature"
        const val TYPE_HOURS_UPDATED = "HOURS_UPDATED"

        fun schedule(context: Context, resetSnapshot: Boolean = false) {
            val appContext = context.applicationContext
            if (resetSnapshot) {
                appContext.getSharedPreferences(SNAPSHOT_PREFERENCES, Context.MODE_PRIVATE).edit().clear().apply()
            }
            val request = PeriodicWorkRequestBuilder<HoursNotificationWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }
}
