package com.blurr.voice

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView // Import ImageView
import android.widget.TextView
import android.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import com.blurr.voice.data.TaskHistoryItem

class MomentsAdapter(
    private val taskHistory: List<TaskHistoryItem>,
    private val onRepeatClick: (String) -> Unit,
    private val onPinClick: (TaskHistoryItem) -> Unit
) : RecyclerView.Adapter<MomentsAdapter.TaskViewHolder>() {

    class TaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val taskText: TextView = itemView.findViewById(R.id.task_text)
        val statusText: TextView = itemView.findViewById(R.id.status_text)
        val timeText: TextView = itemView.findViewById(R.id.time_text)
        val statusEmoji: TextView = itemView.findViewById(R.id.status_emoji)
        // UPDATED: Change type from TextView to ImageView
        val pinnedIndicator: ImageView = itemView.findViewById(R.id.pinned_indicator)
        val menuButton: ImageButton = itemView.findViewById(R.id.menu_button)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_task_history, parent, false)
        return TaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val task = taskHistory[position]

        holder.taskText.text = task.task
        holder.statusEmoji.text = task.getStatusEmoji()

        // The logic to show/hide remains the same, as ImageView also has visibility property
        holder.pinnedIndicator.visibility = if (task.isPinned) View.VISIBLE else View.GONE

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

        holder.menuButton.setOnClickListener { view ->
            showPopupMenu(view, task)
        }
    }
    // ... rest of the adapter remains the same
    private fun showPopupMenu(view: View, task: TaskHistoryItem) {
        val popup = PopupMenu(view.context, view)
        popup.inflate(R.menu.menu_task_item)

        val pinItem = popup.menu.findItem(R.id.action_pin)
        pinItem.title = if (task.isPinned) "Unpin Task" else "Pin Task"

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_repeat -> {
                    onRepeatClick(task.task)
                    true
                }
                R.id.action_pin -> {
                    onPinClick(task)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    override fun getItemCount(): Int = taskHistory.size
}