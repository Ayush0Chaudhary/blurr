/**
 * @file PicovoiceKeyManager.kt
 * @brief Manages the retrieval and caching of the Picovoice access key.
 *
 * This file contains the `PicovoiceKeyManager` class, which is responsible for providing the
 * access key required by the Picovoice SDK. It prioritizes a user-provided key, falls back to a
 * cached key, and as a last resort, fetches a new key from a remote gateway API.
 */
package com.blurr.voice.api

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.blurr.voice.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import com.blurr.voice.utilities.NetworkConnectivityManager
import com.blurr.voice.utilities.UserIdManager
import com.blurr.voice.utilities.UserProfileManager
import com.blurr.voice.utilities.NetworkNotifier

/**
 * Manages the Picovoice access key by fetching it from an API and caching it locally.
 *
 * This class handles the logic for obtaining the Picovoice access key, which is essential for
 * initializing the wake word and speech-to-text engines. It follows a clear priority:
 * 1. Use a key manually provided by the user.
 * 2. Use a key previously fetched and cached in SharedPreferences.
 * 3. Fetch a new key from the remote gateway API and cache it.
 *
 * @param context The application context, used for accessing SharedPreferences.
 */
class PicovoiceKeyManager(private val context: Context) {
    
    /**
     * Companion object holding constants for SharedPreferences keys and API configuration.
     */
    companion object {
        private const val TAG = "PicovoiceKeyManager"
        private const val PREFS_NAME = "PicovoicePrefs"
        private const val KEY_ACCESS_KEY = "access_key"
        private const val KEY_USER_PROVIDED_KEY = "user_provided_access_key"
        private const val API_URL = BuildConfig.GCLOUD_GATEWAY_URL
        private const val API_KEY_HEADER = "x-api-key"
        private const val API_KEY_VALUE = BuildConfig.GCLOUD_GATEWAY_PICOVOICE_KEY
        private const val DEVICE_ID_HEADER = "x-device-id"
    }

    /** SharedPreferences instance for caching the access key. */
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** OkHttpClient for making the API request to fetch the key. */
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    
    /**
     * Gets the Picovoice access key from the most reliable source available.
     *
     * This is the main public method for retrieving the key. It checks for a user-provided key,
     * then a cached key, and finally fetches a new one from the API if necessary.
     *
     * @return The access key as a [String], or null if no key can be obtained.
     */
    suspend fun getAccessKey(): String? = withContext(Dispatchers.IO) {
        try {
            val userKey = getUserProvidedKey()
            if (!userKey.isNullOrBlank()) {
                Log.d(TAG, "Using user-provided Picovoice access key")
                return@withContext userKey
            }

            val cachedKey = getCachedAccessKey()
            if (cachedKey != null) {
                Log.d(TAG, "Using cached Picovoice access key")
                return@withContext cachedKey
            }
            
            Log.d(TAG, "Fetching new Picovoice access key from API")
            val newKey = fetchAccessKeyFromApi()
            if (newKey != null) {
                saveAccessKeyToCache(newKey)
                Log.d(TAG, "Successfully fetched and cached new Picovoice access key")
                return@withContext newKey
            } else {
                Log.e(TAG, "Failed to fetch access key from API")
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting access key", e)
            return@withContext null
        }
    }

    /**
     * Fetches a new access key from the remote gateway API.
     *
     * This private function handles the network request to the gateway, including authentication
     * headers and network connectivity checks.
     *
     * @return The fetched access key as a [String], or null if the request fails.
     */
    private suspend fun fetchAccessKeyFromApi(): String? = withContext(Dispatchers.IO) {
        try {
            val isOnline = NetworkConnectivityManager(context).isNetworkAvailable()
            if (!isOnline) {
                Log.e(TAG, "No internet connection. Skipping Picovoice key fetch.")
                NetworkNotifier.notifyOffline()
                return@withContext null
            }

            var userEmail = UserProfileManager(context).getEmail()

            if(userEmail==null){
                userEmail = UserIdManager(context).getOrCreateUserId()
                Log.d(TAG,userEmail)
            }
            val request = Request.Builder()
                .url(API_URL)
                .header(API_KEY_HEADER, API_KEY_VALUE)
                .header(DEVICE_ID_HEADER, userEmail)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "API request failed with code: ${response.code}")
                    return@withContext null
                }
                
                val responseBody = response.body?.string()
                if (responseBody.isNullOrEmpty()) {
                    Log.e(TAG, "Empty response from API")
                    return@withContext null
                }
                
                val accessKey = responseBody.trim()
                if (accessKey.isNotEmpty()) {
                    Log.d(TAG, "Successfully fetched access key from API")
                    return@withContext accessKey
                } else {
                    Log.e(TAG, "Empty access key in response")
                    return@withContext null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching access key from API", e)
            return@withContext null
        }
    }
    
    /**
     * Retrieves the cached access key from SharedPreferences.
     *
     * @return The cached key, or null if it doesn't exist.
     */
    private fun getCachedAccessKey(): String? {
        return sharedPreferences.getString(KEY_ACCESS_KEY, null)
    }
    
    /**
     * Saves the fetched access key to SharedPreferences for future use.
     *
     * @param accessKey The key to be cached.
     */
    private fun saveAccessKeyToCache(accessKey: String) {
        sharedPreferences.edit {
            putString(KEY_ACCESS_KEY, accessKey)
        }
    }
    
    /**
     * Clears the cached, API-fetched access key from SharedPreferences.
     * This is useful for testing or forcing a refresh.
     */
    fun clearCache() {
        sharedPreferences.edit {
            remove(KEY_ACCESS_KEY)
        }
        Log.d(TAG, "Cleared cached Picovoice access key")
    }

    /**
     * Saves a user-provided access key to SharedPreferences.
     *
     * This key will be prioritized over any cached or fetched key.
     *
     * @param accessKey The access key provided by the user.
     */
    fun saveUserProvidedKey(accessKey: String) {
        sharedPreferences.edit {
            putString(KEY_USER_PROVIDED_KEY, accessKey)
        }
    }

    /**
     * Retrieves the user-provided access key from SharedPreferences.
     *
     * @return The user-provided key, or null if it has not been set.
     */
    fun getUserProvidedKey(): String? {
        return sharedPreferences.getString(KEY_USER_PROVIDED_KEY, null)
    }
} 