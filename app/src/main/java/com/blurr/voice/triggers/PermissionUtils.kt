/**
 * @file PermissionUtils.kt
 * @brief Provides utility functions for checking critical runtime permissions.
 *
 * This file contains the `PermissionUtils` object, which offers helper methods to verify
 * if the app has been granted specific, sensitive permissions that are required for
 * trigger functionality, such as listening to notifications and scheduling exact alarms.
 */
package com.blurr.voice.triggers

import android.content.Context
import android.provider.Settings
import android.app.AlarmManager
import android.os.Build

/**
 * A utility object containing helper methods for checking system permissions required by triggers.
 */
object PermissionUtils {

    /**
     * Checks if the application's [PandaNotificationListenerService] has been enabled by the user.
     *
     * To listen to notifications, the user must manually grant this permission in the system settings.
     *
     * @param context The application context.
     * @return `true` if the notification listener service is enabled, `false` otherwise.
     */
    fun isNotificationListenerEnabled(context: Context): Boolean {
        val enabledListeners = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        val componentName = PandaNotificationListenerService::class.java.canonicalName
        return enabledListeners?.contains(componentName) == true
    }

    /**
     * Checks if the application has permission to schedule exact alarms.
     *
     * On Android S (API 31) and higher, this is a special permission that the user must grant.
     * On older versions, this permission is granted by default.
     *
     * @param context The application context.
     * @return `true` if the app can schedule exact alarms, `false` otherwise.
     */
    fun canScheduleExactAlarms(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else {
            true // Permission is granted by default on older versions
        }
    }
}
