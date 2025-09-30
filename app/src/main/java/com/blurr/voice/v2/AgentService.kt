/**
 * @file AgentService.kt
 * @brief A foreground service to host and manage the V2 Agent's lifecycle.
 *
 * This file defines `AgentService`, a crucial component for running the AI agent reliably
 * in the background. It handles task queuing, agent initialization, lifecycle management,
 * and foreground service notifications to ensure the agent can complete long-running tasks
 * without being terminated by the Android OS. It also integrates with Firebase to track
 * task history for logged-in users.
 */
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
import com.blurr.voice.R
import com.blurr.voice.api.Eyes
import com.blurr.voice.api.Finger
import com.blurr.voice.utilities.ApiKeyManager
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
import java.util.Queue
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * A foreground service responsible for hosting and running the AI Agent.
 *
 * This service manages the entire lifecycle of the agent. It operates on a task queue,
 * processing one task at a time. Key responsibilities include:
 * - Initializing all agent components (Perception, Memory, LLM, etc.).
 * - Running the agent's main loop in a background coroutine.
 * - Managing a foreground notification to prevent the OS from terminating the process.
 * - Handling service start/stop commands and ensuring clean resource disposal.
 * - Tracking task start and completion in Firebase.
 */
class AgentService : Service() {

    private val TAG = "AgentService"

    // A dedicated coroutine scope tied to the service's lifecycle.
    // A SupervisorJob ensures that if one child coroutine fails, it doesn't cancel the whole scope.
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val visualFeedbackManager by lazy { VisualFeedbackManager.getInstance(this) }

    // Agent and its dependencies are initialized in onCreate.
    private val taskQueue: Queue<String> = ConcurrentLinkedQueue()
    private lateinit var agent: Agent
    private lateinit var settings: AgentSettings
    private lateinit var fileSystem: FileSystem
    private lateinit var memoryManager: MemoryManager
    private lateinit var perception: Perception
    private lateinit var llmApi: GeminiApi
    private lateinit var actionExecutor: ActionExecutor
    
    // Firebase instances for task tracking.
    private val db = Firebase.firestore
    private val auth = Firebase.auth

    /**
     * Companion object to provide easy-to-use static methods for controlling the service.
     */
    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "AgentServiceChannelV2"
        private const val NOTIFICATION_ID = 14
        private const val EXTRA_TASK = "com.blurr.voice.v2.EXTRA_TASK"
        private const val ACTION_STOP_SERVICE = "com.blurr.voice.v2.ACTION_STOP_SERVICE"

        /** Indicates if the service is currently processing a task. */
        @Volatile
        var isRunning: Boolean = false
            private set

        /** The description of the task currently being processed. */
        @Volatile
        var currentTask: String? = null
            private set

        /**
         * Sends an intent to stop the service gracefully.
         * @param context The application context.
         */
        fun stop(context: Context) {
            Log.d("AgentService", "External stop request received.")
            val intent = Intent(context, AgentService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.startService(intent)
        }

        /**
         * Starts the service and adds a new task to the queue.
         * @param context The application context.
         * @param task The high-level task for the agent to perform.
         */
        fun start(context: Context, task: String) {
            Log.d("AgentService", "Starting service with task: $task")
            val intent = Intent(context, AgentService::class.java).apply {
                putExtra(EXTRA_TASK, task)
            }
            context.startService(intent)
        }
    }

