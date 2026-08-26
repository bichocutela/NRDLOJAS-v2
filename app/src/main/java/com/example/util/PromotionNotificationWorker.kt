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
import com.example.data.NossaGentePromotionsResult
import com.example.data.StoreCatalog
import com.example.data.UserPreferences
import com.example.data.fingerprintPromotionsForTest
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class PromotionNotificationWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val preferences = UserPreferences(applicationContext)
        val favoriteStoreCode = preferences.favoriteStoreCode.first()?.trim().orEmpty()
        if (favoriteStoreCode.isBlank()) return Result.success()

        return when (val result = NossaGenteApi(applicationContext).fetchPromotions()) {
            is NossaGentePromotionsResult.Success -> {
                val favoritePromotions = result.promotions.mapNotNull { promotion ->
                    val products = promotion.products.filter {
                        it.storeCode?.trim() == favoriteStoreCode
                    }
                    promotion.takeIf { products.isNotEmpty() }?.copy(products = products)
                }
                if (favoritePromotions.isEmpty()) return Result.success()

                val fingerprint = fingerprintPromotionsForTest(favoritePromotions)
                val lastFingerprint = preferences.lastNotifiedPromotionFingerprint.first()
                if (lastFingerprint.isNullOrBlank()) {
                    preferences.setLastNotifiedPromotionFingerprint(fingerprint)
                } else if (lastFingerprint != fingerprint) {
                    val storeName = StoreCatalog.nameFor(favoriteStoreCode)
                    val offerCount = favoritePromotions.sumOf { it.products.size }
                    val title = "Nova oferta em $storeName"
                    val body = "$offerCount oferta(s) disponível(is) na sua loja favorita."
                    preferences.addNotification(
                        AppNotification(
                            id = System.currentTimeMillis(),
                            type = TYPE_PROMOTION_UPDATED,
                            title = title,
                            body = body,
                            read = false,
                            timestamp = System.currentTimeMillis(),
                        )
                    )
                    NotificationHelper.showNotification(
                        applicationContext,
                        TYPE_PROMOTION_UPDATED,
                        title,
                        body,
                    )
                    preferences.setLastNotifiedPromotionFingerprint(fingerprint)
                }
                Result.success()
            }
            NossaGentePromotionsResult.Unauthorized -> Result.success()
            is NossaGentePromotionsResult.Error -> Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "nrdlojas_favorite_store_promotion_check"
        private const val TYPE_PROMOTION_UPDATED = "PROMOTION_UPDATED"

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
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
