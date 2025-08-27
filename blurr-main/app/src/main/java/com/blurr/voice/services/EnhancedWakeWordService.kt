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
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.blurr.voice.ConversationalAgentService
import com.blurr.voice.MainActivity
import com.blurr.voice.R
import com.blurr.voice.api.PorcupineWakeWordDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class EnhancedWakeWordService : Service() {

    private var porcupineDetector: PorcupineWakeWordDetector? = null
    private var usePorcupine = false
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

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
            .setContentText("Listening for 'Panda' with Porcupine engine...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(1338, notification)

        // Start the appropriate wake word detector
        startWakeWordDetection()

        return START_STICKY
    }

    private fun startWakeWordDetection() {
        val onWakeWordDetected: () -> Unit = {
            // Check if the conversational agent isn't already running
            if (!ConversationalAgentService.isRunning) {
                val serviceIntent = Intent(this, ConversationalAgentService::class.java)
                ContextCompat.startForegroundService(this, serviceIntent)

                Toast.makeText(this, "Panda listening...", Toast.LENGTH_SHORT).show()
            } else {
                Log.d("EnhancedWakeWordService", "Conversational agent is already running.")
            }
        }

        val onApiFailure: () -> Unit = {
            Log.d("EnhancedWakeWordService", "Porcupine API failed, starting floating button service")
            // Start the floating button service when API fails
            try {
                val floatingButtonIntent = Intent(this, FloatingPandaButtonService::class.java)
                startService(floatingButtonIntent)
                Log.d("EnhancedWakeWordService", "Floating Panda Button Service started due to API failure")
            } catch (e: Exception) {
                Log.e("EnhancedWakeWordService", "Error starting floating button service", e)
            }
        }

        try {
            if (usePorcupine) {
                Log.d("EnhancedWakeWordService", "Using Porcupine wake word detection")
                porcupineDetector = PorcupineWakeWordDetector(this, onWakeWordDetected, onApiFailure)
                porcupineDetector?.start()
            } else {
                Log.d("EnhancedWakeWordService", "Using Porcupine wake word detection")
                porcupineDetector = PorcupineWakeWordDetector(this, onWakeWordDetected, onApiFailure)
                porcupineDetector?.start()
            }
        } catch (e: Exception) {
            Log.e("EnhancedWakeWordService", "Error starting wake word detection: ${e.message}")
            // If there's any error, start the floating button service
            onApiFailure()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        
        Log.d("EnhancedWakeWordService", "Service onDestroy() called")
        
        porcupineDetector?.stop()
        porcupineDetector = null
        
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