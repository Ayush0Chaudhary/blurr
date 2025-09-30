/**
 * @file ChatAdapter.kt
 * @brief Defines the RecyclerView adapter for the chat interface in `ChatActivity`.
 *
 * This file contains the `ChatAdapter` and the `Message` data class, which work together
 * to display a list of messages in a `RecyclerView`.
 */
package com.blurr.voice

import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * A simple data class to represent a single chat message.
 *
 * @property content The text content of the message.
 * @property isUserMessage `true` if the message is from the user, `false` if it's from the bot.
 */
data class Message(val content: String, val isUserMessage: Boolean)

/**
 * The adapter for the chat `RecyclerView` in [ChatActivity].
 *
 * This adapter takes a list of [Message] objects and is responsible for creating and binding
 * the `ViewHolder` for each message, displaying it correctly in the chat list.
 *
 * @param messages The list of messages to be displayed.
 */
class ChatAdapter(private val messages: List<Message>) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    /**
     * Creates a new `ChatViewHolder` by inflating the message item layout.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message, parent, false)
        return ChatViewHolder(view)
    }

    /**
     * Binds the data from a [Message] object to the views in a `ChatViewHolder`.
     */
    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val message = messages[position]
        holder.bind(message)
    }

    /**
     * Returns the total number of messages in the list.
     */
    override fun getItemCount(): Int = messages.size

    /**
     * A `ViewHolder` that holds the view for a single chat message.
     *
     * It contains the logic to bind a [Message] object to its `TextView` and to style
     * the message differently based on whether it's from the user or the bot.
     */
    class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageText: TextView = itemView.findViewById(R.id.messageText)

        /**
         * Binds a [Message] to the `TextView` and applies styling.
         *
         * User messages are aligned to the right with a light gray background.
         * Bot messages are aligned to the left with a white background.
         *
         * @param message The message to display.
         */
        fun bind(message: Message) {
            messageText.text = message.content
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
