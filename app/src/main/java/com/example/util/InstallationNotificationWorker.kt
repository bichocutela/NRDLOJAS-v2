package com.example.util

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters

/** Cancels the legacy polling job, including work persisted by older versions. */
class InstallationNotificationWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = Result.success()

    companion object {
        private const val UNIQUE_WORK = "nrd_new_installation_notifications"
        fun schedule(context: Context) = cancel(context)
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK)
        }
    }
}
