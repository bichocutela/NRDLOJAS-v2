package com.example.util

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.NordestaoStoreOffersRepository
import com.example.data.UserPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * A lightweight hourly check of the selected store, not a separate Supabase cron.
 * The offers endpoint already owns its server-side five-minute cache.
 */
class StoreOfferNotificationWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        if (StoreOfferNotifications.mode(context) == StoreOfferNotifications.Mode.OFF ||
            !UserPreferences(context).notificationsEnabled.first()) return Result.success()
        val storeId = context.getSharedPreferences("nrd_store_selection", Context.MODE_PRIVATE)
            .getLong("favorite", -1L)
        if (storeId <= 0L) return Result.success()
        return try {
            val snapshot = NordestaoStoreOffersRepository.load(storeId)
            StoreOfferNotifications.notifyIfNew(context, snapshot)
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Retry on the next scheduled check; avoid an aggressive retry storm.
            Result.success()
        }
    }

    companion object {
        private const val PERIODIC = "nrd_store_offers_discount_alerts"
        private const val IMMEDIATE = "nrd_store_offers_discount_alerts_once"
        private fun network() = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<StoreOfferNotificationWorker>(60, TimeUnit.MINUTES)
                .setConstraints(network())
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun enqueueNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<StoreOfferNotificationWorker>()
                .setConstraints(network()).build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(IMMEDIATE, ExistingWorkPolicy.KEEP, request)
        }
    }
}
