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

class SpeechCoordinator private constructor(private val context: Context) {

    companion object {
        private const val TAG = "SpeechCoordinator"

        @Volatile private var INSTANCE: SpeechCoordinator? = null

        fun getInstance(context: Context): SpeechCoordinator {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SpeechCoordinator(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val ttsManager = TTSManager.getInstance(context)
    private val sttManager = STTManager(context)

    // Mutex to ensure only one speech operation at a time
    private val speechMutex = Mutex()
    private var ttsPlaybackJob: Job? = null
    // State tracking
    private var isSpeaking = false
    private var isListening = false

    /**
     * Speak text using TTS, ensuring STT is not listening
     * @param text The text to speak
     */
    suspend fun speakText(text: String) {
        speechMutex.withLock {
            try {
                if (isListening) {
                    Log.d(TAG, "Stopping STT before speaking: $text")
                    sttManager.stopListening()
                    isListening = false
                    delay(250) // Brief pause to ensure STT is fully stopped
                }

                isSpeaking = true
                Log.d(TAG, "Starting TTS: $text")

                // This is a suspend call that will wait until TTS is actually done.
                ttsManager.speakText(text)

                // FIXED: The inaccurate, estimated delay has been removed!

                Log.d(TAG, "TTS completed: $text")

            } finally {
                // Ensure the speaking flag is always reset
                isSpeaking = false
            }
        }
    }

    /**
     * Speak text to user, ensuring STT is not listening
     * @param text The text to speak to the user
     */
    suspend fun speakToUser(text: String) {
        speechMutex.withLock {
            try {
                if (isListening) {
                    Log.d(TAG, "Stopping STT before speaking to user: $text")
                    sttManager.stopListening()
                    isListening = false
                    delay(250) // Brief pause
                }

                isSpeaking = true
                Log.d(TAG, "Starting TTS to user: $text")

                ttsManager.speakToUser(text)


                Log.d(TAG, "TTS to user completed: $text")

            } finally {
                // Ensure the speaking flag is always reset
                isSpeaking = false
            }
        }
    }
    /**
     * Plays raw audio data directly using TTSManager, bypassing synthesis.
     * Ideal for playing cached voice samples.
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
                    // Directly use the TTSManager's playback function
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
     * Start listening with STT, ensuring TTS is not speaking
     * @param onResult Callback for speech recognition results
     * @param onError Callback for speech recognition errors
     * @param onListeningStateChange Callback for listening state changes
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
                    // 1. Synthesize audio with the specific voice HERE
                    val audioData = GoogleTts.synthesize(text, voice)

                    // 2. Play the synthesized audio data
                    ttsManager.playAudioData(audioData)

                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Error during voice test", e)
                }
            }
        }
    }

    fun stop() {
        // Cancel the coroutine managing the playback
        ttsPlaybackJob?.cancel(CancellationException("Playback stopped by user action"))
        // Call the underlying TTS Manager's stop function to halt the hardware
        ttsManager.stop()
        Log.d(TAG, "All TTS playback stopped by coordinator.")
    }

    suspend fun startListening(
        onResult: (String) -> Unit,
        onError: (String) -> Unit,
        onListeningStateChange: (Boolean) -> Unit,
        onPartialResult: (String) -> Unit
    ) {
        stop() // Use our new stop function to ensure TTS is stopped before listening
        speechMutex.withLock {
            try {

                // If TTS is speaking, wait for it to complete. This loop is now
                // much more efficient as isSpeaking is updated accurately.
                if (isSpeaking) {
                    Log.d(TAG, "Waiting for TTS to complete before starting STT")
                    while (isSpeaking) {
                        delay(100) // Check every 100ms
                    }
                    delay(250) // Additional pause after TTS completes
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

    fun stopListening() {
        if (isListening) {
            sttManager.stopListening()
            isListening = false
        }
    }
    fun stopSpeaking() {
        ttsManager.stop()
        Log.d("SpeechCoordinator", "Speaking explicitly stopped.")
    }


    fun isCurrentlySpeaking(): Boolean = isSpeaking

    fun isCurrentlyListening(): Boolean = isListening

    fun isSpeechActive(): Boolean = isSpeaking || isListening

    suspend fun waitForSpeechCompletion() {
        while (isSpeechActive()) {
            delay(100)
        }
    }

    fun shutdown() {
        stopListening()
        sttManager.shutdown()
    }
}