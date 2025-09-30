/**
 * @file MemoryService.kt
 * @brief Provides an interface for interacting with the Mem0 API for memory storage and retrieval.
 *
 * This file contains the `MemoryService` class, which is responsible for adding new memories
 * and searching for existing ones using the Mem0 backend. It handles API requests,
 * authentication, and network connectivity checks.
 */
package com.blurr.voice.api

import android.util.Log
import com.blurr.voice.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import com.blurr.voice.MyApplication
import com.blurr.voice.utilities.NetworkConnectivityManager
import com.blurr.voice.utilities.NetworkNotifier

/**
 * A service class to interact with the Mem0 API for persistent memory storage and retrieval.
 *
 * This class encapsulates the logic for making authenticated requests to the Mem0 API endpoints
 * for creating and searching memories associated with a specific user.
 */
class MemoryService {

    /** The OkHttpClient instance used for all API requests. */
    private val client = OkHttpClient()
    /** The API key for authenticating with the Mem0 service. */
    private val apiKey = BuildConfig.MEM0_API

    /**
     * Adds a new memory to the Mem0 backend based on a user's instruction.
     *
     * This function constructs a payload from the user's instruction and sends it to the
     * Mem0 API to be stored. The memory is associated with the provided `userId`.
     *
     * @param instruction The user's instruction or statement to be stored as a memory.
     * @param userId The unique identifier for the user to associate the memory with.
     */
    suspend fun addMemory(instruction: String, userId: String) {
        if (apiKey.isEmpty()) {
            Log.w("MemoryService", "Mem0 API key is not set. Skipping add memory.")
            return
        }
        // Network check
        try {
            val isOnline = NetworkConnectivityManager(MyApplication.appContext).isNetworkAvailable()
            if (!isOnline) {
                Log.e("MemoryService", "No internet connection. Skipping addMemory call.")
                NetworkNotifier.notifyOffline()
                return
            }
        } catch (e: Exception) {
            Log.e("MemoryService", "Network check failed, assuming offline. ${e.message}")
            return
        }

        withContext(Dispatchers.IO) {
            try {
                val message = JSONObject().put("role", "user").put("content", instruction)
                val messagesArray = JSONArray().put(message)
                val payload = JSONObject().apply {
                    put("messages", messagesArray)
                    put("user_id", userId)
                    put("version", "v2")
                }
                println("Payload being sent to Mem0: ${payload.toString(2)}") // Pretty print JSON

                val request = Request.Builder()
                    .url("https://api.mem0.ai/v1/memories/")
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Token $apiKey")
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseBodyString = response.body?.string()

                    if (response.isSuccessful) {
                        Log.d("MemoryService", "Successfully added memory. Response: $responseBodyString")
                    } else {
                        Log.e("MemoryService", "Failed to add memory. Code: ${response.code}, Body: $responseBodyString")
                    }
                }
            } catch (e: Exception) {
                Log.e("MemoryService", "Error adding memory", e)
            }
        }
    }

    /**
     * Searches for memories relevant to a given query for a specific user.
     *
     * @param query The search query to find relevant memories.
     * @param userId The unique identifier for the user whose memories are to be searched.
     * @return A formatted string containing the search results, or a message indicating
     *         that no memories were found or an error occurred.
     */
    suspend fun searchMemory(query: String, userId: String): String {
        if (apiKey.isEmpty()) {
            Log.w("MemoryService", "Mem0 API key is not set. Skipping search.")
            return "No relevant memories found."
        }
        // Network check
        try {
            val isOnline = NetworkConnectivityManager(MyApplication.appContext).isNetworkAvailable()
            if (!isOnline) {
                Log.e("MemoryService", "No internet connection. Skipping searchMemory call.")
                NetworkNotifier.notifyOffline()
                return "Could not retrieve memories due to no internet connection."
            }
        } catch (e: Exception) {
            Log.e("MemoryService", "Network check failed, assuming offline. ${e.message}")
            return "Could not retrieve memories due to connectivity check error."
        }

        return withContext(Dispatchers.IO) {
            try {
                val filters = JSONObject().put("user_id", userId)
                val payload = JSONObject().apply {
                    put("query", query)
                    put("filters", filters)
                }

                val request = Request.Builder()
                    .url("https://api.mem0.ai/v2/memories/search/")
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Token $apiKey")
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                client.newCall(request).execute().use { response ->
                    val body = response.body?.string()
                    if (!response.isSuccessful || body == null) {
                        throw Exception("Mem0 search failed with HTTP ${response.code}")
                    }

                    val resultsArray = JSONArray(body)
                    if (resultsArray.length() == 0) {
                        return@withContext "No relevant memories found."
                    }

                    // Format the results into a clean string for the prompt
                    val memories = (0 until resultsArray.length()).joinToString("\n") { i ->
                        val memoryObj = resultsArray.getJSONObject(i)
                        // Use optString to avoid crashing if 'memory' key is missing
                        "- ${memoryObj.optString("memory", "Corrupted memory entry")}"
                    }
                    memories
                }
            } catch (e: Exception) {
                Log.e("MemoryService", "Error searching memory", e)
                "Could not retrieve memories due to an error."
            }
        }
    }
}
