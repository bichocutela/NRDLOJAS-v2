package com.example.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.BuildConfig
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.Locale

data class DeviceInstallation(
    val id: String,
    val manufacturer: String,
    val model: String,
    val deviceName: String,
    val installedAt: Long,
    val lastSeenAt: Long,
    val city: String?,
    val state: String?,
    val latitude: Double?,
    val longitude: Double?
)

data class DeviceInstallationPage(
    val total: Int,
    val items: List<DeviceInstallation>,
    val nextPageToken: String?
)

object DeviceInstallationService {
    private val client = OkHttpClient()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun register(context: Context, includeLocation: Boolean) = withContext(Dispatchers.IO) {
        runCatching {
            val preferences = UserPreferences(context.applicationContext)
            val installationId = preferences.getOrCreateInstallationId()
            val location = if (includeLocation) lastKnownCoarseLocation(context) else null
            val address = location?.let { reverseGeocode(context, it) }
            val body = JSONObject()
                .put("action", "REGISTER")
                .put("installationId", installationId)
                .put("manufacturer", Build.MANUFACTURER.orEmpty())
                .put("model", Build.MODEL.orEmpty())
                .put("deviceName", listOf(Build.MANUFACTURER, Build.MODEL).filter { it.isNotBlank() }.joinToString(" "))
                .put("androidVersion", Build.VERSION.RELEASE.orEmpty())
                .put("appVersion", BuildConfig.VERSION_NAME)
            if (location != null) {
                body.put("latitude", location.latitude)
                body.put("longitude", location.longitude)
            }
            address?.first?.takeIf { it.isNotBlank() }?.let { body.put("city", it) }
            address?.second?.takeIf { it.isNotBlank() }?.let { body.put("state", it) }
            execute(body, firebaseToken = null)
        }.onFailure {
            android.util.Log.w("DeviceInstallations", "Não foi possível atualizar a base instalada", it)
        }
    }

    suspend fun reauthenticateMaster(password: String): Boolean {
        val user = FirebaseAuth.getInstance().currentUser ?: return false
        val email = user.email?.lowercase() ?: return false
        if (email != "mestre@nrdlojas.com" || password.isBlank()) return false
        return runCatching {
            user.reauthenticate(EmailAuthProvider.getCredential(email, password)).await()
            true
        }.getOrDefault(false)
    }

    suspend fun loadPage(pageToken: String? = null, pageSize: Int = 25): DeviceInstallationPage? =
        withContext(Dispatchers.IO) {
            runCatching {
                val token = FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token
                    ?: return@runCatching null
                val body = JSONObject()
                    .put("action", "LIST")
                    .put("pageSize", pageSize.coerceIn(1, 25))
                pageToken?.takeIf { it.isNotBlank() }?.let { body.put("pageToken", it) }
                val root = execute(body, token) ?: return@runCatching null
                val itemsJson = root.optJSONArray("items") ?: return@runCatching null
                val items = (0 until itemsJson.length()).mapNotNull { index ->
                    val item = itemsJson.optJSONObject(index) ?: return@mapNotNull null
                    DeviceInstallation(
                        id = item.optString("id"),
                        manufacturer = item.optString("manufacturer"),
                        model = item.optString("model"),
                        deviceName = item.optString("deviceName"),
                        installedAt = item.optLong("installedAt"),
                        lastSeenAt = item.optLong("lastSeenAt"),
                        city = item.optString("city").takeIf { it.isNotBlank() },
                        state = item.optString("state").takeIf { it.isNotBlank() },
                        latitude = item.optDouble("latitude").takeUnless { it.isNaN() },
                        longitude = item.optDouble("longitude").takeUnless { it.isNaN() }
                    )
                }
                DeviceInstallationPage(
                    total = root.optInt("total", items.size),
                    items = items,
                    nextPageToken = root.optString("nextPageToken").takeIf { it.isNotBlank() }
                )
            }.onFailure {
                android.util.Log.e("DeviceInstallations", "Não foi possível carregar a base instalada", it)
            }.getOrNull()
        }

    private fun execute(body: JSONObject, firebaseToken: String?): JSONObject? {
        val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
        if (baseUrl.isBlank()) return null
        val request = Request.Builder()
            .url(baseUrl + "/functions/v1/device-installations")
            .post(body.toString().toRequestBody(jsonMediaType))
            .addHeader("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY)
            .apply {
                firebaseToken?.let { addHeader("x-firebase-token", it) }
            }
            .build()
        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("HTTP " + response.code)
            return JSONObject(responseBody)
        }
    }

    private fun lastKnownCoarseLocation(context: Context): Location? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return manager.getProviders(true)
            .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time }
    }

    @Suppress("DEPRECATION")
    private fun reverseGeocode(context: Context, location: Location): Pair<String, String>? {
        if (!Geocoder.isPresent()) return null
        val address = runCatching {
            Geocoder(context, Locale("pt", "BR"))
                .getFromLocation(location.latitude, location.longitude, 1)
                ?.firstOrNull()
        }.getOrNull() ?: return null
        val city = address.locality ?: address.subAdminArea ?: ""
        val state = address.adminArea.orEmpty()
        return city to state
    }
}
