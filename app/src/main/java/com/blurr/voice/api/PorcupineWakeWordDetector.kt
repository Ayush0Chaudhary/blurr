/**
 * @file PorcupineWakeWordDetector.kt
 * @brief Implements a wake word detector using the Picovoice Porcupine engine.
 *
 * This file contains the `PorcupineWakeWordDetector` class, which encapsulates the logic for
 * initializing and managing the Porcupine wake word engine. It handles fetching the required
 * access key, starting the listening process, and stopping it gracefully.
 */
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

/**
 * A class to detect a wake word using the Picovoice Porcupine engine.
 *
 * This class manages the lifecycle of the [PorcupineManager]. It asynchronously fetches the
 * required access key using [PicovoiceKeyManager] and then initializes and starts the
 * Porcupine engine to listen for the "Panda" wake word.
 *
 * @param context The application context, required for initializing Porcupine and the key manager.
 * @param onWakeWordDetected A callback function that is invoked when the wake word is detected.
 * @param onApiFailure A callback function that is invoked if there is an error initializing
 *                     the Porcupine engine, such as a failure to get the access key.
 */
class PorcupineWakeWordDetector(
    private val context: Context,
    private val onWakeWordDetected: () -> Unit,
    private val onApiFailure: () -> Unit
) {
    /** The instance of the PorcupineManager from the Picovoice SDK. */
    private var porcupineManager: PorcupineManager? = null
    /** A flag to track whether the detector is currently listening. */
    private var isListening = false
    /** An instance of [PicovoiceKeyManager] to retrieve the access key. */
    private val keyManager = PicovoiceKeyManager(context)
    /** The coroutine scope for managing asynchronous operations like fetching the access key. */
    private var coroutineScope: CoroutineScope? = null

    /**
     * Companion object for constants.
     */
    companion object {
        private const val TAG = "PorcupineWakeWordDetector"
    }

    /**
     * Starts the wake word detection process.
     *
     * If not already listening, this method creates a new coroutine scope and launches a task
     * to asynchronously fetch the Picovoice access key. Once the key is obtained, it calls
     * [startPorcupineWithKey] to initialize and start the engine. If fetching the key fails,
     * the [onApiFailure] callback is triggered.
     */
    fun start() {
        if (isListening) {
            Log.d(TAG, "Already started.")
            return
        }

        coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
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

    /**
     * Initializes and starts the Porcupine engine with the provided access key.
     *
     * This function must be called on the main thread. It configures the [PorcupineManager]
     * with the wake word model ("Panda"), sensitivity, and callbacks, then starts listening.
     *
     * @param accessKey The Picovoice access key required for initialization.
     */
    private suspend fun startPorcupineWithKey(accessKey: String) = withContext(Dispatchers.Main) {
        try {
            val wakeWordCallback = PorcupineManagerCallback { keywordIndex ->
                Log.d(TAG, "Wake word detected! Keyword index: $keywordIndex")
                onWakeWordDetected()
            }

            val errorCallback = PorcupineManagerErrorCallback { error ->
                Log.e(TAG, "Porcupine error: ${error.message}")
                if (isListening) {
                    Log.d(TAG, "Porcupine error occurred, triggering API failure callback")
                    onApiFailure()
                }
            }

            porcupineManager = PorcupineManager.Builder()
                .setAccessKey(accessKey)
                .setKeywordPaths(arrayOf("Panda_en_android_v3_0_0.ppn"))
                .setSensitivity(0.5f)
                .setErrorCallback(errorCallback)
                .build(context, wakeWordCallback)

            porcupineManager?.start()
            isListening = true
            Log.d(TAG, "Porcupine wake word detection started successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting Porcupine: ${e.message}")
            Log.d(TAG, "Porcupine failed to start, triggering API failure callback")
            onApiFailure()
        }
    }

    /**
     * Stops the wake word detection process.
     *
     * If the detector is currently listening, this method stops and releases the
     * [PorcupineManager] instance and cancels the associated coroutine scope.
     */
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
            
            coroutineScope?.cancel()
            coroutineScope = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping wake word detection: ${e.message}")
        }
    }
} 