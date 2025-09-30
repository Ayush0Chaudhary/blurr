/**
 * @file ConversationalAgentService.kt
 * @brief Defines the main background service for the V1 voice-first conversational agent.
 *
 * This file contains the `ConversationalAgentService`, a complex, long-running service that
 * orchestrates the entire voice-first user experience. It manages the conversation loop,
 * handles STT/TTS, displays various UI overlays (like the wave visualizer and transcription),
 * checks if tasks need clarification, and delegates autonomous task execution to the V2 `AgentService`.
 * It also integrates with Firebase for analytics and conversation history tracking.
 */
package com.blurr.voice

import android.Manifest
import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import com.blurr.voice.agents.ClarificationAgent
import com.blurr.voice.api.Eyes
import com.blurr.voice.data.MemoryManager
import com.blurr.voice.utilities.FreemiumManager
import com.blurr.voice.utilities.ServicePermissionManager
import com.blurr.voice.utilities.SpeechCoordinator
import com.blurr.voice.utilities.TTSManager
import com.blurr.voice.utilities.UserProfileManager
import com.blurr.voice.utilities.VisualFeedbackManager
import com.blurr.voice.utilities.addResponse
import com.blurr.voice.utilities.getReasoningModelApiResponse
import com.blurr.voice.v2.AgentService
import com.google.ai.client.generativeai.type.TextPart
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
/**
 * Represents the structured decision parsed from the LLM's JSON response.
 * This dictates the agent's next action, whether it's replying, starting a task, or ending the conversation.
 *
 * @property type The type of action to take. Must be one of "Reply", "Task", or "KillTask".
 * @property reply The text to speak to the user.
 * @property instruction For "Task" types, the specific instruction for the V2 agent.
 * @property shouldEnd `true` if the conversation should terminate after this action.
 */
data class ModelDecision(
    val type: String = "Reply",
    val reply: String,
    val instruction: String = "",
    val shouldEnd: Boolean = false
)

/**
 * The primary background service for the V1 conversational agent.
 *
 * This service is the orchestrator for the voice-first experience. It runs as a foreground service
 * to ensure it is not killed by the OS. Its responsibilities include:
 * - Managing the main conversation loop (listen -> process -> speak -> listen).
 * - Handling both voice and text input.
 * - Using `SpeechCoordinator` for STT/TTS operations.
 * - Displaying and managing UI overlays (`VisualFeedbackManager`) for visual feedback.
 * - Parsing LLM responses into structured `ModelDecision` objects.
 * - Running a clarification flow to resolve ambiguous tasks.
 * - Delegating autonomous tasks to the `AgentService` (V2 agent).
 * - Tracking analytics and conversation history with Firebase.
 */
class ConversationalAgentService : Service() {

    private val speechCoordinator by lazy { SpeechCoordinator.getInstance(this) }
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var conversationHistory = listOf<Pair<String, List<Any>>>()
    private val ttsManager by lazy { TTSManager.getInstance(this) }
    private val clarificationQuestionViews = mutableListOf<View>()
    private var transcriptionView: TextView? = null
    private val visualFeedbackManager by lazy { VisualFeedbackManager.getInstance(this) }
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
    private val memoryManager by lazy { MemoryManager.getInstance(this) }
    private val usedMemories = mutableSetOf<String>()
    private var hasHeardFirstUtterance = false
    private lateinit var firebaseAnalytics: FirebaseAnalytics
    private val eyes by lazy { Eyes(this) }
    
    private val db = Firebase.firestore
    private val auth = Firebase.auth
    private var conversationId: String? = null

    companion object {
        const val NOTIFICATION_ID = 3
        const val CHANNEL_ID = "ConversationalAgentChannel"
        const val ACTION_STOP_SERVICE = "com.blurr.voice.ACTION_STOP_SERVICE"
        var isRunning = false
        const val MEMORY_ENABLED = false
    }

