package com.blurr.voice.triggers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.blurr.voice.v2.AgentService
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class TriggerReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_EXECUTE_TASK = "com.blurr.voice.action.EXECUTE_TASK"
        const val EXTRA_TASK_INSTRUCTION = "com.blurr.voice.EXTRA_TASK_INSTRUCTION"
        const val EXTRA_TRIGGER_ID = "com.blurr.voice.EXTRA_TRIGGER_ID"
        private const val TAG = "TriggerReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) {
            Log.w(TAG, "Received null context or intent, cannot proceed.")
            return
        }

        if (intent.action == ACTION_EXECUTE_TASK) {
            val taskInstruction = intent.getStringExtra(EXTRA_TASK_INSTRUCTION)
            val triggerId = intent.getStringExtra(EXTRA_TRIGGER_ID)

            if (taskInstruction.isNullOrBlank()) {
                Log.e(TAG, "Received execute task action but instruction was null or empty.")
                return
            }

            Log.d(TAG, "Received task to execute: '$taskInstruction'")
            AgentService.start(context, taskInstruction)

            // If it was a time-based trigger, reschedule it for the next day
            if (triggerId != null) {
                GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    TriggerManager.getInstance(context).rescheduleTrigger(triggerId)
                }
            }
        }
    }
}
