package com.blurr.voice.v2

import com.blurr.voice.v2.actions.Action
import com.blurr.voice.v2.message_manager.MemoryState
import com.blurr.voice.v2.perception.ScreenAnalysis
import com.blurr.voice.v2.perception.XmlNode
import kotlinx.serialization.Serializable

// --- Type Aliases and Placeholders ---
// Using a typealias to link the concept of a historical screen state to our existing ScreenAnalysis class.

typealias ScreenState = ScreenAnalysis

// Placeholders for classes that we will define in other modules later.
@Serializable
data class FileSystemState(val files: Map<String, String>) // Simplified placeholder
@Serializable
data class UsageSummary(val totalTokens: Int) // Simplified placeholder

/**
 * Defines the method for tool/function calling.
 */
enum class ToolCallingMethod {
    FUNCTION_CALLING,
    JSON_MODE,
    RAW,
    AUTO,
    TOOLS
}

/**
 * Defines the level of detail for vision models.
 */
enum class VisionDetailLevel {
    AUTO, LOW, HIGH
}

/**
 * Complete configuration options for the Agent.
 */
@Serializable
data class AgentSettings(
    // General
    val saveConversationPath: String? = null,
    val saveConversationPathEncoding: String = "utf-8",
    val maxFailures: Int = 3,
    val retryDelay: Int = 10,
    val validateOutput: Boolean = false,
    val calculateCost: Boolean = false,

    // Timeouts
    val llmTimeout: Int = 60, // in seconds
    val stepTimeout: Int = 180, // in seconds

    // Prompt & Message Configuration
    val overrideSystemMessage: String? = null,
    val extendSystemMessage: String? = null,
    val maxHistoryItems: Int? = null,

    // Action & Behavior Configuration
    val maxActionsPerStep: Int = 10,
    val useThinking: Boolean = true,
    val flashMode: Boolean = false,
    val toolCallingMethod: ToolCallingMethod? = ToolCallingMethod.AUTO,
    val includeToolCallExamples: Boolean = false,

    // Extraction LLM
    val pageExtractionLlm: String? = null // Using String as a placeholder
)

/**
 * Holds all state information for an Agent session.
 */
@Serializable
data class AgentState(
    val agentId: String = java.util.UUID.randomUUID().toString(),
    var nSteps: Int = 1,
    var consecutiveFailures: Int = 0,
    var lastResult: List<ActionResult>? = null,
    var lastPlan: String? = null, //todo check if needed else remove this
    var lastModelOutput: AgentOutput? = null,
    var paused: Boolean = false,
    var stopped: Boolean = false,
    val memoryManagerState: MemoryState = MemoryState(),
    val fileSystemState: FileSystemState? = null
)

/**
 * Information about the current step.
 */
@Serializable
data class AgentStepInfo(
    val stepNumber: Int,
    val maxSteps: Int
) {
    fun isLastStep(): Boolean = stepNumber >= maxSteps - 1
}

/**
 * The result of executing a single action.
 */
@Serializable
data class ActionResult(
    val isDone: Boolean? = false,
    val success: Boolean? = null,
    val error: String? = null,
    val attachments: List<String>? = null,
    val longTermMemory: String? = null,
    val extractedContent: String? = null,
    val includeExtractedContentOnlyOnce: Boolean = false
) {
    init {
        if (success == true && isDone != true) {
            throw IllegalArgumentException(
                "success=true can only be set when isDone=true. For regular actions that succeed, leave success as null."
            )
        }
    }
}

/**
 * The "thought process" of the agent for a single step.
 * In Kotlin, we use nullable fields to handle modes like "flash_mode" where some fields are omitted.
 */
@Serializable
data class AgentBrain(
    val thinking: String?,
    val evaluationPreviousGoal: String?,
    val memory: String?,
    val nextGoal: String?
)

/**
 * The complete, structured output from the LLM for a single step.
 * Nullable fields are used to accommodate different modes (e.g., flash_mode, no_thinking).
 */
@Serializable
data class AgentOutput(
    val thinking: String? = null,
    val evaluationPreviousGoal: String? = null,
    val memory: String? = null,
    val nextGoal: String? = null,
    val action: List<Action>
) {
    val currentState: AgentBrain
        get() = AgentBrain(
            thinking = this.thinking,
            evaluationPreviousGoal = this.evaluationPreviousGoal,
            memory = this.memory,
            nextGoal = this.nextGoal
        )
}

/**
 * Metadata for a single step including timing and token information.
 */
@Serializable
data class StepMetadata(
    val stepStartTime: Double,
    val stepEndTime: Double,
    val stepNumber: Int,
    val inputTokens: Int
) {
    val durationSeconds: Double
        get() = stepEndTime - stepStartTime
}

/**
 * A complete record of a single step in the agent's execution history.
 */
data class AgentHistory(
    val modelOutput: AgentOutput?,
    val result: List<ActionResult>,
    val state: ScreenState,
    val metadata: StepMetadata? = null
) {
    companion object {
        /**
         * Finds the UI elements that were interacted with in this history step
         * by mapping action element IDs to the element map in the screen state.
         */
//        fun getInteractedElements(modelOutput: AgentOutput?, screenState: ScreenState): List<XmlNode?> {
//            if (modelOutput == null) return emptyList()
//
//            return modelOutput.action.map { action ->
//                // Determine the element ID based on the type of action
//                val elementId = when (action) {
//                    is Action.TapElement -> action.elementId
//                    is Action.InputText -> action.index
//                    // Add other element-targeting actions here if necessary
//                    else -> null
//                }
//                // Look up the ID in the screen state's element map
//                elementId?.let { screenState.elementMap[it] }
//            }
//        }
    }
}


/**
 * A list of all agent history steps for a session.
 * The generic type T represents a custom, structured output model for the `done` action.
 */
data class AgentHistoryList<T>(
    val history: MutableList<AgentHistory> = mutableListOf(),
    val usage: UsageSummary? = null // Using placeholder
) {
    /**
     * Calculates the total duration of all steps in seconds.
     */
    val totalDurationSeconds: Double
        get() = history.sumOf { it.metadata?.durationSeconds ?: 0.0 }

    /**
     * Calculates the total approximate input tokens used across all steps.
     */
    val totalInputTokens: Int
        get() = history.sumOf { it.metadata?.inputTokens ?: 0 }

    /**
     * Adds a new history item to the list.
     */
    fun addItem(item: AgentHistory) {
        history.add(item)
    }
}