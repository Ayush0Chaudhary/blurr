package com.blurr.voice

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.blurr.voice.data.TaskHistoryItem

class MomentsAdapter(private val taskHistory: List<TaskHistoryItem>) : 
    RecyclerView.Adapter<MomentsAdapter.TaskViewHolder>() {

    class TaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val taskText: TextView = itemView.findViewById(R.id.task_text)
        val statusText: TextView = itemView.findViewById(R.id.status_text)
        val timeText: TextView = itemView.findViewById(R.id.time_text)
        val statusIcon: android.widget.ImageView = itemView.findViewById(R.id.status_icon) // Change ID and type
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_task_history, parent, false)
        return TaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val task = taskHistory[position]

        holder.taskText.text = task.task
        holder.statusIcon.setImageResource(task.getStatusIconRes()) // Change this line

        when (task.status.lowercase()) {
            "started" -> {
                holder.statusText.text = "Started"
                holder.timeText.text = "Started: ${task.getFormattedStartTime()}"
            }
            "completed" -> {
                holder.statusText.text = if (task.success == true) "Completed Successfully" else "Completed with Error"
                holder.timeText.text = "Completed: ${task.getFormattedCompletionTime()}"
            }
            "failed" -> {
                holder.statusText.text = "Failed"
                holder.timeText.text = "Failed: ${task.getFormattedCompletionTime()}"
            }
            else -> {
                holder.statusText.text = "Unknown Status"
                holder.timeText.text = "Started: ${task.getFormattedStartTime()}"
            }
        }
    }

    override fun getItemCount(): Int = taskHistory.size
}

