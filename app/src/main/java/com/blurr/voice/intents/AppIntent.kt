/**
 * @file AppIntent.kt
 * @brief Defines the contract for creating pluggable, agent-invokable Android Intents.
 *
 * This file contains the `AppIntent` interface and the `ParameterSpec` data class, which together
 * provide a standardized way to define, describe, and build Android Intents that can be
 * discovered and used by an AI agent.
 */
package com.blurr.voice.intents

import android.content.Context
import android.content.Intent

/**
 * Defines a contract for a pluggable Android Intent that the agent can discover and invoke.
 *
 * Implementations of this interface represent a specific, self-contained action that can be
 * performed on the Android system, such as dialing a number or composing an email. The agent
 * can use the metadata provided by this interface to understand what the intent does and what
 * parameters it requires.
 *
 * Implementations must have a public, no-argument constructor to allow for reflective discovery
 * and instantiation by the `IntentRegistry`.
 */
interface AppIntent {
    /**
     * A unique, human-readable name used by the LLM to identify and refer to this intent.
     * This should be a simple, single word (e.g., "Dial", "Share").
     */
    val name: String

    /**
     * A short, user-facing description of what this intent does.
     * This is used in prompts to help the user or LLM understand the intent's purpose.
     * @return A string describing the intent's action.
     */
    fun description(): String

    /**
     * Returns a list of parameter specifications that this intent accepts.
     *
     * The list should be in a stable, consistent order, as it is used to construct prompts
     * and validate parameters.
     *
     * @return A list of [ParameterSpec] objects detailing the required parameters.
     */
    fun parametersSpec(): List<ParameterSpec>

    /**
     * Builds the actual Android [Intent] to be launched.
     *
     * This method takes the parameters extracted by the LLM and constructs the appropriate
     * Android Intent. It should perform validation and return null if required parameters
     * are missing or invalid.
     *
     * @param context The application context.
     * @param params A map of parameter names to their provided values.
     * @return A fully configured [Intent] ready to be launched, or null if the intent
     *         cannot be built with the given parameters.
     */
    fun buildIntent(context: Context, params: Map<String, Any?>): Intent?
}

/**
 * A data class that specifies the details of a parameter for an [AppIntent].
 *
 * This structure provides all the necessary information for an agent to prompt for,
 * understand, and validate a parameter.
 *
 * @property name The name of the parameter (e.g., "phoneNumber").
 * @property type The expected data type of the parameter (e.g., "string", "number").
 * @property required A boolean indicating whether this parameter is mandatory for the intent to function.
 * @property description A human-readable description of what the parameter is for.
 */
data class ParameterSpec(
    val name: String,
    val type: String,
    val required: Boolean,
    val description: String
)

