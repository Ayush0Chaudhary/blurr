package com.blurr.voice.data

import android.util.Log
import com.cactus.CactusLM
import com.cactus.CactusInitParams
import com.cactus.ChatMessage
import com.cactus.CactusCompletionParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Singleton manager for the CactusLM on-device model.
 * Handles model downloading, initialization, and embedding generation.
 */
object CactusEmbeddingManager {

    private const val TAG = "CactusEmbeddingManager"

    // FIX 1: We create an INSTANCE of the engine.
    // We do NOT use 'CactusModel' here (that class is just for metadata like file size).
    private val cactusLM = CactusLM()

    private var isModelLoaded = false

    // Using qwen3-0.6 as the default model for efficiency
    private val MODEL_TYPE = "qwen3-0.6"

    /**
     * Checks if the model is already downloaded.
     */
    suspend fun isModelDownloaded(): Boolean {
        return withContext(Dispatchers.IO) {
            // FIX 2: We must ask the engine instance for the list of models to check status
            val models = cactusLM.getModels()
            val targetModel = models.find { it.slug == MODEL_TYPE }

            val isDownloaded = targetModel?.isDownloaded == true
            Log.d(TAG, "Model $MODEL_TYPE downloaded? $isDownloaded")
            isDownloaded
        }
    }

    /**
     * Downloads the model if not present.
     * @return true if successful, false otherwise.
     */
    suspend fun downloadModel(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (isModelDownloaded()) {
                    Log.d(TAG, "Model already downloaded.")
                    return@withContext true
                }
                Log.d(TAG, "Starting model download...")

                // FIX 3: Call download on the instance, not the class
                cactusLM.downloadModel(MODEL_TYPE)

                Log.d(TAG, "Model download finished.")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading model", e)
                false
            }
        }
    }

    /**
     * Initializes the model if it's not already loaded.
     * @return true if initialization was successful.
     */
    suspend fun initialize(): Boolean {
        // FIX 4: Use the instance's state check
        if (isModelLoaded && cactusLM.isLoaded()) return true

        return withContext(Dispatchers.IO) {
            try {
                if (!isModelDownloaded()) {
                    // Auto-download if missing
                    val success = downloadModel()
                    if (!success) {
                        Log.e(TAG, "Model download failed. Cannot initialize.")
                        return@withContext false
                    }
                }

                Log.d(TAG, "Initializing CactusLM...")

                // FIX 5: The method is 'initializeModel', not 'loadModel'.
                // We also need to pass params.
                val params = CactusInitParams(
                    model = MODEL_TYPE,
                    contextSize = 512 // Keep small for embeddings to save RAM
                )
                cactusLM.initializeModel(params)

                isModelLoaded = true
                Log.d(TAG, "CactusLM initialized successfully.")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing CactusLM", e)
                isModelLoaded = false
                false
            }
        }
    }

    /**
     * Generates an embedding for the given text.
     * @param text The input text.
     * @return A list of Floats representing the embedding, or null if failed.
     */
    suspend fun generateEmbedding(text: String): List<Float>? {
        return withContext(Dispatchers.IO) {
            try {
                if (!initialize()) {
                    Log.e(TAG, "Failed to initialize model for embedding generation.")
                    return@withContext null
                }

                // FIX 6: Call generation on the engine instance.
                // It returns a Result object, not the list directly.
                val result = cactusLM.generateEmbedding(text)

                if (result != null && result.success) {
                    // Convert List<Double> (SDK default) to List<Float>
                    result.embeddings.map { it.toFloat() }
                } else {
                    Log.e(TAG, "Embedding generation failed: ${result?.errorMessage}")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error generating embedding", e)
                null
            }
        }
    }

    /**
     * Generates a completion (text response) for a given chat history.
     */
    suspend fun generateCompletion(messages: List<ChatMessage>): String? {
        return withContext(Dispatchers.IO) {
            try {
                if (!initialize()) {
                    return@withContext null
                }

                // FIX 7: Create params object for the completion
                val params = CactusCompletionParams(
                    maxTokens = 200,
                    temperature = 0.7
                )

                // FIX 8: Correct method call
                val result = cactusLM.generateCompletion(messages, params)

                if (result != null && result.success) {
                    result.response
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error generating completion", e)
                null
            }
        }
    }

    /**
     * Unloads the model to free up resources.
     */
    fun unload() {
        try {
            // FIX 9: Unload the instance
            cactusLM.unload()
            isModelLoaded = false
            Log.d(TAG, "CactusLM unloaded.")
        } catch (e: Exception) {
            Log.e(TAG, "Error unloading CactusLM", e)
        }
    }
}