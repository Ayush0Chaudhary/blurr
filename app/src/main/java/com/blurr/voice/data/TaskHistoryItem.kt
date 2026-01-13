package com.blurr.voice.data

import com.google.firebase.Timestamp

data class TaskHistoryItem(
    val id: String = "", // Added ID to uniquely identify tasks
    val task: String,
    val status: String,
    val startedAt: Timestamp?,
    val completedAt: Timestamp?,
    val success: Boolean?,
    val errorMessage: String?,
    var isPinned: Boolean = false // New field for pinning
) {
    fun getStatusEmoji(): String {
        return when (status.lowercase()) {
            "started" -> "🔄"
            "completed" -> if (success == true) "✅" else "❌"
            "failed" -> "❌"
            else -> "⏳"
        }
    }

    fun getFormattedStartTime(): String {
        return startedAt?.toDate()?.let { date ->
            val formatter = java.text.SimpleDateFormat("MMM dd, yyyy 'at' h:mm a", java.util.Locale.getDefault())
            formatter.format(date)
        } ?: "Unknown"
    }

    fun getFormattedCompletionTime(): String {
        return completedAt?.toDate()?.let { date: java.util.Date ->
            val formatter = java.text.SimpleDateFormat("MMM dd, yyyy 'at' h:mm a", java.util.Locale.getDefault())
            formatter.format(date)
        } ?: "Not completed"
    }
}