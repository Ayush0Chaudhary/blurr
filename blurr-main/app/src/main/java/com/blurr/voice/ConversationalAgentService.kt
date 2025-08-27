package com.blurr.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
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
import com.blurr.voice.agent.v1.AgentConfig
import com.blurr.voice.agent.v1.ClarificationAgent
import com.blurr.voice.agent.v1.InfoPool
import com.blurr.voice.agent.v1.VisionHelper
import com.blurr.voice.agent.v1.VisionMode
//import com.blurr.voice.services.AgentTaskService
import com.blurr.voice.utilities.SpeechCoordinator
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.graphics.toColorInt
import com.blurr.voice.utilities.TTSManager
import com.blurr.voice.utilities.addResponse
import com.blurr.voice.utilities.getReasoningModelApiResponse
import com.blurr.voice.data.MemoryManager
import com.blurr.voice.data.MemoryExtractor
import com.blurr.voice.utilities.FreemiumManager
import com.blurr.voice.utilities.UserProfileManager
import com.blurr.voice.utilities.VisualFeedbackManager
import com.blurr.voice.v2.AgentService
import com.google.ai.client.generativeai.type.TextPart
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
    private var transcriptionView: TextView? = null
    private val visualFeedbackManager by lazy { VisualFeedbackManager.getInstance(this) }
    private var isTextModeActive = false
    private val freemiumManager by lazy { FreemiumManager() }

    // Add these at the top of your ConversationalAgentService class
    private var clarificationAttempts = 0
    private val maxClarificationAttempts = 1
    private var sttErrorAttempts = 0
    private val maxSttErrorAttempts = 2

     private val clarificationAgent = ClarificationAgent()
    private val windowManager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val memoryManager by lazy { MemoryManager.getInstance(this) }
    private val usedMemories = mutableSetOf<String>() // Track memories already used in this conversation


    companion object {
        const val NOTIFICATION_ID = 3
        const val CHANNEL_ID = "ConversationalAgentChannel"
        var isRunning = false
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()
        Log.d("ConvAgent", "Service onCreate")
        isRunning = true
        createNotificationChannel()
        initializeConversation()
        ttsManager.setCaptionsEnabled(true)
        clarificationAttempts = 0 // Reset clarification attempts counter
        sttErrorAttempts = 0 // Reset STT error attempts counter
        usedMemories.clear() // Clear used memories for new conversation
        visualFeedbackManager.showTtsWave()
        showInputBoxIfNeeded()


    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun showInputBoxIfNeeded() {
        // This function ensures the input box is always configured correctly
        // whether it's the first time or a subsequent turn in text mode.
        visualFeedbackManager.showInputBox(
            onActivated = {
                // This is called when the user taps the EditText
                enterTextMode()
            },
            onSubmit = { submittedText ->
                // This is the existing callback for when text is submitted
                processUserInput(submittedText)
            },
        )
    }

    /**
     * Call this when the user starts interacting with the text input.
     * It stops any ongoing voice interaction.
     */
    private fun enterTextMode() {
        if (isTextModeActive) return
        Log.d("ConvAgent", "Entering Text Mode. Stopping STT/TTS.")
        isTextModeActive = true
        speechCoordinator.stopListening()
        speechCoordinator.stopSpeaking()
        // Optionally hide the transcription view since user is typing
        visualFeedbackManager.hideTranscription()
    }


    @RequiresApi(Build.VERSION_CODES.O)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("ConvAgent", "Service onStartCommand")
        startForeground(NOTIFICATION_ID, createNotification())

        serviceScope.launch {
            if (conversationHistory.size == 1) {
                val greeting = getPersonalizedGreeting()
                conversationHistory = addResponse("model", greeting, conversationHistory)
                speakAndThenListen(greeting)
            }
        }
        return START_STICKY
    }

    /**
     * Gets a personalized greeting using the user's name from memories if available
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


    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun speakAndThenListen(text: String, draw: Boolean = true) {
        updateSystemPromptWithMemories()
        ttsManager.setCaptionsEnabled(draw)

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
                visualFeedbackManager.updateTranscription(recognizedText)
                mainHandler.postDelayed({
                    visualFeedbackManager.hideTranscription()
                }, 500)
                processUserInput(recognizedText)

            },
            onError = { error ->
                Log.e("ConvAgent", "STT Error: $error")
                if (isTextModeActive) return@startListening // Ignore errors in text mode
                visualFeedbackManager.hideTranscription()
                sttErrorAttempts++
                serviceScope.launch {
                    if (sttErrorAttempts >= maxSttErrorAttempts) {
                        val exitMessage = "I'm having trouble understanding you clearly. Please try calling later!"
                        gracefulShutdown(exitMessage)
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
                    visualFeedbackManager.showTranscription()
                }
            }
        )
        ttsManager.setCaptionsEnabled(true)
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
    @RequiresApi(Build.VERSION_CODES.O)
    private fun processUserInput(userInput: String) {
        serviceScope.launch {
            removeClarificationQuestions()

            conversationHistory = addResponse("user", userInput, conversationHistory)

            try {
                if (userInput.equals("stop", ignoreCase = true) || userInput.equals("exit", ignoreCase = true)) {
                    gracefulShutdown("Goodbye!")
                    return@launch
                }

                val rawModelResponse = getReasoningModelApiResponse(conversationHistory, "") ?: "### Type ###\nReply\n### Reply ###\nI'm sorry, I had an issue.\n### Instruction ###\n\n### Should End ###\nContinue"
                val decision = parseModelResponse(rawModelResponse)

                when (decision.type) {
                    "Task" -> {
                        Log.d("ConvAgent", "Model identified a task. Checking for clarification...")
                        // --- NEW: Check if the task instruction needs clarification ---
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
                                    speakAndThenListen(questionToAsk, false)
                                } else {
                                    Log.d(
                                        "ConvAgent",
                                        "Task is clear. Executing: ${decision.instruction}"
                                    )
//                                val taskIntent = Intent(this@ConversationalAgentService, AgentTaskService::class.java).apply {
//                                    putExtra("TASK_INSTRUCTION", decision.instruction)
//                                    putExtra("VISION_MODE", "XML")
//                                }
                                    AgentService.start(applicationContext, decision.instruction)
//                                startService(taskIntent)
                                    gracefulShutdown(decision.reply)
                                }
                            } else {
                                Log.d(
                                    "ConvAgent",
                                    "Max clarification attempts reached ($maxClarificationAttempts). Proceeding with task execution."
                                )
                                AgentService.start(applicationContext, decision.instruction)

                                gracefulShutdown(decision.reply)
                            }
                        }else{
                            Log.w("ConvAgent", "User has no tasks remaining. Denying request.")
                            val upgradeMessage = "${getPersonalizedGreeting()} You've used all your free tasks for the month. Please upgrade in the app to unlock more. We can still talk in voice mode."
                            conversationHistory = addResponse("model", upgradeMessage, conversationHistory)
//                            gracefulShutdown(upgradeMessage)
                            speakAndThenListen(upgradeMessage)
                        }
                    }
                    else -> { // Default to "Reply"
                        if (decision.shouldEnd) {
                            Log.d("ConvAgent", "Model decided to end the conversation.")
                            gracefulShutdown(decision.reply)
                        } else {
                            conversationHistory = addResponse("model", rawModelResponse, conversationHistory)
                            speakAndThenListen(decision.reply)
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e("ConvAgent", "Error processing user input: ${e.message}", e)
                speakAndThenListen("closing voice mode")
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
            You are a helpful voice assistant called Panda that can either have a conversation or ask executor to execute tasks on the user's phone.
            The executor can speak, listen, see screen, tap screen, and basically use the phone as normal human would

            Some Guideline:
            1. If the user ask you to summarize the screen, just send the task to the executor to summarize the screen getting straight to the point. No questions.
            2. If the user ask you to do something creative, you do this task and be the most creative person in the world.
            3. If you know the user's name from the memories, refer to them by their name to make the conversation more personal and friendly.

            Use these memories to answer the user's question with his personal data
            ### Memory Context Start ###
            {memory_context}
            ### Memory Context Ends ###
        
            Analyze the user's request and respond in the following format:

            ### Type ###
            Either "Task" or "Reply".
            - Use "Task" if the user is asking you to DO something on the device (e.g., "open settings", "send a text to Mom", "post a tweet").
            - Use "Reply" for conversational questions (e.g., "what's the weather?", "tell me a joke", "how are you?").

            ### Reply ###
            The conversational text to speak to the user.
            - If it's a task, this should be a confirmation, like "Okay, opening settings." or "Sure, I can do that.".
            - If it's a reply, this is the answer to the user's question.
            - If you know the user's name, use it naturally in your responses to make the conversation more personal.

            ### Instruction ###
            - If Type is "Task", provide the precise, literal instruction for the task agent here. This should be a complete command.
            - If Type is "Reply", this field should be empty.

            ### Should End ###
            "Continue" or "Finished". Use "Finished" only when the conversation is naturally over.
        """.trimIndent()

        conversationHistory = addResponse("user", systemPrompt, emptyList())
    }

    /**
     * Updates the system prompt with relevant memories from the database
     */
    private suspend fun updateSystemPromptWithMemories() {
        try {
            // Get the last user message to search for relevant memories
            val lastUserMessage = conversationHistory.lastOrNull { it.first == "user" }
                ?.second?.filterIsInstance<TextPart>()
                ?.joinToString(" ") { it.text } ?: ""

            if (lastUserMessage.isNotEmpty()) {
                Log.d("ConvAgent", "Searching for memories relevant to: ${lastUserMessage.take(100)}...")

                var relevantMemories = memoryManager.searchMemories(lastUserMessage, topK = 5).toMutableList() // Get more memories to filter from
                val nameMemories = memoryManager.searchMemories("name", topK = 2)
                relevantMemories.addAll(nameMemories)
                if (relevantMemories.isNotEmpty()) {
                    Log.d("ConvAgent", "Found ${relevantMemories.size} relevant memories")

                    // Filter out memories that have already been used in this conversation
                    val newMemories = relevantMemories.filter { memory ->
                        !usedMemories.contains(memory)
                    }.take(20) // Limit to top 20 new memories

                    if (newMemories.isNotEmpty()) {
                        Log.d("ConvAgent", "Adding ${newMemories.size} new memories to context")

                        // Add new memories to the used set
                        newMemories.forEach { usedMemories.add(it) }

                        // Get current memory context from system prompt
                        val currentPrompt = conversationHistory.first().second
                            .filterIsInstance<TextPart>()
                            .firstOrNull()?.text ?: ""

                        val currentMemoryContext = extractCurrentMemoryContext(currentPrompt)
                        val allMemories = (currentMemoryContext + newMemories).distinct()

                        // Update the system prompt with all memories
                        val memoryContext = allMemories.joinToString("\n") { "- $it" }
                        val updatedPrompt = currentPrompt.replace("{memory_context}", memoryContext)

                        if (updatedPrompt.isNotEmpty()) {
                            // Replace the first system message with updated prompt
                            conversationHistory = conversationHistory.toMutableList().apply {
                                set(0, "user" to listOf(TextPart(updatedPrompt)))
                            }
                            Log.d("ConvAgent", "Updated system prompt with ${allMemories.size} total memories (${newMemories.size} new)")
                        }
                    } else {
                        Log.d("ConvAgent", "No new memories to add (all relevant memories already used)")
                    }
                } else {
                    Log.d("ConvAgent", "No relevant memories found")
                }
            }
        } catch (e: Exception) {
            Log.e("ConvAgent", "Error updating system prompt with memories", e)
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

    private suspend fun gracefulShutdown(exitMessage: String? = null) {
        visualFeedbackManager.hideInputBox()

        if (exitMessage != null) {
                speechCoordinator.speakText(exitMessage)
                delay(2000) // Give TTS time to finish
            }
            // 1. Extract memories from the conversation before ending
            if (conversationHistory.size > 1) {
                Log.d("ConvAgent", "Extracting memories before shutdown.")
                MemoryExtractor.extractAndStoreMemories(conversationHistory, memoryManager, usedMemories)
            }
            // 3. Stop the service
            stopSelf()

    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("ConvAgent", "Service onDestroy")
        removeClarificationQuestions()
        serviceScope.cancel()
        ttsManager.setCaptionsEnabled(false)
        isRunning = false
        // USE the new manager to hide the wave and transcription view
        visualFeedbackManager.hideTtsWave()
        visualFeedbackManager.hideTranscription()
        visualFeedbackManager.hideInputBox()

    }

    override fun onBind(intent: Intent?): IBinder? = null
}