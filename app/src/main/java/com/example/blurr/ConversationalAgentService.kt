package com.example.blurr

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.animation.ValueAnimator
import android.graphics.Typeface
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.example.blurr.agent.AgentConfig
import com.example.blurr.agent.ClarificationAgent
import com.example.blurr.agent.InfoPool
import com.example.blurr.agent.VisionHelper
import com.example.blurr.agent.VisionMode
import com.example.blurr.services.AgentTaskService
import com.example.blurr.utilities.SpeechCoordinator
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.graphics.toColorInt
import com.example.blurr.utilities.STTVisualizer
import com.example.blurr.utilities.TTSManager
import com.example.blurr.utilities.addResponse
import com.example.blurr.utilities.getReasoningModelApiResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    private val clarificationQuestionViews = mutableListOf<View>()

    // Add these at the top of your ConversationalAgentService class
    private var clarificationAttempts = 0
    private val maxClarificationAttempts = 1

     private val clarificationAgent = ClarificationAgent()
    private val windowManager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    companion object {
        const val NOTIFICATION_ID = 3
        const val CHANNEL_ID = "ConversationalAgentChannel"
        var isRunning = false
    }
    private enum class AgentState {
        IDLE,
        AWAITING_CLARIFICATION,
        EXECUTING_TASK
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("ConvAgent", "Service onCreate")
        isRunning = true
        createNotificationChannel()
        initializeConversation()
        ttsManager.setCaptionsEnabled(true)
        clarificationAttempts = 0 // Reset clarification attempts counter
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("ConvAgent", "Service onStartCommand")
        startForeground(NOTIFICATION_ID, createNotification())

        serviceScope.launch {
            if (conversationHistory.size == 1) {
                val greeting = "Hello! How can I help you today?"
                conversationHistory = addResponse("model", greeting, conversationHistory)
                speakAndThenListen(greeting)
            }
        }
        return START_STICKY
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun speakAndThenListen(text: String, draw: Boolean = true) { // Default draw to true
        if (draw) {
            Log.d("ConvAgent", "Displaying agent utterance: $text. Setting captions to true.")
            ttsManager.setCaptionsEnabled(true)
        } else {
            Log.d("ConvAgent", "Not displaying agent utterance: $text. Setting captions to false.")
            ttsManager.setCaptionsEnabled(false)
        }
        speechCoordinator.speakText(text)
        Log.d("ConvAgent", "Panda said: $text")

        speechCoordinator.startListening(
            onResult = { recognizedText ->
                Log.d("ConvAgent", "User said: $recognizedText")
                processUserInput(recognizedText)
            },
            onError = { error ->
                Log.e("ConvAgent", "STT Error: $error")
                serviceScope.launch {
                    speakAndThenListen("I'm sorry, I didn't catch that.")
                }
            },
            onListeningStateChange = { listening ->
                Log.d("ConvAgent", "Listening state: $listening")
            }
        )
        ttsManager.setCaptionsEnabled(true)
    }

    // --- CHANGED: Rewritten to process the new custom text format ---
    @RequiresApi(Build.VERSION_CODES.O)
    private fun processUserInput(userInput: String) {
        serviceScope.launch {
            removeClarificationQuestions()

            conversationHistory = addResponse("user", userInput, conversationHistory)

            try {
                if (userInput.equals("stop", ignoreCase = true) || userInput.equals("exit", ignoreCase = true)) {
                    speakAndThenListen("Goodbye!")
                    delay(2000)
                    stopSelf()
                    return@launch
                }

                val rawModelResponse = getReasoningModelApiResponse(conversationHistory, "") ?: "### Type ###\nReply\n### Reply ###\nI'm sorry, I had an issue.\n### Instruction ###\n\n### Should End ###\nContinue"
                val decision = parseModelResponse(rawModelResponse)

                when (decision.type) {
                    "Task" -> {
                        Log.d("ConvAgent", "Model identified a task. Checking for clarification...")
                        // --- NEW: Check if the task instruction needs clarification ---
                        removeClarificationQuestions()
                        
                        if (clarificationAttempts < maxClarificationAttempts) {
                            val (needsClarification, questions) = checkIfClarificationNeeded(decision.instruction)
                            Log.d("ConcAgent", needsClarification.toString())
                            Log.d("ConcAgent", questions.toString())

                            if (needsClarification) {
                                clarificationAttempts++
                                displayClarificationQuestions(questions)
                                // If clarification is needed, ask the questions and continue the conversation.
                                val questionToAsk = "I can help with that, but first: ${questions.joinToString(" and ")}"
                                Log.d("ConvAgent", "Task needs clarification. Asking: '$questionToAsk' (Attempt $clarificationAttempts/$maxClarificationAttempts)")
                                conversationHistory = addResponse("model", "Clarification needed for task: ${decision.instruction}", conversationHistory)
                                speakAndThenListen(questionToAsk, false)
                            } else {
                                Log.d("ConvAgent", "Task is clear. Executing: ${decision.instruction}")
                                speechCoordinator.speakText(decision.reply)


                                val taskIntent = Intent(this@ConversationalAgentService, AgentTaskService::class.java).apply {
                                    putExtra("TASK_INSTRUCTION", decision.instruction)
                                    putExtra("VISION_MODE", "XML")
                                }
                                startService(taskIntent)
                                stopSelf()
                            }
                        } else {
                            // Max attempts reached, proceed directly without checking for clarification
                            Log.d("ConvAgent", "Max clarification attempts reached ($maxClarificationAttempts). Proceeding with task execution.")
                            speechCoordinator.speakText(decision.reply)
//                            delay(1500)

                            val taskIntent = Intent(this@ConversationalAgentService, AgentTaskService::class.java).apply {
                                putExtra("TASK_INSTRUCTION", decision.instruction)
                                putExtra("VISION_MODE", "XML")
                            }
                            startService(taskIntent)
                            stopSelf()
                        }


                    }
                    else -> { // Default to "Reply"
                        if (decision.shouldEnd) {
                            Log.d("ConvAgent", "Model decided to end the conversation.")
                            speechCoordinator.speakText(decision.reply)
                            delay(2000)
                            stopSelf()
                        } else {
                            conversationHistory = addResponse("model", rawModelResponse, conversationHistory)
                            speakAndThenListen(decision.reply)
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e("ConvAgent", "Error processing user input: ${e.message}", e)
                speakAndThenListen("I encountered an error. Let's try again.")
            }
        }
    }

    // --- NEW: Added the clarification check logic directly into the service ---
    private suspend fun checkIfClarificationNeeded(instruction: String): Pair<Boolean, List<String>> {
        try {
            val tempInfoPool = InfoPool(instruction = instruction)
            // Use 'this' as the context for the service
            val config = AgentConfig(visionMode = VisionMode.XML, apiKey = "", context = this)
            
            Log.d("ConvAgent", "Checking clarification with conversation history (${conversationHistory.size} messages)")
            val prompt = clarificationAgent.getPromptWithHistory(tempInfoPool, config, conversationHistory)
            val chat = clarificationAgent.initChat()
            val combined = VisionHelper.createChatResponse("user", prompt, chat, config)
            val response = withContext(Dispatchers.IO) {
                getReasoningModelApiResponse(combined, apiKey = config.apiKey)
            }

            val parsedResult = clarificationAgent.parseResponse(response.toString())
            val status = parsedResult["status"] ?: "CLEAR"
            val questionsText = parsedResult["questions"] ?: ""

            Log.d("ConvAgent", "Clarification check result: status=$status, questions=${questionsText.take(100)}...")

            return if (status == "NEEDS_CLARIFICATION" && questionsText.isNotEmpty()) {
                val questions = clarificationAgent.parseQuestions(questionsText)
                Log.d("ConvAgent", "Clarification needed. Questions: $questions")
                Pair(true, questions)
            } else {
                Log.d("ConvAgent", "No clarification needed or no questions generated")
                Pair(false, emptyList())
            }
        } catch (e: Exception) {
            Log.e("ConvAgent", "Error checking for clarification", e)
            return Pair(false, emptyList())
        }
    }


    private fun initializeConversation() {
        val systemPrompt = """
            You are a helpful voice assistant that can either have a conversation or execute tasks on the user's phone.
            The agent can do speak, listen, see screen, tap screen, and basically use the phone as normal human would
            Analyze the user's request and respond in the following format:

            ### Type ###
            Either "Task" or "Reply".
            - Use "Task" if the user is asking you to DO something on the device (e.g., "open settings", "send a text to Mom", "post a tweet").
            - Use "Reply" for conversational questions (e.g., "what's the weather?", "tell me a joke", "how are you?").

            ### Reply ###
            The conversational text to speak to the user.
            - If it's a task, this should be a confirmation, like "Okay, opening settings." or "Sure, I can do that.".
            - If it's a reply, this is the answer to the user's question.

            ### Instruction ###
            - If Type is "Task", provide the precise, literal instruction for the task agent here. This should be a complete command.
            - If Type is "Reply", this field should be empty.

            ### Should End ###
            "Continue" or "Finished". Use "Finished" only when the conversation is naturally over.
        """.trimIndent()

        conversationHistory = addResponse("user", systemPrompt, emptyList())
    }

    private fun parseModelResponse(response: String): ModelDecision {
        try {
            val type = response.substringAfter("### Type ###", "").substringBefore("###").trim()
            val reply = response.substringAfter("### Reply ###", "").substringBefore("###").trim()
            val instruction = response.substringAfter("### Instruction ###", "").substringBefore("###").trim()
            val shouldEndStr = response.substringAfter("### Should End ###", "").trim()
            val shouldEnd = shouldEndStr.equals("Finished", ignoreCase = true)

            val finalReply = if (reply.isEmpty() && type.equals("Reply", ignoreCase = true)) {
                "I'm not sure how to respond to that."
            } else {
                reply
            }

            return ModelDecision(type, finalReply, instruction, shouldEnd)
        } catch (e: Exception) {
            Log.e("ConvAgent", "Error parsing custom format, falling back. Response: $response")
            return ModelDecision(reply = "I seem to have gotten my thoughts tangled. Could you repeat that?")
        }
    }
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Conversational Agent")
            .setContentText("Listening for your commands...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
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

                // **--- FIX IS HERE ---**
                // A. Measure the view to get its dimensions *before* positioning.
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

    override fun onDestroy() {
        super.onDestroy()
        Log.d("ConvAgent", "Service onDestroy")
        removeClarificationQuestions()
        serviceScope.cancel()
        ttsManager.setCaptionsEnabled(false)
        isRunning = false
    }

    override fun onBind(intent: Intent?): IBinder? = null
}