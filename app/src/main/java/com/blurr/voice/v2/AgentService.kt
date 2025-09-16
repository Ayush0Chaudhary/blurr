package com.blurr.voice.v2

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.blurr.voice.ConversationalAgentService
import com.blurr.voice.utilities.ApiKeyManager
import com.blurr.voice.api.Eyes
import com.blurr.voice.api.Finger
import com.blurr.voice.utilities.VisualFeedbackManager
import com.blurr.voice.v2.actions.ActionExecutor
import com.blurr.voice.v2.fs.FileSystem
import com.blurr.voice.v2.llm.GeminiApi
import com.blurr.voice.v2.message_manager.MemoryManager
import com.blurr.voice.v2.perception.Perception
import com.blurr.voice.v2.perception.SemanticParser
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * A Foreground Service responsible for hosting and running the AI Agent.
 *
 * This service manages the entire lifecycle of the agent, from initializing its components
 * to running its main loop in a background coroutine. It starts as a foreground service
 * to ensure the OS does not kill it while it's performing a long-running task.
 */
class AgentService : Service() {

    private val TAG = "AgentService"

    // A dedicated coroutine scope tied to the service's lifecycle.
    // Using a SupervisorJob ensures that if one child coroutine fails, it doesn't cancel the whole scope.
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val visualFeedbackManager by lazy { VisualFeedbackManager.getInstance(this) }

    // Declare agent and its dependencies. They will be initialized in onCreate.
    private lateinit var agent: Agent
    private lateinit var settings: AgentSettings
    private lateinit var fileSystem: FileSystem
    private lateinit var memoryManager: MemoryManager
    private lateinit var perception: Perception
    private lateinit var llmApi: GeminiApi
    private lateinit var actionExecutor: ActionExecutor
    
    // Firebase instances for task tracking
    private val db = Firebase.firestore
    private val auth = Firebase.auth

//    companion object {
//        private const val NOTIFICATION_CHANNEL_ID = "AgentServiceChannel"
//        private const val NOTIFICATION_ID = 1
//        private const val EXTRA_TASK = "com.blurr.voice.v2.EXTRA_TASK"
//
//        /**
//         * A helper function to easily start the service from anywhere in the app (e.g., an Activity or ViewModel).
//         *
//         * @param context The application context.
//         * @param task The user's high-level task for the agent to perform.
//         */
//        fun start(context: Context, task: String) {
//            Log.d("AgentService", "Starting service with task: $task")
//            val intent = Intent(context, AgentService::class.java).apply {
//                putExtra(EXTRA_TASK, task)
//            }
//            context.startService(intent)
//        }
//    }
//
//
    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "AgentServiceChannelV2"
        private const val NOTIFICATION_ID = 14
        private const val EXTRA_TASK = "com.blurr.voice.v2.EXTRA_TASK"
        private const val ACTION_STOP_SERVICE = "com.blurr.voice.v2.ACTION_STOP_SERVICE"
        private const val ACTION_STOP_PANDA = "com.blurr.voice.v2.ACTION_STOP_PANDA"

        @Volatile
        var isRunning: Boolean = false
            private set // Allow external read, but only internal write

        @Volatile
        var currentTask: String? = null
            private set // Allow external read, but only internal write

        /**
         * A public method to request the service to stop from outside.
         */
        fun stop(context: Context) {
            Log.d("AgentService", "External stop request received.")
            val intent = Intent(context, AgentService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.startService(intent)
        }

        fun start(context: Context, task: String) {
            Log.d("AgentService", "Starting service with task: $task")
            val intent = Intent(context, AgentService::class.java).apply {
                putExtra(EXTRA_TASK, task)
            }
            context.startService(intent)
        }
    }
    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate: Service is being created.")
        visualFeedbackManager.showTtsWave()

        // Create the notification channel required for foreground services on Android 8.0+
        createNotificationChannel()

        // --- Initialize all the agent's components here ---
        // This is the logic from your example, now placed within the service's lifecycle.
        settings = AgentSettings() // Use default settings for now
        fileSystem = FileSystem(this)
        // Pass an empty initial task; it will be updated in onStartCommand
        memoryManager = MemoryManager(this, "", fileSystem, settings)
        // Assuming Eyes, Finger, and SemanticParser can be instantiated directly
        perception = Perception(Eyes(this), SemanticParser())
        llmApi = GeminiApi(
            "gemini-2.5-flash",
            apiKeyManager = ApiKeyManager,
            maxRetry = 10
        ) // Or your preferred model
        actionExecutor = ActionExecutor(Finger(this))

