/**
 * @file BootReceiver.kt
 * @brief Defines a BroadcastReceiver that responds to the device boot event.
 *
 * This file contains the `BootReceiver` class, which is responsible for re-initializing
 * essential services and rescheduling alarms after the device has finished booting.
 */
package com.blurr.voice.triggers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * A [BroadcastReceiver] that listens for the `ACTION_BOOT_COMPLETED` intent.
 *
 * When the device finishes booting, this receiver is triggered. Its primary responsibilities are:
 * 1. To start the [TriggerMonitoringService] to ensure trigger conditions are monitored.
 * 2. To reschedule any persistent, time-based triggers (alarms) that were lost during the reboot.
 */
class BootReceiver : BroadcastReceiver() {

    private val TAG = "BootReceiver"

    /**
     * This method is called when the BroadcastReceiver is receiving an Intent broadcast.
     *
     * It checks if the action is `ACTION_BOOT_COMPLETED` and, if so, starts the necessary
     * services and reschedules triggers in a background coroutine.
     *
     * @param context The Context in which the receiver is running.
     * @param intent The Intent being received.
     */
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Device boot completed. Rescheduling alarms.")
            val triggerManager = TriggerManager.getInstance(context)

            // Start the TriggerMonitoringService to handle ongoing triggers like charging state.
            val serviceIntent = Intent(context, TriggerMonitoringService::class.java)
            context.startService(serviceIntent)
            Log.d(TAG, "Started TriggerMonitoringService on boot.")

            // Reschedule time-based alarms in a background thread.
            CoroutineScope(Dispatchers.IO).launch {
                val triggers = triggerManager.getTriggers()
                val scheduledTriggers = triggers.filter { it.isEnabled && it.type == TriggerType.SCHEDULED_TIME }
                scheduledTriggers.forEach { trigger ->
                    // Calling updateTrigger will re-calculate and set the next alarm.
                    triggerManager.updateTrigger(trigger)
                }
                Log.d(TAG, "Finished rescheduling ${scheduledTriggers.size} alarms.")
            }
        }
    }
}
