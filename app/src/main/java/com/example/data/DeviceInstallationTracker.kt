package com.example.data

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

    suspend fun register(context: Context): Boolean {
        if (!FirebaseService.isFirebaseConfigured()) return false
        val hash = deviceHash(context)
        if (hash.isBlank()) return false

        val firestore = FirebaseFirestore.getInstance()
        val reference = firestore.collection(COLLECTION).document(hash)
        return try {
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(reference)
                if (!snapshot.exists()) {
                    transaction.set(
                        reference,
                        mapOf(
                            "deviceIdHash" to hash,
                            "firstSeenAt" to FieldValue.serverTimestamp(),
                            "lastSeenAt" to FieldValue.serverTimestamp(),
                            "appVersion" to BuildConfig.VERSION_NAME,
                            "appVersionCode" to BuildConfig.VERSION_CODE,
                            "platform" to "android"
                        )
                    )
                    true
                } else {
                    transaction.update(
                        reference,
                        mapOf(
                            "lastSeenAt" to FieldValue.serverTimestamp(),
                            "appVersion" to BuildConfig.VERSION_NAME,
                            "appVersionCode" to BuildConfig.VERSION_CODE
                        )
                    )
                    false
                }
            }.await()
        } catch (_: Exception) {
            false
        }
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
