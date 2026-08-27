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
import com.example.BuildConfig
import com.example.data.FirebaseService
import com.example.data.UserPreferences
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class UpdateNotificationWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return when (val releaseResult = UpdateChecker.checkLatestRelease()) {
            is ReleaseCheckResult.Success -> {
                val currentVersion = BuildConfig.VERSION_NAME
                val preferences = UserPreferences(applicationContext)
                val localNotificationsEnabled = preferences.notificationsEnabled.first()
                val remoteSettings = FirebaseService.getNotificationSettingsOrNull()
                if (!localNotificationsEnabled || remoteSettings?.enabled != true || !remoteSettings.appUpdateEnabled) {
                    android.util.Log.d(
                        "UpdateNotificationWorker",
                        "Notificação de atualização bloqueada pela política efetiva"
                    )
                } else {
                    val lastNotifiedTag = preferences.lastNotifiedUpdateTag.first()
                    if (UpdateChecker.isRemoteVersionNewer(currentVersion, releaseResult.tagName) &&
                        lastNotifiedTag != releaseResult.tagName
                    ) {
                        NotificationHelper.showUpdateNotification(applicationContext, releaseResult.tagName)
                        preferences.setLastNotifiedUpdateTag(releaseResult.tagName)
                    }
                }
                Result.success()
            }
            is ReleaseCheckResult.NetworkError -> Result.retry()
            is ReleaseCheckResult.HttpError -> {
                if (releaseResult.responseCode == 408 || releaseResult.responseCode == 429 || releaseResult.responseCode >= 500) {
                    Result.retry()
                } else {
                    Result.success()
                }
            }
            is ReleaseCheckResult.InvalidResponse -> Result.success()
        }
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "nrdlojas_periodic_update_check"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<UpdateNotificationWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
