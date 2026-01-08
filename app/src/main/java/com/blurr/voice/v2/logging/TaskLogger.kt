package com.blurr.voice.v2.logging

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.util.UUID

object TaskLogger {
    private const val PREFS_NAME = "TaskLogs"
    private const val KEY_LOGS = "logs"
    private const val MAX_LOGS = 1000

    private val json = Json { ignoreUnknownKeys = true }

    fun log(context: Context, input: String, output: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val logs = getLogs(context).toMutableList()

        val newLog = TaskLog(
            uid = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            input = input,
            output = output
        )

        logs.add(0, newLog) // Add to the beginning

        if (logs.size > MAX_LOGS) {
            logs.removeAt(logs.lastIndex)
        }

        saveLogs(prefs, logs)
    }

    fun getLogs(context: Context): List<TaskLog> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonString = prefs.getString(KEY_LOGS, "[]") ?: "[]"
        return try {
            json.decodeFromString(jsonString)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getLog(context: Context, uid: String): TaskLog? {
        return getLogs(context).find { it.uid == uid }
    }

    fun clearLogs(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_LOGS).apply()
    }

    private fun saveLogs(prefs: SharedPreferences, logs: List<TaskLog>) {
        val jsonString = json.encodeToString(logs)
        prefs.edit().putString(KEY_LOGS, jsonString).apply()
    }
}
