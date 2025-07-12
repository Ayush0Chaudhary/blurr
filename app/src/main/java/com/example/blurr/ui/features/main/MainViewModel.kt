package com.example.blurr.ui.features.main

import android.app.Application
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.blurr.ContentModerationService
import com.example.blurr.ScreenInteractionService
import com.example.blurr.agent.ClarificationAgent
import com.example.blurr.api.Finger
import com.example.blurr.services.AgentTaskService
import com.example.blurr.utilities.STTManager
import com.example.blurr.utilities.TTSManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val app: Application
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainContract.State())
    val uiState = _uiState.asStateFlow()

    // Channel is great for one-time events where you want to be sure they are consumed.
    private val _sideEffectChannel = Channel<MainContract.SideEffect>()
    val sideEffects = _sideEffectChannel.receiveAsFlow()


    private val ttsManager: TTSManager = TTSManager.getInstance(app)
    private val sttManager: STTManager = STTManager(app)
    private val clarificationAgent: ClarificationAgent = ClarificationAgent()


    init {
        onEvent(MainContract.Event.CheckServiceStatus)
    }

    fun onEvent(event: MainContract.Event) {
        when (event) {
            is MainContract.Event.OnInstructionChanged -> _uiState.update { it.copy(instruction = event.instruction) }
            is MainContract.Event.OnContentModerationChanged -> _uiState.update { it.copy(contentModerationText = event.text) }
            is MainContract.Event.OnVisionModeChanged -> _uiState.update { it.copy(visionMode = event.mode) }
            is MainContract.Event.OnWakeWordEngineChanged -> _uiState.update { it.copy(usePorcupineEngine = event.usePorcupine) }
            is MainContract.Event.ExecuteTask -> executeTask(event.instruction)
            is MainContract.Event.ExecuteContentModeration -> executeContentModeration()
            is MainContract.Event.ToggleWakeWordService -> {
                // The UI triggers an event, the ViewModel emits a side effect for the Activity to handle
                viewModelScope.launch {
                    _sideEffectChannel.send(MainContract.SideEffect.ToggleWakeWordService(_uiState.value.usePorcupineEngine))
                }
            }
            is MainContract.Event.OnVoiceInputPressed -> startVoiceInput()
            is MainContract.Event.OnVoiceInputReleased -> stopVoiceInput()
            is MainContract.Event.CheckServiceStatus -> checkServiceStatus()
        }
    }

    private fun executeTask(instructionFromEvent: String?) {
        val instruction = instructionFromEvent ?: _uiState.value.instruction
        if (instruction.isBlank()) {
            viewModelScope.launch { _sideEffectChannel.send(MainContract.SideEffect.ShowToast("Please enter an instruction")) }
            return
        }

        _uiState.update { it.copy(statusText = "Thinking...", isTaskButtonEnabled = false) }

        viewModelScope.launch {
            try {
                // In a real scenario, you would call your clarification agent here.
                // For this example, we'll simulate it.
                val needsClarification = false // Set to true to test clarification
                if (needsClarification) {
                    _sideEffectChannel.send(MainContract.SideEffect.StartClarificationDialogue(instruction, listOf("Question 1?", "Question 2?")))
                } else {
                    performTaskExecution(instruction)
                }

            } catch (e: Exception) {
                val errorMessage = "Error: ${e.message}"
                _uiState.update { it.copy(statusText = errorMessage) }
                ttsManager.speakText(errorMessage)
            } finally {
                _uiState.update { it.copy(isTaskButtonEnabled = true) }
            }
        }
    }

    private suspend fun performTaskExecution(instruction: String) {
        ttsManager.speakText("I will now perform the task")
        _uiState.update { it.copy(statusText = "Agent started to perform task...") }

        val serviceIntent = Intent(app, AgentTaskService::class.java).apply {
            putExtra("TASK_INSTRUCTION", instruction)
            putExtra("VISION_MODE", _uiState.value.visionMode.name)
        }
        app.startService(serviceIntent)
        Finger(app).home()
    }


    private fun executeContentModeration() {
        val instruction = _uiState.value.contentModerationText
        if (instruction.isBlank()) {
            viewModelScope.launch { _sideEffectChannel.send(MainContract.SideEffect.ShowToast("Please enter a moderation instruction")) }
            return
        }
        viewModelScope.launch {
            Finger(app).home()
            val serviceIntent = Intent(app, ContentModerationService::class.java).apply {
                putExtra("MODERATION_INSTRUCTION", instruction)
            }
            app.startService(serviceIntent)
            _uiState.update { it.copy(statusText = "Content Moderation Started") }
        }
    }

    private fun startVoiceInput() {
        _uiState.update { it.copy(isListening = true, voiceStatusText = "Listening...") }
        sttManager.startListening(
            onResult = { recognizedText ->
                _uiState.update {
                    it.copy(
                        isListening = false,
                        voiceStatusText = "Hold to Speak",
                        instruction = recognizedText
                    )
                }
                executeTask(recognizedText)
            },
            onError = { errorMessage ->
                _uiState.update {
                    it.copy(
                        isListening = false,
                        voiceStatusText = "Hold to Speak",
                        statusText = "Error: $errorMessage"
                    )
                }
            },
            onListeningStateChange = { isListening ->
                _uiState.update {
                    it.copy(
                        isListening = isListening,
                        voiceStatusText = if (isListening) "Listening..." else "Hold to Speak"
                    )
                }
            }
        )
    }

    private fun stopVoiceInput() {
        sttManager.stopListening()
        _uiState.update { it.copy(isListening = false, voiceStatusText = "Hold to Speak") }
    }


    private fun checkServiceStatus() {
        _uiState.update {
            it.copy(
                isPermissionGranted = isAccessibilityServiceEnabled(app),
                isWakeWordServiceRunning = com.example.blurr.services.EnhancedWakeWordService.isRunning
            )
        }
    }

    private fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val service = context.packageName + "/" + ScreenInteractionService::class.java.canonicalName
        val accessibilityEnabled = Settings.Secure.getInt(
            context.contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            0
        )
        if (accessibilityEnabled == 1) {
            val settingValue = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            if (settingValue != null) {
                val splitter = TextUtils.SimpleStringSplitter(':')
                splitter.setString(settingValue)
                while (splitter.hasNext()) {
                    if (splitter.next().equals(service, ignoreCase = true)) {
                        return true
                    }
                }
            }
        }
        return false
    }

    override fun onCleared() {
        super.onCleared()
        sttManager.shutdown()
        ttsManager.shutdown()
    }
}
