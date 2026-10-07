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
import com.example.data.NossaGentePromotionsResult
import java.util.concurrent.TimeUnit

class PromotionNotificationWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val coordinator = com.example.data.promotions.PromotionSyncCoordinator.get(applicationContext)
        if (!coordinator.repository.isInitialized()) return Result.success()
        return when (coordinator.sync()) {
            is NossaGentePromotionsResult.Success -> Result.success()
            NossaGentePromotionsResult.Unauthorized -> Result.success()
            is NossaGentePromotionsResult.Error -> Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "nrdlojas_favorite_store_promotion_check"

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
