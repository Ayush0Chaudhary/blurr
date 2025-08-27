package com.blurr.voice.utilities

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.net.URL
import java.net.URLConnection

/**
 * Utility class to handle network connectivity checks and provide a clean API
 * for the rest of the app to check internet connectivity.
 */
class NetworkConnectivityManager(private val context: Context) {
    
    companion object {
        private const val TAG = "NetworkConnectivityManager"
        private const val CONNECTIVITY_TIMEOUT_MS = 5000L // 5 seconds timeout
        private const val TEST_URL = "https://www.google.com" // URL to test connectivity
    }
    
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    /**
     * Check if the device has an active internet connection
     * @return true if internet is available, false otherwise
     */
    suspend fun isNetworkAvailable(): Boolean = withContext(Dispatchers.IO) {
        val sc = SpeechCoordinator.getInstance(context)

        try {

            // First check if network is connected
            if (!isNetworkConnected()) {
                sc.speakText("Network is not connected")
                Log.d(TAG, "Network is not connected")
                return@withContext false
            }

            // Then check if internet is actually accessible
            return@withContext checkInternetConnectivity()
        } catch (e: Exception) {
            sc.speakText("Network is not connected")
            Log.e(TAG, "Error checking network availability", e)
            return@withContext false
        }
    }
    
    /**
     * Check if network is connected (doesn't guarantee internet access)
     */
    private fun isNetworkConnected(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork
            val networkCapabilities = connectivityManager.getNetworkCapabilities(network)
            networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                    networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            networkInfo?.isConnected == true
        }
    }
    
    /**
     * Check if internet is actually accessible by trying to connect to a test URL
     */
    private suspend fun checkInternetConnectivity(): Boolean = withTimeoutOrNull(CONNECTIVITY_TIMEOUT_MS) {
        try {
            val url = URL(TEST_URL)
            val connection: URLConnection = url.openConnection()
            connection.connectTimeout = 3000
            connection.readTimeout = 3000
            connection.connect()
            Log.d(TAG, "Internet connectivity confirmed")
            true
        } catch (e: IOException) {
            Log.d(TAG, "Internet connectivity check failed: ${e.message}")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during internet connectivity check", e)
            false
        }
    } ?: false
    
    /**
     * Check network connectivity with a timeout and return detailed result
     */
    suspend fun checkConnectivityWithTimeout(timeoutMs: Long = CONNECTIVITY_TIMEOUT_MS): ConnectivityResult {
        return try {
            withTimeout(timeoutMs) {
                val isAvailable = isNetworkAvailable()
                if (isAvailable) {
                    ConnectivityResult.Success
                } else {
                    ConnectivityResult.NoInternet
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Connectivity check failed with timeout", e)
            ConnectivityResult.Timeout
        }
    }
    
    /**
     * Register a network callback to listen for network state changes
     */
    fun registerNetworkCallback(callback: NetworkCallback) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val networkRequest = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            
            connectivityManager.registerNetworkCallback(networkRequest, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Log.d(TAG, "Network became available")
                    callback.onNetworkAvailable()
                }
                
                override fun onLost(network: Network) {
                    Log.d(TAG, "Network became unavailable")
                    callback.onNetworkLost()
                }
            })
        }
    }
    
    /**
     * Unregister network callback
     */
    fun unregisterNetworkCallback(callback: ConnectivityManager.NetworkCallback) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }
    
    /**
     * Result of connectivity check
     */
    sealed class ConnectivityResult {
        object Success : ConnectivityResult()
        object NoInternet : ConnectivityResult()
        object Timeout : ConnectivityResult()
    }
    
    /**
     * Callback interface for network state changes
     */
    interface NetworkCallback {
        fun onNetworkAvailable()
        fun onNetworkLost()
    }
} 