package com.blurr.voice.utilities

import android.content.Context
import com.blurr.voice.api.TTSVoice

object VoicePreferenceManager {
    private const val PREFS_NAME = "BlurrSettings"
    private const val KEY_SELECTED_VOICE = "selected_voice"
    private const val KEY_CUSTOM_LLM_BASE_URL = "custom_llm_base_url"
    private const val KEY_CUSTOM_LLM_API_KEY = "custom_llm_api_key"

    fun getSelectedVoice(context: Context): TTSVoice {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val selectedVoiceName = sharedPreferences.getString(KEY_SELECTED_VOICE, TTSVoice.CHIRP_LAOMEDEIA.name)
        return TTSVoice.valueOf(selectedVoiceName ?: TTSVoice.CHIRP_LAOMEDEIA.name)
    }

    fun saveSelectedVoice(context: Context, voice: TTSVoice) {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPreferences.edit().putString(KEY_SELECTED_VOICE, voice.name).apply()
    }

    fun getCustomLlmBaseUrl(context: Context): String? {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sharedPreferences.getString(KEY_CUSTOM_LLM_BASE_URL, null)
    }

    fun saveCustomLlmBaseUrl(context: Context, baseUrl: String) {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPreferences.edit().putString(KEY_CUSTOM_LLM_BASE_URL, baseUrl).apply()
    }

    fun getCustomLlmApiKey(context: Context): String? {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sharedPreferences.getString(KEY_CUSTOM_LLM_API_KEY, null)
    }

    fun saveCustomLlmApiKey(context: Context, apiKey: String) {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPreferences.edit().putString(KEY_CUSTOM_LLM_API_KEY, apiKey).apply()
    }
}