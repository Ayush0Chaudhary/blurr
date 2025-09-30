/**
 * @file ShareTextIntent.kt
 * @brief Defines the AppIntent for sharing text content.
 *
 * This file contains the implementation of the `ShareTextIntent`, which allows the agent to
 * trigger the standard Android share sheet to send text to other applications.
 */
package com.blurr.voice.intents.impl

import android.content.Context
import android.content.Intent
import com.blurr.voice.intents.AppIntent
import com.blurr.voice.intents.ParameterSpec

/**
 * An [AppIntent] that opens the system share sheet to send text content.
 *
 * This intent is useful for sending text to any application that can receive it, such as
 * messaging apps, social media, or note-taking apps. It uses the standard `ACTION_SEND`.
 */
class ShareTextIntent : AppIntent {
    /**
     * The unique name of this intent, "ShareText".
     */
    override val name: String = "ShareText"

    /**
     * A user-facing description of the intent's action.
     * @return A string explaining that this intent opens the share sheet.
     */
    override fun description(): String =
        "Opens the system share sheet to send text to another app."

    /**
     * Specifies the parameters required for this intent.
     * @return A list of [ParameterSpec] objects for "text" and an optional "chooser_title".
     */
    override fun parametersSpec(): List<ParameterSpec> = listOf(
        ParameterSpec(
            name = "text",
            type = "string",
            required = true,
            description = "The text content to be shared."
        ),
        ParameterSpec(
            name = "chooser_title",
            type = "string",
            required = false,
            description = "An optional title to display on the share sheet chooser."
        )
    )

    /**
     * Builds the `ACTION_SEND` intent and wraps it in a chooser.
     *
     * It takes the "text" to be shared and an optional "chooser_title". If the text is empty,
     * it returns null.
     *
     * @param context The application context.
     * @param params A map containing the "text" and optional "chooser_title" parameters.
     * @return A chooser [Intent] for sharing text, or null if the text is missing.
     */
    override fun buildIntent(context: Context, params: Map<String, Any?>): Intent? {
        val text = params["text"]?.toString()?.trim().orEmpty()
        if (text.isEmpty()) return null

        val base = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }

        val title = params["chooser_title"]?.toString()?.takeIf { it.isNotBlank() } ?: "Share via"
        return Intent.createChooser(base, title)
    }
}
