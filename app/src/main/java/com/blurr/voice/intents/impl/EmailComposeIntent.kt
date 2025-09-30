/**
 * @file EmailComposeIntent.kt
 * @brief Defines the AppIntent for composing an email.
 *
 * This file contains the implementation of the `EmailComposeIntent`, which allows the agent to
 * open the default email client with pre-filled recipient, subject, and body fields.
 */
package com.blurr.voice.intents.impl

import android.content.Context
import android.content.Intent
import com.blurr.voice.intents.AppIntent
import com.blurr.voice.intents.ParameterSpec
import androidx.core.net.toUri

/**
 * An [AppIntent] that opens the default email app to compose a new message.
 *
 * This intent uses the standard `ACTION_SENDTO` action with a `mailto:` URI to ensure
 * that only email applications handle the request.
 */
class EmailComposeIntent : AppIntent {
    /**
     * The unique name of this intent, "EmailCompose".
     */
    override val name: String = "EmailCompose"

    /**
     * A user-facing description of the intent's action.
     * @return A string explaining that this intent opens the email app.
     */
    override fun description(): String =
        "Opens the default email app to compose a message."

    /**
     * Specifies the parameters required for this intent.
     * @return A list of [ParameterSpec] objects for "to", "subject", and "body".
     */
    override fun parametersSpec(): List<ParameterSpec> = listOf(
        ParameterSpec("to", "string", false, "Comma-separated email recipients."),
        ParameterSpec("subject", "string", false, "The subject of the email."),
        ParameterSpec("body", "string", false, "The body text of the email.")
    )

    /**
     * Builds the `ACTION_SENDTO` intent for composing an email.
     *
     * It constructs a `mailto:` URI with the recipient(s) and adds the subject and body
     * as intent extras.
     *
     * @param context The application context.
     * @param params A map containing the optional "to", "subject", and "body" parameters.
     * @return A configured `ACTION_SENDTO` [Intent].
     */
    override fun buildIntent(context: Context, params: Map<String, Any?>): Intent? {
        val to = params["to"]?.toString()?.trim().orEmpty()

        // The recipient(s) should be in the `mailto:` URI path.
        // The ACTION_SENDTO intent does not use EXTRA_EMAIL.
        val uriString = "mailto:$to"

        val intent = Intent(Intent.ACTION_SENDTO, uriString.toUri())

        params["subject"]?.toString()?.takeIf { it.isNotBlank() }?.let {
            intent.putExtra(Intent.EXTRA_SUBJECT, it)
        }
        params["body"]?.toString()?.takeIf { it.isNotBlank() }?.let {
            intent.putExtra(Intent.EXTRA_TEXT, it)
        }

        return intent
    }
}
