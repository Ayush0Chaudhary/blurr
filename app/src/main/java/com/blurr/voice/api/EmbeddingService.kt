/**
 * @file EmbeddingService.kt
 * @brief Provides an interface to the Gemini API for generating text embeddings.
 *
 * This file contains the implementation of the EmbeddingService, which is responsible for
 * converting text into numerical vector representations (embeddings) using the
 * 'gemini-embedding-001' model. It includes methods for embedding single and multiple texts,
 * with built-in retry logic and API key management.
 */
package com.blurr.voice.api

import android.util.Log
import com.blurr.voice.utilities.ApiKeyManager
import com.blurr.voice.utilities.NetworkNotifier
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * A singleton object for generating text embeddings using the Google Gemini API.
 *
 * This service handles the complexities of making API requests to the embedding endpoint,
 * including payload construction, API key rotation, and network error handling with retries.
 */
object EmbeddingService {

    /**
     * The OkHttpClient instance used for all API requests.
     * Configured with connection and read timeouts for robustness.
     */
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Generates a numerical embedding for a single piece of text.
     *
     * This function sends the provided text to the Gemini embedding model and returns its
     * vector representation. It includes a retry mechanism to handle transient network issues.
     *
     * @param text The input text to be embedded.
     * @param taskType The intended use case for the embedding, which helps the model generate
     *                 a more relevant vector. Defaults to "RETRIEVAL_DOCUMENT".
     *                 Common types include: "RETRIEVAL_QUERY", "RETRIEVAL_DOCUMENT",
     *                 "SEMANTIC_SIMILARITY", "CLASSIFICATION", "CLUSTERING".
     * @param maxRetries The maximum number of times to retry the API call in case of failure.
     * @return A list of floats representing the text embedding, or null if the generation
     *         fails after all retries.
     */
    suspend fun generateEmbedding(
        text: String,
        taskType: String = "RETRIEVAL_DOCUMENT",
        maxRetries: Int = 3
    ): List<Float>? {
        // Network check - Note: This check is currently hardcoded to true.
        // In a real-world scenario, this should use a proper network connectivity check.
        try {
            val isOnline = true // Placeholder for actual network check
            if (!isOnline) {
                Log.e("EmbeddingService", "No internet connection. Skipping embedding call.")
                NetworkNotifier.notifyOffline()
                return null
            }
        } catch (e: Exception) {
            Log.e("EmbeddingService", "Network check failed, assuming offline. ${e.message}")
            return null
        }

        var attempts = 0
        while (attempts < maxRetries) {
            val currentApiKey = ApiKeyManager.getNextKey()
            Log.d("EmbeddingService", "=== EMBEDDING API REQUEST (Attempt ${attempts + 1}) ===")
            Log.d("EmbeddingService", "Using API key ending in: ...${currentApiKey.takeLast(4)}")
            Log.d("EmbeddingService", "Task type: $taskType")
            Log.d("EmbeddingService", "Text: ${text.take(100)}...")

            try {
                val payload = JSONObject().apply {
                    put("model", "models/gemini-embedding-001")
                    put("content", JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().put("text", text))
                        })
                    })
                    put("taskType", taskType)
                }

                val request = Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-001:embedContent?key=$currentApiKey")
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string()

                    Log.d("EmbeddingService", "=== EMBEDDING API RESPONSE (Attempt ${attempts + 1}) ===")
                    Log.d("EmbeddingService", "HTTP Status: ${response.code}")

                    if (!response.isSuccessful || responseBody.isNullOrEmpty()) {
                        Log.e("EmbeddingService", "API call failed with HTTP ${response.code}. Response: $responseBody")
                        throw Exception("API Error ${response.code}: $responseBody")
                    }

                    val embedding = parseEmbeddingResponse(responseBody)
                    Log.d("EmbeddingService", "Successfully generated embedding with ${embedding.size} dimensions")
                    return embedding
                }

            } catch (e: Exception) {
                Log.e("EmbeddingService", "=== EMBEDDING API ERROR (Attempt ${attempts + 1}) ===", e)
                attempts++
                if (attempts < maxRetries) {
                    val delayTime = 1000L * attempts
                    Log.d("EmbeddingService", "Retrying in ${delayTime}ms...")
                    delay(delayTime)
                } else {
                    Log.e("EmbeddingService", "Embedding generation failed after all $maxRetries retries.")
                    return null
                }
            }
        }
        return null
    }

    /**
     * Generates embeddings for a list of texts by processing them one by one.
     *
     * This function iterates through a list of texts and calls [generateEmbedding] for each.
     * If any of the embedding generations fail, the entire operation is aborted and returns null.
     *
     * @param texts A list of strings, where each string is a text to be embedded.
     * @param taskType The intended use case for the embeddings. See [generateEmbedding] for details.
     * @param maxRetries The maximum number of retries for each individual text embedding call.
     * @return A list containing the embedding vectors for each input text, or null if any
     *         embedding generation fails.
     */
    suspend fun generateEmbeddings(
        texts: List<String>,
        taskType: String = "RETRIEVAL_DOCUMENT",
        maxRetries: Int = 3
    ): List<List<Float>>? {
        Log.d("EmbeddingService", "=== BATCH EMBEDDING REQUEST ===")
        Log.d("EmbeddingService", "Texts count: ${texts.size}")

        val embeddings = mutableListOf<List<Float>>()

        for ((index, text) in texts.withIndex()) {
            Log.d("EmbeddingService", "Processing text ${index + 1}/${texts.size}")
            val embedding = generateEmbedding(text, taskType, maxRetries)
            if (embedding != null) {
                embeddings.add(embedding)
            } else {
                Log.e("EmbeddingService", "Failed to generate embedding for text ${index + 1}")
                return null // Return null if any embedding fails
            }
        }

        Log.d("EmbeddingService", "Successfully generated ${embeddings.size} embeddings")
        return embeddings
    }

    /**
     * Parses the JSON response from the Gemini API to extract the embedding vector.
     *
     * @param responseBody The JSON string response from the API.
     * @return A list of floats representing the embedding vector.
     * @throws org.json.JSONException if the JSON is malformed.
     */
    private fun parseEmbeddingResponse(responseBody: String): List<Float> {
        val json = JSONObject(responseBody)
        val embedding = json.getJSONObject("embedding")
        val values = embedding.getJSONArray("values")

        return (0 until values.length()).map { i ->
            values.getDouble(i).toFloat()
        }
    }
} 