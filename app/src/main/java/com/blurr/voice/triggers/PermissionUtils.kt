package com.blurr.voice.triggers

import android.content.Context
import android.provider.Settings
import android.app.AlarmManager
import android.os.Build

object PermissionUtils {

    fun isNotificationListenerEnabled(context: Context): Boolean {
        val enabledListeners = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        val componentName = PandaNotificationListenerService::class.java.canonicalName
        return enabledListeners?.contains(componentName) == true
    }

    fun canScheduleExactAlarms(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else {
            true // Permission is granted by default on older versions
        }
    }
}
