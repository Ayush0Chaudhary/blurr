/**
 * @file PromptBuilder.kt
 * @brief Constructs system and user prompts for the LLM.
 *
 * This file contains the logic for dynamically building the structured prompts that are sent
 * to the Large Language Model. It separates the construction of the static system prompt from
 * the dynamic user prompt, which changes with every step of the agent's execution.
 */
package com.blurr.voice.v2

import android.content.Context
import android.util.Log
import com.blurr.voice.intents.IntentRegistry
import com.blurr.voice.v2.actions.Action
import com.blurr.voice.v2.fs.FileSystem
import com.blurr.voice.v2.llm.GeminiMessage
import com.blurr.voice.v2.llm.MessageRole
import com.blurr.voice.v2.llm.TextPart
import java.io.IOException

private const val DEFAULT_PROMPT_TEMPLATE = "prompts/system_prompt.md"

/**
 * Loads and prepares the system prompt from a template file.
 *
 * This class is responsible for loading the base system prompt from the app's assets and
 * injecting dynamic information, such as the catalog of available actions and intents,
 * to create the final system message for the LLM.
 *
 * @param context The Android application context, needed to access the AssetManager.
 */
class SystemPromptLoader(private val context: Context) {

    /**
     * Constructs the final system message by populating the template with dynamic content.
     *
     * @param settings The agent's configuration, which may contain prompt overrides.
     * @return A [GeminiMessage] containing the fully formatted system prompt.
     */
    fun getSystemMessage(settings: AgentSettings): GeminiMessage {
        val actionsDescription = generateActionsDescription()
        val intentsCatalog = generateIntentsCatalog()

        var prompt = settings.overrideSystemMessage ?: loadDefaultTemplate()
            .replace("{max_actions}", settings.maxActionsPerStep.toString())
            .replace("{available_actions}", actionsDescription)

        // Append the intents catalog and a usage hint for the launch_intent action.
        if (intentsCatalog.isNotBlank()) {
            prompt += "\n\n<intents_catalog>\n$intentsCatalog\n</intents_catalog>\n\n" +
                "Usage: To launch any of the above intents, add an action like {\"launch_intent\": {\"intent_name\": \"Dial\", \"parameters\": {\"phone_number\": \"+123456789\"}}}."
        }

        if (!settings.extendSystemMessage.isNullOrBlank()) {
            prompt += "\n${settings.extendSystemMessage}"
        }
        Log.d("SYSTEM_PROMPT_BUILDER", prompt)
        return GeminiMessage(role = MessageRole.MODEL, parts = listOf(TextPart(prompt)))
    }

    /**
     * Generates a structured, LLM-friendly description of all available actions.
     * This description is built from the specifications defined in [Action].
     * @return A string containing the formatted actions catalog.
     */
    private fun generateActionsDescription(): String {
        val allActionSpecs = Action.getAllSpecs()
        return buildString {
            allActionSpecs.forEach { spec ->
                append("<action>\n")
                append("  <name>${spec.name}</name>\n")
                append("  <description>${spec.description}</description>\n")
                if (spec.params.isNotEmpty()) {
                    append("  <parameters>\n")
                    spec.params.forEach { param ->
                        append("    <param>\n")
                        append("      <name>${param.name}</name>\n")
                        append("      <type>${param.type.simpleName}</type>\n")
                        append("      <description>${param.description}</description>\n")
                        append("    </param>\n")
                    }
                    append("  </parameters>\n")
                }
                append("</action>\n\n")
            }
        }.trim()
    }

    /**
     * Generates a structured, LLM-friendly description of all registered [AppIntent]s.
     * @return A string containing the formatted intents catalog.
     */
    private fun generateIntentsCatalog(): String {
        val intents = IntentRegistry.listIntents(context)
        if (intents.isEmpty()) return ""
        return buildString {
            intents.forEach { intent ->
                append("<intent>\n")
                append("  <name>${intent.name}</name>\n")
                append("  <description>${intent.description()}</description>\n")
                val params = intent.parametersSpec()
                if (params.isNotEmpty()) {
                    append("  <parameters>\n")
                    params.forEach { p ->
                        append("    <param>\n")
                        append("      <name>${p.name}</name>\n")
                        append("      <type>${p.type}</type>\n")
                        append("      <required>${p.required}</required>\n")
                        append("      <description>${p.description}</description>\n")
                        append("    </param>\n")
                    }
                    append("  </parameters>\n")
                }
                append("</intent>\n\n")
            }
        }.trim()
    }

