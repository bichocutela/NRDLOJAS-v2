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
import com.example.data.FirebaseService
import com.example.data.NossaGenteApi
import com.example.data.NossaGentePromotionsResult
import com.example.data.StoreCatalog
import com.example.data.UserPreferences
import kotlinx.coroutines.flow.first
import java.util.Locale
import java.util.concurrent.TimeUnit

class PromotionNotificationWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val preferences = UserPreferences(applicationContext)
        val localNotificationsEnabled = preferences.notificationsEnabled.first()
        val remoteSettings = FirebaseService.getNotificationSettingsOrNull()
        val shouldNotify = localNotificationsEnabled &&
            remoteSettings?.enabled == true &&
            remoteSettings.promotionUpdatedEnabled
        val favoriteStoreCode = preferences.favoriteStoreCode.first()?.trim().orEmpty()
        if (favoriteStoreCode.isBlank()) return Result.success()

        return when (val result = NossaGenteApi(applicationContext).fetchPromotions()) {
            is NossaGentePromotionsResult.Success -> {
                val favoriteProducts = result.promotions
                    .flatMap { it.products }
                    .filter { it.storeCode?.trim() == favoriteStoreCode }
                if (favoriteProducts.isEmpty()) return Result.success()

                val currentProductKeys = favoriteProducts.mapNotNull { product ->
                    val code = product.code.trim()
                    if (code.isNotBlank()) {
                        "code:${code.lowercase(Locale.ROOT)}"
                    } else {
                        product.name.trim()
                            .takeIf { it.isNotBlank() }
                            ?.lowercase(Locale.ROOT)
                            ?.let { "name:$it" }
                    }
                }.toSet()
                if (currentProductKeys.isEmpty()) return Result.success()

                val snapshotPreferences = applicationContext.getSharedPreferences(
                    SNAPSHOT_PREFERENCES_NAME,
                    Context.MODE_PRIVATE
                )
                val previousStoreCode = snapshotPreferences.getString(KEY_SNAPSHOT_STORE, null)
                val previousProductKeys = snapshotPreferences
                    .getStringSet(KEY_SNAPSHOT_PRODUCTS, null)
                    ?.toSet()

                if (previousStoreCode != favoriteStoreCode || previousProductKeys == null) {
                    saveSnapshot(snapshotPreferences, favoriteStoreCode, currentProductKeys)
                    return Result.success()
                }

                val addedProductKeys = currentProductKeys - previousProductKeys
                saveSnapshot(snapshotPreferences, favoriteStoreCode, currentProductKeys)

                if (addedProductKeys.isNotEmpty()) {
                    val storeName = StoreCatalog.nameFor(favoriteStoreCode)
                    val addedCount = addedProductKeys.size
                    val title = if (addedCount == 1) {
                        "Nova promoção em $storeName"
                    } else {
                        "Novas promoções em $storeName"
                    }
                    val body = if (addedCount == 1) {
                        "Foi adicionado 1 produto à sua loja favorita."
                    } else {
                        "Foram adicionados $addedCount produtos à sua loja favorita."
                    }

                    if (shouldNotify) {
                        val now = System.currentTimeMillis()
                        preferences.addNotification(
                            AppNotification(
                                id = now,
                                type = TYPE_PROMOTION_UPDATED,
                                title = title,
                                body = body,
                                read = false,
                                timestamp = now,
                            )
                        )
                        NotificationHelper.showNotification(
                            applicationContext,
                            TYPE_PROMOTION_UPDATED,
                            title,
                            body,
                        )
                    } else {
                        android.util.Log.d(
                            "PromotionNotificationWorker",
                            "Novos produtos detectados, mas a notificação está desativada"
                        )
                    }
                }
                Result.success()
            }
            NossaGentePromotionsResult.Unauthorized -> Result.success()
            is NossaGentePromotionsResult.Error -> Result.retry()
        }
    }

    private fun saveSnapshot(
        preferences: android.content.SharedPreferences,
        storeCode: String,
        productKeys: Set<String>
    ) {
        preferences.edit()
            .putString(KEY_SNAPSHOT_STORE, storeCode)
            .putStringSet(KEY_SNAPSHOT_PRODUCTS, productKeys.toSet())
            .apply()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "nrdlojas_favorite_store_promotion_check"
        private const val TYPE_PROMOTION_UPDATED = "PROMOTION_UPDATED"
        private const val SNAPSHOT_PREFERENCES_NAME = "favorite_store_promotion_notifications"
        private const val KEY_SNAPSHOT_STORE = "store_code"
        private const val KEY_SNAPSHOT_PRODUCTS = "product_keys"

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
