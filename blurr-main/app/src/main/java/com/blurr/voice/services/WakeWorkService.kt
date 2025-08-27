package com.blurr.voice.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.blurr.voice.MainActivity
import com.blurr.voice.R
import com.blurr.voice.api.WakeWordDetector

class WakeWordService : Service() {

    private lateinit var wakeWordDetector: WakeWordDetector

    companion object {
        const val CHANNEL_ID = "WakeWordServiceChannel"
        var isRunning = false
    }

    override fun onCreate() {
        super.onCreate()
        wakeWordDetector = WakeWordDetector(this) {
            // This is the callback that gets triggered when the wake word is detected
            Log.d("WakeWordService", "Wake word detected! Launching MainActivity.")
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(intent)
        }
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("WakeWordService", "Service starting...")
        createNotificationChannel()

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Blurr Wake Word")
            .setContentText("Listening for 'Panda'...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(1337, notification)

        // Start listening for the wake word
        wakeWordDetector.start()

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        wakeWordDetector.stop()
        isRunning = false
        Log.d("WakeWordService", "Service destroyed.")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Wake Word Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }
}