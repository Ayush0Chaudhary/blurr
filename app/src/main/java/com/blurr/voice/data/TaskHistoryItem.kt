package com.blurr.voice.data

import com.google.firebase.Timestamp

data class TaskHistoryItem(
    val task: String,
    val status: String,
    val startedAt: Timestamp?,
    val completedAt: Timestamp?,
    val success: Boolean?,
    val errorMessage: String?
) {
    fun getStatusIconRes(): Int {
        return when (status.lowercase()) {
            "started" -> com.blurr.voice.R.drawable.ic_moment_pending
            "completed" -> if (success == true) com.blurr.voice.R.drawable.ic_moment_success else com.blurr.voice.R.drawable.ic_moment_fail
            "failed" -> com.blurr.voice.R.drawable.ic_moment_fail
            else -> com.blurr.voice.R.drawable.ic_moment_unknown
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
