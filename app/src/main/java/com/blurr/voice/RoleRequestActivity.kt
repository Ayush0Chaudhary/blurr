/**
 * @file RoleRequestActivity.kt
 * @brief Defines a headless activity for requesting the Default Assistant role.
 *
 * This file contains `RoleRequestActivity`, a no-UI activity whose sole purpose is to
 * trigger the system dialog that allows the user to set this app as their default assistant.
 */
package com.blurr.voice

import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

/**
 * A headless activity that handles the logic for requesting the `ROLE_ASSISTANT`.
 *
 * This activity has no layout. It is launched, immediately requests the system role,
 * and then finishes, returning the result to the calling activity. It uses the `RoleManager`
 * on compatible Android versions and provides a fallback to open system settings if the
 * role request dialog cannot be shown.
 */
class RoleRequestActivity : AppCompatActivity() {

    private var launched = false
    private lateinit var roleLauncher: ActivityResultLauncher<Intent>

    /**
     * Initializes the activity and the result launcher for the role request.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launched = savedInstanceState?.getBoolean("launched") ?: false

        // Register the activity result launcher to handle the outcome of the role request.
        roleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                Toast.makeText(this, "Set as default assistant successfully!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Couldn’t become default assistant. Opening settings…", Toast.LENGTH_SHORT).show()
                Log.w("RoleRequestActivity", "Role request canceled or app not eligible.\n${explainAssistantEligibility()}")
                openAssistantSettingsFallback()
            }
            finish() // Always finish after handling the result.
        }
    }

    /**
     * The core logic is placed here to ensure the activity's window is attached before
     * launching another intent, preventing a `BadTokenException`.
     */
    override fun onPostResume() {
        super.onPostResume()
        if (launched) return // Prevent re-launching on configuration change.
        launched = true

        val rm = getSystemService(RoleManager::class.java)
        Log.d("RoleRequestActivity", explainAssistantEligibility())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            rm?.isRoleAvailable(RoleManager.ROLE_ASSISTANT) == true &&
            !rm.isRoleHeld(RoleManager.ROLE_ASSISTANT)
        ) {
            window.decorView.post {
                try {
                    roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT))
                } catch (_: Exception) {
                    openAssistantSettingsFallback()
                }
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && rm?.isRoleHeld(RoleManager.ROLE_ASSISTANT) == true) {
                Toast.makeText(this, "Already the default assistant.", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                openAssistantSettingsFallback()
            }
        }
    }

    /**
     * A fallback method that attempts to open various system settings screens where the user
     * can manually set the default assistant app.
     */
    private fun openAssistantSettingsFallback() {
        val intents = listOf(
            Intent("android.settings.VOICE_INPUT_SETTINGS"),
            Intent(Settings.ACTION_VOICE_INPUT_SETTINGS),
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        )
        for (i in intents) {
            if (i.resolveActivity(packageManager) != null) {
                startActivity(i)
                return
            }
        }
        Toast.makeText(this, "Assistant settings unavailable on this device.", Toast.LENGTH_LONG).show()
    }

    /**
     * A debugging helper that generates a string explaining the app's eligibility for the
     * assistant role by checking its manifest declarations.
     */
    private fun explainAssistantEligibility(): String {
        val pm = packageManager
        val pkg = packageName

        val assistIntent = Intent(Intent.ACTION_ASSIST).setPackage(pkg)
        val assistActivities = pm.queryIntentActivities(assistIntent, 0)

        val visIntent = Intent("android.service.voice.VoiceInteractionService").setPackage(pkg)
        val visServices = pm.queryIntentServices(visIntent, 0)

        return buildString {
            append("Assistant eligibility:\n")
            append("• ACTION_ASSIST activity: ${if (assistActivities.isNotEmpty()) "FOUND" else "NOT FOUND"}\n")
            append("• VoiceInteractionService: ${if (visServices.isNotEmpty()) "FOUND" else "NOT FOUND"}\n")
            append("Note: Many OEMs only list apps with a VoiceInteractionService as selectable assistants.\n")
        }
    }

    /**
     * Saves the `launched` flag to prevent the role request from being triggered again
     * after a configuration change (e.g., screen rotation).
     */
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("launched", launched)
        super.onSaveInstanceState(outState)
    }
}