    /**
     * Called when the service is first created.
     * This is where we initialize all the agent's core components.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate: Service is being created.")
        visualFeedbackManager.showTtsWave()

        createNotificationChannel()

        // Initialize all the agent's components.
        settings = AgentSettings()
        fileSystem = FileSystem(this)
        memoryManager = MemoryManager(this, "", fileSystem, settings)
        perception = Perception(Eyes(this), SemanticParser())
        llmApi = GeminiApi(
            "gemini-2.5-flash",
            apiKeyManager = ApiKeyManager,
            maxRetry = 10
        )
        actionExecutor = ActionExecutor(Finger(this))

        // Create the Agent instance with all its dependencies.
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

    /**
     * Called every time the service is started with `startService()`.
     * This method handles incoming intents, such as adding a new task or stopping the service.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand received.")

        if (intent?.action == ACTION_STOP_SERVICE) {
            Log.i(TAG, "Received stop action. Stopping service.")
            stopSelf()
            return START_NOT_STICKY
        }

        intent?.getStringExtra(EXTRA_TASK)?.let {
            if (it.isNotBlank()) {
                Log.d(TAG, "Adding task to queue: $it")
                taskQueue.add(it)
            }
        }

        // If the agent is not already processing tasks, start the processing loop.
        if (!isRunning && taskQueue.isNotEmpty()) {
            Log.i(TAG, "Agent not running, starting processing loop.")
            serviceScope.launch {
                processTaskQueue()
            }
        } else {
            if(isRunning) Log.d(TAG, "Task added to queue. Processor is already running.")
            else Log.d(TAG, "Service started with no task, waiting for tasks.")
        }

        // START_STICKY ensures the service stays running until explicitly stopped.
        return START_STICKY
    }

    /**
     * The core loop that processes tasks from the queue one by one.
     * It promotes the service to the foreground and runs the agent for each task.
     * The service stops itself once the queue is empty.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun processTaskQueue() {
        if (isRunning) {
            Log.d(TAG, "processTaskQueue called but already running.")
            return
        }
        isRunning = true

        Log.i(TAG, "Starting task processing loop.")
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        startForeground(NOTIFICATION_ID, createNotification("Agent is starting..."))

        while (taskQueue.isNotEmpty()) {
            val task = taskQueue.poll() ?: continue
            currentTask = task

            notificationManager.notify(NOTIFICATION_ID, createNotification("Agent is running task: $task"))

            try {
                Log.i(TAG, "Executing task: $task")
                trackTaskInFirebase(task)
                agent.run(task)
                trackTaskCompletion(task, true)
                Log.i(TAG, "Task completed successfully: $task")
            } catch (e: Exception) {
                Log.e(TAG, "Task failed with an exception: $task", e)
                trackTaskCompletion(task, false, e.message)
            }
        }

        Log.i(TAG, "Task queue is empty. Stopping service.")
        stopSelf()
    }

    /**
     * Called when the service is being destroyed.
     * This is where we clean up all resources, cancel coroutines, and reset state.
     */
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy: Service is being destroyed.")
        // Reset status.
        isRunning = false
        currentTask = null
        taskQueue.clear()

        // Cancel the coroutine scope to clean up any running jobs and prevent leaks.
        serviceScope.cancel()
        visualFeedbackManager.hideTtsWave()
        Log.i(TAG, "Service destroyed and all resources cleaned up.")
    }

    /** This service does not provide binding, so it returns null. */
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    /**
     * Creates the NotificationChannel required for the foreground service on Android 8.0+.
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
     * Creates the persistent notification displayed while the service is in the foreground.
     * Includes a "Stop" action to allow the user to terminate the agent.
     * @param contentText The text to display in the notification body.
     * @return A configured [Notification] object.
     */
    private fun createNotification(contentText: String): Notification {
        val stopIntent = Intent(this, AgentService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Panda Doing Task (Expand to stop Panda)")
            .setContentText(contentText)
            .addAction(
                android.R.drawable.ic_media_pause,
                "Stop Panda",
                stopPendingIntent
            )
            .setOngoing(true)
             .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
    }

    /**
     * Tracks the start of a task in Firebase by appending it to the user's task history.
     * @param task The description of the task being started.
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

            db.collection("users").document(currentUser.uid)
                .update("taskHistory", FieldValue.arrayUnion(taskEntry))
                .await()

            Log.d(TAG, "Successfully tracked task start in Firebase for user ${currentUser.uid}: $task")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to track task in Firebase", e)
        }
    }

    /**
     * Updates the task completion status in Firebase by appending a new result entry.
     * @param task The description of the task that was completed.
     * @param success Whether the task succeeded or failed.
     * @param errorMessage An optional error message if the task failed.
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
                "completedAt" to Timestamp.now(),
                "success" to success,
                "errorMessage" to errorMessage
            )

            db.collection("users").document(currentUser.uid)
                .update("taskHistory", FieldValue.arrayUnion(completionEntry))
                .await()

            Log.d(TAG, "Successfully tracked task completion in Firebase for user ${currentUser.uid}: $task (success: $success)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to track task completion in Firebase", e)
        }
    }
}
