package com.example.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class NossaGenteSavedCredentials(
    val cpf: String,
    val password: String
)

class NossaGenteCredentialStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): NossaGenteSavedCredentials? {
        val encoded = preferences.getString(KEY_CREDENTIALS, null) ?: return null
        return runCatching {
            val packed = Base64.decode(encoded, Base64.NO_WRAP)
            require(packed.size > IV_LENGTH_BYTES)
            val iv = packed.copyOfRange(0, IV_LENGTH_BYTES)
            val encrypted = packed.copyOfRange(IV_LENGTH_BYTES, packed.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            val plainText = cipher.doFinal(encrypted).toString(StandardCharsets.UTF_8)
            val separator = plainText.indexOf(SEPARATOR)
            require(separator > 0)
            val cpf = plainText.substring(0, separator).filter(Char::isDigit).take(11)
            val password = plainText.substring(separator + 1)
            require(cpf.length == 11 && password.isNotEmpty())
            NossaGenteSavedCredentials(cpf = cpf, password = password)
        }.getOrElse {
            clear()
            null
        }
    }

    fun save(cpf: String, password: String) {
        val normalizedCpf = cpf.filter(Char::isDigit).take(11)
        require(normalizedCpf.length == 11)
        require(password.isNotEmpty())

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val plainText = "$normalizedCpf$SEPARATOR$password".toByteArray(StandardCharsets.UTF_8)
        val encrypted = cipher.doFinal(plainText)
        val packed = cipher.iv + encrypted
        val encoded = Base64.encodeToString(packed, Base64.NO_WRAP)
        preferences.edit().putString(KEY_CREDENTIALS, encoded).apply()
    }

    fun clear() {
        preferences.edit().remove(KEY_CREDENTIALS).apply()
    }

    fun isProfileEnabled(): Boolean = preferences.getBoolean(KEY_PROFILE_ENABLED, false)

    fun setProfileEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_PROFILE_ENABLED, enabled).apply()
    }

    fun isPromotionsEnabled(): Boolean = preferences.getBoolean(KEY_PROMOTIONS_ENABLED, true)

    fun setPromotionsEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_PROMOTIONS_ENABLED, enabled).apply()
    }

    fun isBenefitNotificationsEnabled(): Boolean = preferences.getBoolean(KEY_BENEFIT_NOTIFICATIONS, false)

    fun setBenefitNotificationsEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_BENEFIT_NOTIFICATIONS, enabled).apply()
    }

    fun isHoursNotificationsEnabled(): Boolean = preferences.getBoolean(KEY_HOURS_NOTIFICATIONS, false)

    fun setHoursNotificationsEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_HOURS_NOTIFICATIONS, enabled).apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private companion object {
        const val PREFERENCES_NAME = "nossa_gente_secure_credentials"
        const val KEY_CREDENTIALS = "credentials_v1"
        const val KEY_PROFILE_ENABLED = "profile_enabled_v1"
        const val KEY_PROMOTIONS_ENABLED = "promotions_enabled_v1"
        const val KEY_BENEFIT_NOTIFICATIONS = "benefit_notifications_v1"
        const val KEY_HOURS_NOTIFICATIONS = "hours_notifications_v1"
        const val KEY_ALIAS = "nrd_nossa_gente_credentials_v1"
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
        const val IV_LENGTH_BYTES = 12
        const val SEPARATOR = '\u0000'
    }
}
