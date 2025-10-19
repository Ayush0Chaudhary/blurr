package com.blurr.voice.triggers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ChargingStateReceiver : BroadcastReceiver() {

    private val TAG = "ChargingStateReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "Received broadcast: $action")

        if (action != Intent.ACTION_POWER_CONNECTED && action != Intent.ACTION_POWER_DISCONNECTED) {
            return
        }

        val status = if (action == Intent.ACTION_POWER_CONNECTED) "Connected" else "Disconnected"
        Log.d(TAG, "Device charging status: $status")

        CoroutineScope(Dispatchers.IO).launch {
            val triggerManager = TriggerManager.getInstance(context)
            val triggers = triggerManager.getTriggers()
            val matchingTriggers = triggers.filter {
                it.isEnabled && it.type == TriggerType.CHARGING_STATE && it.chargingStatus == status
            }

            Log.d(TAG, "Found ${matchingTriggers.size} matching triggers for status '$status'")

            matchingTriggers.forEach { trigger ->
                Log.d(TAG, "Executing trigger: ${trigger.id} - ${trigger.instruction}")
                val executeIntent = Intent(context, TriggerReceiver::class.java).apply {
                    this.action = TriggerReceiver.ACTION_EXECUTE_TASK
                    putExtra(TriggerReceiver.EXTRA_TASK_INSTRUCTION, trigger.instruction)
                }
                context.sendBroadcast(executeIntent)
            }
        }
    }
}
