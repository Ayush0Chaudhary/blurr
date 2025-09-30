/**
 * @file AssistEntryActivity.kt
 * @brief Defines a headless Activity that serves as the entry point for the system's Assist action.
 *
 * This file contains the `AssistEntryActivity`, which is configured to launch when the user
 * invokes the default assistant on the device (e.g., by long-pressing the home button).
 */
package com.blurr.voice

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * A headless [Activity] that intercepts the `ACTION_ASSIST` intent.
 *
 * This activity has no user interface. Its sole purpose is to receive the system's assistant
 * invocation event and decide whether to start the [ConversationalAgentService] or simply
 * bring its UI to the foreground if it's already running.
 */
class AssistEntryActivity : Activity() {

    /**
     * Called when the activity is first created. It handles the assist launch and then finishes.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleAssistLaunch(intent)
        // This activity has no UI, so it should be closed immediately after launching the service.
        finish()
    }

    /**
     * Called when the activity is re-launched while already running.
     */
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleAssistLaunch(intent)
        finish()
    }

    /**
     * The core logic for handling the assist action.
     *
     * It checks if the [ConversationalAgentService] is already running.
     * - If not, it starts the service in the foreground.
     * - If it is, it sends a broadcast to request that the service's UI be shown.
     *
     * @param intent The intent that triggered the activity launch.
     */
    private fun handleAssistLaunch(intent: Intent?) {
        Log.d("AssistEntryActivity", "Assistant invoked via ACTION_ASSIST, intent=$intent")

        if (!ConversationalAgentService.isRunning) {
            val serviceIntent = Intent(this, ConversationalAgentService::class.java).apply {
                action = "com.blurr.voice.ACTION_START_FROM_ASSIST"
                putExtra("source", "assist_gesture") // Optional metadata
            }
            ContextCompat.startForegroundService(this, serviceIntent)
        } else {
            // If the service is already running, tell it to bring its overlay/mic UI to the front.
            sendBroadcast(Intent("com.blurr.voice.ACTION_SHOW_OVERLAY"))
        }
    }
}
