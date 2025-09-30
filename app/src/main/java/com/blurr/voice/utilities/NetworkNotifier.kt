/**
 * @file NetworkNotifier.kt
 * @brief Provides a utility for notifying the user about network connectivity issues.
 *
 * This file contains the `NetworkNotifier` object, which is responsible for showing a
 * user-facing toast notification and speaking a TTS message when the device is offline.
 * It includes debounce logic to prevent spamming the user with repeated notifications.
 */
package com.blurr.voice.utilities

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import android.util.Log
import com.blurr.voice.MyApplication

/**
 * A utility object that notifies the user when the application is offline.
 *
 * This notifier shows a toast message and uses Text-to-Speech (TTS) to audibly inform
 * the user about the lack of internet connection. It is debounced to avoid showing
 * multiple notifications in a short period.
 */
object NetworkNotifier {

    private const val TAG = "NetworkNotifier"
    /** The minimum time interval in milliseconds between consecutive notifications. */
    private const val MIN_INTERVAL_MS = 10_000L // 10 seconds
    /** The timestamp of the last notification, used for debouncing. Marked as volatile for thread safety. */
    @Volatile private var lastNotifiedAt: Long = 0L

    /**
     * Shows a user-facing offline notification (toast) and speaks a short TTS message.
     *
     * This function is debounced. If called multiple times within the `MIN_INTERVAL_MS`,
     * subsequent calls will be ignored.
     *
     * @param message The text-to-speech message to be spoken. Defaults to a standard offline message.
     */
    suspend fun notifyOffline(message: String = defaultMessage) {
        val now = System.currentTimeMillis()
        if (now - lastNotifiedAt < MIN_INTERVAL_MS) {
            Log.d(TAG, "Skipping offline notify due to debounce interval")
            return
        }
        lastNotifiedAt = now

        val context = MyApplication.appContext

        // Show a toast popup on the main thread
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(
                context,
                "No internet connection. Panda won’t be able to help right now.",
                Toast.LENGTH_LONG
            ).show()
        }

        try {
            // Speak out loud via TTS
            val tts = TTSManager.getInstance(context)
            tts.speakText(message)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to speak offline message", e)
        }
    }

    /** The default message to be spoken when the device is offline. */
    private const val defaultMessage =
        "It looks like the internet is offline. I won’t be able to help right now. Please try again later."
}


