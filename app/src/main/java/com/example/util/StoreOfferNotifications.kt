package com.example.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.data.NordestaoStoreOffer
import com.example.data.NordestaoStoreOffersSnapshot
import com.example.data.UserPreferences
import kotlinx.coroutines.flow.first

/**
 * Per-device notifications for the OFFICIAL Nossas Lojas > Ofertas catalog.
 * No customer credentials, new server tables, FCM topics or database writes.
 */
object StoreOfferNotifications {
    enum class Mode(val label: String, val explanation: String) {
        OFF("Desativadas", "Não receber avisos de ofertas"),
        ABOVE_50("Acima de 50%", "Descontos maiores que 50% (padrão)"),
        ABOVE_60("Acima de 60%", "Descontos maiores que 60%"),
        BELOW_50("Abaixo de 50%", "Descontos de 1% até 49%"),
        BELOW_60("Abaixo de 60%", "Descontos de 1% até 59%"),
        CASHBACK("Produtos com cashback", "Ofertas que informam cashback")
    }

    private const val PREFS = "nrd_store_offer_notifications"
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // The first installation defaults to >50%, without silently granting Android permission.
    fun mode(context: Context): Mode = runCatching {
        Mode.valueOf(prefs(context).getString("mode", Mode.ABOVE_50.name)!!)
    }.getOrDefault(Mode.ABOVE_50)

    fun setMode(context: Context, selected: Mode) {
        prefs(context).edit().putString("mode", selected.name).apply()
    }

    fun matches(offer: NordestaoStoreOffer, selected: Mode): Boolean = when (selected) {
        Mode.OFF -> false
        Mode.ABOVE_50 -> offer.discount > 50
        Mode.ABOVE_60 -> offer.discount > 60
        Mode.BELOW_50 -> offer.discount in 1 until 50
        Mode.BELOW_60 -> offer.discount in 1 until 60
        Mode.CASHBACK -> offer.cashback.isNotBlank() && offer.cashback != "0"
    }

    private fun fingerprint(offer: NordestaoStoreOffer): String =
        offer.id + ":" + offer.priceCents + ":" + offer.discount + ":" + offer.cashback

    /**
     * The initial fresh catalog becomes a baseline, not a storm of old notifications.
     * The history is independent for each store and selected filter. A stale or
     * failed response must never advance that history.
     */
    internal fun newlyMatching(context: Context, snapshot: NordestaoStoreOffersSnapshot): List<NordestaoStoreOffer> {
        if (snapshot.stale || snapshot.storeId <= 0) return emptyList()
        return synchronized(this) {
            val selected = mode(context)
            if (selected == Mode.OFF) return@synchronized emptyList()
            val storage = prefs(context)
            val key = "seen_" + snapshot.storeId + "_" + selected.name
            val initializedKey = "initialized_" + snapshot.storeId + "_" + selected.name
            val previous = storage.getStringSet(key, emptySet()).orEmpty().toSet()
            val offers = snapshot.offers.filter { it.active() && matches(it, selected) }
            val firstObservation = !storage.getBoolean(initializedKey, false)
            val fresh = if (firstObservation) emptyList() else offers.filter { fingerprint(it) !in previous }
            val updated = (previous + offers.map(::fingerprint)).toList().takeLast(2048).toSet()
            storage.edit().putStringSet(key, updated).putBoolean(initializedKey, true).commit()
            fresh
        }
    }

    suspend fun notifyIfNew(context: Context, snapshot: NordestaoStoreOffersSnapshot) {
        if (mode(context) == Mode.OFF || !UserPreferences(context).notificationsEnabled.first()) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val newOffers = newlyMatching(context, snapshot)
        if (newOffers.isEmpty()) return
        val selected = mode(context)
        val top = newOffers.sortedWith(compareByDescending<NordestaoStoreOffer> { it.discount }.thenBy { it.description })
        val title = if (selected == Mode.CASHBACK) "Novas ofertas com cashback"
            else "Novas ofertas — " + selected.label.lowercase()
        val names = top.take(2).joinToString("; ") { it.description }
        val body = newOffers.size.toString() + (if (newOffers.size == 1) " oferta na sua loja: " else " ofertas na sua loja: ") +
            names + (if (newOffers.size > 2) " e outras." else ".")
        NotificationHelper.showNotification(context, "STORE_OFFER_ALERT", title, body)
    }
}