        // Finally, create the Agent instance with all its dependencies
        agent = Agent(
            settings,
            memoryManager,
            perception,
            llmApi,
            actionExecutor,
            fileSystem,
            this
        )
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: Service has been started with action: ${intent?.action}")
        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                Log.i(TAG, "Received stop action. Stopping agent service.")
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_STOP_PANDA -> {
                Log.i(TAG, "Received stop panda action. Stopping conversational agent.")
                // Stop the ConversationalAgentService
                val conversationalServiceIntent = Intent(this, ConversationalAgentService::class.java)
                stopService(conversationalServiceIntent)

                // Also stop this agent service
                Log.i(TAG, "Stopping agent service as well.")
                stopSelf()
                return START_NOT_STICKY
            }
        }

        // If no action, proceed with starting the task
        val task = intent?.getStringExtra(EXTRA_TASK)
        if (task.isNullOrBlank()) {
            Log.e(TAG, "Service started without a task. Stopping service.")
            stopSelf()
            return START_NOT_STICKY // Don't restart the service
        }
        isRunning = true
        currentTask = task


        // Start the service in the foreground.
        val notification = createNotification("Agent is running task: $task")
        try {
            startForeground(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to start foreground service: ${e.message}")
            stopSelf()
            return START_NOT_STICKY
        }

        // Track the task in Firebase before starting execution
        serviceScope.launch {
            trackTaskInFirebase(task)
        }

        // Launch the agent's main run loop in a background coroutine.
        // This prevents blocking the main thread.
        serviceScope.launch {
            try {
                Log.i(TAG, "Launching agent run loop for task: $task")
                agent.run(task)
                // Track task completion
                trackTaskCompletion(task, true)
            } catch (e: Exception) {
                Log.e(TAG, "Agent run failed with an exception", e)
                // Track task failure
                trackTaskCompletion(task, false, e.message)
                // You could update the notification here to show an error state
            } finally {
                Log.i(TAG, "Agent run loop finished. Stopping service.")
                stopSelf() // Stop the service once the task is complete or has failed
            }
        }

        // If the service is killed by the system, it will not be automatically restarted.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy: Service is being destroyed.")
        // RESET STATUS
        isRunning = false
        currentTask = null

        // Cancel the coroutine scope to clean up the agent's running job and prevent leaks.
        serviceScope.cancel()
        visualFeedbackManager.hideTtsWave()

    }

    /**
     * This service does not provide binding, so we return null.
     */
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    /**
     * Creates the NotificationChannel for the foreground service.
     * This is required for Android 8.0 (API level 26) and higher.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Agent Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    /**
     * Creates the persistent notification for the foreground service.
     */
    private fun createNotification(contentText: String): Notification {
        // --- FIX: Stop Agent Button ---
        // Using FLAG_CANCEL_CURRENT ensures that a new, fresh PendingIntent is created,
        // which resolves issues where the intent would not be delivered if the service was restarted.
        // Using a unique request code (0) for this intent.
        val stopAgentIntent = Intent(this, AgentService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopAgentPendingIntent = PendingIntent.getService(
            this,
            0, // Request code
            stopAgentIntent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // --- NEW: Stop Panda Button ---
        // This creates a new action to stop the ConversationalAgentService.
        // It uses a different request code (1) to ensure it's a unique PendingIntent.
        val stopPandaIntent = Intent(this, AgentService::class.java).apply {
            action = ACTION_STOP_PANDA
        }
        val stopPandaPendingIntent = PendingIntent.getService(
            this,
            1, // Unique request code
            stopPandaIntent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Panda Agent Active")
            .setContentText(contentText)
            // Stop Agent button - stops just the current task
            .addAction(
                android.R.drawable.ic_media_pause,
                "Stop Task",
                stopAgentPendingIntent
            )
            // Stop Panda button - stops the whole assistant
            .addAction(
                android.R.drawable.ic_delete, // A more definitive "stop everything" icon
                "Stop Panda",
                stopPandaPendingIntent
            )
            .setOngoing(true)
            // .setSmallIcon(R.drawable.ic_agent_notification) // TODO: Add a proper icon
            .build()
    }

    /**
     * Tracks the task start in Firebase by appending it to the user's task history array.
     * This method is inspired by FreemiumManager's Firebase operations.
     */
    private suspend fun trackTaskInFirebase(task: String) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.w(TAG, "Cannot track task, user is not logged in.")
            return
        }

        try {
            val taskEntry = hashMapOf(
                "task" to task,
                "status" to "started",
                "startedAt" to Timestamp.now(),
                "completedAt" to null,
                "success" to null,
                "errorMessage" to null
            )

            // Append the task to the user's taskHistory array
            db.collection("users").document(currentUser.uid)
                .update("taskHistory", FieldValue.arrayUnion(taskEntry))
                .await()

            Log.d(TAG, "Successfully tracked task start in Firebase for user ${currentUser.uid}: $task")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to track task in Firebase", e)
            // Don't fail the task execution if Firebase tracking fails
        }
    }

    /**
     * Updates the task completion status in Firebase.
     * Since Firestore doesn't support updating array elements directly,
     * we'll add a new completion entry to track the result.
     */
    private suspend fun trackTaskCompletion(task: String, success: Boolean, errorMessage: String? = null) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.w(TAG, "Cannot track task completion, user is not logged in.")
            return
        }

        try {
            val completionEntry = hashMapOf(
                "task" to task,
                "status" to if (success) "completed" else "failed",
//                "startedAt" to null, // This is a completion entry, not a start entry
                "completedAt" to Timestamp.now(),
                "success" to success,
                "errorMessage" to errorMessage
            )

            // Append the completion status to the user's taskHistory array
            db.collection("users").document(currentUser.uid)
                .update("taskHistory", FieldValue.arrayUnion(completionEntry))
                .await()

            Log.d(TAG, "Successfully tracked task completion in Firebase for user ${currentUser.uid}: $task (success: $success)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to track task completion in Firebase", e)
        }
    }
}
