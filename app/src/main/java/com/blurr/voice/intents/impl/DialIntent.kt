/**
 * @file DialIntent.kt
 * @brief Defines the AppIntent for opening the phone dialer.
 *
 * This file contains the implementation of the `DialIntent`, which allows the agent to
 * open the default phone application with a specified number pre-filled.
 */
package com.blurr.voice.intents.impl

import android.content.Context
import android.content.Intent
import com.blurr.voice.intents.AppIntent
import com.blurr.voice.intents.ParameterSpec
import androidx.core.net.toUri

/**
 * An [AppIntent] that opens the default phone dialer app with a given number pre-filled.
 *
 * This intent uses the `ACTION_DIAL` standard Android action, which prepares the dialer
 * but does NOT place the call automatically. This is a safe action that does not require
 * any special permissions.
 */
class DialIntent : AppIntent {
    /**
     * The unique name of this intent, "Dial".
     */
    override val name: String = "Dial"

    /**
     * A user-facing description of the intent's action.
     * @return A string explaining that this intent opens the dialer.
     */
    override fun description(): String =
        "Open the phone dialer with the specified phone number prefilled (no call is placed)."

    /**
     * Specifies the parameters required for this intent.
     * @return A list containing a single [ParameterSpec] for the "phone_number".
     */
    override fun parametersSpec(): List<ParameterSpec> = listOf(
        ParameterSpec(
            name = "phone_number",
            type = "string",
            required = true,
            description = "The phone number to dial. Digits only or may include + and spaces."
        )
    )

    /**
     * Builds the `ACTION_DIAL` intent.
     *
     * It takes the "phone_number" parameter, sanitizes it, and creates an [Intent] with a
     * "tel:" URI.
     *
     * @param context The application context.
     * @param params A map containing the "phone_number" parameter.
     * @return An `ACTION_DIAL` [Intent], or null if the phone number is missing or invalid.
     */
    override fun buildIntent(context: Context, params: Map<String, Any?>): Intent? {
        val raw = params["phone_number"]?.toString()?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val sanitized = raw.replace(" ", "")
        val uri = "tel:$sanitized".toUri()
        return Intent(Intent.ACTION_DIAL, uri)
    }
}

