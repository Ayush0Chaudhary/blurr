package com.example.blurr.ui.features.main

import com.example.blurr.agent.VisionMode

class MainContract {

    sealed class Event {
        data class OnInstructionChanged(val instruction: String) : Event()
        data class OnContentModerationChanged(val text: String) : Event()
        data class OnVisionModeChanged(val mode: VisionMode) : Event()
        data class OnWakeWordEngineChanged(val usePorcupine: Boolean) : Event()
        data class ExecuteTask(val instruction: String? = null) : Event()
        object ExecuteContentModeration : Event()
        object ToggleWakeWordService : Event()
        object OnVoiceInputPressed : Event()
        object OnVoiceInputReleased : Event()
        object CheckServiceStatus : Event()
    }

    data class State(
        val instruction: String = "",
        val contentModerationText: String = "",
        val statusText: String = "Welcome to Blurr!",
        val isTaskButtonEnabled: Boolean = true,
        val isPermissionGranted: Boolean = false,
        val visionMode: VisionMode = VisionMode.XML,
        val voiceStatusText: String = "Hold to Speak",
        val isListening: Boolean = false,
        val isWakeWordServiceRunning: Boolean = false,
        val usePorcupineEngine: Boolean = false
    )

    // Sealed class to define one-time events
    sealed class SideEffect {
        data class StartClarificationDialogue(val instruction: String, val questions: List<String>) : SideEffect()
        data class ToggleWakeWordService(val usePorcupine: Boolean) : SideEffect()
        data class ShowToast(val message: String) : SideEffect()
    }
}
