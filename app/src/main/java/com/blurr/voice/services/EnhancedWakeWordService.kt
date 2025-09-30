/**
 * @file EnhancedWakeWordService.kt
 * @brief A foreground service dedicated to running the wake word detection engine.
 *
 * This service is responsible for managing the lifecycle of the wake word detector,
 * ensuring it runs persistently in the background. It shows a foreground notification
 * to the user and handles the necessary permissions and engine initialization.
 */
package com.blurr.voice.services

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
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

/**
 * A foreground [Service] that continuously listens for a wake word ("Panda").
 *
 * This service manages a [PorcupineWakeWordDetector] instance. It handles starting the
 * service in the foreground with a persistent notification, checking for audio permissions,
 * and starting the appropriate detector. If the detector fails to initialize (e.g., API key
 * failure), it broadcasts an intent to notify other components to fall back to an
 * alternative, like a floating action button.
 */
class EnhancedWakeWordService : Service() {

    /** The wake word detector instance, currently hardcoded to [PorcupineWakeWordDetector]. */
    private var porcupineDetector: PorcupineWakeWordDetector? = null
    /** A flag to determine whether to use the Porcupine engine. Note: Currently, this is always used. */
    private var usePorcupine = false
    /** A CoroutineScope for managing service-related coroutines. */
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /**
     * Companion object for constants and static properties.
     */
    companion object {
        /** The ID for the notification channel used by this service. */
        const val CHANNEL_ID = "EnhancedWakeWordServiceChannel"
        /** A static flag to indicate if the service is currently running. */
        var isRunning = false
        /** The broadcast action sent when the wake word engine fails to initialize. */
        const val ACTION_WAKE_WORD_FAILED = "com.blurr.voice.WAKE_WORD_FAILED"
        /** The intent extra key to specify whether to use the Porcupine engine. */
        const val EXTRA_USE_PORCUPINE = "use_porcupine"
    }

    /**
     * Called when the service is first created.
     */
    override fun onCreate() {
        super.onCreate()
        isRunning = true
        Log.d("EnhancedWakeWordService", "Service onCreate() called, isRunning set to true")
    }

    /**
     * Called every time the service is started.
     *
     * This method handles permission checks, creates the foreground notification, and
     * starts the wake word detection logic.
     *
     * @return [START_STICKY] to indicate that the service should be restarted if it's killed.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("EnhancedWakeWordService", "Service starting...")
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e("EnhancedWakeWordService", "RECORD_AUDIO permission not granted. Cannot start foreground service.")
            Toast.makeText(this, "Microphone permission required for wake word", Toast.LENGTH_LONG).show()
            isRunning = false
            stopSelf()
            return START_NOT_STICKY
        }
        
        usePorcupine = intent?.getBooleanExtra(EXTRA_USE_PORCUPINE, false) ?: false
        
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
            .setContentText("Listening for 'Panda' with Porcupine engine...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .build()

        try {
            startForeground(1338, notification)
        } catch (e: SecurityException) {
            Log.e("EnhancedWakeWordService", "Failed to start foreground service: ${e.message}")
            Toast.makeText(this, "Cannot start wake word service - permission missing", Toast.LENGTH_LONG).show()
            isRunning = false
            stopSelf()
            return START_NOT_STICKY
        }

        startWakeWordDetection()

        return START_STICKY
    }

    /**
     * Initializes and starts the wake word detector.
     *
     * This function sets up the callbacks for when the wake word is detected or when the
     * detector fails. When the wake word is heard, it starts the [ConversationalAgentService].
     * On failure, it broadcasts `ACTION_WAKE_WORD_FAILED`.
     *
     * Note: The logic currently uses `PorcupineWakeWordDetector` regardless of the `usePorcupine` flag.
     */
    private fun startWakeWordDetection() {
        val onWakeWordDetected: () -> Unit = {
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
            val intent = Intent(ACTION_WAKE_WORD_FAILED)
            sendBroadcast(intent)
            stopSelf()
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
            onApiFailure()
        }
    }

    /**
     * Called when the service is being destroyed.
     *
     * This method ensures that the wake word detector is stopped and cleaned up properly.
     */
    override fun onDestroy() {
        super.onDestroy()
        
        Log.d("EnhancedWakeWordService", "Service onDestroy() called")
        
        porcupineDetector?.stop()
        porcupineDetector = null
        
        isRunning = false
        Log.d("EnhancedWakeWordService", "Service destroyed, isRunning set to false")
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
                "Enhanced Wake Word Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }
} 