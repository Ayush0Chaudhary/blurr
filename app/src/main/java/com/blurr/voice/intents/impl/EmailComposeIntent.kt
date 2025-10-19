package com.blurr.voice.intents.impl

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.blurr.voice.intents.AppIntent
import com.blurr.voice.intents.ParameterSpec

class EmailComposeIntent : AppIntent {
    override val name: String = "EmailCompose"

    override fun description(): String =
        "Always use this intent when you want to send the email to mail:id. this intent will use the default email app."

    override fun parametersSpec(): List<ParameterSpec> = listOf(
        ParameterSpec("to", "string", false, "Comma-separated email recipients."),
        ParameterSpec("subject", "string", false, "Email subject."),
        ParameterSpec("body", "string", false, "Email body text.")
    )

    override fun buildIntent(context: Context, params: Map<String, Any?>): Intent? {
        val to = params["to"]?.toString()?.trim().orEmpty()
        val mailto = if (to.isBlank()) "mailto:" else "mailto:$to"
        return Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(mailto))
            params["subject"]?.toString()?.takeIf { it.isNotBlank() }?.let {
                putExtra(Intent.EXTRA_SUBJECT, it)
            }
            params["body"]?.toString()?.takeIf { it.isNotBlank() }?.let {
                putExtra(Intent.EXTRA_TEXT, it)
            }
        }
    }
}
