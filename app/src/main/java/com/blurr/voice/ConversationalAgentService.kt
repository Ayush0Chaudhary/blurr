package com.blurr.voice

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.animation.ValueAnimator
import android.app.PendingIntent
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.blurr.voice.api.Eyes
//import com.blurr.voice.services.AgentTaskService
import com.blurr.voice.utilities.SpeechCoordinator
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.graphics.toColorInt
import com.blurr.voice.agents.ClarificationAgent
import com.blurr.voice.utilities.TTSManager
import com.blurr.voice.utilities.addResponse
import com.blurr.voice.utilities.getReasoningModelApiResponse
import com.blurr.voice.utilities.FreemiumManager
import com.blurr.voice.overlay.OverlayManager
import com.blurr.voice.overlay.OverlayDispatcher
import com.blurr.voice.utilities.PandaState
import com.blurr.voice.utilities.UserProfileManager
import com.blurr.voice.utilities.VisualFeedbackManager
import com.blurr.voice.v2.AgentService
import com.blurr.voice.data.UserMemory
import com.google.ai.client.generativeai.type.TextPart
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.blurr.voice.utilities.ServicePermissionManager
import com.blurr.voice.utilities.PandaStateManager
import com.blurr.voice.v2.perception.Perception
import com.blurr.voice.v2.perception.SemanticParser
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException

data class ModelDecision(
    val type: String = "Reply",
    val reply: String,
    val instruction: String = "",
    val shouldEnd: Boolean = false
)

class ConversationalAgentService : Service() {

    private val speechCoordinator by lazy { SpeechCoordinator.getInstance(this) }
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var conversationHistory = listOf<Pair<String, List<Any>>>()
    private val ttsManager by lazy { TTSManager.getInstance(this) }
    private val overlayManager by lazy { OverlayManager.getInstance(this) }
    private val clarificationQuestionViews = mutableListOf<View>()
    private var transcriptionView: TextView? = null
    private val visualFeedbackManager by lazy { VisualFeedbackManager.getInstance(this) }
    private val pandaStateManager by lazy { PandaStateManager.getInstance(this) }
    private var isTextModeActive = false
    private val freemiumManager by lazy { FreemiumManager() }
    private val servicePermissionManager by lazy { ServicePermissionManager(this) }

    private var clarificationAttempts = 0
    private val maxClarificationAttempts = 1
    private var sttErrorAttempts = 0
    private val maxSttErrorAttempts = 2

    private val clarificationAgent = ClarificationAgent()
    private val windowManager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private var cachedMemories = listOf<UserMemory>()
    private var hasHeardFirstUtterance = false
    private lateinit var firebaseAnalytics: FirebaseAnalytics
    private lateinit var perception: Perception
    private val client = OkHttpClient()

    
    // Firebase instances for conversation tracking
    private val db = Firebase.firestore
    private val auth = Firebase.auth
    private var conversationId: String? = null // Track current conversation session


    companion object {
        const val NOTIFICATION_ID = 3
        const val CHANNEL_ID = "ConversationalAgentChannel"
        const val ACTION_STOP_SERVICE = "com.blurr.voice.ACTION_STOP_SERVICE"
        var isRunning = false
        const val MEMORY_ENABLED = true
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate() {
        super.onCreate()
        Log.d("ConvAgent", "Service onCreate")
        
        // Initialize Firebase Analytics
        firebaseAnalytics = Firebase.analytics
        
        // Track service creation
        firebaseAnalytics.logEvent("conversational_agent_started", null)
        
        isRunning = true
        createNotificationChannel()
        initializeConversation()
        clarificationAttempts = 0 // Reset clarification attempts counter
        sttErrorAttempts = 0 // Reset STT error attempts counter
        // usedMemories.clear() // Removed
        hasHeardFirstUtterance = false // Reset first utterance flag

        fetchMemories() // Start async memory fetch

        OverlayDispatcher.clearAll()
        overlayManager.startObserving()
        visualFeedbackManager.showSpeakingOverlay() // <-- ADD THIS LINE
        visualFeedbackManager.showTtsWave()

        showInputBoxIfNeeded()
        visualFeedbackManager.showSmallDeltaGlow()

        // Start state monitoring and set initial state
        pandaStateManager.startMonitoring()
        pandaStateManager.setState(PandaState.IDLE)


    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun showInputBoxIfNeeded() {
        visualFeedbackManager.showInputBox(
            onActivated = {
                // This is called when the user taps the EditText
                enterTextMode()
            },
            onSubmit = { submittedText ->
                // This is the existing callback for when text is submitted
                processUserInput(submittedText)
            },
            onOutsideTap = {
                serviceScope.launch {
                    instantShutdown()
                }
            }
        )
    }

    /**
     * Call this when the user starts interacting with the text input.
     * It stops any ongoing voice interaction.
     */
    private fun enterTextMode() {
        if (isTextModeActive) return
        Log.d("ConvAgent", "Entering Text Mode. Stopping STT/TTS.")
        
        // Track text mode activation
        firebaseAnalytics.logEvent("text_mode_activated", null)
        
        isTextModeActive = true
        pandaStateManager.setState(PandaState.IDLE)
        speechCoordinator.stopListening()
        speechCoordinator.stopSpeaking()
        // Optionally hide the transcription view since user is typing
        visualFeedbackManager.hideTranscription()
    }


    @RequiresApi(Build.VERSION_CODES.R)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("ConvAgent", "Service onStartCommand")

        if (intent?.action == ACTION_STOP_SERVICE) {
            Log.i("ConvAgent", "Received stop action. Stopping service.")
            stopSelf()
            return START_NOT_STICKY
        }
        
        // Check if we have the required RECORD_AUDIO permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e("ConvAgent", "RECORD_AUDIO permission not granted. Cannot start foreground service.")
            Toast.makeText(this, "Microphone permission required for voice assistant", Toast.LENGTH_LONG).show()
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            startForeground(NOTIFICATION_ID, createNotification())
        } catch (e: SecurityException) {
            serviceScope.launch {
                speechCoordinator.speakText("Hello, please give microphone permission or some other type of permission you have not given me! My code is open source, so you can check that out if you have any doubts.")
                delay(2000) // Wait for TTS to complete before closing service
                stopSelf()
            }
            Log.e("ConvAgent", "Failed to start foreground service: ${e.message}")
            Toast.makeText(this, "Cannot start voice assistant - permission missing", Toast.LENGTH_LONG).show()
            return START_NOT_STICKY
        }

        if (!servicePermissionManager.isMicrophonePermissionGranted()) {
            Log.e("ConvAgent", "RECORD_AUDIO permission not granted. Shutting down.")
            serviceScope.launch {
                ttsManager.speakText(getString(R.string.microphone_permission_not_granted))
                delay(2000)
                stopSelf()
            }
            return START_NOT_STICKY
        }

        // Track conversation initiation
        firebaseAnalytics.logEvent("conversation_initiated", null)
        trackConversationStart()

        // Skip greeting and start listening immediately
        serviceScope.launch {
            Log.d("ConvAgent", "Starting immediate listening (no greeting)")
            pandaStateManager.setState(PandaState.LISTENING)
            startImmediateListening()
        }
        return START_STICKY
    }

