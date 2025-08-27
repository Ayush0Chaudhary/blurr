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

class TavilyApi(private val apiKey: String) {

    private val client = OkHttpClient()

    // The search function now accepts the full JSON payload
    suspend fun search(searchParameters: JSONObject): String {
        return withContext(Dispatchers.IO) {
            // Network check
            try {
                val isOnline = true
                if (!isOnline) {
                    Log.e("TavilyApi", "No internet connection. Skipping search call.")
                    NetworkNotifier.notifyOffline()
                    return@withContext "{\"error\":\"offline\"}"
                }
            } catch (e: Exception) {
                Log.e("TavilyApi", "Network check failed, assuming offline. ${'$'}{e.message}")
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