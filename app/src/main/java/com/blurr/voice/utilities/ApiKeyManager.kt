package com.blurr.voice.utilities

import android.content.Context
import com.blurr.voice.BuildConfig

class ApiKeyManager private constructor(context: Context) {

    private val geminiKeyManager = GeminiKeyManager(context)
    private val defaultApiKeys: List<String> = if (BuildConfig.GEMINI_API_KEYS.isNotEmpty()) {
        BuildConfig.GEMINI_API_KEYS.split(",")
    } else {
        emptyList()
    }

    private var keyIndex = 0

    fun useCustomKeys(): Boolean {
        return geminiKeyManager.useCustomKeys() && geminiKeyManager.getKeys().isNotEmpty()
    }

    fun getNextKey(): String? {
        if (useCustomKeys()) {
            return geminiKeyManager.getNextKey()
        }

        if (defaultApiKeys.isEmpty()) {
            return null
        }

        val key = defaultApiKeys[keyIndex]
        keyIndex = (keyIndex + 1) % defaultApiKeys.size
        return key
    }

    companion object {
        @Volatile
        private var INSTANCE: ApiKeyManager? = null

        fun getInstance(context: Context): ApiKeyManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ApiKeyManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}