    /**
     * Called when the service is first created.
     * Initializes all components, resets state, and sets up the initial UI.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate() {
        super.onCreate()
        Log.d("ConvAgent", "Service onCreate")
        
        firebaseAnalytics = Firebase.analytics
        firebaseAnalytics.logEvent("conversational_agent_started", null)
        
        isRunning = true
        createNotificationChannel()
        initializeConversation()
        ttsManager.setCaptionsEnabled(true)
        clarificationAttempts = 0
        sttErrorAttempts = 0
        usedMemories.clear()
        hasHeardFirstUtterance = false
        visualFeedbackManager.showTtsWave()
        showInputBoxIfNeeded()
        visualFeedbackManager.showSpeakingOverlay()
    }

    /**
     * Displays the text input box overlay.
     * Wires up callbacks for when the user activates text mode, submits text, or taps outside.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun showInputBoxIfNeeded() {
        visualFeedbackManager.showInputBox(
            onActivated = {
                enterTextMode()
            },
            onSubmit = { submittedText ->
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
     * Activates text input mode.
     * This stops any ongoing voice interaction (STT/TTS) and hides voice-related UI.
     */
    private fun enterTextMode() {
        if (isTextModeActive) return
        Log.d("ConvAgent", "Entering Text Mode. Stopping STT/TTS.")
        
        firebaseAnalytics.logEvent("text_mode_activated", null)
        
        isTextModeActive = true
        speechCoordinator.stopListening()
        speechCoordinator.stopSpeaking()
        visualFeedbackManager.hideTranscription()
    }

    /**
     * Called every time the service is started.
     * Handles incoming intents, checks for required permissions, starts the foreground notification,
     * and initiates the conversation loop.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("ConvAgent", "Service onStartCommand")

        if (intent?.action == ACTION_STOP_SERVICE) {
            Log.i("ConvAgent", "Received stop action. Stopping service.")
            stopSelf()
            return START_NOT_STICKY
        }
        
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
                delay(2000)
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

        firebaseAnalytics.logEvent("conversation_initiated", null)
        trackConversationStart()

        serviceScope.launch {
            Log.d("ConvAgent", "Starting immediate listening (no greeting)")
            startImmediateListening()
        }
        return START_STICKY
    }

    /**
     * Gets a personalized greeting using the user's name from memories.
     * NOTE: This method is kept for potential future use but is not currently called on startup.
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
     * Starts the STT listener immediately without speaking a greeting.
     * Memory extraction is deferred until after the first user utterance is received.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun startImmediateListening() {
        Log.d("ConvAgent", "Starting immediate listening without greeting")
        
        if (isTextModeActive) {
            Log.d("ConvAgent", "In text mode, ensuring input box is visible and skipping voice listening.")
            mainHandler.post {
                showInputBoxIfNeeded()
            }
            return
        }
        
        speechCoordinator.startListening(
            onResult = { recognizedText ->
                if (isTextModeActive) return@startListening
                Log.d("ConvAgent", "Final user transcription: $recognizedText")
                visualFeedbackManager.updateTranscription(recognizedText)
                mainHandler.postDelayed({
                    visualFeedbackManager.hideTranscription()
                }, 500)
                
                if (!hasHeardFirstUtterance) {
                    hasHeardFirstUtterance = true
                    Log.d("ConvAgent", "First utterance received, triggering memory extraction")
                    serviceScope.launch {
                        try {
                            updateSystemPromptWithMemories()
                        } catch (e: Exception) {
                            Log.e("ConvAgent", "Error during first utterance memory extraction", e)
                        }
                    }
                }
                
                processUserInput(recognizedText)
            },
            onError = { error ->
                Log.e("ConvAgent", "STT Error: $error")
                if (isTextModeActive) return@startListening
                
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
                if (isTextModeActive) return@startListening
                visualFeedbackManager.updateTranscription(partialText)
            },
            onListeningStateChange = { listening ->
                Log.d("ConvAgent", "Listening state: $listening")
                if (listening) {
                    if (isTextModeActive) return@startListening
                    visualFeedbackManager.showTranscription()
                }
            }
        )
    }

    /**
     * A core function of the conversation loop. Speaks a given text and then immediately
     * starts listening for the user's response.
     * @param text The text for the assistant to speak.
     * @param draw A flag to enable or disable on-screen captions for the spoken text.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun speakAndThenListen(text: String, draw: Boolean = true) {
        if (hasHeardFirstUtterance) {
            updateSystemPromptWithMemories()
        }
        ttsManager.setCaptionsEnabled(draw)

        speechCoordinator.speakText(text)
        Log.d("ConvAgent", "Panda said: $text")
        if (isTextModeActive) {
            Log.d("ConvAgent", "In text mode, ensuring input box is visible and skipping voice listening.")
            mainHandler.post {
                showInputBoxIfNeeded()
            }
            return
        }
        speechCoordinator.startListening(
            onResult = { recognizedText ->
                if (isTextModeActive) return@startListening
                Log.d("ConvAgent", "Final user transcription: $recognizedText")
                visualFeedbackManager.updateTranscription(recognizedText)
                mainHandler.postDelayed({
                    visualFeedbackManager.hideTranscription()
                }, 500)
                
                if (!hasHeardFirstUtterance) {
                    hasHeardFirstUtterance = true
                    Log.d("ConvAgent", "First utterance received, triggering memory extraction")
                    serviceScope.launch {
                        try {
                            updateSystemPromptWithMemories()
                        } catch (e: Exception) {
                            Log.e("ConvAgent", "Error during first utterance memory extraction", e)
                        }
                    }
                }
                
                processUserInput(recognizedText)

            },
            onError = { error ->
                Log.e("ConvAgent", "STT Error: $error")
                if (isTextModeActive) return@startListening
                
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
                if (isTextModeActive) return@startListening
                visualFeedbackManager.updateTranscription(partialText)
            },
            onListeningStateChange = { listening ->
                Log.d("ConvAgent", "Listening state: $listening")
                if (listening) {
                    if (isTextModeActive) return@startListening
                    visualFeedbackManager.showTranscription()
                }
            }
        )
        ttsManager.setCaptionsEnabled(true)
    }

    /**
     * Displays the transcription text view overlay. (Legacy, now handled by VisualFeedbackManager).
     */
    private fun showTranscriptionView() {
        if (transcriptionView != null) return

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
                y = 250
            }

