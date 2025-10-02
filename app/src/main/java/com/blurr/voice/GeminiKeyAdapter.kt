package com.blurr.voice

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class GeminiKeyAdapter(
    private var keys: MutableList<String>,
    private val onDelete: (String) -> Unit
) : RecyclerView.Adapter<GeminiKeyAdapter.KeyViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): KeyViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_gemini_key, parent, false)
        return KeyViewHolder(view)
    }

    override fun onBindViewHolder(holder: KeyViewHolder, position: Int) {
        val key = keys[position]
        holder.bind(key, onDelete)
    }

    override fun getItemCount(): Int = keys.size

    fun updateKeys(newKeys: List<String>) {
        keys.clear()
        keys.addAll(newKeys)
        notifyDataSetChanged()
    }

    class KeyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textGeminiKey: TextView = itemView.findViewById(R.id.textGeminiKey)
        private val buttonDeleteKey: Button = itemView.findViewById(R.id.buttonDeleteKey)

        fun bind(key: String, onDelete: (String) -> Unit) {
            textGeminiKey.text = key
            buttonDeleteKey.setOnClickListener {
                onDelete(key)
            }
        }
    }
}