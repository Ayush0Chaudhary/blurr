package com.blurr.voice.v2.message_manager

import com.blurr.voice.v2.llm.GeminiMessage
import kotlinx.serialization.Serializable


/**
 * Represents a single item in the agent's high-level history summary.
 * This is used to build the <agent_history> section of the prompt.
 */
@Serializable
data class HistoryItem(
    val stepNumber: Int? = null,
    val evaluation: String? = null,
    val memory: String? = null,
    val nextGoal: String? = null,
    val actionResults: String? = null,
    val error: String? = null,
    val systemMessage: String? = null // For special messages like "Task updated"
) {
    /**
     * Formats this item into a string for the LLM prompt.
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
 * It separates the static system prompt from the dynamic state message.
 */
@Serializable
data class MessageHistory(
    var systemMessage: GeminiMessage?,
    var stateMessage: GeminiMessage?,
    val contextMessages: MutableList<GeminiMessage> = mutableListOf() // For temporary, one-off messages
) {
    /**
     * Assembles all messages in the correct order for the LLM API call.
     */
    fun getMessages(): List<GeminiMessage> {
        return listOfNotNull(systemMessage, stateMessage) + contextMessages
    }
}


/**
 * The complete, self-contained state of the MemoryManager.
 * This can be saved and loaded to resume an agent's session.
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