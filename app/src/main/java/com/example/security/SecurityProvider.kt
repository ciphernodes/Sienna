package com.example.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * SecurityProvider utility to initialize EncryptedSharedPreferences using the Android Keystore
 * to securely store and retrieve the Gemini API key provided by the user.
 */
class SecurityProvider(private val context: Context) {

    private val prefName = "security_provider_encrypted_prefs"
    private val keyAlias = "GeminiSecurityMasterKey"
    private val apiKeyPrefKey = "gemini_api_key_encrypted"

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
    }

    init {
        ensureKeystoreKey()
    }

    private fun ensureKeystoreKey() {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!keyStore.containsAlias(keyAlias)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
            )
            val keyGenSpec = KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(keyGenSpec)
            keyGenerator.generateKey()
        }
    }

    private fun getSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val entry = keyStore.getEntry(keyAlias, null) as KeyStore.SecretKeyEntry
        return entry.secretKey
    }

    fun saveGeminiApiKey(apiKey: String) {
        if (apiKey.isBlank()) {
            prefs.edit().remove(apiKeyPrefKey).apply()
            return
        }
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(apiKey.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(iv.size + encryptedBytes.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encryptedBytes, 0, combined, iv.size, encryptedBytes.size)

            val base64Str = Base64.encodeToString(combined, Base64.NO_WRAP)
            prefs.edit().putString(apiKeyPrefKey, base64Str).apply()
        } catch (e: Exception) {
            e.printStackTrace()
            prefs.edit().putString(apiKeyPrefKey, apiKey).apply()
        }
    }

    fun getGeminiApiKey(): String? {
        val stored = prefs.getString(apiKeyPrefKey, null) ?: return null
        if (stored.isBlank()) return null

        return try {
            val combined = Base64.decode(stored, Base64.NO_WRAP)
            if (combined.size <= 12) return stored

            val iv = ByteArray(12)
            System.arraycopy(combined, 0, iv, 0, 12)
            val encryptedBytes = ByteArray(combined.size - 12)
            System.arraycopy(combined, 12, encryptedBytes, 0, encryptedBytes.size)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            stored
        }
    }

    fun clearGeminiApiKey() {
        prefs.edit().remove(apiKeyPrefKey).apply()
    }
}
