package com.blurr.voice.v2

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.blurr.voice.api.ApiKeyManager
import com.blurr.voice.api.Eyes
import com.blurr.voice.api.Finger
import com.blurr.voice.utilities.VisualFeedbackManager
import com.blurr.voice.v2.actions.ActionExecutor
import com.blurr.voice.v2.fs.FileSystem
import com.blurr.voice.v2.llm.GeminiApi
import com.blurr.voice.v2.message_manager.MemoryManager
import com.blurr.voice.v2.perception.Perception
import com.blurr.voice.v2.perception.SemanticParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

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

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "AgentServiceChannel"
        private const val NOTIFICATION_ID = 1
        private const val EXTRA_TASK = "com.blurr.voice.v2.EXTRA_TASK"

        /**
         * A helper function to easily start the service from anywhere in the app (e.g., an Activity or ViewModel).
         *
         * @param context The application context.
         * @param task The user's high-level task for the agent to perform.
         */
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
        Log.d(TAG, "onStartCommand: Service has been started.")

        // Extract the task from the intent
        val task = intent?.getStringExtra(EXTRA_TASK)
        if (task.isNullOrBlank()) {
            Log.e(TAG, "Service started without a task. Stopping service.")
            stopSelf()
            return START_NOT_STICKY // Don't restart the service
        }

        // Start the service in the foreground.
        val notification = createNotification("Agent is running task: $task")
        startForeground(NOTIFICATION_ID, notification)

        // Launch the agent's main run loop in a background coroutine.
        // This prevents blocking the main thread.
        serviceScope.launch {
            try {
                Log.i(TAG, "Launching agent run loop for task: $task")
                agent.run(task)
            } catch (e: Exception) {
                Log.e(TAG, "Agent run failed with an exception", e)
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
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("AI Agent Active")
            .setContentText(contentText)
            // .setSmallIcon(R.drawable.ic_agent_notification) // TODO: Add a notification icon
            .build()
    }
}