    /**
     * Gets a personalized greeting using the user's name from memories if available
     * NOTE: This method is kept for potential future use but no longer called on startup
     */
    private fun getPersonalizedGreeting(): String {
        try {
            val userProfile = UserProfileManager(this@ConversationalAgentService)
            Log.d("ConvAgent", "No name found in memories, using generic greeting")
            return "Hey ${userProfile.getName()}!"
        } catch (e: Exception) {
            Log.e("ConvAgent", "Error getting personalized greeting", e)
            return "Hey!"
        }
    }

    /**
     * Starts listening immediately without speaking any greeting or performing memory extraction
     * Memory extraction will be deferred until after the first user utterance
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun startImmediateListening() {
        Log.d("ConvAgent", "Starting immediate listening without greeting")
        
        // Check if we are in text mode before starting to listen
        if (isTextModeActive) {
            Log.d("ConvAgent", "In text mode, ensuring input box is visible and skipping voice listening.")
            mainHandler.post {
                showInputBoxIfNeeded() // Re-show the input box for the next turn.
            }
            return // Skip starting the voice listener entirely.
        }

        speechCoordinator.startListening(
            onResult = { recognizedText ->
                if (isTextModeActive) return@startListening // Ignore results in text mode
                Log.d("ConvAgent", "Final user transcription: $recognizedText")
                pandaStateManager.setState(PandaState.PROCESSING)
                visualFeedbackManager.updateTranscription(recognizedText)
                mainHandler.postDelayed({
                    visualFeedbackManager.hideTranscription()
                }, 500)

                
                processUserInput(recognizedText)
            },
            onError = { error ->
                Log.e("ConvAgent", "STT Error: $error")
                if (isTextModeActive) return@startListening // Ignore errors in text mode
                
                // Trigger error state in state manager
                pandaStateManager.triggerErrorState()
                
                // Track STT errors
                val sttErrorBundle = android.os.Bundle().apply {
                    putString("error_message", error.take(100))
                    putInt("error_attempt", sttErrorAttempts + 1)
                    putInt("max_attempts", maxSttErrorAttempts)
                }
                firebaseAnalytics.logEvent("stt_error", sttErrorBundle)
                
                visualFeedbackManager.hideTranscription()
                sttErrorAttempts++
                serviceScope.launch {
                    if (sttErrorAttempts >= maxSttErrorAttempts) {
                        firebaseAnalytics.logEvent("conversation_ended_stt_errors", null)
                        val exitMessage = "I'm having trouble understanding you clearly. Please try calling later!"
                        trackMessage("model", exitMessage, "error_message")
                        gracefulShutdown(exitMessage, "stt_errors")
                    } else {
                        val retryMessage = "I'm sorry, I didn't catch that. Could you please repeat?"
                        speakAndThenListen(retryMessage)
                    }
                }
            },
            onPartialResult = { partialText ->
                if (isTextModeActive) return@startListening // Ignore partial results in text mode
                visualFeedbackManager.updateTranscription(partialText)
            },
            onListeningStateChange = { listening ->
                Log.d("ConvAgent", "Listening state: $listening")
                if (listening) {
                    if (isTextModeActive) return@startListening // Ignore state changes in text mode
                    pandaStateManager.setState(PandaState.LISTENING)
                    visualFeedbackManager.showTranscription()
                } else {
                    if (!isTextModeActive) {
                        pandaStateManager.setState(PandaState.IDLE)
                    }
                }
            }
        )
    }


    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun speakAndThenListen(text: String, draw: Boolean = true) {
        // Only update system prompt with memories if we've heard the first utterance
//        if (hasHeardFirstUtterance) {
//            updateSystemPromptWithMemories()
//        }
        updateSystemPromptWithTime()
        pandaStateManager.setState(PandaState.SPEAKING)
        speechCoordinator.speakText(text)
        Log.d("ConvAgent", "Panda said: $text")
        // --- CHANGE 4: Check if we are in text mode before starting to listen ---
        if (isTextModeActive) {
            Log.d("ConvAgent", "In text mode, ensuring input box is visible and skipping voice listening.")
            // Post to main handler to ensure UI operations are on the main thread.
            mainHandler.post {
                showInputBoxIfNeeded() // Re-show the input box for the next turn.
            }
            return // IMPORTANT: Skip starting the voice listener entirely.
        }
        speechCoordinator.startListening(
            onResult = { recognizedText ->
                if (isTextModeActive) return@startListening // Ignore errors in text mode
                Log.d("ConvAgent", "Final user transcription: $recognizedText")
                pandaStateManager.setState(PandaState.PROCESSING)
                visualFeedbackManager.updateTranscription(recognizedText)
                mainHandler.postDelayed({
                    visualFeedbackManager.hideTranscription()
                }, 500)
                
                // Mark that we've heard the first utterance and trigger memory extraction if not already done
                if (!hasHeardFirstUtterance) {
                    hasHeardFirstUtterance = true
                    Log.d("ConvAgent", "First utterance received, triggering memory extraction")
                    serviceScope.launch {
                        try {
                            updateSystemPromptWithScreenContext()
                        } catch (e: Exception) {
                            Log.e("ConvAgent", "Error during first utterance memory extraction", e)
                            // Continue execution even if memory extraction fails
                        }
                    }
                }
                
                processUserInput(recognizedText)

            },
            onError = { error ->
                Log.e("ConvAgent", "STT Error: $error")
                if (isTextModeActive) return@startListening // Ignore errors in text mode
                
                // Trigger error state in state manager
                pandaStateManager.triggerErrorState()
                
                // Track STT errors
                val sttErrorBundle = android.os.Bundle().apply {
                    putString("error_message", error.take(100))
                    putInt("error_attempt", sttErrorAttempts + 1)
                    putInt("max_attempts", maxSttErrorAttempts)
                }
                firebaseAnalytics.logEvent("stt_error", sttErrorBundle)
                
                visualFeedbackManager.hideTranscription()
                sttErrorAttempts++
                serviceScope.launch {
                    if (sttErrorAttempts >= maxSttErrorAttempts) {
                        firebaseAnalytics.logEvent("conversation_ended_stt_errors", null)
                        val exitMessage = "I'm having trouble understanding you clearly. Please try calling later!"
                        trackMessage("model", exitMessage, "error_message")
                        gracefulShutdown(exitMessage, "stt_errors")
                    } else {
                        speakAndThenListen("I'm sorry, I didn't catch that. Could you please repeat?")
                    }
                }
            },
            onPartialResult = { partialText ->
                if (isTextModeActive) return@startListening // Ignore errors in text mode
                visualFeedbackManager.updateTranscription(partialText)
            },
            onListeningStateChange = { listening ->
                Log.d("ConvAgent", "Listening state: $listening")
                if (listening) {
                    if (isTextModeActive) return@startListening // Ignore errors in text mode
                    pandaStateManager.setState(PandaState.LISTENING)
                    visualFeedbackManager.showTranscription()
                } else {
                    if (!isTextModeActive) {
                        pandaStateManager.setState(PandaState.IDLE)
                    }
                }
            }
        )
    }

    // START: ADD THESE NEW METHODS AT THE END OF THE CLASS, before onDestroy()
    private fun showTranscriptionView() {
        if (transcriptionView != null) return // Already showing

        mainHandler.post {
            transcriptionView = TextView(this).apply {
                text = "Listening..."
                val glassBackground = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(0xDD0D0D2E.toInt(), 0xDD2A0D45.toInt())
                ).apply {
                    cornerRadius = 28f
                    setStroke(1, 0x80FFFFFF.toInt())
                }
                background = glassBackground
                setTextColor(0xFFE0E0E0.toInt())
                textSize = 16f
                setPadding(40, 24, 40, 24)
                typeface = Typeface.MONOSPACE
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                y = 250 // Position it 250px above the bottom edge
            }

            try {
                windowManager.addView(transcriptionView, params)
            } catch (e: Exception) {
                Log.e("ConvAgent", "Failed to add transcription view.", e)
                transcriptionView = null
            }
        }
    }

    private fun updateTranscriptionView(text: String) {
        transcriptionView?.text = text
    }

    private fun hideTranscriptionView() {
        mainHandler.post {
            transcriptionView?.let {
                if (it.isAttachedToWindow) {
                    try {
                        windowManager.removeView(it)
                    } catch (e: Exception) {
                        Log.e("ConvAgent", "Error removing transcription view.", e)
                    }
                }
            }
            transcriptionView = null
        }
    }


    // --- CHANGED: Rewritten to process the new custom text format ---
    @RequiresApi(Build.VERSION_CODES.R)
    private fun processUserInput(userInput: String) {
        serviceScope.launch {
            removeClarificationQuestions()
            updateSystemPromptWithAgentStatus()
            updateSystemPromptWithScreenContext()

            // Mark that we've heard the first utterance and trigger memory extraction if not already done
            if (!hasHeardFirstUtterance) {
                hasHeardFirstUtterance = true
                Log.d("ConvAgent", "First utterance received via processUserInput, triggering memory extraction")
                try {
                    updateSystemPromptWithScreenContext()
                } catch (e: Exception) {
                    Log.e("ConvAgent", "Error during first utterance memory extraction", e)
                    // Continue execution even if memory extraction fails
                }
            }

            conversationHistory = addResponse("user", userInput, conversationHistory)
            
            // Track user message in Firebase
            trackMessage("user", userInput, "input")

            // Track user input
            val inputBundle = android.os.Bundle().apply {
                putString("input_type", if (isTextModeActive) "text" else "voice")
                putInt("input_length", userInput.length)
                putBoolean("is_command", userInput.equals("stop", ignoreCase = true) || userInput.equals("exit", ignoreCase = true))
            }
            firebaseAnalytics.logEvent("user_input_processed", inputBundle)

            try {
                if (userInput.equals("stop", ignoreCase = true) || userInput.equals("exit", ignoreCase = true)) {
                    firebaseAnalytics.logEvent("conversation_ended_by_command", null)
                    trackMessage("model", "Goodbye!", "farewell")
                    gracefulShutdown("Goodbye!", "command")
                    return@launch
                }
                pandaStateManager.setState(PandaState.PROCESSING)
                visualFeedbackManager.showThinkingIndicator()
                val defaultJsonResponse = """{"Type": "Reply", "Reply": "I'm sorry, I had an issue.", "Instruction": "", "Should End": "Continue"}"""
                val rawModelResponse = getReasoningModelApiResponse(conversationHistory) ?: defaultJsonResponse
                visualFeedbackManager.hideThinkingIndicator()
                val decision = parseModelResponse(rawModelResponse)
                Log.d("TTS_DEBUG", "Reply received from GeminiApi: -->${rawModelResponse}<--")
                when (decision.type) {
                    "Task" -> {
                        // Track task request
                        val taskBundle = android.os.Bundle().apply {
                            putString("task_instruction", decision.instruction.take(100)) // Limit length for analytics
                            putBoolean("agent_already_running", AgentService.isRunning)
                        }
                        firebaseAnalytics.logEvent("task_requested", taskBundle)
                        
                        if (AgentService.isRunning) {
                            firebaseAnalytics.logEvent("task_rejected_agent_busy", null)
                            val busyMessage = "I'm already working on '${AgentService.currentTask}'. Please let me finish that first, or you can ask me to stop it."
                            speakAndThenListen(busyMessage)
                            conversationHistory = addResponse("model", busyMessage, conversationHistory)
                            return@launch
                        }

                        if (!servicePermissionManager.isAccessibilityServiceEnabled()) {
                            speakAndThenListen(getString(R.string.accessibility_permission_needed_for_task))
                            conversationHistory = addResponse("model", R.string.accessibility_permission_needed_for_task.toString(), conversationHistory)
                            return@launch
                        }

                        Log.d("ConvAgent", "Model identified a task. Checking for clarification...")
                        // --- NEW: Check if the task instruction needs clarification ---
                        removeClarificationQuestions()
                        if(freemiumManager.canPerformTask()){
                            Log.d("ConvAgent", "Allowance check passed. Proceeding with task.")

                            if (clarificationAttempts < maxClarificationAttempts) {
                                val (needsClarification, questions) = checkIfClarificationNeeded(
                                    decision.instruction
                                )
                                Log.d("ConcAgent", needsClarification.toString())
                                Log.d("ConcAgent", questions.toString())

                                if (needsClarification) {
                                    // Track clarification needed
                                    val clarificationBundle = android.os.Bundle().apply {
                                        putInt("clarification_attempt", clarificationAttempts + 1)
                                        putInt("questions_count", questions.size)
                                    }
                                    firebaseAnalytics.logEvent("task_clarification_needed", clarificationBundle)
                                    
                                    clarificationAttempts++
                                    displayClarificationQuestions(questions)
                                    val questionToAsk =
                                        "I can help with that, but first: ${questions.joinToString(" and ")}"
                                    Log.d(
                                        "ConvAgent",
                                        "Task needs clarification. Asking: '$questionToAsk' (Attempt $clarificationAttempts/$maxClarificationAttempts)"
                                    )
                                    conversationHistory = addResponse(
                                        "model",
                                        "Clarification needed for task: ${decision.instruction}",
                                        conversationHistory
                                    )
                                    trackMessage("model", questionToAsk, "clarification")
                                    speakAndThenListen(questionToAsk, false)
                                } else {
                                    Log.d(
                                        "ConvAgent",
                                        "Task is clear. Executing: ${decision.instruction}"
                                    )
                                    
                                    // Track task execution
                                    firebaseAnalytics.logEvent("task_executed", taskBundle)
                                    
                                    val originalInstruction = decision.instruction
                                    AgentService.start(applicationContext, originalInstruction)
                                    trackMessage("model", decision.reply, "task_confirmation")
                                    gracefulShutdown(decision.reply, "task_executed")
                                }
                            } else {
                                Log.d(
                                    "ConvAgent",
                                    "Max clarification attempts reached ($maxClarificationAttempts). Proceeding with task execution."
                                )
                                
                                // Track max clarification attempts reached
                                firebaseAnalytics.logEvent("task_executed_max_clarification", taskBundle)
                                
                                AgentService.start(applicationContext, decision.instruction)
                                trackMessage("model", decision.reply, "task_confirmation")
                                gracefulShutdown(decision.reply, "task_executed")
                            }
                        }else{
                            Log.w("ConvAgent", "User has no tasks remaining. Denying request.")
                            
                            // Track freemium limit reached
                            firebaseAnalytics.logEvent("task_rejected_freemium_limit", null)
                            
                            val upgradeMessage = "Hey! You've used all your free tasks for the month. Please upgrade in the app to unlock more. We can still talk in voice mode."
                            conversationHistory = addResponse("model", upgradeMessage, conversationHistory)
                            trackMessage("model", upgradeMessage, "freemium_limit")
                            speakAndThenListen(upgradeMessage)
                        }
                    }
                    "KillTask" -> {
                        Log.d("ConvAgent", "Model requested to kill the running agent service.")
                        
                        // Track kill task request
                        val killTaskBundle = android.os.Bundle().apply {
                            putBoolean("task_was_running", AgentService.isRunning)
                        }
                        firebaseAnalytics.logEvent("kill_task_requested", killTaskBundle)
                        
                        if (AgentService.isRunning) {
                            AgentService.stop(applicationContext)
                            trackMessage("model", decision.reply, "kill_task_response")
                            gracefulShutdown(decision.reply, "task_killed")
                        } else {
                            val noTaskMessage = "There was no automation running, but I can help with something else."
                            trackMessage("model", noTaskMessage, "kill_task_response")
                            speakAndThenListen(noTaskMessage)
                        }
                    }
                    else -> { // Default to "Reply"
                        // Track conversational reply
                        val replyBundle = android.os.Bundle().apply {
                            putBoolean("conversation_ended", decision.shouldEnd)
                            putInt("reply_length", decision.reply.length)
                        }
                        firebaseAnalytics.logEvent("conversational_reply", replyBundle)
                        
                        if (decision.shouldEnd) {
                            Log.d("ConvAgent", "Model decided to end the conversation.")
                            firebaseAnalytics.logEvent("conversation_ended_by_model", null)
                            trackMessage("model", decision.reply, "farewell")
                            gracefulShutdown(decision.reply, "model_ended")
                        } else {
                            conversationHistory = addResponse("model", rawModelResponse, conversationHistory)
                            trackMessage("model", decision.reply, "reply")
                            speakAndThenListen(decision.reply)
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e("ConvAgent", "Error processing user input: ${e.message}", e)
                
                // Trigger error state in state manager
                pandaStateManager.triggerErrorState()
                
                // Track processing errors
                val errorBundle = android.os.Bundle().apply {
                    putString("error_message", e.message?.take(100) ?: "Unknown error")
                    putString("error_type", e.javaClass.simpleName)
                }
                firebaseAnalytics.logEvent("input_processing_error", errorBundle)
                
                speakAndThenListen("closing voice mode")
            }
        }
    }

    //    private suspend fun getGroundedStepsForTask(taskInstruction: String): String {
//        Log.d("ConvAgent", "Performing grounded search for task: '$taskInstruction'")
//
//        // We create a specific prompt for the search.
//        val searchPrompt = """
//        Search the web and provide a concise, step-by-step guide for a human assistant to perform the following task on an Android phone: '$taskInstruction'.
//        Focus on the exact taps and settings involved.
//    """.trimIndent()
//
//        // Here we use the direct REST API call with search that we created previously.
//        // We need an instance of GeminiApi to call it.
//        // NOTE: You might need to adjust how you get your GeminiApi instance.
//        // For now, we'll assume we can create one or access it.
//        val geminiApi = GeminiApi("gemini-2.5-flash", ApiKeyManager, 2)
//
//        val searchResult = geminiApi.generateGroundedContent(searchPrompt)
//        Log.d("CONVO_SEARCH", searchResult.toString())
//        return if (!searchResult.isNullOrBlank()) {
//            searchResult
//        } else {
//            ""
//        }
//    }
    private suspend fun checkIfClarificationNeeded(instruction: String): Pair<Boolean, List<String>> {
        Log.d("ConvAgent", "Checking for clarification on instruction: '$instruction'")
        return Pair(false, listOf())
    }

    private fun initializeConversation() {
        val memoryContextSection = if (MEMORY_ENABLED) {
            """
            Use these memories to answer the user's question with his personal data
            ### Memory Context Start ###
            {memory_context}
            ### Memory Context Ends ###
            """
        } else {
            """
            ### Memory Status ###
            Memory system is temporarily disabled. Panda cannot remember or learn from previous conversations at this time.
            some memories added by developers
            {memory_context}
            ### End Memory Status ###
            """
        }

        val systemPrompt = """
            You are a helpful voice assistant called Panda that can either have a conversation or ask an executor to execute tasks on the user's phone.
            The executor can speak, listen, see the screen, tap the screen, and basically use the phone as a normal human would.

            {agent_status_context}

            ### Current Screen Context ###
            {screen_context}
            ### End Screen Context ###

            Some Guideline:
            1. If the user ask you to do something creative, you do this task and be the most creative person in the world.
            2. If you know the user's name from the memories, refer to them by their name to make the conversation more personal and friendly as often as possible.
            3. Use the current screen context to better understand what the user is looking at and provide more relevant responses.
            4. If the user asks about something on the screen, you can reference the screen content directly.
            5. Always ask for clarification if the user's request is ambiguous or unclear.
            6. When the user ask to sing, shout or produce any sound, just generate text, we will sing it for you.
            7. Your code is opensource so you can tell tell that to user. repo is ayush0chaudhary/blurr
            8. Give a warning for the tasks related to banking, games, shopping and app with Canvas (no a11y tree) that you wont be able to do them properly but you will try your best.
            
            Use these memories to answer the user's question with his personal data
            ### Memory Context Start ###
            {memory_context}
            ### Memory Context Ends ###
            
            Analyze the user's request and respond ONLY with a single, valid JSON object.
            Do not include any text, notes, or explanations outside of the JSON object.
            The JSON object must have the following structure:
            
            {
              "Type": "String",
              "Reply": "String",
              "Instruction": "String",
              "Should End": "String"
            }

            Here are the rules for the JSON values:
            - "Type": Must be one of "Task", "Reply", or "KillTask".
              - Use "Task" if the user is asking you to DO something on the device (e.g., "open settings", "send a text to Mom").
              - Use "Reply" for conversational questions (e.g., "what's the weather?", "tell me a joke").
              - Use "KillTask" ONLY if an automation task is running and the user wants to stop it.
            - "Reply": The text to speak to the user. This is a confirmation for a "Task", or the direct answer for a "Reply".
            - "Instruction": The precise, literal instruction for the task agent. This field should be an empty string "" if the "Type" is not "Task".
            - "Should End": Must be either "Continue" or "Finished". Use "Finished" only when the conversation is naturally over.
        
            Current Time : {time_context}
        """.trimIndent()

        conversationHistory = addResponse("user", systemPrompt, emptyList())
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun updateSystemPromptWithTime() {

        val currentPromptText = conversationHistory.firstOrNull()?.second
            ?.filterIsInstance<TextPart>()?.firstOrNull()?.text ?: return

        val currentTime = java.time.ZonedDateTime.now(java.time.ZoneId.systemDefault())
        val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
        val formattedTime = currentTime.format(formatter)

        val timeContext = "Current Date and Time: $formattedTime"

        val updatedPromptText = currentPromptText.replace("{time_context}", timeContext)

        // Replace the first system message with the updated prompt
        conversationHistory = conversationHistory.toMutableList().apply {
            set(0, "user" to listOf(TextPart(updatedPromptText)))
        }
    }

    private fun updateSystemPromptWithAgentStatus() {
        val currentPromptText = conversationHistory.firstOrNull()?.second
            ?.filterIsInstance<TextPart>()?.firstOrNull()?.text ?: return

        val agentStatusContext = if (AgentService.isRunning) {
            """
            IMPORTANT CONTEXT: An automation task is currently running in the background.
            Task Description: "${AgentService.currentTask}".
            If the user asks to stop, cancel, or kill this task, you MUST use the "KillTask" type.
            """.trimIndent()
        } else {
            "CONTEXT: No automation task is currently running."
        }

        val updatedPromptText = currentPromptText.replace("{agent_status_context}", agentStatusContext)

        // Replace the first system message with the updated prompt
        conversationHistory = conversationHistory.toMutableList().apply {
            set(0, "user" to listOf(TextPart(updatedPromptText)))
        }
        Log.d("ConvAgent", "System prompt updated with agent status: ${AgentService.isRunning}")
    }

    /**
     * Updates the system prompt with relevant memories and current screen context
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun updateSystemPromptWithScreenContext() {
        try {

            perception = Perception(Eyes(this), SemanticParser())
            val analysis = perception.analyze(all = true)
            Log.d("ConvAgent", "Screen analysis: ${analysis.uiRepresentation}")
            val currentPrompt = conversationHistory.firstOrNull()?.second
                ?.filterIsInstance<TextPart>()?.firstOrNull()?.text ?: return

            // Update screen context first
            var updatedPrompt = currentPrompt.replace("{screen_context}", analysis.uiRepresentation)

            // Check if memory is enabled before processing memories
            if (!MEMORY_ENABLED) {
                var userProfile = UserProfileManager(this@ConversationalAgentService)

                Log.d("ConvAgent", "Memory is disabled, skipping memory operations")
                Log.d("ConvAgent", "User name is ${userProfile.getName()}")
                // Replace memory context with disabled message

                updatedPrompt = updatedPrompt.replace("{memory_context}", "User name is ${userProfile.getName()}")
            } else {
                // Use cached memories from Firestore
                if (cachedMemories.isNotEmpty()) {
                    Log.d("ConvAgent", "Injecting ${cachedMemories.size} cached memories into context")
                    
                    // Take top 100 memories (already sorted by date desc in fetch logic if needed, or just take latest)
                    // For now, we just take the list as is, assuming it's not huge, or take top 100.
                    val topMemories = cachedMemories.take(100)
                    
                    val memoryContext = topMemories.joinToString("\n") { memory -> 
                        "- ${memory.text} (Source: ${memory.source})" 
                    }
                    updatedPrompt = updatedPrompt.replace("{memory_context}", memoryContext)
                } else {
                     Log.d("ConvAgent", "No cached memories available yet")
                     updatedPrompt = updatedPrompt.replace("{memory_context}", "No memories available yet.")
                }
            }

            if (updatedPrompt.isNotEmpty()) {
                // Replace the first system message with updated prompt
                conversationHistory = conversationHistory.toMutableList().apply {
                    set(0, "user" to listOf(TextPart(updatedPrompt)))
                }
                Log.d("ConvAgent", "Updated system prompt with screen context and memories")
            }
        } catch (e: Exception) {
            Log.e("ConvAgent", "Error updating system prompt with memories and screen context", e)
        }
    }

    /**
     * Extracts current memory context from the system prompt
     */
    private fun extractCurrentMemoryContext(prompt: String): List<String> {
        return try {
            val memorySection = prompt.substringAfter("##### MEMORY CONTEXT #####")
                .substringBefore("##### END MEMORY CONTEXT #####")
                .trim()

            if (memorySection.isNotEmpty() && !memorySection.contains("{memory_context}")) {
                memorySection.lines()
                    .filter { it.trim().startsWith("- ") }
                    .map { it.trim().substring(2) } // Remove "- " prefix
                    .filter { it.isNotEmpty() }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("ConvAgent", "Error extracting current memory context", e)
            emptyList()
        }
    }
    private fun parseModelResponse(response: String): ModelDecision {
        try {
            val json = JSONObject(response)
            Log.d("justchecking", json.toString())
            // Use optString for safety, providing a default value if the key doesn't exist.
            val type = json.optString("Type", "Reply")
            val reply = json.optString("Reply", "")
            val instruction = json.optString("Instruction", "")
            val shouldEndStr = json.optString("Should End", "Continue")
            val shouldEnd = shouldEndStr.equals("Finished", ignoreCase = true)
            
            // Add a fallback reply if the model provides an empty one for a conversational turn.
            val finalReply = if (reply.isEmpty() && type.equals("Reply", ignoreCase = true)) {
                "I'm not sure how to respond to that."
            } else {
                reply
            }

            return ModelDecision(type, finalReply, instruction, shouldEnd)
        } catch (e: org.json.JSONException) {
            Log.e("ConvAgent", "Error parsing JSON response, falling back. Response: $response", e)
            // Fallback for malformed JSON
            return ModelDecision(reply = "I seem to have gotten my thoughts tangled. Could you repeat that?")
        } catch (e: Exception) {
            Log.e("ConvAgent", "Generic error parsing model response, falling back. Response: $response", e)
            return ModelDecision(reply = "I had a minor issue processing that. Could you try again?")
        }
    }
    private fun createNotification(): Notification {
        val stopIntent = Intent(this, ConversationalAgentService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Conversational Agent")
            .setContentText("Listening for your commands...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_media_pause, // Using built-in pause icon as stop button
                "Stop",
                stopPendingIntent
            )
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Conversational Agent Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    /**
     * Displays a list of futuristic-styled clarification questions at the top of the screen.
     * Each question animates in from the top with a fade-in effect.
     *
     * @param questions The list of question strings to display.
     */
    private fun displayClarificationQuestions(questions: List<String>) {
        mainHandler.post {
            // First, remove any questions that might already be on screen

            val topMargin = 100 // Base margin from the very top of the screen
            val verticalSpacing = 20 // Space between question boxes
            var accumulatedHeight = 0 // Tracks the vertical space used by previous questions

            questions.forEachIndexed { index, questionText ->
                // 1. Create and style the TextView
                val textView = TextView(this).apply {
                    text = questionText
                    // --- (Your existing styling code is perfect, no changes needed here) ---
                    val glowEffect = GradientDrawable(
                        GradientDrawable.Orientation.BL_TR,
                        intArrayOf("#BE63F3".toColorInt(), "#5880F7".toColorInt())
                    ).apply { cornerRadius = 32f }

                    val glassBackground = GradientDrawable(
                        GradientDrawable.Orientation.TL_BR,
                        intArrayOf(0xEE0D0D2E.toInt(), 0xEE2A0D45.toInt())
                    ).apply {
                        cornerRadius = 28f
                        setStroke(1, 0x80FFFFFF.toInt())
                    }

                    val layerDrawable = LayerDrawable(arrayOf(glowEffect, glassBackground)).apply {
                        setLayerInset(1, 4, 4, 4, 4)
                    }
                    background = layerDrawable
                    setTextColor(0xFFE0E0E0.toInt())
                    textSize = 15f
                    setPadding(40, 24, 40, 24)
                    typeface = Typeface.MONOSPACE
                }

                textView.measure(
                    View.MeasureSpec.makeMeasureSpec((windowManager.defaultDisplay.width * 0.9).toInt(), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                )
                val viewHeight = textView.measuredHeight

                // B. Pre-calculate the final Y position using the current accumulated height.
                val finalYPosition = topMargin + accumulatedHeight

                // C. Update accumulatedHeight for the *next* view in the loop.
                accumulatedHeight += viewHeight + verticalSpacing
                // **--- END OF FIX ---**


                // 2. Prepare layout params
                val params = WindowManager.LayoutParams(
                    (windowManager.defaultDisplay.width * 0.9).toInt(), // 90% of screen width
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    // Initial animation state: off-screen at the top and fully transparent
                    y = -viewHeight // Start above the screen
                    alpha = 0f
                }

                // 3. Add the view and start the animation
                try {
                    windowManager.addView(textView, params)
                    clarificationQuestionViews.add(textView)

                    // Animate the view from its starting position to the calculated finalYPosition
                    val animator = ValueAnimator.ofFloat(0f, 1f).apply {
                        duration = 500L
                        startDelay = (index * 150).toLong() // Stagger animation

                        addUpdateListener { animation ->
                            val progress = animation.animatedValue as Float
                            // Animate Y position from its off-screen start to its final place
                            params.y = (finalYPosition * progress - viewHeight * (1 - progress)).toInt()
                            params.alpha = progress
                            windowManager.updateViewLayout(textView, params)
                        }
                    }
                    animator.start()

                } catch (e: Exception) {
                    Log.e("ConvAgent", "Failed to display futuristic clarification question.", e)
                }
            }
        }
    }

    /**
     * Removes all currently displayed clarification questions from the screen.
     */
    private fun removeClarificationQuestions() {
        mainHandler.post {
            clarificationQuestionViews.forEach { view ->
                if (view.isAttachedToWindow) {
                    try {
                        windowManager.removeView(view)
                    } catch (e: Exception) {
                        Log.e("ConvAgent", "Error removing clarification view.", e)
                    }
                }
            }
            clarificationQuestionViews.clear()
        }
    }

    private suspend fun gracefulShutdown(exitMessage: String? = null, endReason: String = "graceful") {
        // Track graceful shutdown
        val shutdownBundle = android.os.Bundle().apply {
            putBoolean("had_exit_message", exitMessage != null)
            putInt("conversation_length", conversationHistory.size)
            putBoolean("text_mode_used", isTextModeActive)
            putInt("clarification_attempts", clarificationAttempts)
            putInt("stt_error_attempts", sttErrorAttempts)
        }
        firebaseAnalytics.logEvent("conversation_ended_gracefully", shutdownBundle)
        
        // Track conversation end in Firebase

        trackConversationEnd(endReason)
        
        visualFeedbackManager.hideTtsWave()
        visualFeedbackManager.hideTranscription()
        visualFeedbackManager.hideSpeakingOverlay()
        visualFeedbackManager.hideInputBox()

        if (exitMessage != null) {
                speechCoordinator.speakText(exitMessage)
                delay(2000) // Give TTS time to finish
            }
            // 1. Extract memories from the conversation before ending
            // Removed old memory extraction logic
            triggerMemoryGeneration()
            
            // 3. Stop the service
            stopSelf()

    }

    /**
     * Immediately stops all TTS, STT, and background tasks, hides all UI, and stops the service.
     * This is used for forceful termination, like an outside tap.
     */
    private suspend fun instantShutdown() {
        // Track instant shutdown
        val instantShutdownBundle = android.os.Bundle().apply {
            putInt("conversation_length", conversationHistory.size)
            putBoolean("text_mode_used", isTextModeActive)
            putInt("clarification_attempts", clarificationAttempts)
            putInt("stt_error_attempts", sttErrorAttempts)
        }
        firebaseAnalytics.logEvent("conversation_ended_instantly", instantShutdownBundle)
        
        // Track conversation end in Firebase
        trackConversationEnd("instant")
        
        Log.d("ConvAgent", "Instant shutdown triggered by user.")
        speechCoordinator.stopSpeaking()
        speechCoordinator.stopListening()
        visualFeedbackManager.hideTtsWave()
        visualFeedbackManager.hideTranscription()
        visualFeedbackManager.hideSpeakingOverlay()
        visualFeedbackManager.hideInputBox()

        removeClarificationQuestions()
        // Make a thread-safe copy of the conversation history.
        // Removed old memory extraction logic
        triggerMemoryGeneration()
        
        serviceScope.cancel("User tapped outside, forcing instant shutdown.")

        stopSelf()
    }

    /**
     * Tracks the conversation start in Firebase by creating a new conversation entry.
     * This method is inspired by AgentService's Firebase operations.
     */
    private fun trackConversationStart() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.w("ConvAgent", "Cannot track conversation, user is not logged in.")
            return
        }

        // Generate a unique conversation ID
        conversationId = "${System.currentTimeMillis()}_${currentUser.uid.take(8)}"

        serviceScope.launch {
            try {
                val conversationEntry = hashMapOf(
                    "conversationId" to conversationId,
                    "startedAt" to Timestamp.now(),
                    "endedAt" to null,
                    "messageCount" to 0,
                    "textModeUsed" to false,
                    "clarificationAttempts" to 0,
                    "sttErrorAttempts" to 0,
                    "endReason" to null, // "graceful", "instant", "command", "model", "stt_errors"
                    "tasksRequested" to 0,
                    "tasksExecuted" to 0
                )

                // Append the conversation to the user's conversationHistory array
                db.collection("users").document(currentUser.uid)
                    .update("conversationHistory", FieldValue.arrayUnion(conversationEntry))
                    .await()

                Log.d("ConvAgent", "Successfully tracked conversation start in Firebase for user ${currentUser.uid}: $conversationId")
            } catch (e: Exception) {
                Log.e("ConvAgent", "Failed to track conversation start in Firebase", e)
                // Don't fail the conversation if Firebase tracking fails
            }
        }
    }

    /**
     * Tracks individual messages in the conversation.
     * Fire and forget operation.
     */
    private fun trackMessage(role: String, message: String, messageType: String = "text") {
        val currentUser = auth.currentUser
        if (currentUser == null || conversationId == null) {
            return
        }

        serviceScope.launch {
            try {
                val messageEntry = hashMapOf(
                    "conversationId" to conversationId,
                    "role" to role, // "user" or "model"
                    "message" to message.take(500), // Limit message length for storage
                    "messageType" to messageType, // "text", "task", "clarification"
                    "timestamp" to Timestamp.now(),
                    "inputMode" to if (isTextModeActive) "text" else "voice"
                )

                // Append the message to the user's messageHistory array
                db.collection("users").document(currentUser.uid)
                    .update("messageHistory", FieldValue.arrayUnion(messageEntry))
                    .await()

                Log.d("ConvAgent", "Successfully tracked message in Firebase: $role - ${message.take(50)}...")
            } catch (e: Exception) {
                Log.e("ConvAgent", "Failed to track message in Firebase", e)
            }
        }
    }

    /**
     * Updates the conversation completion status in Firebase.
     * Fire and forget operation.
     */
    private fun trackConversationEnd(endReason: String, tasksRequested: Int = 0, tasksExecuted: Int = 0) {
        val currentUser = auth.currentUser
        if (currentUser == null || conversationId == null) {
            return
        }

        serviceScope.launch {
            try {
                val completionEntry = hashMapOf(
                    "conversationId" to conversationId,
                    "endedAt" to Timestamp.now(),
                    "messageCount" to conversationHistory.size,
                    "textModeUsed" to isTextModeActive,
                    "clarificationAttempts" to clarificationAttempts,
                    "sttErrorAttempts" to sttErrorAttempts,
                    "endReason" to endReason,
                    "tasksRequested" to tasksRequested,
                    "tasksExecuted" to tasksExecuted,
                    "status" to "completed"
                )

                // Append the completion status to the user's conversationHistory array
                db.collection("users").document(currentUser.uid)
                    .update("conversationHistory", FieldValue.arrayUnion(completionEntry))
                    .await()

                Log.d("ConvAgent", "Successfully tracked conversation end in Firebase: $conversationId ($endReason)")
            } catch (e: Exception) {
                Log.e("ConvAgent", "Failed to track conversation end in Firebase", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("ConvAgent", "Service onDestroy")
        
        overlayManager.stopObserving()
        firebaseAnalytics.logEvent("conversational_agent_destroyed", null)
        
        // Track conversation end if not already tracked
        if (conversationId != null) {
            trackConversationEnd("service_destroyed")
        }
        
        removeClarificationQuestions()
        serviceScope.cancel()
        isRunning = false
        
        // Stop state monitoring and set final state
        pandaStateManager.setState(PandaState.IDLE)
        pandaStateManager.stopMonitoring()
        visualFeedbackManager.hideSmallDeltaGlow()
        visualFeedbackManager.hideSpeakingOverlay() // <-- ADD THIS LINE
        // USE the new manager to hide the wave and transcription view
        visualFeedbackManager.hideTtsWave()
        visualFeedbackManager.hideTranscription()
        visualFeedbackManager.hideInputBox()

    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun fetchMemories() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.w("ConvAgent", "User not logged in, cannot fetch memories")
            return
        }

        Log.d("ConvAgent", "Starting async memory fetch for user: ${currentUser.uid}")
        db.collection("users").document(currentUser.uid)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("ConvAgent", "Listen failed.", e)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val memoriesList = snapshot.get("memories") as? List<Map<String, Any>>
                    if (memoriesList != null) {
                        cachedMemories = memoriesList.mapNotNull { map ->
                            try {
                                UserMemory(
                                    id = map["id"] as? String ?: "",
                                    text = map["text"] as? String ?: "",
                                    source = map["source"] as? String ?: "User",
                                    createdAt = (map["createdAt"] as? Timestamp)?.toDate() ?: java.util.Date()
                                )
                            } catch (e: Exception) {
                                Log.e("ConvAgent", "Error parsing memory", e)
                                null
                            }
                        }.sortedByDescending { it.createdAt } // Sort by newest first
                        
                        Log.d("ConvAgent", "Fetched ${cachedMemories.size} memories from Firestore")
                    } else {
                        Log.d("ConvAgent", "No memories field found in user document")
                        cachedMemories = emptyList()
                    }
                } else {
                    Log.d("ConvAgent", "Current data: null")
                }
            }
    }

    private fun triggerMemoryGeneration() {
        val currentUser = auth.currentUser
        val userEmail = currentUser?.email

        if (userEmail == null) {
            Log.w("ConvAgent", "User email not found, cannot trigger memory generation")
            return
        }

        Log.d("ConvAgent", "Triggering memory generation for email: $userEmail")

        val json = JSONObject()
        json.put("email", userEmail)

        val requestBody = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url("https://getuserdatabyemail-w7fh6zvo4q-uc.a.run.app")
            .addHeader("X-API-Key", BuildConfig.GCLOUD_PROXY_URL_KEY)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("ConvAgent", "Memory generation request failed", e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        Log.e("ConvAgent", "Memory generation request failed with code: ${response.code}")
                    } else {
                        Log.d("ConvAgent", "Memory generation request sent successfully. Response: ${response.body?.string()}")
                    }
                }
            }
        })
    }

}