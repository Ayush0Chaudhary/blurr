package com.blurr.voice.triggers

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PandaNotificationListenerService : NotificationListenerService() {

    private val TAG = "PandaNotification"
    private lateinit var triggerManager: TriggerManager

    override fun onCreate() {
        super.onCreate()
        triggerManager = TriggerManager.getInstance(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName
        Log.d(TAG, "Notification posted from package: $packageName")

        CoroutineScope(Dispatchers.IO).launch {
            val notificationTriggers = triggerManager.getTriggers()
                .filter { it.type == TriggerType.NOTIFICATION && it.isEnabled }

            val matchingTrigger = notificationTriggers.find { it.packageName == packageName }

            if (matchingTrigger != null) {
                val extras = sbn.notification.extras
                val title = extras.getString("android.title") ?: ""
                val text = extras.getCharSequence("android.text")?.toString() ?: ""
                val notificationContent = "Notification Content: $title - $text"
                val finalInstruction = "${matchingTrigger.instruction}\n\n$notificationContent"

                Log.d(TAG, "Found matching trigger for package: $packageName. Executing instruction: $finalInstruction")
                // Use the TriggerReceiver to start the agent service
                val intent = android.content.Intent(this@PandaNotificationListenerService, TriggerReceiver::class.java).apply {
                    action = TriggerReceiver.ACTION_EXECUTE_TASK
                    putExtra(TriggerReceiver.EXTRA_TASK_INSTRUCTION, finalInstruction)
                }
                sendBroadcast(intent)
            }
        }
    }
}
