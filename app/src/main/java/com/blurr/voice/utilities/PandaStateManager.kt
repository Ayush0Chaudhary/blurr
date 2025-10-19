package com.blurr.voice.utilities

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.blurr.voice.ConversationalAgentService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CopyOnWriteArrayList

data class PandaStateInfo(
    val state: PandaState = PandaState.IDLE,
    val message: String? = null
)

class PandaStateManager private constructor(private val context: Context) {

    companion object {
        @Volatile private var INSTANCE: PandaStateManager? = null

        fun getInstance(context: Context): PandaStateManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PandaStateManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val speechCoordinator by lazy { SpeechCoordinator.getInstance(context) }
    private val visualFeedbackManager by lazy { VisualFeedbackManager.getInstance(context) }

    private val _stateFlow = MutableStateFlow(PandaStateInfo())
    val stateFlow = _stateFlow.asStateFlow()

    private var currentState: PandaState = PandaState.IDLE
    private var hasRecentError: Boolean = false
    private var errorClearRunnable: Runnable? = null
    private val stateChangeListeners = CopyOnWriteArrayList<(PandaState) -> Unit>()
    private var isMonitoring = false
    private var monitoringRunnable: Runnable? = null

    fun addStateChangeListener(listener: (PandaState) -> Unit) {
        stateChangeListeners.add(listener)
    }

    fun removeStateChangeListener(listener: (PandaState) -> Unit) {
        stateChangeListeners.remove(listener)
    }

    fun getCurrentState(): PandaState = currentState

    fun updateStateWithMessage(newState: PandaState, message: String? = null) {
        Logger.ui("updateStateWithMessage called with newState: $newState")
        val newPandaStateInfo = PandaStateInfo(state = newState, message = message)

        if (_stateFlow.value != newPandaStateInfo) {
            _stateFlow.value = newPandaStateInfo
            Logger.ui("StateFlow updated to ${newState.name} with message: ${message?.take(50)}")
        } else {
            Logger.ui("StateFlow new value is same as old value. No update sent.")
        }

        updateState(newState)
    }

    fun startMonitoring() {
        if (isMonitoring) {
            Logger.ui("PandaStateManager: Already monitoring, skipping start.")
            return
        }

        isMonitoring = true
        Logger.ui("PandaStateManager: Starting state monitoring.")
        scheduleStateUpdate()
    }

    fun stopMonitoring() {
        if (!isMonitoring) {
            return
        }

        isMonitoring = false
        Logger.ui("PandaStateManager: Stopping state monitoring.")

        monitoringRunnable?.let { mainHandler.removeCallbacks(it) }
        errorClearRunnable?.let { mainHandler.removeCallbacks(it) }

        updateState(PandaState.IDLE)
    }

    fun triggerErrorState() {
        Logger.ui("PandaStateManager: Error state triggered")
        hasRecentError = true

        errorClearRunnable?.let { mainHandler.removeCallbacks(it) }
        errorClearRunnable = Runnable {
            hasRecentError = false
            Logger.ui("PandaStateManager: Error flag cleared")
            updateStateFromServices()
        }
        mainHandler.postDelayed(errorClearRunnable!!, 5000)

        updateStateFromServices()
    }

    private fun scheduleStateUpdate() {
        if (!isMonitoring) return

        monitoringRunnable = Runnable {
            updateStateFromServices()
            scheduleStateUpdate()
        }

        mainHandler.postDelayed(monitoringRunnable!!, 200)
    }

    private fun updateStateFromServices() {
        if (!isMonitoring) return

        val newState = determineCurrentState()

        if (newState != currentState) {
            Logger.ui("PandaStateManager: State changed from $currentState to $newState")
            updateState(newState)
        }
    }

    /**
     * Determine the current state based on service conditions
     */
    private fun determineCurrentState(): PandaState {
        // --- THIS IS THE FIX ---
        // If the state is AWAITING_INPUT, it should persist until explicitly changed.
        // The monitoring loop should not override it.
        if (currentState == PandaState.AWAITING_INPUT) {
            Logger.ui("determineCurrentState: Current state is AWAITING_INPUT, preserving it.")
            return PandaState.AWAITING_INPUT
        }

        val determinedState = when {
            hasRecentError -> PandaState.ERROR.also { Logger.ui("determineCurrentState: hasRecentError is true -> ERROR") }
            !ConversationalAgentService.isRunning -> PandaState.IDLE.also { Logger.ui("determineCurrentState: Service not running -> IDLE") }
            speechCoordinator.isCurrentlySpeaking() -> PandaState.SPEAKING.also { Logger.ui("determineCurrentState: isCurrentlySpeaking is true -> SPEAKING") }
            speechCoordinator.isCurrentlyListening() -> PandaState.LISTENING.also { Logger.ui("determineCurrentState: isCurrentlyListening is true -> LISTENING") }
            isThinkingIndicatorVisible() -> PandaState.PROCESSING.also { Logger.ui("determineCurrentState: isThinkingIndicatorVisible is true -> PROCESSING") }
            else -> PandaState.IDLE.also { Logger.ui("determineCurrentState: No active conditions -> IDLE") }
        }
        return determinedState
    }

    private fun isThinkingIndicatorVisible(): Boolean {
        return try {
            val field = VisualFeedbackManager::class.java.getDeclaredField("thinkingIndicatorView")
            field.isAccessible = true
            val thinkingIndicatorView = field.get(visualFeedbackManager)
            thinkingIndicatorView != null
        } catch (e: Exception) {
            false
        }
    }

    private fun updateState(newState: PandaState) {
        val previousState = currentState
        if (previousState == newState) return

        currentState = newState

        if (_stateFlow.value.state != newState) {
            _stateFlow.value = _stateFlow.value.copy(state = newState)
        }

        Logger.ui("PandaStateManager: updateState confirmed: $previousState -> $newState")

        mainHandler.post {
            stateChangeListeners.forEach { listener ->
                try {
                    listener(newState)
                } catch (e: Exception) {
                    Log.e("PandaStateManager", "Error notifying state change listener", e)
                }
            }
        }
    }

    fun getStatusText(): String {
        return DeltaStateColorMapper.getStatusText(currentState)
    }

    fun getStateColor(): Int {
        return DeltaStateColorMapper.getColor(context, currentState)
    }

    fun getDeltaVisualState(): DeltaStateColorMapper.DeltaVisualState {
        return DeltaStateColorMapper.getDeltaVisualState(context, currentState)
    }
}