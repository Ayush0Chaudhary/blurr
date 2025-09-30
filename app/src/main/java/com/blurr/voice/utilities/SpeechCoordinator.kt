/**
 * @file SpeechCoordinator.kt
 * @brief Manages the coordination between Text-to-Speech (TTS) and Speech-to-Text (STT) operations.
 *
 * This file contains the `SpeechCoordinator` class, a crucial singleton that ensures TTS and STT
 * operations do not overlap, preventing issues like the agent listening to its own voice.
 * It uses a mutex to serialize speech-related tasks.
 */
package com.blurr.voice.utilities

import android.content.Context
import android.util.Log
import com.blurr.voice.api.GoogleTts
import com.blurr.voice.api.TTSVoice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException

/**
 * A singleton class that coordinates TTS and STT operations to prevent conflicts.
 *
 * This class acts as a central hub for all speech input and output. It uses a [Mutex] to
 * ensure that only one speech operation (either speaking or listening) can occur at a time.
 * For example, it will automatically stop an active STT session before starting a TTS playback.
 *
 * @param context The application context.
 */
class SpeechCoordinator private constructor(private val context: Context) {

    /**
     * Companion object for creating and retrieving the singleton instance of the coordinator.
     */
    companion object {
        private const val TAG = "SpeechCoordinator"

        @Volatile private var INSTANCE: SpeechCoordinator? = null

        /**
         * Gets the singleton instance of the [SpeechCoordinator].
         * @param context The application context.
         * @return The singleton [SpeechCoordinator] instance.
         */
        fun getInstance(context: Context): SpeechCoordinator {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SpeechCoordinator(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    /** The manager for Text-to-Speech operations. */
    private val ttsManager = TTSManager.getInstance(context)
    /** The manager for Speech-to-Text operations. */
    private val sttManager = STTManager(context)

    /** A mutex to ensure that speech operations are serialized and do not overlap. */
    private val speechMutex = Mutex()
    /** A coroutine job for the current TTS playback, allowing it to be cancelled. */
    private var ttsPlaybackJob: Job? = null
    /** A state flag indicating if TTS is currently active. */
    private var isSpeaking = false
    /** A state flag indicating if STT is currently active. */
    private var isListening = false

    /**
     * Speaks the given text using the default TTS voice.
     *
     * This function acquires a lock to ensure no other speech operations are running. It will
     * stop any active listening session before speaking.
     *
     * @param text The text to be spoken.
     */
    suspend fun speakText(text: String) {
        speechMutex.withLock {
            try {
                if (isListening) {
                    Log.d(TAG, "Stopping STT before speaking: $text")
                    sttManager.stopListening()
                    isListening = false
                    delay(250)
                }

                isSpeaking = true
                Log.d(TAG, "Starting TTS: $text")

                ttsManager.speakText(text)

                Log.d(TAG, "TTS completed: $text")

            } finally {
                isSpeaking = false
            }
        }
    }

    /**
     * Speaks the given text using a user-centric TTS voice configuration.
     *
     * This function is similar to [speakText] but may use different TTS settings optimized
     * for direct user interaction.
     *
     * @param text The text to be spoken to the user.
     */
    suspend fun speakToUser(text: String) {
        speechMutex.withLock {
            try {
                if (isListening) {
                    Log.d(TAG, "Stopping STT before speaking to user: $text")
                    sttManager.stopListening()
                    isListening = false
                    delay(250)
                }

                isSpeaking = true
                Log.d(TAG, "Starting TTS to user: $text")

                ttsManager.speakToUser(text)


                Log.d(TAG, "TTS to user completed: $text")

            } finally {
                isSpeaking = false
            }
        }
    }
    /**
     * Plays raw audio data directly, bypassing TTS synthesis.
     *
     * This is useful for playing pre-synthesized or cached audio responses. It cancels any
     * ongoing playback before starting the new one.
     *
     * @param data The raw audio data to be played.
     */
    suspend fun playAudioData(data: ByteArray) {
        ttsPlaybackJob?.cancel(CancellationException("New audio data request received"))
        ttsPlaybackJob = CoroutineScope(Dispatchers.IO).launch {
            speechMutex.withLock {
                try {
                    if (isListening) {
                        sttManager.stopListening()
                        isListening = false
                        delay(200)
                    }
                    ttsManager.playAudioData(data)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Error during audio data playback", e)
                }
            }
        }
    }
    /**
     * Synthesizes and plays a given text with a specific voice for testing purposes.
     *
     * @param text The text to synthesize and play.
     * @param voice The [TTSVoice] to use for the synthesis.
     */
    suspend fun testVoice(text: String, voice: TTSVoice) {
        ttsPlaybackJob?.cancel(CancellationException("New voice test request received"))
        ttsPlaybackJob = CoroutineScope(Dispatchers.IO).launch {
            speechMutex.withLock {
                try {
                    if (isListening) {
                        sttManager.stopListening()
                        isListening = false
                        delay(200)
                    }
                    val audioData = GoogleTts.synthesize(text, voice)
                    ttsManager.playAudioData(audioData)

                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Error during voice test", e)
                }
            }
        }
    }

    /**
     * Stops any active TTS playback immediately.
     */
    fun stop() {
        ttsPlaybackJob?.cancel(CancellationException("Playback stopped by user action"))
        ttsManager.stop()
        Log.d(TAG, "All TTS playback stopped by coordinator.")
    }

    /**
     * Starts listening for speech, ensuring TTS is not active.
     *
     * This function acquires a lock and waits for any ongoing speech to finish before
     * starting the STT manager.
     *
     * @param onResult Callback for the final recognized text.
     * @param onError Callback for any recognition errors.
     * @param onListeningStateChange Callback for changes in the listening state.
     * @param onPartialResult Callback for partial (interim) recognition results.
     */
    suspend fun startListening(
        onResult: (String) -> Unit,
        onError: (String) -> Unit,
        onListeningStateChange: (Boolean) -> Unit,
        onPartialResult: (String) -> Unit
    ) {
        stop()
        speechMutex.withLock {
            try {

                if (isSpeaking) {
                    Log.d(TAG, "Waiting for TTS to complete before starting STT")
                    while (isSpeaking) {
                        delay(100)
                    }
                    delay(250)
                }

                isListening = true
                sttManager.startListening(
                    onResult = { result -> onResult(result) },
                    onError = { error -> onError(error) },
                    onListeningStateChange = { listening ->
                        isListening = listening
                        onListeningStateChange(listening)
                    },
                    onPartialResult = { partialText -> onPartialResult(partialText) }
                )

            } catch (e: Exception) {
                isListening = false
                onError("Failed to start speech recognition: ${e.message}")
            }
        }
    }

    /**
     * Stops an active STT listening session.
     */
    fun stopListening() {
        if (isListening) {
            sttManager.stopListening()
            isListening = false
        }
    }

    /**
     * Explicitly stops any ongoing TTS playback.
     */
    fun stopSpeaking() {
        ttsManager.stop()
        Log.d("SpeechCoordinator", "Speaking explicitly stopped.")
    }


    /**
     * Checks if TTS is currently speaking.
     * @return `true` if speaking, `false` otherwise.
     */
    fun isCurrentlySpeaking(): Boolean = isSpeaking

    /**
     * Checks if STT is currently listening.
     * @return `true` if listening, `false` otherwise.
     */
    fun isCurrentlyListening(): Boolean = isListening

    /**
     * Checks if any speech-related activity (speaking or listening) is in progress.
     * @return `true` if either speaking or listening, `false` otherwise.
     */
    fun isSpeechActive(): Boolean = isSpeaking || isListening

    /**
     * A suspend function that waits until all speech activity has completed.
     */
    suspend fun waitForSpeechCompletion() {
        while (isSpeechActive()) {
            delay(100)
        }
    }

    /**
     * Shuts down and releases all resources used by the coordinator and its underlying managers.
     */
    fun shutdown() {
        stopListening()
        sttManager.shutdown()
    }
}