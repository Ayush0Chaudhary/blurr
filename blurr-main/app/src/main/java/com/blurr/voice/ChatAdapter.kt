package com.blurr.voice

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.graphics.Color
import androidx.recyclerview.widget.RecyclerView

data class Message(val content: String, val isUserMessage: Boolean)


class ChatAdapter(private val messages: List<Message>) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val message = messages[position]
        holder.bind(message)
    }

    override fun getItemCount(): Int = messages.size

    class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageText: TextView = itemView.findViewById(R.id.messageText)

        fun bind(message: Message) {
            messageText.text = message.content
            // Style the text depending on whether it's a user message or not
            if (message.isUserMessage) {
                messageText.setBackgroundColor(Color.LTGRAY)
                messageText.gravity = Gravity.END
            } else {
                messageText.setBackgroundColor(Color.WHITE)
                messageText.gravity = Gravity.START
            }
        }
    }
}
