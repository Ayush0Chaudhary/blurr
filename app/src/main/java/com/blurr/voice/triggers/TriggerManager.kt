/**
 * @file TriggerManager.kt
 * @brief Manages the lifecycle of triggers, including storage, scheduling, and execution.
 *
 * This file contains the `TriggerManager` class, which is the central component for handling
 * all trigger-related logic. It saves triggers to SharedPreferences, schedules and cancels
 * time-based alarms with `AlarmManager`, and initiates the execution of trigger instructions.
 */
package com.blurr.voice.triggers

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Intent
import java.util.Calendar

/**
 * A singleton class responsible for managing the lifecycle of all [Trigger]s.
 *
 * This manager handles CRUD (Create, Read, Update, Delete) operations for triggers,
 * persisting them to SharedPreferences. It also interacts with the Android `AlarmManager`
 * to schedule and cancel alarms for time-based triggers.
 *
 * @param context The application context.
 */
class TriggerManager(private val context: Context) {

    /** Lazily-initialized SharedPreferences for storing triggers. */
    private val sharedPreferences: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    /** Lazily-initialized AlarmManager for scheduling time-based triggers. */
    private val alarmManager: AlarmManager by lazy {
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    }
    /** A Gson instance for serializing and deserializing the trigger list. */
    private val gson = Gson()

    /**
     * Adds a new trigger to the system.
     *
     * It saves the trigger to persistent storage and schedules an alarm if it's an
     * enabled, time-based trigger.
     *
     * @param trigger The [Trigger] to add.
     */
    fun addTrigger(trigger: Trigger) {
        val triggers = loadTriggers()
        triggers.add(trigger)
        saveTriggers(triggers)
        if (trigger.isEnabled) {
            scheduleAlarm(trigger)
        }
    }

    /**
     * Removes a trigger from the system.
     *
     * It cancels any pending alarm associated with the trigger and then removes it
     * from persistent storage.
     *
     * @param trigger The [Trigger] to remove.
     */
    fun removeTrigger(trigger: Trigger) {
        val triggers = loadTriggers()
        val triggerToRemove = triggers.find { it.id == trigger.id }
        if (triggerToRemove != null) {
            cancelAlarm(triggerToRemove)
            triggers.remove(triggerToRemove)
            saveTriggers(triggers)
        }
    }

    /**
     * Retrieves a list of all currently saved triggers.
     * @return A list of all [Trigger] objects.
     */
    fun getTriggers(): List<Trigger> {
        return loadTriggers()
    }

    /**
     * Reschedules a single, repeating time-based trigger.
     *
     * This is typically called by the `TriggerReceiver` after a repeating alarm has fired
     * to set the alarm for its next occurrence.
     *
     * @param triggerId The ID of the trigger to reschedule.
     */
    fun rescheduleTrigger(triggerId: String) {
        val triggers = loadTriggers()
        val trigger = triggers.find { it.id == triggerId }
        if (trigger != null && trigger.isEnabled) {
            scheduleAlarm(trigger)
            android.util.Log.d("TriggerManager", "Rescheduled trigger: ${trigger.id}")
        }
    }

    /**
     * Updates an existing trigger.
     *
     * It replaces the old trigger data with the new data in storage. It also updates
     * the associated alarm, scheduling a new one if the trigger is enabled or canceling
     * the old one if it's disabled.
     *
     * @param trigger The [Trigger] object with updated information.
     */
    fun updateTrigger(trigger: Trigger) {
        val triggers = loadTriggers()
        val index = triggers.indexOfFirst { it.id == trigger.id }
        if (index != -1) {
            triggers[index] = trigger
            saveTriggers(triggers)
            if (trigger.isEnabled) {
                scheduleAlarm(trigger)
            } else {
                cancelAlarm(trigger)
            }
        }
    }

    /**
     * Sends a broadcast to the [TriggerReceiver] to execute a task instruction.
     *
     * @param instruction The instruction string for the agent to execute.
     */
    fun executeInstruction(instruction: String) {
        android.util.Log.d("TriggerManager", "Executing instruction: $instruction")
        val intent = Intent(context, TriggerReceiver::class.java).apply {
            action = TriggerReceiver.ACTION_EXECUTE_TASK
            putExtra(TriggerReceiver.EXTRA_TASK_INSTRUCTION, instruction)
        }
        context.sendBroadcast(intent)
    }

