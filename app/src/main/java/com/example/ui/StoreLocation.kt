package com.example.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

internal enum class StoreLocationFailure(val message: String) {
    PERMISSION_DENIED("Permita o acesso à localização nas configurações do NRD para encontrar sua posição."),
    DISABLED("A localização do aparelho está desligada. Ative-a e toque novamente em Minha localização."),
    UNAVAILABLE("O Android não conseguiu fornecer uma posição. Tente novamente ou permita localização precisa para usar o GPS."),
    TIMEOUT("A busca demorou sem encontrar sua posição. Tente perto de uma janela ou em local aberto e tente novamente.")
}
internal sealed interface StoreLocationResult {
    data class Found(val point: Pair<Double,Double>) : StoreLocationResult
    data class Error(val reason: StoreLocationFailure) : StoreLocationResult
}
internal interface StoreLocationSource {
    fun permitted(): Boolean
    fun enabled(): Boolean
    fun providers(): List<String>
    fun cached(): List<Location>
    fun request(provider: String, callback: (Location?) -> Unit): () -> Unit
}
internal fun storeLocationProviders(enabled: List<String>, fine: Boolean): List<String> =
    listOf("fused", LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
        .filter { it in enabled && (it != LocationManager.GPS_PROVIDER || fine) }

internal fun recentStoreLocation(location: Location, nowNanos: Long): Boolean {
    val age = nowNanos - location.elapsedRealtimeNanos
    return location.elapsedRealtimeNanos > 0 && age in 0..120_000_000_000L &&
        location.latitude.isFinite() && location.longitude.isFinite() &&
        kotlin.math.abs(location.latitude) <= 90 && kotlin.math.abs(location.longitude) <= 180
}
internal suspend fun resolveStoreLocation(source: StoreLocationSource, timeoutMs: Long = 30_000,
    nowNanos: () -> Long = { SystemClock.elapsedRealtimeNanos() }): StoreLocationResult {
    if(!source.permitted()) return StoreLocationResult.Error(StoreLocationFailure.PERMISSION_DENIED)
    if(!source.enabled()) return StoreLocationResult.Error(StoreLocationFailure.DISABLED)
    source.cached().filter { recentStoreLocation(it, nowNanos()) }.maxByOrNull { it.elapsedRealtimeNanos }?.let {
        return StoreLocationResult.Found(it.latitude to it.longitude)
    }
    val providers = source.providers().distinct()
    if(providers.isEmpty()) return StoreLocationResult.Error(StoreLocationFailure.UNAVAILABLE)
    return withTimeoutOrNull(timeoutMs) {
        suspendCancellableCoroutine<StoreLocationResult> { continuation ->
            val cancellations = mutableListOf<() -> Unit>()
            fun cleanup() { cancellations.toList().forEach { runCatching { it() } }; cancellations.clear() }
            fun finish(result: StoreLocationResult) {
                if(continuation.isActive) { cleanup(); continuation.resume(result) }
            }
            var remaining = providers.size
            fun receive(location: Location?) {
                if(!continuation.isActive) return
                if(location != null && recentStoreLocation(location, nowNanos()))
                    finish(StoreLocationResult.Found(location.latitude to location.longitude))
                else if(--remaining == 0) finish(StoreLocationResult.Error(
                    if(source.permitted()) StoreLocationFailure.UNAVAILABLE else StoreLocationFailure.PERMISSION_DENIED))
            }
            continuation.invokeOnCancellation { cleanup() }
            for(provider in providers) {
                if(!continuation.isActive) break
                try {
                    val cancel = source.request(provider, ::receive)
                    // A cached provider can call back synchronously before registration returns.
                    if(continuation.isActive) cancellations.add(cancel) else runCatching { cancel() }
                } catch(_: RuntimeException) { receive(null) }
            }
        }
    } ?: StoreLocationResult.Error(StoreLocationFailure.TIMEOUT)
}
@SuppressLint("MissingPermission")
internal suspend fun storeLocation(context: Context): StoreLocationResult = withContext(Dispatchers.Main) {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        ?: return@withContext StoreLocationResult.Error(StoreLocationFailure.UNAVAILABLE)
    fun allowed(permission: String) = ContextCompat.checkSelfPermission(context,permission) == PackageManager.PERMISSION_GRANTED
    val source = object : StoreLocationSource {
        override fun permitted() = allowed(Manifest.permission.ACCESS_COARSE_LOCATION) || allowed(Manifest.permission.ACCESS_FINE_LOCATION)
        override fun enabled() = LocationManagerCompat.isLocationEnabled(manager)
        override fun providers() = storeLocationProviders(manager.getProviders(true), allowed(Manifest.permission.ACCESS_FINE_LOCATION))
        override fun cached() = (providers() + LocationManager.PASSIVE_PROVIDER).distinct()
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
        override fun request(provider: String, callback: (Location?) -> Unit): () -> Unit {
            val signal = CancellationSignal()
            try {
                LocationManagerCompat.getCurrentLocation(manager, provider, signal, ContextCompat.getMainExecutor(context)) { callback(it) }
            } catch(error: RuntimeException) { signal.cancel(); throw error }
            return { signal.cancel() }
        }
    }
    resolveStoreLocation(source)
}
internal fun storeDistance(lat: Double, lon: Double, targetLat: Double, targetLon: Double): Double {
    val latitude = Math.toRadians(targetLat-lat); val longitude = Math.toRadians(targetLon-lon)
    val a = kotlin.math.sin(latitude/2)*kotlin.math.sin(latitude/2) + kotlin.math.cos(Math.toRadians(lat))*kotlin.math.cos(Math.toRadians(targetLat))*kotlin.math.sin(longitude/2)*kotlin.math.sin(longitude/2)
    return 6371.0 * 2 * kotlin.math.asin(kotlin.math.sqrt(a.coerceIn(0.0,1.0)))
}
