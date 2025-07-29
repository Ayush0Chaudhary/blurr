package com.example.blurr

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentCallbacks2
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.*

import android.util.Log
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.example.blurr.api.Finger
import kotlinx.coroutines.*
import kotlinx.coroutines.delay

import android.graphics.LinearGradient
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.blurr.agent.DeepSearch
import com.example.blurr.agent.VisionMode
import com.example.blurr.agent.ClarificationAgent
import com.example.blurr.agent.InfoPool
import com.example.blurr.agent.AgentConfig
import com.example.blurr.utilities.TTSManager
import com.example.blurr.utilities.STTManager
import com.example.blurr.utilities.UserIdManager
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import com.example.blurr.agent.VisionHelper
import com.example.blurr.ConversationalAgentService
import com.example.blurr.services.WakeWordService
import com.example.blurr.services.EnhancedWakeWordService
import com.example.blurr.utilities.getReasoningModelApiResponse
import android.view.View
import com.example.blurr.services.AgentTaskService
import com.example.blurr.utilities.PermissionManager

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var inputField: EditText
    private lateinit var contentModerationInputField: EditText
    private lateinit var performTaskButton: TextView
    private lateinit var contentModerationButton: TextView
    private lateinit var handler: Handler
    private lateinit var grantPermission: Button
    private lateinit var tvPermissionStatus: TextView
    private lateinit var voiceInputButton: ImageButton
    private lateinit var voiceStatusText: TextView
    private lateinit var settingsButton: ImageButton
    // REMOVED settings-related UI variables

    private lateinit var ttsManager: TTSManager
    private lateinit var sttManager: STTManager
    private lateinit var deepSearchAgent: DeepSearch
    private lateinit var clarificationAgent: ClarificationAgent
    private lateinit var userId: String
    private lateinit var permissionManager: PermissionManager
    private lateinit var conversationalAgentButton: TextView

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Toast.makeText(this, "Microphone permission granted!", Toast.LENGTH_SHORT).show()
                // The onResume will handle updating the UI
            } else {
                Toast.makeText(this, "Permission denied.", Toast.LENGTH_SHORT).show()
            }
        }

    private val dialogueLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val enhancedInstruction = result.data?.getStringExtra(DialogueActivity.EXTRA_ENHANCED_INSTRUCTION)
            if (!enhancedInstruction.isNullOrEmpty()) {
                executeTask(enhancedInstruction)
            }
        } else if (result.resultCode == RESULT_CANCELED) {
            Toast.makeText(this, "Dialogue cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val userIdManager = UserIdManager(applicationContext)
        userId = userIdManager.getOrCreateUserId()
        println(userId)
        askForNotificationPermission()
        checkAndRequestOverlayPermission()
        // Initialize permission manager
        permissionManager = PermissionManager(this)
        permissionManager.initializePermissionLauncher()
        permissionManager.requestAllPermissions()

        grantPermission = findViewById(R.id.btn_request_permission)
        tvPermissionStatus = findViewById(R.id.tv_permission_status)
        inputField = findViewById(R.id.inputField)
        contentModerationInputField = findViewById(R.id.contentMoniterInputField)
        performTaskButton = findViewById(R.id.performTaskButton)
        contentModerationButton = findViewById(R.id.contentMoniterButton)
        statusText = findViewById(R.id.tv_service_status)
        voiceInputButton = findViewById(R.id.voiceInputButton)
        voiceStatusText = findViewById(R.id.voiceStatusText)

        conversationalAgentButton = findViewById(R.id.conversationalAgentButton)
        settingsButton = findViewById(R.id.settingsButton)
        // REMOVED findViewById for settings views

        grantPermission.setOnClickListener {
            permissionManager.openAccessibilitySettings()
        }

        ttsManager = TTSManager.getInstance(this)
        sttManager = STTManager(this)
        deepSearchAgent = DeepSearch()
        clarificationAgent = ClarificationAgent()

        setupClickListeners()
        setupVoiceInput()
        setupSettingsButton()
        // REMOVED call to setupVisionModeListener()

        handler = Handler(Looper.getMainLooper())

        val karanTextView = findViewById<TextView>(R.id.karan_textview_gradient)
        karanTextView.measure(0, 0)
        val textShader: Shader = LinearGradient(
            0f, 0f, karanTextView.measuredWidth.toFloat(), 0f,
            intArrayOf("#BE63F3".toColorInt(), "#5880F7".toColorInt()),
            null, Shader.TileMode.CLAMP
        )
        karanTextView.paint.shader = textShader
        permissionManager.checkAndRequestOverlayPermission()
        val githubLink = findViewById<TextView>(R.id.github_link_textview)
        githubLink.setOnClickListener {
            val url = "https://github.com/Ayush0Chaudhary/blurr"
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = url.toUri()
            startActivity(intent)
        }
    }

    @SuppressLint("SetTextI18n")
    @RequiresApi(Build.VERSION_CODES.R)
    private fun setupClickListeners() {
        performTaskButton.setOnClickListener {
            if (!isAccessibilityServiceEnabled()) {
                Toast.makeText(this, "Accessibility permission is required to perform this task.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val instruction = inputField.text.toString().trim()
            if (instruction.isBlank()) {
                Toast.makeText(this, "Please enter an instruction", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            statusText.text = "Thinking..."
            performTaskButton.isEnabled = false

            lifecycleScope.launch {
                try {
                    val needsClarification = checkIfClarificationNeeded(instruction)
                    if (needsClarification.first) {
                        startClarificationDialogue(instruction, needsClarification.second)
                    } else {
                        executeTask(instruction)
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error processing instruction", e)
                    val errorMessage = "Error processing your instruction: ${e.message}"
                    statusText.text = errorMessage
                    ttsManager.speakText(errorMessage)
                } finally {
                    performTaskButton.isEnabled = true
                }
            }
        }

        contentModerationButton.setOnClickListener {
            if (!isAccessibilityServiceEnabled()) {
                Toast.makeText(this, "Accessibility permission is required for content filtering.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            lifecycleScope.launch {
                val instruction = contentModerationInputField.text.toString()
                if (instruction.isBlank()) {
                    Toast.makeText(this@MainActivity, "Please enter an instruction", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val fin = Finger(this@MainActivity)
                fin.home()
                delay(1500)

                Log.d("MainActivity", "Starting ContentModerationService after delay.")
                val serviceIntent = Intent(this@MainActivity, ContentModerationService::class.java).apply {
                    putExtra("MODERATION_INSTRUCTION", instruction)
                }
                startService(serviceIntent)
                Toast.makeText(this@MainActivity, "Content Moderation Started", Toast.LENGTH_SHORT).show()
            }
        }

    }

    // REMOVED setupVisionModeListener()

    @SuppressLint("ClickableViewAccessibility")
    private fun setupVoiceInput() {
        voiceInputButton.setOnTouchListener { _, event ->
            if (!permissionManager.isMicrophonePermissionGranted()) {
                permissionManager.requestMicrophonePermission()
            }
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    startVoiceInput()
                    true
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    stopVoiceInput()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupSettingsButton() {
        settingsButton.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
    }

    private fun startVoiceInput() {
        // ... (this logic remains unchanged)
        voiceStatusText.text = getString(R.string.listening)
        voiceInputButton.isPressed = true
        sttManager.startListening(
            onResult = { recognizedText ->
                runOnUiThread {
                    voiceStatusText.text = getString(R.string.hold_to_speak)
                    voiceInputButton.isPressed = false
                    inputField.setText(recognizedText)
                    Toast.makeText(this, "Recognized: $recognizedText", Toast.LENGTH_SHORT).show()
                    performTaskFromVoiceInput(recognizedText)
                }
            },
            onError = { errorMessage ->
                runOnUiThread {
                    voiceStatusText.text = getString(R.string.hold_to_speak)
                    voiceInputButton.isPressed = false
                    Toast.makeText(this, "Error: $errorMessage", Toast.LENGTH_SHORT).show()
                }
            },
            onListeningStateChange = { isListening ->
                runOnUiThread {
                    voiceInputButton.isPressed = isListening
                    voiceStatusText.text = if (isListening) getString(R.string.listening) else getString(R.string.hold_to_speak)
                }
            }
        )
    }

    // New function to start the service
    private fun setUpConversationalAgent() {
        conversationalAgentButton.setOnClickListener {
            if (!ConversationalAgentService.isRunning) {
//                handler.postDelayed({ updateUI() }, 500)
                Log.d("ConvAgent", "Checking")
                Log.d("MainActivity", "Permission granted, starting ConversationalAgentService.")
                val serviceIntent = Intent(this, ConversationalAgentService::class.java)
                ContextCompat.startForegroundService(this, serviceIntent)
                Toast.makeText(this, "Conversation agent is active", Toast.LENGTH_SHORT).show()
                handler.postDelayed({ updateUI() }, 100)
            }else{
                Log.d("MainActivity", "Stopping ConversationalAgentService.")
                stopService(Intent(this, ConversationalAgentService::class.java))
                Toast.makeText(this, "Conversation ended", Toast.LENGTH_SHORT).show()
                handler.postDelayed({ updateUI() }, 100)
            }
        }

    }


    private fun performTaskFromVoiceInput(instruction: String) {
        // ... (this logic remains unchanged)
        statusText.text = "Processing voice command..."
        lifecycleScope.launch {
            try {
                val needsClarification = checkIfClarificationNeeded(instruction)
                if (needsClarification.first) {
                    startClarificationDialogue(instruction, needsClarification.second)
                } else {
                    executeTask(instruction)
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error processing voice command", e)
                val errorMessage = "Error processing your command: ${e.message}"
                statusText.text = errorMessage
                ttsManager.speakText(errorMessage)
            }
        }
    }

    private suspend fun checkIfClarificationNeeded(instruction: String): Pair<Boolean, List<String>> {
        // ... (this logic remains unchanged)
        try {
            val tempInfoPool = InfoPool(instruction = instruction)
            val config = AgentConfig(visionMode = VisionMode.XML, apiKey = "", context = this)
            val prompt = clarificationAgent.getPrompt(tempInfoPool, config)
            val chat = clarificationAgent.initChat()
            val combined = VisionHelper.createChatResponse("user", prompt, chat, config)
            val response = withContext(Dispatchers.IO) {
                getReasoningModelApiResponse(combined, apiKey = config.apiKey, agentState = tempInfoPool)
            }
            val parsedResult = clarificationAgent.parseResponse(response.toString())
            val status = parsedResult["status"] ?: "CLEAR"
            val questionsText = parsedResult["questions"] ?: ""
            return if (status == "NEEDS_CLARIFICATION" && questionsText.isNotEmpty()) {
                val questions = clarificationAgent.parseQuestions(questionsText)
                Pair(true, questions)
            } else {
                Pair(false, emptyList())
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error checking clarification", e)
            return Pair(false, emptyList())
        }
    }

    private fun startClarificationDialogue(originalInstruction: String, questions: List<String>) {
        // ... (this logic remains unchanged)
        val intent = Intent(this, DialogueActivity::class.java).apply {
            putExtra(DialogueActivity.EXTRA_ORIGINAL_INSTRUCTION, originalInstruction)
            putExtra(DialogueActivity.EXTRA_QUESTIONS, ArrayList(questions))
        }
        dialogueLauncher.launch(intent)
    }

    private fun executeTask(instruction: String) {
        // ... (this logic remains unchanged, but you might want to pass vision mode from settings)
        // For now, it defaults to what was previously hardcoded behavior.
         val visionMode =  VisionMode.XML.name

        lifecycleScope.launch {
            try {
                val announcement = "I will now perform the task"
                ttsManager.speakText(announcement)

                delay(1000)
                Log.d("MainActivity", "This is a UI Task. Starting AgentTaskService.")
                statusText.text = "Agent started to perform task..."
                val serviceIntent = Intent(this@MainActivity, AgentTaskService::class.java).apply {
                    putExtra("TASK_INSTRUCTION", instruction)
                    putExtra("VISION_MODE", visionMode)
                }
                startService(serviceIntent)
                val fin = Finger(this@MainActivity)
                fin.home()

            } catch (e: Exception) {
                Log.e("MainActivity", "Error executing task", e)
                val errorMessage = "Error executing task: ${e.message}"
                statusText.text = errorMessage
                ttsManager.speakText(errorMessage)
            }
        }
    }

    private fun stopVoiceInput() {
        // ... (this logic remains unchanged)
        sttManager.stopListening()
        voiceStatusText.text = getString(R.string.hold_to_speak)
        voiceInputButton.isPressed = false
    }

    override fun onTrimMemory(level: Int) {
        // ... (this logic remains unchanged)
        super.onTrimMemory(level)
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    override fun onDestroy() {
        super.onDestroy()
        sttManager.shutdown()
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        return permissionManager.isAccessibilityServiceEnabled()
    }

    @SuppressLint("SetTextI18n")
    private fun updateUI() {
        val isPermissionGranted = isAccessibilityServiceEnabled()
        if (isPermissionGranted) {
            tvPermissionStatus.visibility = View.GONE
            grantPermission.visibility = View.GONE
        } else {
            tvPermissionStatus.visibility = View.VISIBLE
            grantPermission.visibility = View.VISIBLE
            tvPermissionStatus.text = "Permission: Not Granted"
            tvPermissionStatus.setTextColor(Color.RED)
        }
        // REMOVED logic to update wakeWordButton
    }

    private fun askForNotificationPermission() {
        // ... (this logic remains unchanged)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun checkAndRequestOverlayPermission() {
        // ... (this logic remains unchanged)
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
        }

    }
}