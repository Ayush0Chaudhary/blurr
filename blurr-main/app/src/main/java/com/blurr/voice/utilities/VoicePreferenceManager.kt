package com.blurr.voice.utilities

import android.content.Context
import com.blurr.voice.api.TTSVoice

object VoicePreferenceManager {
    // FIX: Changed PREFS_NAME to match SettingsActivity for consistency.
    // This ensures both read/write to the same preferences file.
    private const val PREFS_NAME = "BlurrSettings" // THIS LINE WAS CHANGED

    private const val KEY_SELECTED_VOICE = "selected_voice"

    fun getSelectedVoice(context: Context): TTSVoice {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Ensure this default also matches your intended default (CHIRP_PUCK)
        val selectedVoiceName = sharedPreferences.getString(KEY_SELECTED_VOICE, TTSVoice.CHIRP_PUCK.name)

        return TTSVoice.valueOf(selectedVoiceName ?: TTSVoice.CHIRP_PUCK.name)
    }

    fun saveSelectedVoice(context: Context, voice: TTSVoice) {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPreferences.edit()
            .putString(KEY_SELECTED_VOICE, voice.name)
            .apply()
    }
}