/**
 * @file MessageManager.kt
 * @brief Manages the agent's short-term memory and constructs prompts for the LLM.
 *
 * This file contains the `MemoryManager` class, which is the central hub for managing the
 * conversational state that gets sent to the LLM. It maintains the history of the conversation,
 * constructs the user prompt for each step, and handles sensitive data filtering.
 */
package com.blurr.voice.v2.message_manager

import android.content.Context
import com.blurr.voice.v2.ActionResult
import com.blurr.voice.v2.AgentOutput
import com.blurr.voice.v2.AgentSettings
import com.blurr.voice.v2.AgentStepInfo
import com.blurr.voice.v2.ScreenState
import com.blurr.voice.v2.SystemPromptLoader
import com.blurr.voice.v2.UserMessageBuilder
import com.blurr.voice.v2.fs.FileSystem
import com.blurr.voice.v2.llm.GeminiMessage
import com.blurr.voice.v2.llm.TextPart

/**
 * Manages the agent's short-term memory, including conversation history and prompt construction.
 *
 * This class orchestrates the creation of prompts sent to the LLM. It maintains a structured
 * history of the agent's actions and observations, uses builders to construct the system and user
 * messages, and applies necessary data filtering.
 *
 * @param context The Android application context.
 * @param task The initial user request or task for the agent.
 * @param fileSystem An instance of the agent's file system for accessing file content.
 * @param settings The agent's configuration settings, used for prompt customization.
 * @param sensitiveData A map of placeholder keys to sensitive string values to be filtered from prompts.
 * @param initialState An optional initial state to resume from a previous session.
 */
