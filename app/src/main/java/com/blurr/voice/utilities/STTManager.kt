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

class STTManager(private val context: Context) {
    
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var onResultCallback: ((String) -> Unit)? = null
    private var onErrorCallback: ((String) -> Unit)? = null
    private var onListeningStateChange: ((Boolean) -> Unit)? = null
    private var onPartialResultCallback: ((String) -> Unit)? = null
    private var isInitialized = false
    private val visualizerManager = STTVisualizer(context)
    private val COMPLETE_SILENCE_MS = 2500  // time of silence to consider input complete
    private val POSSIBLE_SILENCE_MS = 2000  // shorter silence hint window
    private val MIN_UTTERANCE_MS     = 1500 // enforce a minimum listening duration

    

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
            
            override fun onBufferReceived(buffer: ByteArray?) {
            }
            
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
            
            override fun onEvent(eventType: Int, params: Bundle?) {
            }
        }
    }
    
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
//                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, COMPLETE_SILENCE_MS)
//                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, POSSIBLE_SILENCE_MS)
//                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, MIN_UTTERANCE_MS)
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
    
    fun stopListening() {
        CoroutineScope(Dispatchers.Main).launch {
            if (isListening && speechRecognizer != null) {
                try {
                    speechRecognizer?.stopListening()
                    Log.d("STTManager", "Stopped listening")
                } catch (e: Exception) {
                    Log.e("STTManager", "Error stopping speech recognition", e)
                }
            }
        }
    }
    
    fun isCurrentlyListening(): Boolean = isListening
    
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