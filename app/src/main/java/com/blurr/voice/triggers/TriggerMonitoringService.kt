/**
 * @file TriggerMonitoringService.kt
 * @brief Defines a foreground service for monitoring non-alarm-based triggers.
 *
 * This file contains the `TriggerMonitoringService`, which is responsible for monitoring
 * triggers that rely on system broadcasts, such as charging state changes. It runs as a
 * foreground service to ensure it is not killed by the system.
 */
package com.blurr.voice.triggers

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.blurr.voice.MainActivity
import com.blurr.voice.R

/**
 * A foreground [Service] for monitoring triggers that are not based on `AlarmManager`.
 *
 * This service is essential for triggers that require a long-running process to listen for
 * system broadcasts, such as `ACTION_POWER_CONNECTED` and `ACTION_POWER_DISCONNECTED`.
 * It runs in the foreground with a persistent notification to prevent the system from
 * terminating it.
 */
class TriggerMonitoringService : Service() {

    private val TAG = "TriggerMonitoringSvc"
    /** The receiver for handling charging state changes. */
    private val chargingStateReceiver = ChargingStateReceiver()

    /**
     * Companion object for service-related constants.
     */
    companion object {
        /** The ID for the notification channel used by this service. */
        const val CHANNEL_ID = "TriggerMonitoringServiceChannel"
    }

    /**
     * Called when the service is first created.
     * It creates the notification channel and registers the necessary broadcast receivers.
     */
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        createNotificationChannel()
        registerReceivers()
    }

    /**
     * Called every time the service is started.
     * It promotes the service to a foreground service by displaying a notification.
     * @return [START_STICKY] to ensure the service is restarted if killed by the system.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand")
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Blurr Trigger Service")
            .setContentText("Monitoring for app triggers in the background.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(1339, notification)

        return START_STICKY
    }

    /**
     * Called when the service is being destroyed.
     * It unregisters the broadcast receivers to prevent memory leaks.
     */
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service onDestroy")
        unregisterReceiver(chargingStateReceiver)
    }

    /**
     * This service does not support binding, so this method returns null.
     */
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    /**
     * Creates the notification channel required for the foreground service on Android Oreo and higher.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Trigger Monitoring Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    /**
     * Registers the broadcast receivers required for monitoring triggers.
     */
    private fun registerReceivers() {
        val intentFilter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        registerReceiver(chargingStateReceiver, intentFilter)
        Log.d(TAG, "ChargingStateReceiver registered")
    }
}