class MemoryManager(
    context: Context,
    private var task: String,
    private val fileSystem: FileSystem,
    private val settings: AgentSettings,
    private val sensitiveData: Map<String, String>? = null,
    initialState: MemoryState = MemoryState()
) {
    /** The current, mutable state of the memory manager. */
    val state: MemoryState = initialState

    init {
        // On initialization, create and set the system message if it doesn't already exist.
        if (state.history.systemMessage == null) {
            val systemPromptLoader = SystemPromptLoader(context)
            val systemMessage = systemPromptLoader.getSystemMessage(settings)
            state.history.systemMessage = filterSensitiveData(systemMessage)
        }
    }

    /**
     * The primary method to update memory and generate the next prompt for the user role.
     *
     * This should be called once per agent step. It takes the results of the previous step,
     * updates the internal history, and constructs the new user message containing the
     * current agent and device state.
     *
     * @param modelOutput The [AgentOutput] from the LLM in the previous step.
     * @param result The list of [ActionResult] from executing the last action.
     * @param stepInfo Information about the current step number.
     * @param screenState The current state of the device screen.
     */
    fun createStateMessage(
        modelOutput: AgentOutput?,
        result: List<ActionResult>?,
        stepInfo: AgentStepInfo?,
        screenState: ScreenState
    ) {
        // 1. Update the structured history with the outcome of the last step.
        updateHistory(modelOutput, result, stepInfo)

        // 2. Build the arguments for the prompt builder.
        val builderArgs = UserMessageBuilder.Args(
            task = this.task,
            screenState = screenState,
            fileSystem = this.fileSystem,
            agentHistoryDescription = getAgentHistoryDescription(),
            readStateDescription = state.readStateDescription,
            stepInfo = stepInfo,
            sensitiveDataDescription = getSensitiveDataDescription(),
            availableFilePaths = null // TODO: Populate this from fileSystem.
        )

        // 3. Construct the new user message using the builder.
        var stateMessage = UserMessageBuilder.build(builderArgs)
        stateMessage = filterSensitiveData(stateMessage)

        // 4. Update the history with the new state message, clearing any old context messages.
        state.history.stateMessage = stateMessage
        state.history.contextMessages.clear()
    }

    /**
     * Adds a new task, replacing the old one, and records this change as an event in the history.
     * @param newTask The new task description for the agent.
     */
    fun addNewTask(newTask: String) {
        this.task = newTask

        val taskUpdateItem = HistoryItem(
            stepNumber = 0, // Task updates are considered step 0.
            systemMessage = "<user_request> added: $newTask"
        )
        state.agentHistoryItems.add(taskUpdateItem)
    }

    /**
     * Adds a temporary, contextual message to the history.
     * This is often used to provide corrective feedback to the LLM if a previous output was invalid.
     * @param message The [GeminiMessage] to add to the context.
     */
    fun addContextMessage(message: GeminiMessage){
        // TODO: Implement sensitive data filtering for context messages.
        state.history.contextMessages.add(message)
    }

    /**
     * Assembles and returns the complete list of messages ready to be sent to the LLM.
     * This includes the system message, conversation history, and the latest state message.
     * @return A list of [GeminiMessage]s.
     */
    fun getMessages(): List<GeminiMessage> {
        return state.history.getMessages()
    }

    /**
     * Processes the results of the last agent step and adds a new [HistoryItem] to the state.
     * This method is responsible for summarizing the LLM's thoughts and the action's results
     * into a concise format for the long-term history.
     *
     * @param modelOutput The output from the model in the last step.
     * @param result The list of results from the executed actions.
     * @param stepInfo Information about the step number.
     */
    private fun updateHistory(
        modelOutput: AgentOutput?,
        result: List<ActionResult>?,
        stepInfo: AgentStepInfo?
    ) {
        // Clear the one-time read state from the previous turn.
        state.readStateDescription = ""

        val actionResultsText = result?.mapIndexedNotNull { index, actionResult ->
            // Populate the one-time read state if the action result specifies it.
            if (actionResult.includeExtractedContentOnlyOnce && !actionResult.extractedContent.isNullOrBlank()) {
                state.readStateDescription += actionResult.extractedContent + "\n"
            }

            // Format the action result into a string for the long-term history.
            when {
                !actionResult.longTermMemory.isNullOrBlank() -> "Action ${index + 1}: ${actionResult.longTermMemory}"
                !actionResult.extractedContent.isNullOrBlank() && !actionResult.includeExtractedContentOnlyOnce -> "Action ${index + 1}: ${actionResult.extractedContent}"
                !actionResult.error.isNullOrBlank() -> "Action ${index + 1}: ERROR - ${actionResult.error.take(200)}"
                else -> null
            }
        }?.joinToString("\n")

        val historyItem = if (modelOutput == null) {
            if(stepInfo?.stepNumber != 1){
                HistoryItem(stepNumber = stepInfo?.stepNumber, error = "Agent failed to produce a valid output.")
            } else {
                HistoryItem(stepNumber = stepInfo.stepNumber, error = "Agent not asked to create output yet")
            }
        } else {
            HistoryItem(
                stepNumber = stepInfo?.stepNumber,
                evaluation = modelOutput.evaluationPreviousGoal,
                memory = modelOutput.memory,
                nextGoal = modelOutput.nextGoal,
                actionResults = actionResultsText?.let { "Action Results:\n$it" }
            )
        }
        state.agentHistoryItems.add(historyItem)
    }

    /**
     * Generates the `<agent_history>` string for the prompt.
     * If the history exceeds `maxHistoryItems`, it truncates the middle part to save space,
     * always keeping the first and last items.
     * @return A formatted string representing the agent's history.
     */
    private fun getAgentHistoryDescription(): String {
        val items = state.agentHistoryItems
        val maxItems = settings.maxHistoryItems ?: items.size

        if (items.size <= maxItems) {
            return items.joinToString("\n") { it.toPromptString() }
        }

        val omittedCount = items.size - maxItems
        val recentItemsCount = maxItems - 1

        val result = mutableListOf<String>()
        result.add(items.first().toPromptString()) // Always include the first item.
        result.add("<sys>[... $omittedCount previous steps omitted...]</sys>")
        result.addAll(items.takeLast(recentItemsCount).map { it.toPromptString() })

        return result.joinToString("\n")
    }

    /**
     * Creates a description of available sensitive data placeholders for the LLM.
     * @return A formatted string explaining how to use sensitive data placeholders, or null if none exist.
     */
    private fun getSensitiveDataDescription(): String? {
        val placeholders = sensitiveData?.keys
        if (placeholders.isNullOrEmpty()) return null

        return "Here are placeholders for sensitive data:\n${placeholders.joinToString()}\nTo use them, write <secret>the placeholder name</secret>"
    }

    /**
     * Scrubs sensitive data from a message before it's added to the history,
     * replacing actual values with placeholders.
     * @param message The original [GeminiMessage].
     * @return A new [GeminiMessage] with sensitive data filtered out.
     */
    private fun filterSensitiveData(message: GeminiMessage): GeminiMessage {
        if (sensitiveData.isNullOrEmpty()) return message

        val newParts = message.parts.map { part ->
            if (part is TextPart) {
                var newText = part.text
                sensitiveData.forEach { (key, value) ->
                    newText = newText.replace(value, "<secret>$key</secret>")
                }
                TextPart(newText)
            } else {
                part
            }
        }
        return message.copy(parts = newParts)
    }
}