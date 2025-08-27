package com.blurr.voice.utilities

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import android.util.Log
import com.blurr.voice.MyApplication

/**
 * Shows a user-facing offline notification (toast) and speaks a short TTS message.
 * Debounced to avoid spamming the user when multiple calls occur in a short time.
 */
object NetworkNotifier {

    private const val TAG = "NetworkNotifier"
    private const val MIN_INTERVAL_MS = 10_000L // 10 seconds
    @Volatile private var lastNotifiedAt: Long = 0L

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

    private const val defaultMessage =
        "It looks like the internet is offline. I won’t be able to help right now. Please try again later."
}


