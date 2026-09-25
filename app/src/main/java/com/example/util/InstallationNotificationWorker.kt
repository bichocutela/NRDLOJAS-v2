package com.example.util

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.DeviceInstallationSummaryResult
import com.example.data.DeviceInstallationTracker
import com.example.data.UserPreferences
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class InstallationNotificationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        if (!FcmTopicSubscription.isMasterAuthenticated()) return Result.success()

        val preferences = UserPreferences(applicationContext)
        if (!preferences.masterInstallationNotificationsEnabled.first()) return Result.success()

        val baseline = preferences.masterInstallationNotificationBaseline.first()
        if (baseline <= 0L) {
            val latest = when (val result = DeviceInstallationTracker.fetchSummary()) {
                is DeviceInstallationSummaryResult.Success -> result.summary.lastInstallationAt
                is DeviceInstallationSummaryResult.Error -> null
            }
            preferences.setMasterInstallationNotificationBaseline(latest ?: System.currentTimeMillis())
            return Result.success()
        }

        val newInstallations = DeviceInstallationTracker.installationsAfter(baseline)
        if (newInstallations.isEmpty()) return Result.success()

        preferences.setMasterInstallationNotificationBaseline(
            newInstallations.maxOf { it.firstSeenAt }
        )

        val count = newInstallations.map { it.deviceIdHash }.distinct().size
        val body = if (count == 1) {
            "O NRD V2 foi instalado em um aparelho novo."
        } else {
            count.toString() + " aparelhos novos instalaram o NRD V2."
        }
        NotificationHelper.showNotification(
            context = applicationContext,
            type = "NEW_INSTALLATION",
            title = "Nova instalação do NRD V2",
            body = body
        )
        return Result.success()
    }

    companion object {
        private const val UNIQUE_WORK = "nrd_new_installation_notifications"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<InstallationNotificationWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK)
        }
    }
}
