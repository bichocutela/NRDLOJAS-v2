package com.example.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import java.util.UUID
import android.content.Context
import android.provider.Settings
import com.example.BuildConfig
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class DeviceInstallationSummary(
    val totalCount: Int,
    val activeCount: Int,
    val lastInstallationAt: Long?
)

data class DeviceInstallationRecord(
    val deviceIdHash: String,
    val firstSeenAt: Long
)

sealed interface DeviceInstallationSummaryResult {
    data class Success(val summary: DeviceInstallationSummary) : DeviceInstallationSummaryResult
    data class Error(val message: String) : DeviceInstallationSummaryResult
}

object DeviceInstallationTracker {
    private const val COLLECTION = "app_installations"
    private val activeWindowMs = TimeUnit.DAYS.toMillis(7)

    private val registrationMutex = Mutex()
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS).callTimeout(60, TimeUnit.SECONDS).build()

    /** Register immediately; a persisted capability lets retries notify only this immutable event. */
    suspend fun register(context: Context): Boolean = registrationMutex.withLock {
        if (!FirebaseService.isFirebaseConfigured()) return@withLock false
        val hash = deviceHash(context)
        if (hash.isBlank()) return@withLock false
        val prefs = context.getSharedPreferences("installation_delivery", Context.MODE_PRIVATE)
        val versionKey = "$hash:${BuildConfig.VERSION_CODE}"
        val acknowledged = prefs.getBoolean("ack:$versionKey", false)
        val proof = if (acknowledged) null else (prefs.getString("proof:$versionKey", null)
            ?: (UUID.randomUUID().toString() + UUID.randomUUID().toString()).replace("-", "").also {
                if (!prefs.edit().putString("proof:$versionKey", it).commit()) return@withLock false
            })
        val proofHash = proof?.let { value -> MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) } }
        val firestore = FirebaseFirestore.getInstance()
        val reference = firestore.collection(COLLECTION).document(hash)
        val eventPublished = prefs.getBoolean("published:$versionKey", false)
        val registered = runCatching {
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(reference)
                val firstSeen: Any = snapshot.getTimestamp("firstSeenAt") ?: FieldValue.serverTimestamp()
                val fields = mapOf("lastSeenAt" to FieldValue.serverTimestamp(),
                    "appVersion" to BuildConfig.VERSION_NAME, "appVersionCode" to BuildConfig.VERSION_CODE)
                if (!snapshot.exists()) transaction.set(reference, fields + mapOf(
                    "deviceIdHash" to hash, "firstSeenAt" to firstSeen, "platform" to "android"))
                else transaction.update(reference, fields)
                if (proofHash != null && !eventPublished) {
                    transaction.set(firestore.collection("app_installation_events").document(proofHash), mapOf(
                        "deviceIdHash" to hash, "appVersion" to BuildConfig.VERSION_NAME,
                        "appVersionCode" to BuildConfig.VERSION_CODE,
                        "firstSeenAt" to firstSeen, "createdAt" to FieldValue.serverTimestamp()))
                }
            }.await()
            if (proofHash != null) prefs.edit().putBoolean("published:$versionKey", true).commit()
            true
        }.getOrElse { if (it is CancellationException) throw it; false }
        if (proof == null) return@withLock registered
        // Also retry the server after an interrupted local commit: the event may already exist.
        val delivered = runCatching {
            withContext(Dispatchers.IO) {
                val request = Request.Builder().url(BuildConfig.SUPABASE_URL.trimEnd('/') + "/functions/v1/nrd-installation-event")
                    .header("x-installation-proof", proof).header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                    .post("{}".toRequestBody("application/json".toMediaType())).build()
                client.newCall(request).execute().use { response ->
                    response.isSuccessful && JSONObject(response.body?.string().orEmpty()).optBoolean("delivered")
                }
            }
        }.getOrElse { if (it is CancellationException) throw it; false }
        if (delivered) prefs.edit().putBoolean("ack:$versionKey", true)
            .remove("proof:$versionKey").remove("published:$versionKey").commit()
        delivered
    }

    suspend fun fetchSummary(nowMillis: Long = System.currentTimeMillis()): DeviceInstallationSummaryResult {
        if (!FirebaseService.isFirebaseConfigured()) {
            return DeviceInstallationSummaryResult.Error("Firebase não configurado.")
        }
        return try {
            val snapshot = FirebaseFirestore.getInstance().collection(COLLECTION).get().await()
            val cutoff = nowMillis - activeWindowMs
            val active = snapshot.documents.count { document ->
                (document.getTimestamp("lastSeenAt")?.toDate()?.time ?: 0L) >= cutoff
            }
            val lastInstallation = snapshot.documents.maxOfOrNull { document ->
                document.getTimestamp("firstSeenAt")?.toDate()?.time ?: 0L
            }?.takeIf { it > 0L }

            DeviceInstallationSummaryResult.Success(
                DeviceInstallationSummary(
                    totalCount = snapshot.size(),
                    activeCount = active,
                    lastInstallationAt = lastInstallation
                )
            )
        } catch (_: Exception) {
            DeviceInstallationSummaryResult.Error(
                "Não foi possível consultar as instalações. Verifique a conexão e as permissões do Firebase."
            )
        }
    }

    suspend fun installationsAfter(timestamp: Long): List<DeviceInstallationRecord> {
        if (!FirebaseService.isFirebaseConfigured()) return emptyList()
        return try {
            FirebaseFirestore.getInstance()
                .collection(COLLECTION)
                .whereGreaterThan("firstSeenAt", java.util.Date(timestamp))
                .get()
                .await()
                .documents
                .mapNotNull { document ->
                    val firstSeen = document.getTimestamp("firstSeenAt")?.toDate()?.time
                        ?: return@mapNotNull null
                    DeviceInstallationRecord(
                        deviceIdHash = document.getString("deviceIdHash") ?: document.id,
                        firstSeenAt = firstSeen
                    )
                }
                .sortedBy { it.firstSeenAt }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun deviceHash(context: Context): String {
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ).orEmpty()
        if (androidId.isBlank()) return ""
        val digest = MessageDigest.getInstance("SHA-256")
            .digest((context.packageName + ":" + androidId).toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
