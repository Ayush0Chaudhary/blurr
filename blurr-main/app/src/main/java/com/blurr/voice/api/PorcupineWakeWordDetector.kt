package com.blurr.voice.api

import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback
import ai.picovoice.porcupine.PorcupineManagerErrorCallback
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PorcupineWakeWordDetector(
    private val context: Context,
    private val onWakeWordDetected: () -> Unit,
    private val onApiFailure: () -> Unit
) {
    private var porcupineManager: PorcupineManager? = null
    private var isListening = false
    private val keyManager = PicovoiceKeyManager(context)
    private var coroutineScope: CoroutineScope? = null

    companion object {
        private const val TAG = "PorcupineWakeWordDetector"
    }

    fun start() {
        if (isListening) {
            Log.d(TAG, "Already started.")
            return
        }

        // Create a new coroutine scope for this start operation
        coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        // Start the key fetching process asynchronously
        coroutineScope?.launch {
            try {
                val accessKey = keyManager.getAccessKey()
                if (accessKey != null) {
                    Log.d(TAG, "Successfully obtained Picovoice access key")
                    startPorcupineWithKey(accessKey)
                } else {
                    Log.e(TAG, "Failed to obtain Picovoice access key. Triggering API failure callback.")
                    onApiFailure()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting access key: ${e.message}")
                onApiFailure()
            }
        }
    }

    private suspend fun startPorcupineWithKey(accessKey: String) = withContext(Dispatchers.Main) {
        try {
            // Create the wake word callback
            val wakeWordCallback = PorcupineManagerCallback { keywordIndex ->
                Log.d(TAG, "Wake word detected! Keyword index: $keywordIndex")
                onWakeWordDetected()
                // PorcupineManager should automatically continue listening after detection
            }

            // Create error callback for debugging
            val errorCallback = PorcupineManagerErrorCallback { error ->
                Log.e(TAG, "Porcupine error: ${error.message}")
                // If there's an error, trigger API failure callback
                if (isListening) {
                    Log.d(TAG, "Porcupine error occurred, triggering API failure callback")
                    onApiFailure()
                }
            }

            // Build and start PorcupineManager
            porcupineManager = PorcupineManager.Builder()
                .setAccessKey(accessKey)
                .setKeywordPaths(arrayOf("Panda_en_android_v3_0_0.ppn"))
                .setSensitivity(0.5f) // Set sensitivity to 0.5 for better detection
                .setErrorCallback(errorCallback)
                .build(context, wakeWordCallback)

            porcupineManager?.start()
            isListening = true
            Log.d(TAG, "Porcupine wake word detection started successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting Porcupine: ${e.message}")
            // Trigger API failure callback if Porcupine fails
            Log.d(TAG, "Porcupine failed to start, triggering API failure callback")
            onApiFailure()
        }
    }

    fun stop() {
        if (!isListening) {
            Log.d(TAG, "Already stopped.")
            return
        }

        try {
            porcupineManager?.stop()
            porcupineManager?.delete()
            porcupineManager = null
            isListening = false
            Log.d(TAG, "Porcupine wake word detection stopped.")
            
            // Cancel the coroutine scope
            coroutineScope?.cancel()
            coroutineScope = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping wake word detection: ${e.message}")
        }
    }
} 