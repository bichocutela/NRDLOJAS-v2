package com.example.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Persiste somente o token de sessão do Nossa Gente, cifrado com uma chave AES
 * mantida pelo Android Keystore. CPF e senha nunca são gravados no aparelho.
 */
internal class NossaGenteSecureSession(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun saveToken(token: String) {
        if (token.isBlank()) {
            clear()
            return
        }

        runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
            val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
            val encoded = listOf(
                Base64.encodeToString(cipher.iv, Base64.NO_WRAP),
                Base64.encodeToString(encrypted, Base64.NO_WRAP)
            ).joinToString(SEPARATOR)
            preferences.edit().putString(KEY_ENCRYPTED_TOKEN, encoded).apply()
        }.onFailure {
            clear()
        }
    }

    fun readToken(): String? {
        val stored = preferences.getString(KEY_ENCRYPTED_TOKEN, null)
            ?.takeIf { it.isNotBlank() }
            ?: return null

        return runCatching {
            val parts = stored.split(SEPARATOR, limit = 2)
            require(parts.size == 2)
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val encrypted = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateSecretKey(),
                GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            )
            String(cipher.doFinal(encrypted), Charsets.UTF_8).takeIf { it.isNotBlank() }
        }.getOrElse {
            clear()
            null
        }
    }

    fun clear() {
        preferences.edit().remove(KEY_ENCRYPTED_TOKEN).apply()
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
            generateKey()
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "nossa_gente_secure_session"
        const val KEY_ENCRYPTED_TOKEN = "encrypted_session_token"
        const val KEY_ALIAS = "nrd_nossa_gente_session_key_v1"
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
        const val SEPARATOR = ":"
    }
}