            try {
                windowManager.addView(transcriptionView, params)
            } catch (e: Exception) {
                Log.e("ConvAgent", "Failed to add transcription view.", e)
                transcriptionView = null
            }
        }
    }

    /**
     * Updates the text of the transcription view. (Legacy).
     */
    private fun updateTranscriptionView(text: String) {
        transcriptionView?.text = text
    }

    /**
     * Hides the transcription text view overlay. (Legacy).
     */
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

    /**
     * The central logic hub for processing user input.
     *
     * This function takes the user's transcribed text, adds it to the conversation history,
     * queries the reasoning model (LLM), parses the response, and then dispatches the
     * appropriate action (e.g., reply, start task, kill task, clarify).
     *
     * @param userInput The text transcribed from the user's speech or entered via keyboard.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun processUserInput(userInput: String) {
        serviceScope.launch {
            removeClarificationQuestions()
            updateSystemPromptWithAgentStatus()
            
            if (!hasHeardFirstUtterance) {
                hasHeardFirstUtterance = true
                Log.d("ConvAgent", "First utterance received via processUserInput, triggering memory extraction")
                try {
                    updateSystemPromptWithMemories()
                } catch (e: Exception) {
                    Log.e("ConvAgent", "Error during first utterance memory extraction", e)
                }
            }

            conversationHistory = addResponse("user", userInput, conversationHistory)
            
            trackMessage("user", userInput, "input")

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
                val defaultJsonResponse = """{"Type": "Reply", "Reply": "I'm sorry, I had an issue.", "Instruction": "", "Should End": "Continue"}"""
                val rawModelResponse = getReasoningModelApiResponse(conversationHistory) ?: defaultJsonResponse
                val decision = parseModelResponse(rawModelResponse)
                Log.d("TTS_DEBUG", "Reply received from GeminiApi: -->${rawModelResponse}<--")
                when (decision.type) {
                    "Task" -> {
                        val taskBundle = android.os.Bundle().apply {
                            putString("task_instruction", decision.instruction.take(100))
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
                        removeClarificationQuestions()
                        if(freemiumManager.canPerformTask()){
                            Log.d("ConvAgent", "Allowance check passed. Proceeding with task.")

                            freemiumManager.decrementTaskCount()
                            if (clarificationAttempts < maxClarificationAttempts) {
                                val (needsClarification, questions) = checkIfClarificationNeeded(
                                    decision.instruction
                                )
                                Log.d("ConcAgent", needsClarification.toString())
                                Log.d("ConcAgent", questions.toString())

                                if (needsClarification) {
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
                                
                                firebaseAnalytics.logEvent("task_executed_max_clarification", taskBundle)
                                
                                AgentService.start(applicationContext, decision.instruction)
                                trackMessage("model", decision.reply, "task_confirmation")
                                gracefulShutdown(decision.reply, "task_executed")
                            }
                        }else{
                            Log.w("ConvAgent", "User has no tasks remaining. Denying request.")
                            
                            firebaseAnalytics.logEvent("task_rejected_freemium_limit", null)
                            
                            val upgradeMessage = "Hey! You've used all your free tasks for the month. Please upgrade in the app to unlock more. We can still talk in voice mode."
                            conversationHistory = addResponse("model", upgradeMessage, conversationHistory)
                            trackMessage("model", upgradeMessage, "freemium_limit")
                            speakAndThenListen(upgradeMessage)
                        }
                    }
                    "KillTask" -> {
                        Log.d("ConvAgent", "Model requested to kill the running agent service.")
                        
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
                
                val errorBundle = android.os.Bundle().apply {
                    putString("error_message", e.message?.take(100) ?: "Unknown error")
                    putString("error_type", e.javaClass.simpleName)
                }
                firebaseAnalytics.logEvent("input_processing_error", errorBundle)
                
                speakAndThenListen("closing voice mode")
            }
        }
    }
    /**
     * Uses the [ClarificationAgent] to determine if a task instruction is ambiguous and requires
     * follow-up questions.
     * @param instruction The task instruction from the user.
     * @return A Pair where the first element is `true` if clarification is needed, and the second
     * is a list of questions to ask the user.
     */
    private suspend fun checkIfClarificationNeeded(instruction: String): Pair<Boolean, List<String>> {
        Log.d("ConvAgent", "Checking for clarification on instruction: '$instruction'")

        val result = clarificationAgent.analyze(
            instruction = instruction,
            conversationHistory = conversationHistory,
            context = this@ConversationalAgentService
        )

        val needsClarification = result.status == "NEEDS_CLARIFICATION" && result.questions.isNotEmpty()

        if (needsClarification) {
            Log.d("ConvAgent", "Clarification is needed. Questions: ${result.questions}")
        } else {
            Log.d("ConvAgent", "Instruction is clear. Status: ${result.status}")
        }

        return Pair(needsClarification, result.questions)
    }

    /**
     * Constructs the initial system prompt from a template.
     * It sets up the agent's persona, guidelines, and placeholders for dynamic context
     * like screen content and memories.
     */
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
            2. If you know the user's name from the memories, refer to them by their name to make the conversation more personal and friendly.
            3. Use the current screen context to better understand what the user is looking at and provide more relevant responses.
            4. If the user asks about something on the screen, you can reference the screen content directly.
            5. When the user ask to sing, shout or produce any sound, just generate text, we will sing it for you.
            6. Your code is opensource so you can tell tell that to user. repo is ayush0chaudhary/blurr
            
            $memoryContextSection
        
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
        """.trimIndent()

        conversationHistory = addResponse("user", systemPrompt, emptyList())
    }

    /**
     * Dynamically updates the system prompt to include the current status of the V2 `AgentService`.
     * This lets the LLM know if a task is already in progress.
     */
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

        conversationHistory = conversationHistory.toMutableList().apply {
            set(0, "user" to listOf(TextPart(updatedPromptText)))
        }
        Log.d("ConvAgent", "System prompt updated with agent status: ${AgentService.isRunning}")
    }

    /**
     * Gets the current screen context using the [Eyes] class, including the current app,
     * keyboard status, and a simplified XML representation of the view hierarchy.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun getScreenContext(): String {
        return try {
            val currentApp = eyes.getCurrentActivityName()
            val screenXml = eyes.openXMLEyes()
            val keyboardStatus = eyes.getKeyBoardStatus()
            
            val screenContextBundle = android.os.Bundle().apply {
                putString("current_app", currentApp.take(50))
                putBoolean("keyboard_visible", keyboardStatus)
                putInt("screen_xml_length", screenXml.length)
            }
            firebaseAnalytics.logEvent("screen_context_captured", screenContextBundle)
            
            """
            Current App: $currentApp
            Keyboard Visible: $keyboardStatus
            Screen Content:
            $screenXml
            """.trimIndent()
        } catch (e: Exception) {
            Log.e("ConvAgent", "Error getting screen context", e)
            
            val errorBundle = android.os.Bundle().apply {
                putString("error_message", e.message?.take(100) ?: "Unknown error")
                putString("error_type", e.javaClass.simpleName)
            }
            firebaseAnalytics.logEvent("screen_context_error", errorBundle)
            
            "Screen context unavailable"
        }
    }

    /**
     * Enriches the system prompt with relevant memories and the current screen context.
     * It searches for memories related to the user's last utterance and injects them into
     * the `{memory_context}` placeholder.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun updateSystemPromptWithMemories() {
        try {
            val screenContext = getScreenContext()
            Log.d("ConvAgent", "Retrieved screen context: ${screenContext.take(200)}...")
            
            val currentPrompt = conversationHistory.first().second
                .filterIsInstance<TextPart>()
                .firstOrNull()?.text ?: ""

            var updatedPrompt = currentPrompt.replace("{screen_context}", screenContext)

            if (!MEMORY_ENABLED) {
                Log.d("ConvAgent", "Memory is disabled, skipping memory operations")
                updatedPrompt = updatedPrompt.replace("{memory_context}", "Memory system is temporarily disabled")
            } else {
                val lastUserMessage = conversationHistory.lastOrNull { it.first == "user" }
                    ?.second?.filterIsInstance<TextPart>()
                    ?.joinToString(" ") { it.text } ?: ""

                if (lastUserMessage.isNotEmpty()) {
                    Log.d("ConvAgent", "Searching for memories relevant to: ${lastUserMessage.take(100)}...")

                    var relevantMemories = memoryManager.searchMemories(lastUserMessage, topK = 5).toMutableList()
                    val nameMemories = memoryManager.searchMemories("name", topK = 2)
                    relevantMemories.addAll(nameMemories)
                    if (relevantMemories.isNotEmpty()) {
                        Log.d("ConvAgent", "Found ${relevantMemories.size} relevant memories")

                        val newMemories = relevantMemories.filter { memory ->
                            !usedMemories.contains(memory)
                        }.take(20)

                        if (newMemories.isNotEmpty()) {
                            Log.d("ConvAgent", "Adding ${newMemories.size} new memories to context")

                            newMemories.forEach { usedMemories.add(it) }

                            val currentMemoryContext = extractCurrentMemoryContext(updatedPrompt)
                            val allMemories = (currentMemoryContext + newMemories).distinct()

                            val memoryContext = allMemories.joinToString("\n") { "- $it" }
                            updatedPrompt = updatedPrompt.replace("{memory_context}", memoryContext)

                            Log.d("ConvAgent", "Updated system prompt with ${allMemories.size} total memories (${newMemories.size} new)")
                        } else {
                            Log.d("ConvAgent", "No new memories to add (all relevant memories already used)")
                            val currentMemoryContext = extractCurrentMemoryContext(updatedPrompt)
                            val memoryContext = currentMemoryContext.joinToString("\n") { "- $it" }
                            updatedPrompt = updatedPrompt.replace("{memory_context}", memoryContext)
                        }
                    } else {
                        Log.d("ConvAgent", "No relevant memories found")
                        updatedPrompt = updatedPrompt.replace("{memory_context}", "No relevant memories found")
                    }
                } else {
                    updatedPrompt = updatedPrompt.replace("{memory_context}", "")
                }
            }

            if (updatedPrompt.isNotEmpty()) {
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
     * Extracts the current memory context from the system prompt string.
     */
    private fun extractCurrentMemoryContext(prompt: String): List<String> {
        return try {
            val memorySection = prompt.substringAfter("##### MEMORY CONTEXT #####")
                .substringBefore("##### END MEMORY CONTEXT #####")
                .trim()

            if (memorySection.isNotEmpty() && !memorySection.contains("{memory_context}")) {
                memorySection.lines()
                    .filter { it.trim().startsWith("- ") }
                    .map { it.trim().substring(2) }
                    .filter { it.isNotEmpty() }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("ConvAgent", "Error extracting current memory context", e)
            emptyList()
        }
    }

    /**
     * Safely parses the JSON string from the LLM into a [ModelDecision] object.
     * Includes fallback logic to handle malformed JSON or other parsing errors.
     * @param response The raw JSON string from the model.
     * @return A [ModelDecision] object.
     */
    private fun parseModelResponse(response: String): ModelDecision {
        try {
            val json = JSONObject(response)
            Log.d("justchecking", json.toString())
            val type = json.optString("Type", "Reply")
            val reply = json.optString("Reply", "")
            val instruction = json.optString("Instruction", "")
            val shouldEndStr = json.optString("Should End", "Continue")
            val shouldEnd = shouldEndStr.equals("Finished", ignoreCase = true)

            val finalReply = if (reply.isEmpty() && type.equals("Reply", ignoreCase = true)) {
                "I'm not sure how to respond to that."
            } else {
                reply
            }

            return ModelDecision(type, finalReply, instruction, shouldEnd)
        } catch (e: org.json.JSONException) {
            Log.e("ConvAgent", "Error parsing JSON response, falling back. Response: $response", e)
            return ModelDecision(reply = "I seem to have gotten my thoughts tangled. Could you repeat that?")
        } catch (e: Exception) {
            Log.e("ConvAgent", "Generic error parsing model response, falling back. Response: $response", e)
            return ModelDecision(reply = "I had a minor issue processing that. Could you try again?")
        }
    }

    /**
     * Creates the foreground service notification.
     */
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
                android.R.drawable.ic_media_pause,
                "Stop",
                stopPendingIntent
            )
            .build()
    }

    /**
     * Creates the notification channel required for foreground services on Android 8.0+.
     */
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
            val topMargin = 100
            val verticalSpacing = 20
            var accumulatedHeight = 0

            questions.forEachIndexed { index, questionText ->
                val textView = TextView(this).apply {
                    text = questionText
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

                val finalYPosition = topMargin + accumulatedHeight

                accumulatedHeight += viewHeight + verticalSpacing

                val params = WindowManager.LayoutParams(
                    (windowManager.defaultDisplay.width * 0.9).toInt(),
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    y = -viewHeight
                    alpha = 0f
                }

                try {
                    windowManager.addView(textView, params)
                    clarificationQuestionViews.add(textView)

                    val animator = ValueAnimator.ofFloat(0f, 1f).apply {
                        duration = 500L
                        startDelay = (index * 150).toLong()

                        addUpdateListener { animation ->
                            val progress = animation.animatedValue as Float
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

    /**
     * Shuts down the service gracefully.
     * It speaks an optional exit message, extracts memories from the conversation,
     * and then stops the service.
     * @param exitMessage The final message to speak before shutting down.
     * @param endReason A string indicating why the conversation ended, for analytics.
     */
    private suspend fun gracefulShutdown(exitMessage: String? = null, endReason: String = "graceful") {
        val shutdownBundle = android.os.Bundle().apply {
            putBoolean("had_exit_message", exitMessage != null)
            putInt("conversation_length", conversationHistory.size)
            putBoolean("text_mode_used", isTextModeActive)
            putInt("clarification_attempts", clarificationAttempts)
            putInt("stt_error_attempts", sttErrorAttempts)
        }
        firebaseAnalytics.logEvent("conversation_ended_gracefully", shutdownBundle)
        
        trackConversationEnd(endReason)
        
        visualFeedbackManager.hideTtsWave()
        visualFeedbackManager.hideTranscription()
        visualFeedbackManager.hideSpeakingOverlay()
        visualFeedbackManager.hideInputBox()

        if (exitMessage != null) {
                speechCoordinator.speakText(exitMessage)
                delay(2000)
            }
            if (conversationHistory.size > 1 && MEMORY_ENABLED) {
                Log.d("ConvAgent", "Extracting memories before shutdown.")
            } else if (!MEMORY_ENABLED) {
                Log.d("ConvAgent", "Memory disabled, skipping memory extraction.")
            }
            stopSelf()

    }

    /**
     * Immediately stops all TTS, STT, and background tasks, hides all UI, and stops the service.
     * This is used for forceful termination, such as when the user taps outside the UI.
     */
    private suspend fun instantShutdown() {
        val instantShutdownBundle = android.os.Bundle().apply {
            putInt("conversation_length", conversationHistory.size)
            putBoolean("text_mode_used", isTextModeActive)
            putInt("clarification_attempts", clarificationAttempts)
            putInt("stt_error_attempts", sttErrorAttempts)
        }
        firebaseAnalytics.logEvent("conversation_ended_instantly", instantShutdownBundle)
        
        trackConversationEnd("instant")
        
        Log.d("ConvAgent", "Instant shutdown triggered by user.")
        speechCoordinator.stopSpeaking()
        speechCoordinator.stopListening()
        visualFeedbackManager.hideTtsWave()
        visualFeedbackManager.hideTranscription()
        visualFeedbackManager.hideSpeakingOverlay()
        visualFeedbackManager.hideInputBox()

        removeClarificationQuestions()
        if (conversationHistory.size > 1 && MEMORY_ENABLED) {
            Log.d("ConvAgent", "Extracting memories before shutdown.")
        } else if (!MEMORY_ENABLED) {
            Log.d("ConvAgent", "Memory disabled, skipping memory extraction.")
        }
        serviceScope.cancel("User tapped outside, forcing instant shutdown.")

        stopSelf()
    }

    /**
     * Tracks the start of a conversation in Firebase by creating a new conversation entry.
     */
    private fun trackConversationStart() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.w("ConvAgent", "Cannot track conversation, user is not logged in.")
            return
        }

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
                    "endReason" to null,
                    "tasksRequested" to 0,
                    "tasksExecuted" to 0
                )

                db.collection("users").document(currentUser.uid)
                    .update("conversationHistory", FieldValue.arrayUnion(conversationEntry))
                    .await()

                Log.d("ConvAgent", "Successfully tracked conversation start in Firebase for user ${currentUser.uid}: $conversationId")
            } catch (e: Exception) {
                Log.e("ConvAgent", "Failed to track conversation start in Firebase", e)
            }
        }
    }

    /**
     * Tracks an individual message in the current conversation in Firebase.
     * This is a fire-and-forget operation.
     * @param role The role of the message author ("user" or "model").
     * @param message The content of the message.
     * @param messageType The type of message (e.g., "text", "task", "clarification").
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
                    "role" to role,
                    "message" to message.take(500),
                    "messageType" to messageType,
                    "timestamp" to Timestamp.now(),
                    "inputMode" to if (isTextModeActive) "text" else "voice"
                )

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
     * Updates the conversation record in Firebase with completion details.
     * This is a fire-and-forget operation.
     * @param endReason A string indicating why the conversation ended.
     * @param tasksRequested The number of tasks requested during the conversation.
     * @param tasksExecuted The number of tasks executed.
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

                db.collection("users").document(currentUser.uid)
                    .update("conversationHistory", FieldValue.arrayUnion(completionEntry))
                    .await()

                Log.d("ConvAgent", "Successfully tracked conversation end in Firebase: $conversationId ($endReason)")
            } catch (e: Exception) {
                Log.e("ConvAgent", "Failed to track conversation end in Firebase", e)
            }
        }
    }

    /**
     * Called when the service is being destroyed.
     * Ensures all resources are released, coroutines are cancelled, and UI overlays are removed.
     */
    override fun onDestroy() {
        super.onDestroy()
        Log.d("ConvAgent", "Service onDestroy")
        
        firebaseAnalytics.logEvent("conversational_agent_destroyed", null)
        
        if (conversationId != null) {
            trackConversationEnd("service_destroyed")
        }
        
        removeClarificationQuestions()
        serviceScope.cancel()
        ttsManager.setCaptionsEnabled(false)
        isRunning = false
        visualFeedbackManager.hideSpeakingOverlay()
        visualFeedbackManager.hideTtsWave()
        visualFeedbackManager.hideTranscription()
        visualFeedbackManager.hideInputBox()

    }

    override fun onBind(intent: Intent?): IBinder? = null
}