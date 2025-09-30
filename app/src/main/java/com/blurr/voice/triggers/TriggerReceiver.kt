/**
 * @file TriggerReceiver.kt
 * @brief Defines a BroadcastReceiver to handle the execution of triggered tasks.
 *
 * This file contains the `TriggerReceiver`, which is the entry point for executing tasks
 * initiated by any trigger type (e.g., scheduled alarms, notification events). It includes
 * debounce logic to prevent the same task from running multiple times in quick succession.
 */
package com.blurr.voice.triggers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.blurr.voice.v2.AgentService
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * A [BroadcastReceiver] responsible for receiving and handling trigger execution requests.
 *
 * This receiver listens for the `ACTION_EXECUTE_TASK` intent. When received, it extracts the
 * task instruction, performs debounce checking, and then starts the [AgentService] to carry
 * out the instruction. If the trigger was a repeating alarm, it also reschedules it for the
 * next occurrence.
 */
class TriggerReceiver : BroadcastReceiver() {

    /**
     * Companion object for constants and the debounce cache.
     */
    companion object {
        /** The intent action that signals this receiver to execute a task. */
        const val ACTION_EXECUTE_TASK = "com.blurr.voice.action.EXECUTE_TASK"
        /** The key for the intent extra containing the task instruction string. */
        const val EXTRA_TASK_INSTRUCTION = "com.blurr.voice.EXTRA_TASK_INSTRUCTION"
        /** The key for the intent extra containing the ID of the trigger that fired. */
        const val EXTRA_TRIGGER_ID = "com.blurr.voice.EXTRA_TRIGGER_ID"
        private const val TAG = "TriggerReceiver"
        /** The time window in milliseconds for debouncing duplicate tasks. */
        private const val DEBOUNCE_INTERVAL_MS = 60 * 1000 // 1 minute

        /** A cache to store the last execution timestamp for each task instruction to handle debouncing. */
        private val recentTasks = ConcurrentHashMap<String, Long>()
    }

    /**
     * This method is called when the BroadcastReceiver receives an Intent broadcast.
     *
     * It validates the incoming intent, checks for duplicate tasks within the debounce
     * interval, and if the task is valid, starts the [AgentService].
     *
     * @param context The Context in which the receiver is running.
     * @param intent The Intent being received.
     */
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

            val currentTime = System.currentTimeMillis()
            val lastExecutionTime = recentTasks[taskInstruction]

            if (lastExecutionTime != null && (currentTime - lastExecutionTime) < DEBOUNCE_INTERVAL_MS) {
                Log.d(TAG, "Debouncing duplicate task: '$taskInstruction'")
                return
            }

            recentTasks[taskInstruction] = currentTime

            Log.d(TAG, "Received task to execute: '$taskInstruction'")

            AgentService.start(context, taskInstruction)

            val triggerId = intent.getStringExtra(EXTRA_TRIGGER_ID)
            if (triggerId != null) {
                kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    TriggerManager.getInstance(context).rescheduleTrigger(triggerId)
                }
            }

            cleanupRecentTasks(currentTime)
        }
    }

    /**
     * Cleans up old entries from the debounce cache.
     *
     * This method iterates through the cache and removes any task execution records
     * that are older than the debounce interval.
     *
     * @param currentTime The current system time in milliseconds, used as a reference for cleanup.
     */
    private fun cleanupRecentTasks(currentTime: Long) {
        val iterator = recentTasks.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if ((currentTime - entry.value) > DEBOUNCE_INTERVAL_MS) {
                iterator.remove()
            }
        }
    }
}
