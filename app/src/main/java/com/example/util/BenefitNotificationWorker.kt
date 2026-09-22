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
import com.example.data.BenefitPurchase
import com.example.data.BenefitSummary
import com.example.data.NossaGenteApi
import com.example.data.NossaGenteBenefitResult
import com.example.data.NossaGenteCredentialStore
import com.example.data.NossaGenteLoginResult
import com.example.data.NossaGenteSessionScope
import com.example.data.UserPreferences
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class BenefitNotificationWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val credentialStore = NossaGenteCredentialStore(applicationContext)
        if (!credentialStore.isBenefitNotificationsEnabled()) return Result.success()

        val api = NossaGenteApi(applicationContext, NossaGenteSessionScope.PROFILE)
        var result = api.fetchBenefit()
        if (result == NossaGenteBenefitResult.Unauthorized) {
            val credentials = credentialStore.load() ?: return Result.success()
            if (api.login(credentials.cpf, credentials.password) == NossaGenteLoginResult.Success) {
                result = api.fetchBenefit()
            }
        }

        return when (result) {
            is NossaGenteBenefitResult.Success -> {
                processSnapshot(result.benefit)
                Result.success()
            }
            NossaGenteBenefitResult.Unauthorized -> Result.success()
            is NossaGenteBenefitResult.Error -> Result.retry()
        }
    }

    private suspend fun processSnapshot(benefit: BenefitSummary) {
        val snapshot = applicationContext.getSharedPreferences(SNAPSHOT_PREFERENCES, Context.MODE_PRIVATE)
        val currentPurchases = benefit.purchases.associateBy(::purchaseKey)
        val previousPurchases = snapshot.getStringSet(KEY_PURCHASES, null)?.toSet()
        val previousPeriod = snapshot.getString(KEY_PERIOD, null)
        val previousLimit = snapshot.getString(KEY_LIMIT, null)
        val previousBalance = snapshot.getString(KEY_BALANCE, null)

        saveSnapshot(snapshot, benefit, currentPurchases.keys)
        if (previousPurchases == null) return

        val preferences = UserPreferences(applicationContext)
        if (!preferences.notificationsEnabled.first()) return

        val newPurchases = currentPurchases.filterKeys { it !in previousPurchases }
        val benefitReleased = benefit.period != previousPeriod || benefit.limit != previousLimit ||
            (newPurchases.isEmpty() && benefit.balance != previousBalance)
        if (benefitReleased) {
            notify(
                preferences,
                TYPE_BENEFIT_RELEASED,
                "Convênio Liberado",
                "Seu convênio foi atualizado. Saldo disponível: ${benefit.balance ?: "não informado"}."
            )
        }

        newPurchases.values
            .forEach { purchase ->
                val place = purchase.place?.takeIf { it.isNotBlank() }?.let { " em $it" }.orEmpty()
                notify(
                    preferences,
                    TYPE_BENEFIT_PURCHASE,
                    "Compras no Convênio",
                    "Compra no valor de ${purchase.amount ?: "valor não informado"}$place."
                )
            }
    }

    private suspend fun notify(preferences: UserPreferences, type: String, title: String, body: String) {
        val now = System.currentTimeMillis()
        preferences.addNotification(AppNotification(now, type, title, body, false, now))
        NotificationHelper.showNotification(applicationContext, type, title, body)
    }

    private fun purchaseKey(purchase: BenefitPurchase): String = listOf(
        purchase.date.orEmpty(), purchase.time.orEmpty(), purchase.place.orEmpty(), purchase.amount.orEmpty()
    ).joinToString("|")

    private fun saveSnapshot(
        snapshot: android.content.SharedPreferences,
        benefit: BenefitSummary,
        purchaseKeys: Set<String>,
    ) {
        snapshot.edit()
            .putString(KEY_PERIOD, benefit.period)
            .putString(KEY_LIMIT, benefit.limit)
            .putString(KEY_BALANCE, benefit.balance)
            .putStringSet(KEY_PURCHASES, purchaseKeys)
            .apply()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "nrdlojas_benefit_notifications"
        private const val SNAPSHOT_PREFERENCES = "nossa_gente_benefit_snapshot"
        private const val KEY_PERIOD = "period"
        private const val KEY_LIMIT = "limit"
        private const val KEY_BALANCE = "balance"
        private const val KEY_PURCHASES = "purchases"
        const val TYPE_BENEFIT_RELEASED = "BENEFIT_RELEASED"
        const val TYPE_BENEFIT_PURCHASE = "BENEFIT_PURCHASE"

        fun schedule(context: Context, resetSnapshot: Boolean = false) {
            val appContext = context.applicationContext
            if (resetSnapshot) {
                appContext.getSharedPreferences(SNAPSHOT_PREFERENCES, Context.MODE_PRIVATE)
                    .edit().clear().apply()
            }
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<BenefitNotificationWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }
}
