/**
 * @file STTManager.kt
 * @brief Manages the Android Speech-to-Text (STT) service.
 *
 * This file contains the `STTManager` class, which encapsulates the logic for using
 * Android's built-in `SpeechRecognizer`. It handles initialization, starting and stopping
 * listening, and processing recognition results and errors. It also integrates with
 * an `STTVisualizer` to show microphone input levels.
 */
package com.blurr.voice.utilities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*

/**
 * A manager class for handling all Speech-to-Text (STT) operations.
 *
 * This class provides a simplified interface for interacting with the Android `SpeechRecognizer`.
 * It manages the lifecycle of the recognizer, handles callbacks for results, errors, and state
 * changes, and integrates with a visualizer to provide user feedback.
 *
 * @param context The application context.
 */
class STTManager(private val context: Context) {
    
    /** The underlying Android `SpeechRecognizer` instance. */
    private var speechRecognizer: SpeechRecognizer? = null
    /** A flag indicating if the recognizer is currently listening. */
    private var isListening = false
    /** Callback for when a final recognition result is available. */
    private var onResultCallback: ((String) -> Unit)? = null
    /** Callback for when a recognition error occurs. */
    private var onErrorCallback: ((String) -> Unit)? = null
    /** Callback for changes in the listening state (started/stopped). */
    private var onListeningStateChange: ((Boolean) -> Unit)? = null
    /** Callback for when a partial (interim) recognition result is available. */
    private var onPartialResultCallback: ((String) -> Unit)? = null
    /** A flag to ensure the recognizer is only initialized once. */
    private var isInitialized = false
    /** The manager for the STT visualization overlay. */
    private val visualizerManager = STTVisualizer(context)

    /**
     * Lazily initializes the [SpeechRecognizer] instance.
     *
     * This method checks if recognition is available on the device and creates a new
     * `SpeechRecognizer` instance if one doesn't already exist.
     */
    private fun initializeSpeechRecognizer() {
        if (isInitialized) return
        
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            try {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
                speechRecognizer?.setRecognitionListener(createRecognitionListener())
                isInitialized = true
                Log.d("STTManager", "Speech recognizer initialized successfully")
            } catch (e: Exception) {
                Log.e("STTManager", "Failed to initialize speech recognizer", e)
            }
        } else {
            Log.e("STTManager", "Speech recognition not available on this device")
        }
    }
    
    /**
     * Creates and configures the [RecognitionListener] to handle callbacks from the `SpeechRecognizer`.
     * @return A configured [RecognitionListener] instance.
     */
    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d("STTManager", "Ready for speech")
                isListening = true
                onListeningStateChange?.invoke(true)
            }
            
            override fun onBeginningOfSpeech() {
                Log.d("STTManager", "Beginning of speech")
            }

            override fun onRmsChanged(rmsdB: Float) {
                visualizerManager.onRmsChanged(rmsdB)
            }
            
            override fun onBufferReceived(buffer: ByteArray?) {}
            
            override fun onEndOfSpeech() {
                Log.d("STTManager", "End of speech")
                isListening = false
                onListeningStateChange?.invoke(false)
                onPartialResultCallback = null
            }
            
            override fun onError(error: Int) {
                isListening = false
                onListeningStateChange?.invoke(false)
                visualizerManager.hide()

                val errorMessage = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech match"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                    SpeechRecognizer.ERROR_SERVER -> "Server error"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                    else -> "Unknown error: $error"
                }
                
                Log.e("STTManager", "Speech recognition error: $errorMessage")
                onErrorCallback?.invoke(errorMessage)
                onPartialResultCallback = null
            }
            
            override fun onResults(results: Bundle?) {
                isListening = false
                onListeningStateChange?.invoke(false)
                visualizerManager.hide()

                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val recognizedText = matches[0]
                    Log.d("STTManager", "Recognized text: $recognizedText")
                    onResultCallback?.invoke(recognizedText)
                } else {
                    Log.w("STTManager", "No results from speech recognition")
                    onErrorCallback?.invoke("No speech detected")
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val partialText = matches[0]
                    Log.d("STTManager", "Partial result: $partialText")
                    onPartialResultCallback?.invoke(partialText)
                }
            }
            
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }
    
    /**
     * Starts listening for speech input.
     *
     * This function initializes the recognizer if needed, shows the visualizer, and starts
     * the listening process with the provided callbacks.
     *
     * @param onResult Callback for the final recognized text.
     * @param onError Callback for any recognition errors.
     * @param onListeningStateChange Callback for changes in the listening state.
     * @param onPartialResult Callback for partial (interim) recognition results.
     */
    fun startListening(
        onResult: (String) -> Unit,
        onError: (String) -> Unit,
        onListeningStateChange: (Boolean) -> Unit,
        onPartialResult: (String) -> Unit
    ) {
        if (isListening) {
            Log.w("STTManager", "Already listening")
            return
        }
        
        this.onResultCallback = onResult
        this.onErrorCallback = onError
        this.onListeningStateChange = onListeningStateChange
        this.onPartialResultCallback = onPartialResult

        CoroutineScope(Dispatchers.Main).launch {
            initializeSpeechRecognizer()
            
            if (speechRecognizer == null) {
                onError("Speech recognition not available")
                return@launch
            }
            visualizerManager.show()


            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            
            try {
                speechRecognizer?.startListening(intent)
                Log.d("STTManager", "Started listening")
            } catch (e: Exception) {
                Log.e("STTManager", "Error starting speech recognition", e)
                onError("Failed to start speech recognition: ${e.message}")
            }
        }
    }
    
    /**
     * Stops the speech recognizer from listening.
     */
    fun stopListening() {
        if (isListening && speechRecognizer != null) {
            try {
                speechRecognizer?.stopListening()
                Log.d("STTManager", "Stopped listening")
            } catch (e: Exception) {
                Log.e("STTManager", "Error stopping speech recognition", e)
            }
        }
    }
    
    /**
     * Checks if the STT manager is currently listening for speech.
     * @return `true` if listening, `false` otherwise.
     */
    fun isCurrentlyListening(): Boolean = isListening
    
    /**
     * Releases resources used by the `SpeechRecognizer`.
     * This should be called when the STT functionality is no longer needed, e.g., in `onDestroy`.
     */
    fun shutdown() {
        try {
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.e("STTManager", "Error destroying speech recognizer", e)
        }
        visualizerManager.hide()

        speechRecognizer = null
        isListening = false
        isInitialized = false
        Log.d("STTManager", "STT Manager shutdown")
    }
} 