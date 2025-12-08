package com.blurr.voice

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.blurr.voice.data.UserMemory
import java.text.SimpleDateFormat
import java.util.*

class MemoriesAdapter(
    private var memories: List<UserMemory>,
    private val onEditClick: (UserMemory) -> Unit,
    private val onDeleteClick: (UserMemory) -> Unit
) : RecyclerView.Adapter<MemoriesAdapter.MemoryViewHolder>() {

    class MemoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val memoryText: TextView = itemView.findViewById(R.id.memoryText)
        val memoryDate: TextView = itemView.findViewById(R.id.memoryDate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_memory, parent, false)
        return MemoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: MemoryViewHolder, position: Int) {
        val memory = memories[position]
        holder.memoryText.text = memory.text
        
        // Format the date
        val dateFormat = SimpleDateFormat("MMM dd, yyyy 'at' HH:mm", Locale.getDefault())
        holder.memoryDate.text = dateFormat.format(memory.createdAt)

        holder.itemView.setOnLongClickListener {
            onDeleteClick(memory)
            true
        }

        holder.itemView.setOnClickListener {
            onEditClick(memory)
        }
    }

    override fun getItemCount(): Int = memories.size

    fun getMemoryAt(position: Int): UserMemory? {
        return if (position >= 0 && position < memories.size) {
            memories[position]
        } else {
            null
        }
    }

    fun updateMemories(newMemories: List<UserMemory>) {
        memories = newMemories
        notifyDataSetChanged()
    }

    fun removeMemory(memory: UserMemory) {
        val position = memories.indexOf(memory)
        if (position != -1) {
            val newList = memories.toMutableList()
            newList.removeAt(position)
            memories = newList
            notifyItemRemoved(position)
        }
    }
}