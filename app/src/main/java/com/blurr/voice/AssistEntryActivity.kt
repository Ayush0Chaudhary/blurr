package com.blurr.voice

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.blurr.voice.utilities.RoleManagerHelper

class AssistEntryActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleAssistLaunch(intent)
        // No UI — finish immediately
        finish()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleAssistLaunch(intent)
        finish()
    }

    private fun handleAssistLaunch(intent: Intent?) {
        Log.d("AssistEntryActivity", "Assistant invoked via ACTION_ASSIST, intent=$intent")

        if (RoleManagerHelper.isDefaultAssistant(this)) {
            // If agent already running, you can signal it to focus/show UI instead of starting again
            if (!ConversationalAgentService.isRunning) {
                val serviceIntent = Intent(this, ConversationalAgentService::class.java).apply {
                    action = "com.blurr.voice.ACTION_START_FROM_ASSIST"
                    putExtra("source", "assist_gesture")       // optional metadata
                }
                ContextCompat.startForegroundService(this, serviceIntent)
            } else {
                // e.g., tell the service to bring its overlay/mic UI to front
                sendBroadcast(Intent("com.blurr.voice.ACTION_SHOW_OVERLAY"))
            }
        } else {
            // App is not the default assistant, guide user to set it
            Toast.makeText(this, "Please set Panda as the default assistant", Toast.LENGTH_LONG).show()
            val roleIntent = Intent(this, RoleRequestActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(roleIntent)
        }
    }
}
