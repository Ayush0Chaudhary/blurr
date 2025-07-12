package com.example.blurr.services

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
import com.example.blurr.R
import com.example.blurr.api.PorcupineWakeWordDetector
import com.example.blurr.api.WakeWordDetector
import com.example.blurr.ui.features.main.MainActivity
import kotlin.jvm.java

class EnhancedWakeWordService : Service() {

    private var porcupineDetector: PorcupineWakeWordDetector? = null
    private var sttDetector: WakeWordDetector? = null
    private var usePorcupine = false

    companion object {
        const val CHANNEL_ID = "EnhancedWakeWordServiceChannel"
        var isRunning = false
        const val EXTRA_USE_PORCUPINE = "use_porcupine"
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        Log.d("EnhancedWakeWordService", "Service onCreate() called, isRunning set to true")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("EnhancedWakeWordService", "Service starting...")
        
        usePorcupine = intent?.getBooleanExtra(EXTRA_USE_PORCUPINE, false) ?: false
        
        createNotificationChannel()

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val engineName = if (usePorcupine) "Porcupine" else "STT"
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Blurr Wake Word")
            .setContentText("Listening for 'Panda' with $engineName engine...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(1338, notification)

        // Start the appropriate wake word detector
        startWakeWordDetection()

        return START_STICKY
    }

    private fun startWakeWordDetection() {
        val onWakeWordDetected = {
            Log.d("EnhancedWakeWordService", "Wake word detected! Launching MainActivity.")
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(intent)
        }

        try {
            if (usePorcupine) {
                Log.d("EnhancedWakeWordService", "Starting Porcupine wake word detection")
                porcupineDetector = PorcupineWakeWordDetector(this, onWakeWordDetected)
                porcupineDetector?.start()
            } else {
                Log.d("EnhancedWakeWordService", "Starting STT-based wake word detection")
                sttDetector = WakeWordDetector(this, onWakeWordDetected)
                sttDetector?.start()
            }
        } catch (e: Exception) {
            Log.e("EnhancedWakeWordService", "Error starting wake word detection: ${e.message}")
            // Fallback to STT-based detection if there's any error
            try {
                Log.d("EnhancedWakeWordService", "Falling back to STT-based wake word detection")
                sttDetector = WakeWordDetector(this, onWakeWordDetected)
                sttDetector?.start()
            } catch (fallbackError: Exception) {
                Log.e("EnhancedWakeWordService", "Error in fallback wake word detection: ${fallbackError.message}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        
        Log.d("EnhancedWakeWordService", "Service onDestroy() called")
        
        porcupineDetector?.stop()
        porcupineDetector = null
        
        sttDetector?.stop()
        sttDetector = null
        
        isRunning = false
        Log.d("EnhancedWakeWordService", "Service destroyed, isRunning set to false")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Enhanced Wake Word Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }
} 