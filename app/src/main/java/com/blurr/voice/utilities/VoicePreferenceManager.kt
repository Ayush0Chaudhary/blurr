/**
 * @file VoicePreferenceManager.kt
 * @brief Manages the user's selected Text-to-Speech (TTS) voice preference.
 *
 * This file contains the `VoicePreferenceManager` object, a singleton utility for persisting
 * and retrieving the user's chosen TTS voice using Android's SharedPreferences.
 */
package com.blurr.voice.utilities

import android.content.Context
import com.blurr.voice.api.TTSVoice

/**
 * A singleton object to manage saving and retrieving the user's preferred TTS voice.
 *
 * This utility uses `SharedPreferences` to persist the user's choice of voice, ensuring it
 * is remembered across application sessions. It provides simple methods to get the currently
 * selected voice or save a new one.
 */
object VoicePreferenceManager {
    /** The name of the SharedPreferences file, consistent with SettingsActivity. */
    private const val PREFS_NAME = "BlurrSettings"

    /** The key used to store the selected voice's name in SharedPreferences. */
    private const val KEY_SELECTED_VOICE = "selected_voice"

    /**
     * Retrieves the user's selected TTS voice from SharedPreferences.
     *
     * If no voice has been previously selected, it returns a default voice
     * ([TTSVoice.CHIRP_LAOMEDEIA]).
     *
     * @param context The application context, used to access SharedPreferences.
     * @return The selected [TTSVoice], or the default if none is set.
     */
    fun getSelectedVoice(context: Context): TTSVoice {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val selectedVoiceName = sharedPreferences.getString(KEY_SELECTED_VOICE, TTSVoice.CHIRP_LAOMEDEIA.name)
        return TTSVoice.valueOf(selectedVoiceName ?: TTSVoice.CHIRP_LAOMEDEIA.name)
    }

    /**
     * Saves the user's selected TTS voice to SharedPreferences.
     *
     * @param context The application context, used to access SharedPreferences.
     * @param voice The [TTSVoice] to save as the user's preference.
     */
    fun saveSelectedVoice(context: Context, voice: TTSVoice) {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPreferences.edit()
            .putString(KEY_SELECTED_VOICE, voice.name)
            .apply()
    }
}