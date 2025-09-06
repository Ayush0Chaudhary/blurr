package com.blurr.voice.intents

import android.content.Context
import android.content.Intent

/**
 * Contract for pluggable Android Intents the agent can invoke.
 * Implementations must have a public no-arg constructor to allow reflective discovery.
 */
interface AppIntent {
    /**
     * Unique, human-readable name used by the LLM to refer to this intent.
     * Example: "Dial".
     */
    val name: String

    /** A short description to show in prompts. */
    fun description(): String

    /**
     * Returns the parameters this intent accepts in a stable order, for prompting.
     */
    fun parametersSpec(): List<ParameterSpec>

    /**
     * Builds the actual Android Intent to launch, based on provided params.
     * Should return null if required parameters are missing/invalid.
     */
    fun buildIntent(context: Context, params: Map<String, Any?>): Intent?
}

/** Parameter specification for prompting and validation */
data class ParameterSpec(
    val name: String,
    val type: String,
    val required: Boolean,
    val description: String
)

