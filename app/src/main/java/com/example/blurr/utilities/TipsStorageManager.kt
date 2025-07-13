package com.example.blurr.utilities

import android.content.Context
import androidx.core.content.edit

object TipsStorageManager {
    private const val PREFS_NAME = "tips"
    private const val TIPS_KEY = "stored_tips"

    fun getLocalTips(context: Context): String {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sharedPreferences.getString(TIPS_KEY, "") ?: ""
    }

    fun saveTipsToLocal(context: Context, tips: String) {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPreferences.edit {
            putString(TIPS_KEY, tips)
        }
    }

    fun appendTips(context: Context, newTips: String) {
        val existingTips = getLocalTips(context)
        val updatedTips = if (existingTips.isBlank()) {
            newTips
        } else {
            "$existingTips\n$newTips"
        }
        saveTipsToLocal(context, updatedTips)
    }

    fun clearTips(context: Context) {
        saveTipsToLocal(context, "")
    }
}