    /**
     * Loads the default system prompt template from the assets folder.
     * @return The raw content of the prompt template file.
     * @throws RuntimeException if the template file cannot be loaded.
     */
    private fun loadDefaultTemplate(): String {
        return try {
            context.assets.open(DEFAULT_PROMPT_TEMPLATE).bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            throw RuntimeException("Failed to load default system prompt template: $DEFAULT_PROMPT_TEMPLATE", e)
        }
    }
}

/**
 * A builder responsible for constructing the detailed user message for each step of the agent's loop.
 * It aggregates all current state information into a single, structured prompt for the LLM.
 */
object UserMessageBuilder {

    /**
     * A data class holding all the dynamic information required to build the user message for a single step.
     *
     * @property task The original high-level task from the user.
     * @property screenState The current state of the device screen.
     * @property fileSystem A reference to the agent's file system.
     * @property agentHistoryDescription A summary of past actions and results.
     * @property readStateDescription The content of any file the agent has recently read.
     * @property stepInfo Information about the current step number and limits.
     * @property sensitiveDataDescription Description of any sensitive data visible.
     * @property availableFilePaths A list of all file paths available in the agent's file system.
     * @property maxUiRepresentationLength The maximum character length for the UI representation to avoid overly long prompts.
     */
    data class Args(
        val task: String,
        val screenState: ScreenState,
        val fileSystem: FileSystem,
        val agentHistoryDescription: String?,
        val readStateDescription: String?,
        val stepInfo: AgentStepInfo?,
        val sensitiveDataDescription: String?,
        val availableFilePaths: List<String>?,
        val maxUiRepresentationLength: Int = 40000
    )

    /**
     * Builds the user message by assembling various blocks of state information.
     *
     * @param args An [Args] object containing all necessary data for construction.
     * @return A [GeminiMessage] ready to be sent to the LLM.
     */
    fun build(args: Args): GeminiMessage {
        val messageContent = buildString {
            append("<agent_history>\n")
            append(args.agentHistoryDescription?.trim() ?: "No history yet.")
            append("\n</agent_history>\n\n")

            append("<agent_state>\n")
            append(buildAgentStateBlock(args))
            append("\n</agent_state>\n\n")

            append("<android_state>\n")
            append(buildAndroidStateBlock(args.screenState, args.maxUiRepresentationLength))
            append("\n</android_state>\n\n")

            if (!args.readStateDescription.isNullOrBlank()) {
                append("<read_state>\n")
                append(args.readStateDescription.trim())
                append("\n</read_state>\n\n")
            }
        }

        return GeminiMessage(text = messageContent.trim())
    }

    /**
     * Constructs the XML block describing the current state of the Android UI.
     * This includes the current activity and a potentially truncated representation of the UI hierarchy.
     */
    private fun buildAndroidStateBlock(screenState: ScreenState, maxUiRepresentationLength: Int): String {
        val originalUiString = screenState.uiRepresentation
        val truncationMessage: String
        val finalUiString: String

        if (originalUiString.length > maxUiRepresentationLength) {
            finalUiString = originalUiString.substring(0, maxUiRepresentationLength)
            truncationMessage = " (truncated to $maxUiRepresentationLength characters)"
        } else {
            finalUiString = originalUiString
            truncationMessage = ""
        }

        return buildString {
            appendLine("Current Activity: ${screenState.activityName}")
            appendLine("Visible elements on the current screen:$truncationMessage")
            append(finalUiString)
        }.trim()
    }

    /**
     * Constructs the XML block describing the agent's internal state.
     * This includes the user's request, file system contents, todo list, and step information.
     */
    private fun buildAgentStateBlock(args: Args): String {
        val todoContents = args.fileSystem.getTodoContents().let {
            it.ifBlank { "[Current todo.md is empty, fill it with your plan when applicable]" }
        }

        val stepInfoDescription = args.stepInfo?.let {
            val timeStr = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
            "Step ${it.stepNumber + 1} of ${it.maxSteps} max possible steps\nCurrent date and time: $timeStr"
        } ?: "Step information not available."

        return buildString {
            appendLine("<user_request>")
            appendLine(args.task)
            appendLine("</user_request>")

            appendLine("<file_system>")
            appendLine(args.fileSystem.describe())
            appendLine("</file_system>")

            appendLine("<todo_contents>")
            appendLine(todoContents)
            appendLine("</todo_contents>")

            if (!args.sensitiveDataDescription.isNullOrBlank()) {
                appendLine("<sensitive_data>")
                appendLine(args.sensitiveDataDescription)
                appendLine("</sensitive_data>")
            }

            appendLine("<step_info>")
            appendLine(stepInfoDescription)
            appendLine("</step_info>")

            if (!args.availableFilePaths.isNullOrEmpty()) {
                appendLine("<available_file_paths>")
                appendLine(args.availableFilePaths.joinToString("\n"))
                appendLine("</available_file_paths>")
            }
        }.trim()
    }
}