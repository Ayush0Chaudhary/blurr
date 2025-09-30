/**
 * @file AgentModel.kt
 * @brief Defines all core data models, states, and configurations for the V2 Agent.
 *
 * This file serves as the single source of truth for the data structures used throughout the
 * agent's lifecycle. It includes configurations (`AgentSettings`), runtime state (`AgentState`),
 * action results (`ActionResult`), LLM outputs (`AgentOutput`), and historical records (`AgentHistory`).
 * Using `@Serializable` allows these models to be easily logged or transferred.
 */
package com.blurr.voice.v2

import com.blurr.voice.v2.actions.Action
import com.blurr.voice.v2.message_manager.MemoryState
import com.blurr.voice.v2.perception.ScreenAnalysis
import kotlinx.serialization.Serializable

/** A typealias to represent the state of the screen at a specific moment in time. */
typealias ScreenState = ScreenAnalysis
/**
 * A placeholder representing the state of the agent's virtual file system.
 * @property files A map of filenames to their content.
 */
@Serializable
data class FileSystemState(val files: Map<String, String>)
/**
 * A placeholder for summarizing token usage.
 * @property totalTokens The total number of tokens consumed.
 */
@Serializable
data class UsageSummary(val totalTokens: Int)

/**
 * Defines the method the LLM should use for tool or function calling.
 */
enum class ToolCallingMethod {
    /** Let the model decide which function to call, if any. */
    FUNCTION_CALLING,
    /** Constrains the model to output valid JSON. */
    JSON_MODE,
    /** Model generates raw text without function calling. */
    RAW,
    /** Automatically select the best method. */
    AUTO,
    /** Use a specific set of tools. */
    TOOLS
}

/**
 * Defines the level of detail for vision models when processing images.
 */
enum class VisionDetailLevel {
    /** The model automatically decides the detail level. */
    AUTO,
    /** Low detail, suitable for quick analysis. */
    LOW,
    /** High detail, for intricate visual tasks. */
    HIGH
}

/**
 * Contains all static configuration options for an Agent instance.
 * These settings are typically defined once at the beginning of a session.
 *
 * @property saveConversationPath If set, the agent's history will be saved to this file path.
 * @property saveConversationPathEncoding The encoding to use when saving the conversation.
 * @property maxFailures The maximum number of consecutive LLM failures before the agent stops.
 * @property retryDelay The delay in seconds before retrying after a failure.
 * @property validateOutput Whether to validate the LLM's output against a schema.
 * @property calculateCost Whether to calculate and track the token cost of the session.
 * @property llmTimeout The timeout in seconds for a single LLM API call.
 * @property stepTimeout The timeout in seconds for a single agent step.
 * @property overrideSystemMessage A complete replacement for the default system message.
 * @property extendSystemMessage Text to append to the default system message.
 * @property maxHistoryItems The maximum number of `AgentHistory` items to keep in memory.
 * @property maxActionsPerStep The maximum number of actions the LLM can propose in a single step.
 * @property useThinking Whether to instruct the LLM to provide a 'thinking' process in its output.
 * @property flashMode A mode for faster execution, potentially by simplifying prompts or actions.
 * @property toolCallingMethod The specific method to use for tool calling.
 * @property includeToolCallExamples Whether to include examples of tool calls in the prompt.
 * @property pageExtractionLlm The specific LLM to use for page content extraction.
 */
@Serializable
data class AgentSettings(
    val saveConversationPath: String? = null,
    val saveConversationPathEncoding: String = "utf-8",
    val maxFailures: Int = 3,
    val retryDelay: Int = 10,
    val validateOutput: Boolean = false,
    val calculateCost: Boolean = false,
    val llmTimeout: Int = 60,
    val stepTimeout: Int = 180,
    val overrideSystemMessage: String? = null,
    val extendSystemMessage: String? = null,
    val maxHistoryItems: Int? = null,
    val maxActionsPerStep: Int = 10,
    val useThinking: Boolean = true,
    val flashMode: Boolean = false,
    val toolCallingMethod: ToolCallingMethod? = ToolCallingMethod.AUTO,
    val includeToolCallExamples: Boolean = false,
    val pageExtractionLlm: String? = null
)

/**
 * Holds all the dynamic state information for an ongoing Agent session.
 * This object is mutated as the agent executes each step.
 *
 * @property agentId A unique identifier for this agent session.
 * @property nSteps The current step number, starting from 1.
 * @property consecutiveFailures The number of times the LLM has failed in a row.
 * @property lastResult The list of [ActionResult] from the most recently executed step.
 * @property lastModelOutput The [AgentOutput] from the LLM in the previous step.
 * @property paused Whether the agent's execution is currently paused.
 * @property stopped Whether the agent's execution has been terminated.
 * @property memoryManagerState The current state of the agent's memory manager.
 * @property fileSystemState The current state of the agent's file system.
 */
