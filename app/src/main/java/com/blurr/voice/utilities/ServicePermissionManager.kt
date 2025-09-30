/**
 * @file ServicePermissionManager.kt
 * @brief Provides a utility for checking permissions from a Service context.
 *
 * This file contains the `ServicePermissionManager` class, which is a variation of
 * `PermissionManager` designed to be used from background services where UI interactions
 * like permission dialogs are not possible. It only provides methods to check the current
 * state of permissions.
 */
package com.blurr.voice.utilities

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import androidx.core.content.ContextCompat
import com.blurr.voice.ScreenInteractionService

/**
 * A utility class to handle permission checking from a non-Activity context (e.g., a Service).
 *
 * This class provides methods to check the status of essential permissions but does not include
 * methods to request them, as that requires an Activity context.
 *
 * @param context The application or service context.
 */
class ServicePermissionManager(private val context: Context) {

    /**
     * Checks if all essential permissions required by the services are granted.
     *
     * @return `true` if all necessary permissions are granted, `false` otherwise.
     */
    fun areAllPermissionsGranted(): Boolean {
        return isAccessibilityServiceEnabled() &&
                isMicrophonePermissionGranted() &&
                isOverlayPermissionGranted() &&
                isNotificationPermissionGranted()
    }

    /**
     * Checks if the `RECORD_AUDIO` permission has been granted.
     *
     * @return `true` if the permission is granted, `false` otherwise.
     */
    fun isMicrophonePermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
    }

    /**
     * Checks if the `POST_NOTIFICATIONS` permission has been granted.
     *
     * This is only relevant for Android 13 (API 33) and above.
     *
     * @return `true` if the permission is granted or not required for the current Android version, `false` otherwise.
     */
    fun isNotificationPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            true // Notification permission not required before Android 13
        }
    }

    /**
     * Checks if the application's [ScreenInteractionService] is enabled in the system's
     * accessibility settings.
     *
     * @return `true` if the service is enabled, `false` otherwise.
     */
    fun isAccessibilityServiceEnabled(): Boolean {
        val service = context.packageName + "/" + ScreenInteractionService::class.java.canonicalName
        val accessibilityEnabled = Settings.Secure.getInt(
            context.contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            0
        )
        if (accessibilityEnabled == 1) {
            val settingValue = Settings.Secure.getString(
                context.contentResolver,
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
     * Checks if the "Draw over other apps" permission has been granted.
     *
     * @return `true` if the permission is granted, `false` otherwise.
     */
    fun isOverlayPermissionGranted(): Boolean {
        return Settings.canDrawOverlays(context)
    }
}