    /**
     * Schedules an exact alarm for a time-based trigger.
     *
     * It calculates the next valid trigger time and uses `AlarmManager` to set a wakeup alarm
     * that will fire even if the device is in doze mode.
     *
     * @param trigger The time-based [Trigger] to schedule.
     */
    private fun scheduleAlarm(trigger: Trigger) {
        if (trigger.type != TriggerType.SCHEDULED_TIME) {
            android.util.Log.w("TriggerManager", "Attempted to schedule alarm for non-time-based trigger: ${trigger.id}")
            return
        }

        val intent = Intent(context, TriggerReceiver::class.java).apply {
            action = TriggerReceiver.ACTION_EXECUTE_TASK
            putExtra(TriggerReceiver.EXTRA_TASK_INSTRUCTION, trigger.instruction)
            putExtra(TriggerReceiver.EXTRA_TRIGGER_ID, trigger.id)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            trigger.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextTriggerTime = getNextTriggerTime(trigger.hour!!, trigger.minute!!, trigger.daysOfWeek)
        if (nextTriggerTime == null) {
            android.util.Log.w("TriggerManager", "No valid day of week for trigger: ${trigger.id}")
            return
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nextTriggerTime.timeInMillis,
                    pendingIntent
                )
            } else {
                android.util.Log.w("TriggerManager", "Cannot schedule exact alarm, permission not granted.")
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                nextTriggerTime.timeInMillis,
                pendingIntent
            )
        }
    }

    /**
     * Calculates the next upcoming date and time for a given time and set of weekdays.
     *
     * @param hour The hour of the day (0-23).
     * @param minute The minute of the hour (0-59).
     * @param daysOfWeek A set of [Calendar] day constants (e.g., `Calendar.MONDAY`).
     * @return A [Calendar] object set to the next trigger time, or null if no valid day is found.
     */
    private fun getNextTriggerTime(hour: Int, minute: Int, daysOfWeek: Set<Int>): Calendar? {
        val now = Calendar.getInstance()
        var nextTrigger = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        for (i in 0..7) {
            val day = (now.get(Calendar.DAY_OF_WEEK) + i - 1) % 7 + 1
            if (day in daysOfWeek) {
                nextTrigger.add(Calendar.DAY_OF_YEAR, i)
                if (nextTrigger.after(now)) {
                    return nextTrigger
                }
                nextTrigger.add(Calendar.DAY_OF_YEAR, -i)
            }
        }
        return null
    }

    /**
     * Cancels a pending alarm for a given trigger.
     *
     * @param trigger The [Trigger] whose alarm should be canceled.
     */
    private fun cancelAlarm(trigger: Trigger) {
        if (trigger.type != TriggerType.SCHEDULED_TIME) {
            return
        }
        val intent = Intent(context, TriggerReceiver::class.java).apply {
            action = TriggerReceiver.ACTION_EXECUTE_TASK
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            trigger.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    /**
     * Saves the complete list of triggers to SharedPreferences as a JSON string.
     * @param triggers The list of [Trigger] objects to save.
     */
    private fun saveTriggers(triggers: List<Trigger>) {
        val json = gson.toJson(triggers)
        sharedPreferences.edit().putString(KEY_TRIGGERS, json).apply()
    }

    /**
     * Loads the list of triggers from SharedPreferences.
     * @return A mutable list of [Trigger] objects, or an empty list if none are saved.
     */
    private fun loadTriggers(): MutableList<Trigger> {
        val json = sharedPreferences.getString(KEY_TRIGGERS, null)
        return if (json != null) {
            val type = object : TypeToken<MutableList<Trigger>>() {}.type
            gson.fromJson(json, type)
        } else {
            mutableListOf()
        }
    }

    /**
     * Companion object for constants and the singleton instance provider.
     */
    companion object {
        private const val PREFS_NAME = "com.blurr.voice.triggers.prefs"
        private const val KEY_TRIGGERS = "triggers_list"

        @Volatile
        private var INSTANCE: TriggerManager? = null

        /**
         * Gets the singleton instance of the [TriggerManager].
         * @param context The application context.
         * @return The singleton [TriggerManager] instance.
         */
        fun getInstance(context: Context): TriggerManager {
            return INSTANCE ?: synchronized(this) {
                val instance = TriggerManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