@Serializable
data class AgentState(
    val agentId: String = java.util.UUID.randomUUID().toString(),
    var nSteps: Int = 1,
    var consecutiveFailures: Int = 0,
    var lastResult: List<ActionResult>? = null,
    var lastPlan: String? = null, // TODO: Check if needed, else remove.
    var lastModelOutput: AgentOutput? = null,
    var paused: Boolean = false,
    var stopped: Boolean = false,
    val memoryManagerState: MemoryState = MemoryState(),
    val fileSystemState: FileSystemState? = null
)

/**
 * Provides information about the current step, primarily for inclusion in the LLM prompt.
 *
 * @property stepNumber The current step number.
 * @property maxSteps The maximum number of steps for this agent run.
 */
@Serializable
data class AgentStepInfo(
    val stepNumber: Int,
    val maxSteps: Int
) {
    /** Returns `true` if the current step is the last one before the max step limit is reached. */
    fun isLastStep(): Boolean = stepNumber >= maxSteps - 1
}

/**
 * Encapsulates the result of executing a single [Action].
 *
 * @property isDone If `true`, this action signifies the successful completion of the entire task.
 * @property success Can only be `true` if `isDone` is also `true`. Indicates overall task success.
 * @property error A description of an error if the action failed.
 * @property attachments A list of file paths or other attachments produced by the action.
 * @property longTermMemory A string to be saved to the agent's long-term memory.
 * @property extractedContent Content extracted from the screen by the action.
 * @property includeExtractedContentOnlyOnce If true, the extracted content will not be re-included in subsequent prompts.
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
 * Represents the "thought process" of the agent for a single step, as generated by the LLM.
 *
 * @property thinking The LLM's reasoning or internal monologue about the current situation.
 * @property evaluationPreviousGoal The LLM's assessment of the previous step's outcome.
 * @property memory A summary of relevant information from past steps.
 * @property nextGoal The immediate, high-level goal for the current step.
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
 * This is the primary model that the LLM is expected to return.
 *
 * @property thinking The LLM's reasoning for this step.
 * @property evaluationPreviousGoal The LLM's assessment of the last step.
 * @property memory A summary of relevant memories.
 * @property nextGoal The high-level goal for the current step.
 * @property action The list of specific [Action]s to be executed in this step.
 */
@Serializable
data class AgentOutput(
    val thinking: String? = null,
    val evaluationPreviousGoal: String? = null,
    val memory: String? = null,
    val nextGoal: String? = null,
    val action: List<Action>
) {
    /** A computed property to easily access the "brain" components of the output. */
    val currentState: AgentBrain
        get() = AgentBrain(
            thinking = this.thinking,
            evaluationPreviousGoal = this.evaluationPreviousGoal,
            memory = this.memory,
            nextGoal = this.nextGoal
        )
}

/**
 * Contains metadata for a single agent step, such as timing and token usage.
 *
 * @property stepStartTime The timestamp when the step began.
 * @property stepEndTime The timestamp when the step ended.
 * @property stepNumber The number of the step.
 * @property inputTokens The number of tokens used in the input prompt for this step.
 */
@Serializable
data class StepMetadata(
    val stepStartTime: Double,
    val stepEndTime: Double,
    val stepNumber: Int,
    val inputTokens: Int
) {
    /** The total duration of the step in seconds. */
    val durationSeconds: Double
        get() = stepEndTime - stepStartTime
}

/**
 * A complete, immutable record of a single step in the agent's execution history.
 * This captures the state, decision, and result of one SENSE->THINK->ACT cycle.
 *
 * @property modelOutput The [AgentOutput] received from the LLM for this step.
 * @property result The list of [ActionResult] after executing the proposed actions.
 * @property state The [ScreenState] that was the input for this step.
 * @property metadata Optional [StepMetadata] containing performance metrics.
 */
@Serializable
data class AgentHistory(
    val modelOutput: AgentOutput?,
    val result: List<ActionResult>,
    val state: ScreenState,
    val metadata: StepMetadata? = null
)

/**
 * A container for the entire list of agent history steps for a session.
 *
 * @param T A generic type representing a custom, structured output model for the `done` action.
 * @property history The mutable list of all [AgentHistory] steps.
 * @property usage A summary of token usage for the session.
 */
@Serializable
data class AgentHistoryList<T>(
    val history: MutableList<AgentHistory> = mutableListOf(),
    val usage: UsageSummary? = null
) {
    /**
     * Calculates the total duration of all steps in the history in seconds.
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
     * @param item The [AgentHistory] item to add.
     */
    fun addItem(item: AgentHistory) {
        history.add(item)
    }
}