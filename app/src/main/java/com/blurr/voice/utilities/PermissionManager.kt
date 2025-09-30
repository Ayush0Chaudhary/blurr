/**
 * @file PermissionManager.kt
 * @brief Provides a centralized utility for handling Android runtime permissions.
 *
 * This file contains the `PermissionManager` class, which simplifies the process of
 * checking for and requesting various permissions required by the application, such as
 * Microphone, Notifications, Accessibility Service, and Draw Over Other Apps.
 */
package com.blurr.voice.utilities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.blurr.voice.ScreenInteractionService

/**
 * A utility class to handle all permission-related functionality for a given [AppCompatActivity].
 *
 * This manager provides a unified interface for checking and requesting the various permissions
 * needed for the app to function correctly. It must be initialized with an activity instance.
 *
 * @param activity The [AppCompatActivity] from which permissions will be requested.
 */
class PermissionManager(private val activity: AppCompatActivity) {

        /** The launcher for handling permission request results. */
        private var permissionLauncher: ActivityResultLauncher<String>? = null
        /** A callback to be invoked when a permission request result is received. */
        private var onPermissionResult: ((String, Boolean) -> Unit)? = null

    /**
     * Checks if all essential permissions (Accessibility, Microphone, Overlay, Notifications)
     * have been granted.
     * @return `true` if all necessary permissions are granted, `false` otherwise.
     */
    fun areAllPermissionsGranted(): Boolean {
        return isAccessibilityServiceEnabled() &&
                isMicrophonePermissionGranted() &&
                isOverlayPermissionGranted() &&
                isNotificationPermissionGranted()
    }

        /**
         * Initializes the permission launcher. This must be called, typically in the `onCreate`
         * method of the activity, before any permission requests are made.
         */
        fun initializePermissionLauncher() {
                permissionLauncher = activity.registerForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
                ) { isGranted: Boolean ->
                        onPermissionResult?.invoke("", isGranted)
                        
                        if (isGranted) {
                            Log.i("PermissionManager", "Permission GRANTED.")
                            Toast.makeText(activity, "Permission granted!", Toast.LENGTH_SHORT).show()
                        } else {
                            Log.w("PermissionManager", "Permission DENIED.")
                            Toast.makeText(activity, "Permission denied. Some features may not work properly.", Toast.LENGTH_LONG).show()
                        }
                    }
            }

        /**
         * Requests the `POST_NOTIFICATIONS` permission, required on Android 13 (API 33) and higher.
         */
        fun requestNotificationPermission() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        when {
                            ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) ==
                                    PackageManager.PERMISSION_GRANTED -> {
                                Log.i("PermissionManager", "Notification permission is already granted.")
                            }
                            activity.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                                Log.w("PermissionManager", "Showing rationale and requesting notification permission.")
                                permissionLauncher?.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            else -> {
                                Log.i("PermissionManager", "Requesting notification permission for the first time.")
                                permissionLauncher?.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                    }
            }

        /**
         * Requests the `RECORD_AUDIO` permission, required for voice input.
         */
        fun requestMicrophonePermission() {
                when {
                        ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) ==
                                PackageManager.PERMISSION_GRANTED -> {
                            Log.i("PermissionManager", "Microphone permission is already granted.")
                        }
                        activity.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) -> {
                            Log.w("PermissionManager", "Showing rationale and requesting microphone permission.")
                            permissionLauncher?.launch(Manifest.permission.RECORD_AUDIO)
                        }
                        else -> {
                            Log.i("PermissionManager", "Requesting microphone permission for the first time.")
                            permissionLauncher?.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
            }

        /**
         * A convenience method to request all standard runtime permissions at once.
         */
        fun requestAllPermissions() {
                requestNotificationPermission()
                requestMicrophonePermission()
            }

        /**
         * Checks if the `RECORD_AUDIO` permission has been granted.
         * @return `true` if the permission is granted, `false` otherwise.
         */
        fun isMicrophonePermissionGranted(): Boolean {
                return ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED
            }

        /**
         * Checks if the `POST_NOTIFICATIONS` permission has been granted.
         * Always returns true for versions below Android 13.
         * @return `true` if the permission is granted or not required, `false` otherwise.
         */
        fun isNotificationPermissionGranted(): Boolean {
                return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) ==
                                PackageManager.PERMISSION_GRANTED
                    } else {
                        true
                    }
            }

        /**
         * Checks if the application's [ScreenInteractionService] is enabled in the system's
         * accessibility settings.
         * @return `true` if the service is enabled, `false` otherwise.
         */
        fun isAccessibilityServiceEnabled(): Boolean {
                val service = activity.packageName + "/" + ScreenInteractionService::class.java.canonicalName
                val accessibilityEnabled = Settings.Secure.getInt(
                    activity.applicationContext.contentResolver,
                    Settings.Secure.ACCESSIBILITY_ENABLED,
                    0
                )
                if (accessibilityEnabled == 1) {
                        val settingValue = Settings.Secure.getString(
                            activity.applicationContext.contentResolver,
                            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                        )
                        if (settingValue != null) {
                            val splitter = TextUtils.SimpleStringSplitter(':')
                            splitter.setString(settingValue)
                            while (splitter.hasNext()) {
                                val componentName = splitter.next()
                                if (componentName.equals(service, ignoreCase = true)) {
                                    return true
                                }
                            }
                        }
                    }
                return false
            }

        /**
         * Opens the system's accessibility settings screen for the user.
         */
        fun openAccessibilitySettings() {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                activity.startActivity(intent)
            }

        /**
         * Checks for the "Draw over other apps" permission and, if not granted, opens the
         * corresponding system settings screen for the user.
         */
        fun checkAndRequestOverlayPermission() {
                if (!Settings.canDrawOverlays(activity)) {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${activity.packageName}")
                        )
                        activity.startActivity(intent)
                    }
            }

        /**
         * Checks if the "Draw over other apps" permission has been granted.
         * @return `true` if the permission is granted, `false` otherwise.
         */
        fun isOverlayPermissionGranted(): Boolean {
                return Settings.canDrawOverlays(activity)
            }

        /**
         * Sets a callback to be invoked with the result of a permission request.
         * @param callback A lambda function that takes the permission name (String) and a
         *                 boolean indicating if it was granted.
         */
        fun setPermissionResultCallback(callback: (String, Boolean) -> Unit) {
                onPermissionResult = callback
            }

        /**
         * Generates a summary string of the current status of all major permissions.
         * @return A comma-separated string indicating the status of each permission (e.g., "Microphone: ✓, ...").
         */
        fun getPermissionStatusSummary(): String {
                val status = mutableListOf<String>()
                
                if (isMicrophonePermissionGranted()) {
                        status.add("Microphone: ✓")
                    } else {
                        status.add("Microphone: ✗")
                    }
                
                if (isNotificationPermissionGranted()) {
                        status.add("Notifications: ✓")
                    } else {
                        status.add("Notifications: ✗")
                    }
                
                if (isAccessibilityServiceEnabled()) {
                        status.add("Accessibility: ✓")
                    } else {
                        status.add("Accessibility: ✗")
                    }
                
                if (isOverlayPermissionGranted()) {
                        status.add("Overlay: ✓")
                    } else {
                        status.add("Overlay: ✗")
                    }
                
                return status.joinToString(", ")
            }
}