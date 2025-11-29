package com.blurr.voice.utilities

import android.content.Context
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Manages user input for agent questions
 * This class handles the communication between the agent and user for interactive tasks
 */
class UserInputManager(private val context: Context) {
    
    companion object {
        private const val TAG = "UserInputManager"
        private const val SPEECH_TIMEOUT_MS = 30000L // 30 seconds timeout for speech input
        private const val FALLBACK_TIMEOUT_MS = 5000L // 5 seconds for fallback response
        private const val MAX_SPEECH_ATTEMPTS = 3 // Maximum number of speech recognition attempts
        private var currentQuestion: String? = null
        private var currentResponse: String? = null
        private var responseCallback: ((String) -> Unit)? = null
    }

    private val speechCoordinator = SpeechCoordinator.getInstance(context)
    
    /**
     * Check if speech recognition is available on this device
     * @return true if speech recognition is available
     */
    fun isSpeechRecognitionAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }
    
    /**
     * Ask a question to the user and wait for their response using speech-to-text
     * The system will attempt speech recognition up to 3 times if no speech is detected
     * @param question The question to ask the user
     * @return The user's response
     */
    suspend fun askQuestion(question: String): String {
        return suspendCancellableCoroutine { continuation ->
            try {
                currentQuestion = question
                responseCallback = { response ->
                    currentResponse = response
                    continuation.resume(response)
                }
                
                Log.d(TAG, "Agent asked: $question")
                
                // Check if speech recognition is available
                if (!isSpeechRecognitionAvailable()) {
                    Log.w(TAG, "Speech recognition not available, using fallback")
                    useFallbackResponse(question)
                    return@suspendCancellableCoroutine
                }
                
                Log.d(TAG, "Starting speech recognition for user response...")
                
                // Start speech recognition with multiple attempts
                CoroutineScope(Dispatchers.Main).launch {
                    try {
                        var response: String? = null
                        var attempt = 1
                        
                        while (attempt <= MAX_SPEECH_ATTEMPTS && (response == null || response.isEmpty())) {
                            Log.d(TAG, "Speech recognition attempt $attempt of $MAX_SPEECH_ATTEMPTS")
                            
                            if (attempt > 1) {
                                // Give user a moment to prepare for next attempt
                                delay(2000)
                                // Re-ask the question for subsequent attempts using SpeechCoordinator
                                speechCoordinator.speakToUser("Please try again. $question")
                                delay(1000) // Brief pause after re-asking
                            }
                            
                            response = suspendCancellableCoroutine<String> { speechContinuation ->
                                // Use a separate coroutine to call the suspend function
                                CoroutineScope(Dispatchers.Main).launch {
                                    try {
                                        withTimeoutOrNull(SPEECH_TIMEOUT_MS) {
                                            speechCoordinator.startListening(
                                                onResult = { recognizedText ->
                                                    Log.d(TAG, "Speech recognized on attempt $attempt: $recognizedText")
                                                    speechContinuation.resume(recognizedText)
                                                },
                                                onError = { errorMessage ->
                                                    Log.e(TAG, "Speech recognition error on attempt $attempt: $errorMessage")
                                                    // Don't throw exception, just return empty string for this attempt
                                                    speechContinuation.resume("")
                                                },
                                                onListeningStateChange = { isListening ->
                                                    Log.d(TAG, "Listening state changed on attempt $attempt: $isListening")
                                                },
                                                onPartialResult = { } // <-- FIX: Added the missing fourth argument

                                            )
                                        } ?: run {
                                            Log.w(TAG, "Speech recognition timed out on attempt $attempt")
                                            speechContinuation.resume("")
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error in startListening", e)
                                        speechContinuation.resume("")
                                    }
                                }
                            }
                            
                            if (response != null && response.isNotEmpty()) {
                                Log.d(TAG, "User responded via speech on attempt $attempt: $response")
                                break
                            } else {
                                Log.w(TAG, "Speech recognition failed on attempt $attempt")
                                attempt++
                            }
                        }
                        
                        if (response != null && response.isNotEmpty()) {
                            responseCallback?.invoke(response)
                        } else {
                            Log.w(TAG, "All $MAX_SPEECH_ATTEMPTS speech recognition attempts failed, using fallback")
                            useFallbackResponse(question)
                        }
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "Error during speech recognition", e)
                        useFallbackResponse(question)
                    } finally {
                        // Stop listening and clean up
                        speechCoordinator.stopListening()
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error asking question", e)
                continuation.resume("Error: Could not get user response")
            }
        }
    }
    
    /**
     * Use fallback response when STT is not available or fails after all attempts
     * @param question The original question
     */
    private fun useFallbackResponse(question: String) {
        CoroutineScope(Dispatchers.Main).launch {
            delay(FALLBACK_TIMEOUT_MS) // Give user time to read the question
            val fallbackResponse = "User provided fallback response after $MAX_SPEECH_ATTEMPTS failed speech recognition attempts for: $question"
            Log.d(TAG, "Using fallback response: $fallbackResponse")
            responseCallback?.invoke(fallbackResponse)
        }
    }
} 