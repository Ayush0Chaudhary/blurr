package com.example.blurr

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.example.blurr.R // Make sure to import your R class
import com.example.blurr.agent.Judge
import com.example.blurr.api.Eyes
import com.example.blurr.api.Finger
import com.example.blurr.utilities.Persistent
import com.example.blurr.utilities.TTSManager
import com.example.blurr.utilities.addResponse
import com.example.blurr.utilities.getReasoningModelApiResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.text.isNotEmpty
import kotlin.text.uppercase
import androidx.core.graphics.scale
import com.example.blurr.ui.features.main.MainActivity

class ContentModerationService : Service() {

    private lateinit var handler: Handler
    private lateinit var moderationRunnable: Runnable

    // Create objects once to prevent memory leaks
    private lateinit var ttsManager: TTSManager
    private lateinit var finger: Finger
    private lateinit var eyes: Eyes
    private lateinit var judge: Judge
    private lateinit var persistent: Persistent


    companion object {
        const val NOTIFICATION_CHANNEL_ID = "ContentModerationChannel"
        var isRunning = false // Add this flag
    }

    override fun onCreate() {
        super.onCreate()
        handler = Handler(Looper.getMainLooper())

        // Initialize objects once when the service is created
        ttsManager = TTSManager.getInstance(this)
        finger = Finger(this)
        eyes = Eyes(this)

        // initializing Agents
        judge = Judge()
        persistent = Persistent()

        isRunning = true
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val instruction = intent?.getStringExtra("MODERATION_INSTRUCTION") ?: "Default Instruction"

        Log.d("ModerationService", "Service starting with instruction: $instruction")

        //TODO: This is not working, i want notification telling user the content moderation has started
        createNotificationChannel()

        // Create an intent that will open your app when the notification is tapped
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        // Build the notification using NotificationCompat for best compatibility
        val notification: Notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Content Moderation Enabled")
            .setContentText("Monitoring screen content. Tap to return to app.")
            .setSmallIcon(R.mipmap.ic_launcher) // Make sure you have this icon
            .setContentIntent(pendingIntent) // Set the tap action
            .setOngoing(true) // Makes the notification non-swipeable
            .build()

        startForeground(1, notification)

        moderationRunnable = object : Runnable {
            override fun run() {
                CoroutineScope(Dispatchers.IO).launch {
                    contentModeration(instruction)
                }
            }
        }

        // Start the loop
        handler.post(moderationRunnable)

        return START_REDELIVER_INTENT
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(moderationRunnable)
        ttsManager.shutdown()
        Log.d("ModerationService", "Service stopped and loop cancelled.")
        isRunning = false
    }

    override fun onBind(intent: Intent?): IBinder? {
        // We don't need to bind to this service, so return null
        return null
    }

    // --- MOVE YOUR contentModeration LOGIC HERE ---
    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun contentModeration(inst: String) {
        Log.d("ModerationService", "contentModeration function started")
        val startTime = System.currentTimeMillis()
        try {
            var screenshotBitmap = eyes.openEyes()
            val API_KEY = "AIzaSyBlepfkVTJAS6oVquyYlctE299v8PIFbQg"

            // 3. Prepare AI Prompt
            val judge = Judge()
            val init = judge.initChat()
            val xml = eyes.openXMLEyes()
            println(xml);
            val pro = judge.getPrompt(inst, xml, true)

            // Check if the screenshot was captured successfully
            if (screenshotBitmap != null) {
                val scaledBitmap = scaleDownBitmap(screenshotBitmap, 720)

                // If the original bitmap is not the same as the scaled one,
                // it means a new, smaller bitmap was created, so we can recycle the original large one.
                if (screenshotBitmap != scaledBitmap) {
                    screenshotBitmap.recycle()
                }
                screenshotBitmap = scaledBitmap // Work with the smaller bitmap from now on

                // (Optional but Recommended) Save the bitmap for debugging in the background
                CoroutineScope(Dispatchers.IO).launch {
                    var persentance = Persistent()
                    persentance.saveBitmapForDebugging(screenshotBitmap)
                }

                // 4. Call the AI with the in-memory bitmap
                val combined = addResponse("user", pro, init,screenshotBitmap)
                val output = getReasoningModelApiResponse(combined, apiKey = API_KEY)
                val parsed = judge.parseResponse(output.toString())

                println("JUDGEMENT: ${parsed["judgement"]}")
                println("REASON: ${parsed["reason"]}")

                // 5. Act on the result
                if (parsed["judgement"]?.isNotEmpty() == true && parsed["judgement"]?.uppercase() == "B") {
                    ttsManager.speakText(parsed["reason"].toString())
                    finger.goToChatRoom(parsed["reason"].toString().replace("\"", ""))
                }
            } else {
                Log.e("ContentModeration", "Failed to capture screenshot.")
            }

        } catch (e: Exception) {
            // Now you can handle errors from any suspend function in one place
            Log.e("ContentModeration", "An error occurred during the moderation process", e)
        } finally {
                handler.postDelayed(moderationRunnable, 8000)
                Log.d("ModerationService", "Next moderation cycle scheduled in 8 seconds.")
        }

        println("Total time: ${System.currentTimeMillis() - startTime}ms")
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        // A NotificationChannel is only needed on API 26+
        val serviceChannel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Content Moderation Service", // Name visible to the user in settings
            NotificationManager.IMPORTANCE_LOW // Use LOW to prevent sound/vibration
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(serviceChannel)
    }


    private fun scaleDownBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val originalWidth = bitmap.width
        val originalHeight = bitmap.height
        var resizedWidth = originalWidth
        var resizedHeight = originalHeight

        if (originalHeight > maxDimension || originalWidth > maxDimension) {
            if (originalWidth > originalHeight) {
                resizedWidth = maxDimension
                resizedHeight = (resizedWidth * originalHeight) / originalWidth
            } else {
                resizedHeight = maxDimension
                resizedWidth = (resizedHeight * originalWidth) / originalHeight
            }
        } else {
            return bitmap // No need to scale down
        }
        return bitmap.scale(resizedWidth, resizedHeight)
    }
}