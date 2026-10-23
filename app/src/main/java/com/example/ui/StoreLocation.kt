package com.example.ui

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

@SuppressLint("MissingPermission")
internal suspend fun storeLocation(context: Context): Pair<Double,Double>? = withContext(Dispatchers.Main) {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return@withContext null
    withTimeoutOrNull(15_000) {
        suspendCancellableCoroutine<Pair<Double,Double>?> { continuation ->
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    runCatching { manager.removeUpdates(this) }
                    if(continuation.isActive) continuation.resume(location.latitude to location.longitude)
                }
                override fun onProviderDisabled(provider: String) {
                    runCatching { manager.removeUpdates(this) }
                    if(continuation.isActive) continuation.resume(null)
                }
                override fun onProviderEnabled(provider: String) = Unit
                @Deprecated("Deprecated in Android") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            }
            continuation.invokeOnCancellation { runCatching { manager.removeUpdates(listener) } }
            try {
                if(!manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) continuation.resume(null)
                else manager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER,listener,Looper.getMainLooper())
            } catch(_: SecurityException) { if(continuation.isActive) continuation.resume(null) }
            catch(_: IllegalArgumentException) { if(continuation.isActive) continuation.resume(null) }
        }
    }
}
internal fun storeDistance(lat: Double, lon: Double, targetLat: Double, targetLon: Double): Double {
    val latitude = Math.toRadians(targetLat-lat); val longitude = Math.toRadians(targetLon-lon)
    val a = kotlin.math.sin(latitude/2)*kotlin.math.sin(latitude/2) + kotlin.math.cos(Math.toRadians(lat))*kotlin.math.cos(Math.toRadians(targetLat))*kotlin.math.sin(longitude/2)*kotlin.math.sin(longitude/2)
    return 6371.0 * 2 * kotlin.math.asin(kotlin.math.sqrt(a.coerceIn(0.0,1.0)))
}
