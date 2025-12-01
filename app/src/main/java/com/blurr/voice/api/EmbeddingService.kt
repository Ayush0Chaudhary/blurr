package com.blurr.voice.api

import android.util.Log
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import com.blurr.voice.utilities.ApiKeyManager
import com.blurr.voice.utilities.NetworkNotifier

/**
 * Service for generating embeddings using Gemini API
 */
object EmbeddingService {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    /**
     * Generate embedding for a single text
     */
    suspend fun generateEmbedding(
        text: String,
        taskType: String = "RETRIEVAL_DOCUMENT",
        maxRetries: Int = 3
    ): List<Float>? {
        // Network check

        try {
            val isOnline = true
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
     * Generate embeddings for multiple texts by calling the API for each text individually
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
    
    private fun parseEmbeddingResponse(responseBody: String): List<Float> {
        val json = JSONObject(responseBody)
        val embedding = json.getJSONObject("embedding")
        val values = embedding.getJSONArray("values")
        
        return (0 until values.length()).map { i ->
            values.getDouble(i).toFloat()
        }
    }
} 