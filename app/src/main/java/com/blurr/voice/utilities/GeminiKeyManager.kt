package com.blurr.voice.utilities

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

class GeminiKeyManager(context: Context) {

    private val prefs: SharedPreferences

    init {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        prefs = EncryptedSharedPreferences.create(
            PREFS_NAME,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun addKey(key: String) {
        val keys = getKeys().toMutableSet()
        keys.add(key)
        prefs.edit().putStringSet(KEY_GEMINI_KEYS, keys).apply()
    }

    fun getKeys(): List<String> {
        return prefs.getStringSet(KEY_GEMINI_KEYS, emptySet())?.toList() ?: emptyList()
    }

    fun deleteKey(key: String) {
        val keys = getKeys().toMutableSet()
        if (keys.remove(key)) {
            prefs.edit().putStringSet(KEY_GEMINI_KEYS, keys).apply()
        }
    }

    fun setUseCustomKeys(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_USE_CUSTOM_KEYS, enabled).apply()
    }

    fun useCustomKeys(): Boolean {
        return prefs.getBoolean(KEY_USE_CUSTOM_KEYS, false)
    }

    fun getNextKey(): String? {
        val keys = getKeys()
        if (keys.isEmpty()) {
            return null
        }
        val lastUsedIndex = prefs.getInt(KEY_LAST_USED_INDEX, -1)
        val nextIndex = (lastUsedIndex + 1) % keys.size
        prefs.edit().putInt(KEY_LAST_USED_INDEX, nextIndex).apply()
        return keys[nextIndex]
    }

    companion object {
        private const val PREFS_NAME = "GeminiKeyPrefs"
        private const val KEY_GEMINI_KEYS = "gemini_keys"
        private const val KEY_USE_CUSTOM_KEYS = "use_custom_keys"
        private const val KEY_LAST_USED_INDEX = "last_used_index"
    }
}