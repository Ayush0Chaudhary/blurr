package com.blurr.voice.triggers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    private val TAG = "BootReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Device boot completed. Rescheduling alarms.")
            val triggerManager = TriggerManager.getInstance(context)

            CoroutineScope(Dispatchers.IO).launch {
                val triggers = triggerManager.getTriggers()
                val scheduledTriggers = triggers.filter { it.isEnabled && it.type == TriggerType.SCHEDULED_TIME }
                scheduledTriggers.forEach { trigger ->
                    triggerManager.updateTrigger(trigger)
                }
                Log.d(TAG, "Finished rescheduling ${scheduledTriggers.size} alarms.")
            }
        }
    }
}
