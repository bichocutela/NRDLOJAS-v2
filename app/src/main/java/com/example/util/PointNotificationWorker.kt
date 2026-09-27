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
import com.example.data.NossaGenteApi
import com.example.data.NossaGenteCredentialStore
import com.example.data.NossaGenteLoginResult
import com.example.data.NossaGentePointResult
import com.example.data.PointSummary
import com.example.data.UserPreferences
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class PointNotificationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val store = NossaGenteCredentialStore(applicationContext)
        if (!store.isPointNotificationsEnabled()) return Result.success()
        val api = NossaGenteApi(applicationContext)
        var response = api.fetchPoint()
        if (response == NossaGentePointResult.Unauthorized) {
            val credentials = store.load() ?: return Result.success()
            if (api.login(credentials.cpf, credentials.password) == NossaGenteLoginResult.Success) response = api.fetchPoint()
        }
        return when (response) {
            is NossaGentePointResult.Success -> { notifyIfChanged(response.point); Result.success() }
            NossaGentePointResult.Unauthorized -> Result.success()
            is NossaGentePointResult.Error -> Result.retry()
        }
    }

    private suspend fun notifyIfChanged(point: PointSummary) {
        val prefs = applicationContext.getSharedPreferences("nossa_gente_point_snapshot", Context.MODE_PRIVATE)
        val signature = buildString {
            append(point.period).append('|').append(point.status).append('|').append(point.balance).append('|').append(point.worked)
            point.records.forEach { append('|').append(it.date).append(':').append(it.entry).append(':').append(it.exit).append(':').append(it.status) }
        }
        val previous = prefs.getString("signature", null)
        prefs.edit().putString("signature", signature).apply()
        if (previous == null || previous == signature || !UserPreferences(applicationContext).notificationsEnabled.first()) return
        val title = "Ponto atualizado"
        val body = point.period?.let { "Há novos dados de ponto para $it." } ?: "Os dados de ponto do seu perfil foram atualizados."
        val now = System.currentTimeMillis()
        UserPreferences(applicationContext).addNotification(AppNotification(now, TYPE_POINT_UPDATED, title, body, false, now))
        NotificationHelper.showNotification(applicationContext, TYPE_POINT_UPDATED, title, body)
    }

    companion object {
        const val TYPE_POINT_UPDATED = "POINT_UPDATED"
        private const val WORK = "nrdlojas_point_notifications"
        fun schedule(context: Context, resetSnapshot: Boolean = false) {
            if (resetSnapshot) context.getSharedPreferences("nossa_gente_point_snapshot", Context.MODE_PRIVATE).edit().clear().apply()
            val request = PeriodicWorkRequestBuilder<PointNotificationWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        }
        fun cancel(context: Context) = WorkManager.getInstance(context.applicationContext).cancelUniqueWork(WORK)
    }
}
