package com.blurr.voice.triggers

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Calendar

class TriggerManager(private val context: Context) {

    private val sharedPreferences: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    private val alarmManager: AlarmManager by lazy {
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    }
    private val gson = Gson()

    fun addTrigger(trigger: Trigger) {
        val triggers = loadTriggers()
        triggers.add(trigger)
        saveTriggers(triggers)
        if (trigger.isEnabled && trigger.type == TriggerType.SCHEDULED_TIME) {
            scheduleAlarm(trigger)
        }
    }

    fun removeTrigger(trigger: Trigger) {
        val triggers = loadTriggers()
        val triggerToRemove = triggers.find { it.id == trigger.id }
        if (triggerToRemove != null) {
            if (triggerToRemove.type == TriggerType.SCHEDULED_TIME) {
                cancelAlarm(triggerToRemove)
            }
            triggers.remove(triggerToRemove)
            saveTriggers(triggers)
        }
    }

    fun getTriggers(): List<Trigger> {
        return loadTriggers()
    }

    fun rescheduleTrigger(triggerId: String) {
        val triggers = loadTriggers()
        val trigger = triggers.find { it.id == triggerId }
        if (trigger != null && trigger.isEnabled && trigger.type == TriggerType.SCHEDULED_TIME) {
            scheduleAlarm(trigger)
            android.util.Log.d("TriggerManager", "Rescheduled trigger: ${trigger.id}")
        }
    }

    fun updateTrigger(trigger: Trigger) {
        val triggers = loadTriggers()
        val index = triggers.indexOfFirst { it.id == trigger.id }
        if (index != -1) {
            triggers[index] = trigger
            saveTriggers(triggers)
            if (trigger.type == TriggerType.SCHEDULED_TIME) {
                if (trigger.isEnabled) {
                    scheduleAlarm(trigger)
                } else {
                    cancelAlarm(trigger)
                }
            }
        }
    }

    private fun scheduleAlarm(trigger: Trigger) {
        if (trigger.type != TriggerType.SCHEDULED_TIME) {
            android.util.Log.w("TriggerManager", "Attempted to schedule alarm for non-time-based trigger: ${trigger.id}")
            return
        }

        val nextTriggerTime = getNextTriggerTime(trigger.hour!!, trigger.minute!!, trigger.daysOfWeek)
        if (nextTriggerTime == null) {
            android.util.Log.w("TriggerManager", "No valid day of week for trigger: ${trigger.id}")
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

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTriggerTime.timeInMillis, pendingIntent)
            } else {
                android.util.Log.w("TriggerManager", "Cannot schedule exact alarm, permission not granted.")
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTriggerTime.timeInMillis, pendingIntent)
        }
    }

    private fun getNextTriggerTime(hour: Int, minute: Int, daysOfWeek: Set<Int>): Calendar? {
        val now = Calendar.getInstance()

        for (i in 0..7) {
            val nextDay = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, i) }
            val dayOfWeek = nextDay.get(Calendar.DAY_OF_WEEK)

            if (dayOfWeek in daysOfWeek) {
                val nextTrigger = (nextDay.clone() as Calendar).apply {
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                if (nextTrigger.after(now)) {
                    return nextTrigger
                }
            }
        }
        return null
    }

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

    private fun saveTriggers(triggers: List<Trigger>) {
        val json = gson.toJson(triggers)
        sharedPreferences.edit().putString(KEY_TRIGGERS, json).apply()
    }

    private fun loadTriggers(): MutableList<Trigger> {
        val json = sharedPreferences.getString(KEY_TRIGGERS, null)
        return if (json != null) {
            val type = object : TypeToken<MutableList<Trigger>>() {}.type
            gson.fromJson(json, type)
        } else {
            mutableListOf()
        }
    }

    companion object {
        private const val PREFS_NAME = "com.blurr.voice.triggers.prefs"
        private const val KEY_TRIGGERS = "triggers_list"

        @Volatile
        private var INSTANCE: TriggerManager? = null

        fun getInstance(context: Context): TriggerManager {
            return INSTANCE ?: synchronized(this) {
                val instance = TriggerManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
