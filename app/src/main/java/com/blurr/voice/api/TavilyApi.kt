/**
 * @file TavilyApi.kt
 * @brief Provides a client for interacting with the Tavily Search API.
 *
 * This file contains the `TavilyApi` class, which is a lightweight client for making
 * search requests to the Tavily API.
 */
package com.blurr.voice.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import com.blurr.voice.MyApplication
import com.blurr.voice.utilities.NetworkConnectivityManager
import com.blurr.voice.utilities.NetworkNotifier

/**
 * A client for making search requests to the Tavily Search API.
 *
 * This class handles the construction and execution of POST requests to the Tavily search endpoint,
 * including authentication via an API key.
 *
 * @param apiKey The API key for authenticating with the Tavily service.
 */
class TavilyApi(private val apiKey: String) {

    /** The OkHttpClient instance used for all API requests. */
    private val client = OkHttpClient()

    /**
     * Performs a search request to the Tavily API using a JSON payload.
     *
     * @param searchParameters A [JSONObject] containing the complete search query and parameters
     *                         as defined by the Tavily API documentation.
     * @return A [String] containing the raw JSON response from the Tavily API. In case of an
     *         error (e.g., network failure), a JSON string with an "error" key is returned.
     */
    suspend fun search(searchParameters: JSONObject): String {
        return withContext(Dispatchers.IO) {
            // Network check
            try {
                val isOnline = NetworkConnectivityManager(MyApplication.appContext).isNetworkAvailable()
                if (!isOnline) {
                    Log.e("TavilyApi", "No internet connection. Skipping search call.")
                    NetworkNotifier.notifyOffline()
                    return@withContext "{\"error\":\"offline\"}"
                }
            } catch (e: Exception) {
                Log.e("TavilyApi", "Network check failed, assuming offline. ${e.message}")
                return@withContext "{\"error\":\"offline\"}"
            }

            val request = Request.Builder()
                .url("https://api.tavily.com/search")
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer $apiKey")
                .post(searchParameters.toString().toRequestBody("application/json".toMediaType()))
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string()
                    if (!response.isSuccessful || body == null) {
                        val errorBody = body ?: "No response body"
                        Log.e("TavilyApi", "API Error ${response.code}: $errorBody")
                        throw Exception("Tavily API call failed with HTTP ${response.code}")
                    }
                    body
                }
            } catch (e: Exception) {
                Log.e("TavilyApi", "Search failed", e)
                "{\"error\": \"Search failed: ${e.message}\"}"
            }
        }
    }
}