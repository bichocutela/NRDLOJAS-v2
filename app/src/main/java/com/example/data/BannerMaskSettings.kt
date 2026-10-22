package com.example.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest

data class BannerMaskSettings(
    val style: String = STYLE_SOFT,
    val strength: Float = 0f,
    val depth: Float = 0.22f,
    val shadeTop: Boolean = false,
    val shadeBottom: Boolean = true,
    val shadeLeft: Boolean = false,
    val shadeRight: Boolean = false
) {
    fun normalized(): BannerMaskSettings = copy(
        style = style.takeIf { it in SUPPORTED_STYLES } ?: STYLE_SOFT,
        strength = strength.coerceIn(0f, 1f),
        depth = depth.coerceIn(0.08f, 0.45f)
    )

    fun hasVisibleShade(): Boolean =
        strength > 0.005f && (shadeTop || shadeBottom || shadeLeft || shadeRight)

    fun toFirestoreMap(): Map<String, Any> {
        val safe = normalized()
        return mapOf(
            "style" to safe.style,
            "strength" to safe.strength.toDouble(),
            "depth" to safe.depth.toDouble(),
            "shadeTop" to safe.shadeTop,
            "shadeBottom" to safe.shadeBottom,
            "shadeLeft" to safe.shadeLeft,
            "shadeRight" to safe.shadeRight
        )
    }

    companion object {
        const val STYLE_SOFT = "soft"
        const val STYLE_DEFINED = "defined"
        const val STYLE_DIFFUSE = "diffuse"

        val SUPPORTED_STYLES = setOf(STYLE_SOFT, STYLE_DEFINED, STYLE_DIFFUSE)

        fun fromFirestoreMap(data: Map<String, Any?>?): BannerMaskSettings {
            if (data == null) return BannerMaskSettings()
            return BannerMaskSettings(
                style = data["style"] as? String ?: STYLE_SOFT,
                strength = (data["strength"] as? Number)?.toFloat() ?: 0f,
                depth = (data["depth"] as? Number)?.toFloat() ?: 0.22f,
                shadeTop = data["shadeTop"] as? Boolean ?: false,
                shadeBottom = data["shadeBottom"] as? Boolean ?: true,
                shadeLeft = data["shadeLeft"] as? Boolean ?: false,
                shadeRight = data["shadeRight"] as? Boolean ?: false
            ).normalized()
        }
    }
}

object BannerMaskStore {
    private const val CONFIG_COLLECTION = "config"
    private const val DOCUMENT_PREFIX = "banner-mask-"

    fun observe(themeKey: String, backgroundUrl: String?): Flow<BannerMaskSettings> = callbackFlow {
        val documentId = documentId(themeKey, backgroundUrl)
        val registration = runCatching {
            FirebaseFirestore.getInstance()
                .collection(CONFIG_COLLECTION)
                .document(documentId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        trySend(BannerMaskSettings())
                        return@addSnapshotListener
                    }
                    trySend(BannerMaskSettings.fromFirestoreMap(snapshot?.data))
                }
        }.getOrElse {
            trySend(BannerMaskSettings())
            null
        }

        awaitClose { registration?.remove() }
    }

    suspend fun save(
        themeKey: String,
        backgroundUrl: String?,
        settings: BannerMaskSettings
    ): Boolean {
        return try {
            val payload = settings.toFirestoreMap().toMutableMap().apply {
                put("themeKey", themeKey.trim().lowercase())
                put("backgroundUrl", backgroundUrl.orEmpty().trim())
                put("updatedAt", FieldValue.serverTimestamp())
            }
            FirebaseFirestore.getInstance()
                .collection(CONFIG_COLLECTION)
                .document(documentId(themeKey, backgroundUrl))
                .set(payload, SetOptions.merge())
                .await()
            true
        } catch (error: Exception) {
            FirebaseService.lastError = error.message
            false
        }
    }

    internal fun documentId(themeKey: String, backgroundUrl: String?): String {
        val rawKey = "${themeKey.trim().lowercase()}|${backgroundUrl.orEmpty().trim()}"
        val digest = MessageDigest.getInstance("SHA-256").digest(rawKey.toByteArray(Charsets.UTF_8))
        val compactHash = digest.take(12).joinToString(separator = "") { byte -> "%02x".format(byte) }
        return DOCUMENT_PREFIX + compactHash
    }
}
