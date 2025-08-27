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
 * Utility class to handle all permission-related functionality
 */
class PermissionManager(private val activity: AppCompatActivity) {

        private var permissionLauncher: ActivityResultLauncher<String>? = null
        private var onPermissionResult: ((String, Boolean) -> Unit)? = null

    /**
     * NEW: Checks if all essential permissions are granted.
     */
    fun areAllPermissionsGranted(): Boolean {
        return isAccessibilityServiceEnabled() &&
                isMicrophonePermissionGranted() &&
                isOverlayPermissionGranted() &&
                isNotificationPermissionGranted()
    }

        /**
         * Initialize the permission launcher
         */
        fun initializePermissionLauncher() {
                permissionLauncher = activity.registerForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
                ) { isGranted: Boolean ->
                        val permission = onPermissionResult?.let { callback ->
                            callback("", isGranted)
                        }
                        
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
         * Request notification permission (Android 13+)
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
         * Request microphone permission for voice input
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
         * Request all necessary permissions for the app
         */
        fun requestAllPermissions() {
                requestNotificationPermission()
                requestMicrophonePermission()
            }

        /**
         * Check if microphone permission is granted
         */
        fun isMicrophonePermissionGranted(): Boolean {
                return ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED
            }

        /**
         * Check if notification permission is granted
         */
        fun isNotificationPermissionGranted(): Boolean {
                return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) ==
                                PackageManager.PERMISSION_GRANTED
                    } else {
                        true // Notification permission not required before Android 13
                    }
            }

        /**
         * Check if accessibility service is enabled
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
         * Open accessibility settings
         */
        fun openAccessibilitySettings() {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                activity.startActivity(intent)
            }

        /**
         * Check and request overlay permission
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
         * Check if overlay permission is granted
         */
        fun isOverlayPermissionGranted(): Boolean {
                return Settings.canDrawOverlays(activity)
            }

        /**
         * Set callback for permission results
         */
        fun setPermissionResultCallback(callback: (String, Boolean) -> Unit) {
                onPermissionResult = callback
            }

        /**
         * Get permission status summary
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