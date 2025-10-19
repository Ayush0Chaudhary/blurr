package com.blurr.voice

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log

class AssistEntryActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleAssistLaunch(intent)
        finish()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleAssistLaunch(intent)
        finish()
    }

    private fun handleAssistLaunch(intent: Intent?) {
        Log.d("AssistEntryActivity", "Assistant invoked. Launching AssistantActivity.")

        // We now launch our transparent AssistantActivity instead of the old service.
        val activityIntent = Intent(this, AssistantActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(activityIntent)
    }
}