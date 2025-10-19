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
import okhttp3.internal.platform.PlatformRegistry.applicationContext
import java.util.UUID

/**
 * Manages the Picovoice access key by fetching it from the API and caching it locally.
 * Only fetches the key once and keeps it permanently.
 */
class PicovoiceKeyManager(private val context: Context) {

    companion object {
        private const val TAG = "PicovoiceKeyManager"
        private const val PREFS_NAME = "PicovoicePrefs"
        private const val KEY_ACCESS_KEY = "access_key"
        // Inside companion object
        private const val KEY_USER_PROVIDED_KEY = "user_provided_access_key"
        private const val API_URL = BuildConfig.GCLOUD_GATEWAY_URL
        private const val API_KEY_HEADER = "x-api-key"
        private const val API_KEY_VALUE = BuildConfig.GCLOUD_GATEWAY_PICOVOICE_KEY
        private const val DEVICE_ID_HEADER = "x-device-id"
    }
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Gets the Picovoice access key. If not cached, fetches it from the API once.
     * @return The access key, or null if fetching failed
     */
    suspend fun getAccessKey(): String? = withContext(Dispatchers.IO) {
        try {
            val userKey = getUserProvidedKey()
            if (!userKey.isNullOrBlank()) {
                Log.d(TAG, "Using user-provided Picovoice access key")
                return@withContext userKey
            }
            // Check if we have a cached key
            val cachedKey = getCachedAccessKey()
            if (cachedKey != null) {
                Log.d(TAG, "Using cached Picovoice access key")
                return@withContext cachedKey
            }

            // Fetch new key from API (only once)
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
     * Fetches the access key from the API endpoint
     */
    private suspend fun fetchAccessKeyFromApi(): String? = withContext(Dispatchers.IO) {
        try {
            // Network check
            val isOnline = try {
                NetworkConnectivityManager(context).isNetworkAvailable()
            } catch (e: Exception) {
                false
            }
            if (!isOnline) {
                Log.e(TAG, "No internet connection. Skipping Picovoice key fetch.")
                com.blurr.voice.utilities.NetworkNotifier.notifyOffline()
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

                // The response is the key directly (base64 encoded)
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
     * Gets the cached access key from SharedPreferences
     */
    private fun getCachedAccessKey(): String? {
        return sharedPreferences.getString(KEY_ACCESS_KEY, null)
    }

    /**
     * Saves the access key to SharedPreferences permanently
     */
    private fun saveAccessKeyToCache(accessKey: String) {
        sharedPreferences.edit {
            putString(KEY_ACCESS_KEY, accessKey)
        }
    }

    /**
     * Clears the cached access key (useful for testing or forcing a refresh)
     */
    fun clearCache() {
        sharedPreferences.edit {
            remove(KEY_ACCESS_KEY)
        }
        Log.d(TAG, "Cleared cached Picovoice access key")
    }

    /**
     * Saves a key provided by the user to SharedPreferences.
     */
    fun saveUserProvidedKey(accessKey: String) {
        sharedPreferences.edit {
            putString(KEY_USER_PROVIDED_KEY, accessKey)
        }
    }

    /**
     * Gets the key provided by the user from SharedPreferences.
     */
    fun getUserProvidedKey(): String? {
        return sharedPreferences.getString(KEY_USER_PROVIDED_KEY, null)
    }
}