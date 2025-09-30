/**
 * @file ChargingStateReceiver.kt
 * @brief Defines a BroadcastReceiver that responds to changes in the device's charging state.
 *
 * This file contains the `ChargingStateReceiver`, which listens for `ACTION_POWER_CONNECTED`
 * and `ACTION_POWER_DISCONNECTED` system broadcasts to execute charging-related triggers.
 */
package com.blurr.voice.triggers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * A [BroadcastReceiver] that listens for changes in the device's power connection status.
 *
 * When the device is plugged in or unplugged, this receiver is triggered. It then checks
 * for any enabled triggers of type `CHARGING_STATE` that match the new status
 * ("Connected" or "Disconnected") and sends an intent to the [TriggerReceiver] to
 * execute their associated tasks.
 */
class ChargingStateReceiver : BroadcastReceiver() {

    private val TAG = "ChargingStateReceiver"

    /**
     * This method is called when the BroadcastReceiver is receiving an Intent broadcast.
     *
     * It checks for power connected/disconnected actions and executes any matching triggers.
     *
     * @param context The Context in which the receiver is running.
     * @param intent The Intent being received, which contains the action.
     */
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
