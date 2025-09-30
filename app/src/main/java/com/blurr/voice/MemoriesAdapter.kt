/**
 * @file MemoriesAdapter.kt
 * @brief Defines the RecyclerView adapter for displaying a list of memories.
 *
 * This file contains the `MemoriesAdapter` and its `ViewHolder`, used by `MemoriesActivity`
 * to populate the `RecyclerView` with memory data.
 */
package com.blurr.voice

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.blurr.voice.data.Memory
import java.text.SimpleDateFormat
import java.util.*

/**
 * An adapter for displaying a list of [Memory] objects in a `RecyclerView`.
 *
 * @param memories The initial list of memories to display.
 * @param onDeleteClick A lambda function to be invoked when a memory item is clicked for deletion.
 */
class MemoriesAdapter(
    private var memories: List<Memory>,
    private val onDeleteClick: (Memory) -> Unit
) : RecyclerView.Adapter<MemoriesAdapter.MemoryViewHolder>() {

    /**
     * A `ViewHolder` that holds the views for a single memory item in the list.
     */
    class MemoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val memoryText: TextView = itemView.findViewById(R.id.memoryText)
        val memoryDate: TextView = itemView.findViewById(R.id.memoryDate)
    }

    /**
     * Creates a new `MemoryViewHolder` by inflating the item layout.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_memory, parent, false)
        return MemoryViewHolder(view)
    }

    /**
     * Binds the data from a [Memory] object to the views in the `MemoryViewHolder`.
     * This includes setting the memory text and formatting the timestamp for display.
     */
    override fun onBindViewHolder(holder: MemoryViewHolder, position: Int) {
        val memory = memories[position]
        holder.memoryText.text = memory.originalText
        
        val dateFormat = SimpleDateFormat("MMM dd, yyyy 'at' HH:mm", Locale.getDefault())
        val date = Date(memory.timestamp)
        holder.memoryDate.text = dateFormat.format(date)
    }

    /**
     * Returns the total number of memories in the list.
     */
    override fun getItemCount(): Int = memories.size

    /**
     * Safely retrieves the memory at a given position.
     * @param position The position of the memory in the list.
     * @return The [Memory] object at the specified position, or null if the position is out of bounds.
     */
    fun getMemoryAt(position: Int): Memory? {
        return if (position >= 0 && position < memories.size) {
            memories[position]
        } else {
            null
        }
    }

    /**
     * Updates the adapter's dataset with a new list of memories and refreshes the view.
     * @param newMemories The new list of [Memory] objects to display.
     */
    fun updateMemories(newMemories: List<Memory>) {
        memories = newMemories
        notifyDataSetChanged()
    }

    /**
     * Removes a single memory from the dataset and notifies the adapter to update the view.
     * @param memory The [Memory] object to remove.
     */
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