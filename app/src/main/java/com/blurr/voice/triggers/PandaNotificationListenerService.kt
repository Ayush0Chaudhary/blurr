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
                // Extract notification content
                val notification = sbn.notification
                val extras = notification?.extras
                val title = extras?.getCharSequence("android.title")?.toString()
                val text = extras?.getCharSequence("android.text")?.toString()

                Log.d(TAG, "Found matching trigger for package: $packageName. Executing instruction: ${matchingTrigger.instruction}")
                Log.d(TAG, "Notification content - Title: '$title', Text: '$text'")

                // Use the TriggerReceiver to start the agent service with notification content
                val intent = android.content.Intent(this@PandaNotificationListenerService, TriggerReceiver::class.java).apply {
                    action = TriggerReceiver.ACTION_EXECUTE_TASK
                    putExtra(TriggerReceiver.EXTRA_TASK_INSTRUCTION, matchingTrigger.instruction)
                    putExtra(TriggerReceiver.EXTRA_NOTIFICATION_TITLE, title)
                    putExtra(TriggerReceiver.EXTRA_NOTIFICATION_TEXT, text)
                    putExtra(TriggerReceiver.EXTRA_NOTIFICATION_PACKAGE, packageName)
                }
                sendBroadcast(intent)
            }
        }
    }
}
