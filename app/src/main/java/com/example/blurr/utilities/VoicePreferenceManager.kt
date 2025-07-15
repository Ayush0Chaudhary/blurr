package com.example.blurr.utilities

import android.content.Context
import android.content.SharedPreferences
import com.example.blurr.api.TTSVoice
import androidx.core.content.edit

object VoicePreferenceManager {
    private const val PREFS_NAME = "TTSVoiceSettings"
    private const val KEY_SELECTED_VOICE = "selected_voice"

    fun getSelectedVoice(context: Context): TTSVoice {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val selectedVoiceName = sharedPreferences.getString(KEY_SELECTED_VOICE, TTSVoice.CHIRP_ZEPHYR.name)
        return TTSVoice.valueOf(selectedVoiceName ?: TTSVoice.CHIRP_ZEPHYR.name)
    }

    fun saveSelectedVoice(context: Context, voice: TTSVoice) {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPreferences.edit {
            putString(KEY_SELECTED_VOICE, voice.name)
        }
    }
} 