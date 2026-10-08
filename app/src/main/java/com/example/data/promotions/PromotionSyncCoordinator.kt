package com.example.data.promotions

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.data.AppNotification
import com.example.data.FirebaseService
import com.example.data.NossaGentePromotionsResult
import com.example.data.RestrictedAccessRepository
import com.example.data.StoreCatalog
import com.example.data.UserPreferences
import com.example.util.NotificationHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

internal data class PromotionSyncState(
    val running: Boolean = false,
    val visibleNetwork: Boolean = false,
    val attempted: Boolean = false,
    val error: String? = null
)

/** One owner for foreground polling, screen refresh and WorkManager; no screen-owned timer. */
internal class PromotionSyncCoordinator private constructor(context: Context) {
    private val context = context.applicationContext
    internal val repository = AcpPromotionsRepository(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gate = Mutex()
    private val mutableState = MutableStateFlow(PromotionSyncState())
    val state: StateFlow<PromotionSyncState> = mutableState
    private var monitoring = false
    private var monitorJob: Job? = null
    private var lastSuccessAt = 0L
    private var failureCount = 0
    private var retryAt = 0L
    private val requests = PromotionSyncRequests(scope) { sync(it) }

    fun startForegroundMonitoring() {
        if (monitoring) return
        monitoring = true
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                monitorJob?.cancel()
                monitorJob = scope.launch {
                    while (isActive) {
                        if (repository.isInitialized()) requestSync()
                        delay(60_000)
                    }
                }
            }
            override fun onStop(owner: LifecycleOwner) {
                monitorJob?.cancel()
            }
        })
    }

    fun requestSync(interactive: Boolean = false): Job = requests.request(interactive)

    suspend fun sync(interactive: Boolean = false): NossaGentePromotionsResult = gate.withLock {
        if (!interactive && System.currentTimeMillis() < retryAt) {
            return@withLock repository.cached() ?: NossaGentePromotionsResult.Error("Aguardando nova tentativa de sincronização.")
        }
        mutableState.value = PromotionSyncState(running = true, visibleNetwork = interactive, attempted = true)
        try {
            if (!RestrictedAccessRepository.current().promotions) {
                val message = "Peça ao Mestre para liberar seu acesso às promoções."
                mutableState.value = mutableState.value.copy(error = message)
                return@withLock NossaGentePromotionsResult.Unauthorized
            }
            if (!interactive && System.currentTimeMillis() - lastSuccessAt < 30_000) {
                deliverPendingNotifications()
                return@withLock repository.cached() ?: NossaGentePromotionsResult.Error("Aguardando sincronização.")
            }
            val result = repository.fetchPromotions(forceRefresh = interactive) { active ->
                mutableState.value = mutableState.value.copy(visibleNetwork = active || interactive)
            }
            when (result) {
                is NossaGentePromotionsResult.Success -> {
                    lastSuccessAt = System.currentTimeMillis()
                    failureCount = 0
                    retryAt = 0L
                    mutableState.value = mutableState.value.copy(error = null)
                    deliverPendingNotifications()
                }
                is NossaGentePromotionsResult.Error -> {
                    deferRetry()
                    mutableState.value = mutableState.value.copy(error = result.message.takeIf { interactive || !repository.isInitialized() })
                }
                NossaGentePromotionsResult.Unauthorized -> {
                    deferRetry()
                    mutableState.value = mutableState.value.copy(error = "Acesso às ofertas indisponível.")
                }
            }
            result
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            deferRetry()
            val message = "Não foi possível sincronizar agora. A lista salva foi mantida."
            mutableState.value = mutableState.value.copy(error = message.takeIf { interactive || !repository.isInitialized() })
            NossaGentePromotionsResult.Error(message)
        } finally {
            mutableState.value = mutableState.value.copy(running = false, visibleNetwork = false, attempted = true)
        }
    }

    private fun deferRetry() {
        failureCount = (failureCount + 1).coerceAtMost(5)
        retryAt = System.currentTimeMillis() + (60_000L shl (failureCount - 1)).coerceAtMost(15 * 60_000L)
    }

    private suspend fun deliverPendingNotifications() {
        val pending = repository.dao.outbox()
        if (pending.isEmpty()) return
        val preferences = UserPreferences(context)
        val enabled = preferences.notificationsEnabled.first()
        // Foreground notifications also matter: do not discard new offers while the app is open.
        if (!enabled) {
            pending.forEach { repository.dao.deleteMetadata(it.key) }
            return
        }
        val favorite = preferences.favoriteStoreCode.first()?.trim().orEmpty()
        if (favorite.isBlank()) {
            // No chosen store: do not notify about unrelated stores.
            pending.forEach { repository.dao.deleteMetadata(it.key) }
            return
        }
        val remote = FirebaseService.getNotificationSettingsOrNull() ?: return // Retry on a later worker if config is unavailable.
        val current = repository.cached()?.promotions.orEmpty().associateBy { it.id }
        for (event in pending) {
            val json = JSONObject(event.value)
            val ids = json.getJSONArray("ids")
            val products = (0 until ids.length()).mapNotNull { current[ids.getString(it)] }
                .filter { it.products.any { product -> matchesFavoriteStore(favorite, product.storeCode) } }
            if (products.isNotEmpty() && remote.enabled && remote.promotionUpdatedEnabled) {
                val count = products.size
                val suffix = " em ${StoreCatalog.nameFor(favorite)}"
                val title = if (count == 1) "Nova oferta$suffix" else "Novas ofertas$suffix"
                val body = if (count == 1) "1 produto entrou em oferta. Toque para ver em Promoções." else "$count produtos entraram em oferta. Toque para ver em Promoções."
                val timestamp = json.getLong("createdAt")
                val notificationId = java.util.UUID.fromString(event.key.removePrefix("outbox_")).mostSignificantBits
                preferences.addNotification(AppNotification(id = notificationId, type = "PROMOTION_UPDATED", title = title,
                    body = body, read = false, timestamp = timestamp))
                NotificationHelper.showNotification(context, "PROMOTION_UPDATED", title, body, notificationTag = event.key)
            }
            // Stable Android tag + history ID make retries idempotent if the process stops before acknowledgment.
            repository.dao.deleteMetadata(event.key)
        }
    }

    companion object {
        @Volatile private var instance: PromotionSyncCoordinator? = null
        fun get(context: Context): PromotionSyncCoordinator = instance ?: synchronized(this) {
            instance ?: PromotionSyncCoordinator(context).also { instance = it }
        }
    }
}

/** Requires an explicitly selected favorite store: a generic broadcast must never notify every store. */
internal fun matchesFavoriteStore(favorite: String?, offerStore: String?): Boolean =
    !favorite.isNullOrBlank() && favorite == offerStore
