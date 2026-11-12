package com.example.data

import android.content.Context
import com.example.security.SecurityProvider

class KeyManager(context: Context) {

    private val securityProvider = SecurityProvider(context)

    fun saveApiKey(apiKey: String) {
        securityProvider.saveGeminiApiKey(apiKey)
    }

    fun getApiKey(): String? {
        val keyFromProvider = securityProvider.getGeminiApiKey()
        if (!keyFromProvider.isNullOrBlank()) {
            return keyFromProvider
        }
        return getBuildConfigFallbackKey()
    }

    private fun getBuildConfigFallbackKey(): String? {
        return try {
            val buildConfigClass = Class.forName("com.example.BuildConfig")
            val field = buildConfigClass.getField("GEMINI_API_KEY")
            val key = field.get(null) as? String
            if (!key.isNull_or_blank_key()) key else null
        } catch (e: Exception) {
            null
        }
    }

    private fun String?.isNull_or_blank_key(): Boolean {
        return this == null || this.isBlank() || this == "MY_GEMINI_API_KEY"
    }

    fun clearApiKey() {
        securityProvider.clearGeminiApiKey()
    }
}
