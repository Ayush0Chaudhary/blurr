/**
 * @file ViewUrlIntent.kt
 * @brief Defines the AppIntent for opening a web URL.
 *
 * This file contains the implementation of the `ViewUrlIntent`, which allows the agent to
 * open a given URL in the user's default web browser.
 */
package com.blurr.voice.intents.impl

import android.content.Context
import android.content.Intent
import com.blurr.voice.intents.AppIntent
import com.blurr.voice.intents.ParameterSpec
import androidx.core.net.toUri

/**
 * An [AppIntent] that opens a web URL in the default browser.
 *
 * This intent uses the standard `ACTION_VIEW` action with an HTTP or HTTPS URI.
 */
class ViewUrlIntent : AppIntent {
    /**
     * The unique name of this intent, "ViewUrl".
     */
    override val name: String = "ViewUrl"

    /**
     * A user-facing description of the intent's action.
     * @return A string explaining that this intent opens a URL.
     */
    override fun description(): String =
        "Opens a web URL in the default browser."

    /**
     * Specifies the parameters required for this intent.
     * @return A list containing a single [ParameterSpec] for the "url".
     */
    override fun parametersSpec(): List<ParameterSpec> = listOf(
        ParameterSpec(
            name = "url",
            type = "string",
            required = true,
            description = "The fully qualified HTTP/HTTPS URL to open."
        )
    )

    /**
     * Builds the `ACTION_VIEW` intent for a web URL.
     *
     * It takes the "url" parameter and creates an `ACTION_VIEW` [Intent].
     *
     * @param context The application context.
     * @param params A map containing the "url" parameter.
     * @return An `ACTION_VIEW` [Intent], or null if the URL is missing.
     */
    override fun buildIntent(context: Context, params: Map<String, Any?>): Intent? {
        val url = params["url"]?.toString()?.trim().orEmpty()
        if (url.isEmpty()) return null
        return Intent(Intent.ACTION_VIEW, url.toUri())
    }
}
