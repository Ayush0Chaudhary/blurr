package com.blurr.voice

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.blurr.voice.data.Memory
import java.text.SimpleDateFormat
import java.util.*

class MemoriesAdapter(
    private var memories: List<Memory>,
    private val onDeleteClick: (Memory) -> Unit
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
        holder.memoryText.text = memory.originalText
        
        // Format the date
        val dateFormat = SimpleDateFormat("MMM dd, yyyy 'at' HH:mm", Locale.getDefault())
        val date = Date(memory.timestamp)
        holder.memoryDate.text = dateFormat.format(date)
    }

    override fun getItemCount(): Int = memories.size

    fun getMemoryAt(position: Int): Memory? {
        return if (position >= 0 && position < memories.size) {
            memories[position]
        } else {
            null
        }
    }

    fun updateMemories(newMemories: List<Memory>) {
        memories = newMemories
        notifyDataSetChanged()
    }

    fun removeMemory(memory: Memory) {
        val position = memories.indexOf(memory)
        if (position != -1) {
            val newList = memories.toMutableList()
            newList.removeAt(position)
            memories = newList
            notifyItemRemoved(position)
        }
    }
} 