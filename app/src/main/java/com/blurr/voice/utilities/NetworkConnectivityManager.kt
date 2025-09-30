/**
 * @file NetworkConnectivityManager.kt
 * @brief Provides a utility for checking and monitoring network connectivity.
 *
 * This file contains the `NetworkConnectivityManager` class, which offers robust methods
 * for checking the device's internet connection status and for listening to network state changes.
 */
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
 * A utility class to handle network connectivity checks and provide a clean API
 * for monitoring the device's internet connection.
 *
 * @param context The application context, used to get the `ConnectivityManager` system service.
 */
class NetworkConnectivityManager(private val context: Context) {
    
    /**
     * Companion object for constants used within the manager.
     */
    companion object {
        private const val TAG = "NetworkConnectivityManager"
        private const val CONNECTIVITY_TIMEOUT_MS = 5000L // 5 seconds
        private const val TEST_URL = "https://www.google.com"
    }
    
    /** The Android `ConnectivityManager` system service instance. */
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    /**
     * Checks if the device has an active and validated internet connection.
     *
     * This method first checks for a connected network and then attempts to reach a test URL
     * to confirm actual internet access.
     *
     * @return `true` if internet is available, `false` otherwise.
     */
    suspend fun isNetworkAvailable(): Boolean = withContext(Dispatchers.IO) {
        val sc = SpeechCoordinator.getInstance(context)

        try {

            if (!isNetworkConnected()) {
                sc.speakText("Network is not connected")
                Log.d(TAG, "Network is not connected")
                return@withContext false
            }

            return@withContext checkInternetConnectivity()
        } catch (e: Exception) {
            sc.speakText("Network is not connected")
            Log.e(TAG, "Error checking network availability", e)
            return@withContext false
        }
    }
    
    /**
     * Checks if the device is connected to any network (e.g., Wi-Fi, mobile data).
     * This does not guarantee actual internet access.
     * @return `true` if a network is connected, `false` otherwise.
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
     * Verifies actual internet access by attempting to open a connection to a reliable URL.
     * @return `true` if the connection is successful, `false` otherwise.
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
     * Checks network connectivity with a specified timeout.
     * @param timeoutMs The timeout for the check in milliseconds.
     * @return A [ConnectivityResult] indicating the outcome (Success, NoInternet, or Timeout).
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
     * Registers a callback to listen for network state changes.
     * @param callback The [NetworkCallback] to be invoked on state changes.
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
     * Unregisters a previously registered network callback.
     * @param callback The `ConnectivityManager.NetworkCallback` instance to unregister.
     */
    fun unregisterNetworkCallback(callback: ConnectivityManager.NetworkCallback) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }
    
    /**
     * Represents the result of a connectivity check.
     */
    sealed class ConnectivityResult {
        /** Indicates that the internet connection is available. */
        object Success : ConnectivityResult()
        /** Indicates that a network is connected but there is no internet access. */
        object NoInternet : ConnectivityResult()
        /** Indicates that the connectivity check timed out. */
        object Timeout : ConnectivityResult()
    }
    
    /**
     * An interface for receiving network state change notifications.
     */
    interface NetworkCallback {
        /** Called when a network with internet capability becomes available. */
        fun onNetworkAvailable()
        /** Called when the network is lost. */
        fun onNetworkLost()
    }
} 