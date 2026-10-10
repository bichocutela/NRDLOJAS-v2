package com.example.util

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.NossaGentePromotionsResult
import java.util.concurrent.TimeUnit

class PromotionNotificationWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val coordinator = com.example.data.promotions.PromotionSyncCoordinator.get(applicationContext)
        return when (coordinator.sync()) {
            is NossaGentePromotionsResult.Success -> Result.success()
            NossaGentePromotionsResult.Unauthorized -> Result.success()
            is NossaGentePromotionsResult.Error -> Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "nrdlojas_favorite_store_promotion_check"

        fun enqueueNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<PromotionNotificationWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                "nrdlojas_promotion_preload", ExistingWorkPolicy.KEEP, request)
        }

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<PromotionNotificationWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }
}
