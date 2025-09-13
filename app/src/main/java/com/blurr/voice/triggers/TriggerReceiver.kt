package com.blurr.voice.triggers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.blurr.voice.v2.AgentService
import kotlinx.coroutines.launch

class TriggerReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_EXECUTE_TASK = "com.blurr.voice.action.EXECUTE_TASK"
        const val EXTRA_TASK_INSTRUCTION = "com.blurr.voice.EXTRA_TASK_INSTRUCTION"
        const val EXTRA_TRIGGER_ID = "com.blurr.voice.EXTRA_TRIGGER_ID"
        const val EXTRA_NOTIFICATION_TITLE = "com.blurr.voice.EXTRA_NOTIFICATION_TITLE"
        const val EXTRA_NOTIFICATION_TEXT = "com.blurr.voice.EXTRA_NOTIFICATION_TEXT"
        const val EXTRA_NOTIFICATION_PACKAGE = "com.blurr.voice.EXTRA_NOTIFICATION_PACKAGE"
        private const val TAG = "TriggerReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) {
            Log.w(TAG, "Received null context or intent, cannot proceed.")
            return
        }

        if (intent.action == ACTION_EXECUTE_TASK) {
            val taskInstruction = intent.getStringExtra(EXTRA_TASK_INSTRUCTION)

            if (taskInstruction.isNullOrBlank()) {
                Log.e(TAG, "Received execute task action but instruction was null or empty.")
                return
            }

            // Extract notification content if available
            val notificationTitle = intent.getStringExtra(EXTRA_NOTIFICATION_TITLE)
            val notificationText = intent.getStringExtra(EXTRA_NOTIFICATION_TEXT)
            val notificationPackage = intent.getStringExtra(EXTRA_NOTIFICATION_PACKAGE)

            // Build enhanced instruction with notification context
            val enhancedInstruction = buildEnhancedInstruction(
                taskInstruction, 
                notificationTitle, 
                notificationText, 
                notificationPackage
            )

            Log.d(TAG, "Received task to execute: '$taskInstruction'")
            Log.d(TAG, "Enhanced instruction with notification context: '$enhancedInstruction'")

            // Directly start the v2 AgentService with enhanced instruction
            AgentService.start(context, enhancedInstruction)

            // Reschedule the alarm for the next day
            val triggerId = intent.getStringExtra(EXTRA_TRIGGER_ID)
            if (triggerId != null) {
                kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    TriggerManager.getInstance(context).rescheduleTrigger(triggerId)
                }
            }
        }
    }

    /**
     * Builds an enhanced instruction that includes notification content for better context.
     */
    private fun buildEnhancedInstruction(
        baseInstruction: String,
        notificationTitle: String?,
        notificationText: String?,
        notificationPackage: String?
    ): String {
        if (notificationTitle.isNullOrBlank() && notificationText.isNullOrBlank()) {
            // No notification content available, return original instruction
            return baseInstruction
        }

        val notificationContext = buildString {
            append("A notification was received")
            if (!notificationPackage.isNullOrBlank()) {
                append(" from $notificationPackage")
            }
            if (!notificationTitle.isNullOrBlank()) {
                append(" with title: \"$notificationTitle\"")
            }
            if (!notificationText.isNullOrBlank()) {
                append(" and content: \"$notificationText\"")
            }
            append(". ")
        }

        return "$notificationContext$baseInstruction"
    }
}
