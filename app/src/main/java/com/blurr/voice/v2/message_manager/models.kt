/**
 * @file models.kt
 * @brief Defines the data models for the MemoryManager, structuring the agent's memory and history.
 *
 * This file contains the serializable data classes that represent the agent's memory state,
 * including individual history items and the overall message history structure.
 */
package com.blurr.voice.v2.message_manager

import com.blurr.voice.v2.llm.GeminiMessage
import kotlinx.serialization.Serializable

/**
 * Represents a single, summarized item in the agent's high-level history.
 * This is used to build the `<agent_history>` section of the prompt, providing a condensed
 * view of past steps to the LLM.
 *
 * @property stepNumber The number of the step this item represents.
 * @property evaluation The LLM's evaluation of the previous step.
 * @property memory The LLM's summary of relevant memories.
 * @property nextGoal The LLM's stated goal for this step.
 * @property actionResults A summary of the results from the executed actions.
 * @property error An error message if the step failed.
 * @property systemMessage A special message for system-level events, like a new task being added.
 */
@Serializable
data class HistoryItem(
    val stepNumber: Int? = null,
    val evaluation: String? = null,
    val memory: String? = null,
    val nextGoal: String? = null,
    val actionResults: String? = null,
    val error: String? = null,
    val systemMessage: String? = null
) {
    /**
     * Formats this history item into a structured string for inclusion in the LLM prompt.
     * @return A string formatted with XML-like tags (e.g., `<step_1>...</step_1>`).
     */
    fun toPromptString(): String {
        val stepStr = stepNumber?.let { "step_$it" } ?: "step_unknown"
        val content = when {
            error != null -> error
            systemMessage != null -> systemMessage
            else -> listOfNotNull(
                evaluation?.let { "Evaluation of Previous Step: $it" },
                memory?.let { "Memory: $it" },
                nextGoal?.let { "Next Goal: $it" },
                actionResults
            ).joinToString("\n")
        }
        return "<$stepStr>\n$content\n</$stepStr>"
    }
}

/**
 * Holds the current, structured message history to be sent to the LLM.
 *
 * It separates the static system prompt from the dynamic state message and allows for
 * temporary context messages to be added, for instance, to provide error feedback.
 *
 * @property systemMessage The static system prompt that initializes the agent's persona and instructions.
 * @property stateMessage The dynamic user message, updated each step with the latest agent and device state.
 * @property contextMessages A list of temporary messages to be included in the next prompt only.
 */
@Serializable
data class MessageHistory(
    var systemMessage: GeminiMessage?,
    var stateMessage: GeminiMessage?,
    val contextMessages: MutableList<GeminiMessage> = mutableListOf()
) {
    /**
     * Assembles all messages in the correct order (system, state, context) for the LLM API call.
     * @return A list of [GeminiMessage]s ready to be sent.
     */
    fun getMessages(): List<GeminiMessage> {
        return listOfNotNull(systemMessage, stateMessage) + contextMessages
    }
}

/**
 * Represents the complete, self-contained, and serializable state of the [MemoryManager].
 * This class can be saved and loaded to pause and resume an agent's session.
 *
 * @property history The [MessageHistory] object containing the messages for the LLM.
 * @property toolId A counter for tool call IDs (currently unused).
 * @property agentHistoryItems The list of high-level [HistoryItem]s used to build the prompt's history section.
 * @property readStateDescription The content of a file that was just read, to be included in the next prompt only.
 */
@Serializable
data class MemoryState(
    val history: MessageHistory = MessageHistory(null, null),
    val toolId: Int = 1,
    val agentHistoryItems: MutableList<HistoryItem> = mutableListOf(
        HistoryItem(stepNumber = 0, systemMessage = "Agent initialized")
    ),
    var readStateDescription: String = ""
)