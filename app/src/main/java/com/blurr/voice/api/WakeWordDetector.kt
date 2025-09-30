/**
 * @file WakeWordDetector.kt
 * @brief Implements a wake word detector using the Android SpeechRecognizer.
 *
 * This file contains the `WakeWordDetector` class, which provides a simple, non-Picovoice
 * implementation for wake word detection. It continuously listens for speech and triggers a
 * callback when the specified wake word ("Panda") is detected.
 *
 * Note: This implementation is less efficient and reliable than [PorcupineWakeWordDetector]
 * and is likely intended for fallback or testing purposes.
 */
package com.blurr.voice.api

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.blurr.voice.utilities.STTManager
import java.util.Locale

/**
 * A basic wake word detector that uses the standard Android Speech-to-Text (STT) service.
 *
 * This class works by continuously starting and restarting the STT service, checking the
 * recognized text for the presence of the wake word. It includes logic to mute the STT
 * startup chime to provide a seamless listening experience.
 *
 * @param context The application context, required for [STTManager] and [AudioManager].
 * @param onWakeWordDetected A callback function that is invoked when the wake word is detected.
 */
class WakeWordDetector(
    private val context: Context,
    private val onWakeWordDetected: () -> Unit
) {
    /** The STTManager instance used for speech recognition. */
    private var sttManager: STTManager? = null
    /** The AudioManager used to mute the STT startup chime. */
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    /** The wake word to listen for. */
    private val wakeWord = "Panda"
    /** A flag to control the listening loop. */
    private var isListening = false
    /** A handler for scheduling the restart of the listening process. */
    private val handler = Handler(Looper.getMainLooper())
    /** The delay in milliseconds before restarting the listener. */
    private val restartDelayMs = 250L

    /**
     * Starts the continuous listening process for the wake word.
     *
     * If not already listening, it sets the [isListening] flag to true and initiates
     * the listening loop via [startContinuousListening].
     */
    fun start() {
        if (isListening) {
            Log.d("WakeWordDetector", "Already started.")
            return
        }
        isListening = true
        Log.d("WakeWordDetector", "Starting to listen for wake word.")
        startContinuousListening()
    }

    /**
     * Stops the wake word detection process.
     *
     * This method stops the STT manager, cancels any pending restart callbacks, and ensures
     * that the notification sound stream is unmuted.
     */
    fun stop() {
        if (!isListening) {
            Log.d("WakeWordDetector", "Already stopped.")
            return
        }
        isListening = false
        handler.removeCallbacksAndMessages(null)
        sttManager?.stopListening()
        sttManager?.shutdown()

        audioManager.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_UNMUTE, 0)
        Log.d("WakeWordDetector", "Stopped listening for wake word.")
    }

    /**
     * The core of the listening loop.
     *
     * This function initializes the [STTManager] if necessary, mutes the notification sound,
     * starts listening for speech, and schedules the sound to be unmuted shortly after.
     * The callbacks for the STT manager handle the wake word detection and trigger the
     * restart of the loop.
     */
    private fun startContinuousListening() {
        if (!isListening) return

        if (sttManager == null) {
            sttManager = STTManager(context)
        }

        audioManager.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_MUTE, 0)

        sttManager?.startListening(
            onResult = { recognizedText ->
                Log.d("WakeWordDetector", "Recognized: '$recognizedText'")
                if (recognizedText.lowercase(Locale.ROOT).contains(wakeWord.lowercase(Locale.ROOT))) {
                    onWakeWordDetected()
                }
                restartListening()
            },
            onError = { errorMessage ->
                Log.e("WakeWordDetector", "STT Error: $errorMessage")
                restartListening()
            },
            onListeningStateChange = { },
            onPartialResult = { }
        )

        handler.postDelayed({
            audioManager.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_UNMUTE, 0)
        }, 500)
    }

    /**
     * Schedules the listening process to restart after a short delay.
     *
     * This ensures that the detector is always listening, even after an STT result or error.
     */
    private fun restartListening() {
        if (!isListening) return
        handler.postDelayed({
            sttManager?.stopListening()
            startContinuousListening()
        }, restartDelayMs)
    }
}