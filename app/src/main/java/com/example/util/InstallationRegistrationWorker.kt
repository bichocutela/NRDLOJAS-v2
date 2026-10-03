package com.example.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.*
import com.example.BuildConfig
import com.example.data.DeviceInstallationTracker
import java.util.concurrent.TimeUnit

/** Network retry only: the foreground app makes the first attempt immediately. */
class InstallationRegistrationWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = if (DeviceInstallationTracker.register(applicationContext)) Result.success() else Result.retry()

    companion object {
        fun schedule(context: Context) {
            val builder = OneTimeWorkRequestBuilder<InstallationRegistrationWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            }
            val request = builder.build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "installation_registration_${BuildConfig.VERSION_CODE}", ExistingWorkPolicy.KEEP, request)
        }
    }
}

class InstallationUpdatedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) InstallationRegistrationWorker.schedule(context)
    }
}
