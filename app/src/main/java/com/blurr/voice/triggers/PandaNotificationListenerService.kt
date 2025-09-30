/**
 * @file PandaNotificationListenerService.kt
 * @brief Defines a service that listens for system notifications to activate triggers.
 *
 * This file contains the implementation of `PandaNotificationListenerService`, which extends
 * Android's `NotificationListenerService` to intercept notifications from other applications.
 * It checks these notifications against user-defined triggers and executes tasks accordingly.
 */
package com.blurr.voice.triggers

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * A [NotificationListenerService] that monitors for new notifications and executes triggers.
 *
 * This service requires the `BIND_NOTIFICATION_LISTENER_SERVICE` permission and must be
 * enabled by the user in the system settings. When a notification is posted, it checks
 * against the list of enabled `NOTIFICATION` type triggers.
 *
 * It supports both specific-app triggers and a wildcard ("*") trigger for all applications.
 * If a match is found, it appends the notification's title and text to the trigger's
 * instruction and broadcasts an intent to the [TriggerReceiver] to execute the task.
 */
class PandaNotificationListenerService : NotificationListenerService() {

    private val TAG = "PandaNotification"
    /** The manager for accessing and handling triggers. */
    private lateinit var triggerManager: TriggerManager

    /**
     * Called when the service is first created. Initializes the trigger manager.
     */
    override fun onCreate() {
        super.onCreate()
        triggerManager = TriggerManager.getInstance(this)
    }

    /**
     * Called by the system when a new notification is posted.
     *
     * This is the core method where the service inspects incoming notifications, finds
     * matching triggers, and initiates task execution.
     *
     * @param sbn A [StatusBarNotification] object containing the notification details.
     */
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName
        Log.d(TAG, "Notification posted from package: $packageName")

        // Ignore notifications from our own app to prevent loops.
        if (packageName == this.packageName) {
            Log.d(TAG, "Ignoring notification from own package.")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            val notificationTriggers = triggerManager.getTriggers()
                .filter { it.type == TriggerType.NOTIFICATION && it.isEnabled }

            // Check for a wildcard trigger first, then for a specific app trigger.
            val matchingTrigger = notificationTriggers.find { it.packageName == "*" }
                ?: notificationTriggers.find { it.packageName == packageName }

            if (matchingTrigger != null) {
                val extras = sbn.notification.extras
                val title = extras.getString("android.title") ?: ""
                val text = extras.getCharSequence("android.text")?.toString() ?: ""
                val notificationContent = "Notification Content: $title - $text"
                val finalInstruction = "${matchingTrigger.instruction}\n\n$notificationContent"

                Log.d(TAG, "Found matching trigger for package: $packageName. Executing instruction: $finalInstruction")

                // Use the TriggerReceiver to start the agent service.
                val intent = android.content.Intent(this@PandaNotificationListenerService, TriggerReceiver::class.java).apply {
                    action = TriggerReceiver.ACTION_EXECUTE_TASK
                    putExtra(TriggerReceiver.EXTRA_TASK_INSTRUCTION, finalInstruction)
                }
                sendBroadcast(intent)
            }
        }
    }
}
