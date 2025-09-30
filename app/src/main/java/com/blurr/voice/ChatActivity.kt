/**
 * @file ChatActivity.kt
 * @brief Defines a simple activity for displaying a basic chat interface.
 *
 * This file contains the `ChatActivity`, which provides a user interface for a mock
 * conversation. It is not connected to any real chat logic or LLM backend.
 */
package com.blurr.voice

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * An activity that displays a basic, non-functional chat screen.
 *
 * This class sets up a `RecyclerView` to show a list of messages. It allows the user to
 * type and send messages, which are added to the list, and receives a hardcoded default
 * response. It can be launched with an `Intent` extra "custom_message" to display an
 * initial message from the bot.
 */
class ChatActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var editText: EditText
    private lateinit var sendButton: Button
    private val messages = mutableListOf<Message>()
    private lateinit var chatAdapter: ChatAdapter

    /**
     * Called when the activity is first created.
     *
     * This method initializes the layout and UI components, sets up the `RecyclerView` with its
     * adapter, displays an initial message received from the launching intent, and sets up the
     * click listener for the send button.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        recyclerView = findViewById(R.id.recyclerView)
        editText = findViewById(R.id.editText)
        sendButton = findViewById(R.id.sendButton)

        // Get the custom message from the intent, or use a default greeting.
        val customMessage = intent.getStringExtra("custom_message") ?: "Hello! How can I help?"

        // Set up the RecyclerView to display chat messages.
        recyclerView.layoutManager = LinearLayoutManager(this)
        chatAdapter = ChatAdapter(messages)
        recyclerView.adapter = chatAdapter

        // Display the initial custom or default message from the "bot".
        messages.add(Message(customMessage, isUserMessage = false))
        chatAdapter.notifyItemInserted(messages.size - 1)

        // Handle the action of sending a message.
        sendButton.setOnClickListener {
            val messageContent = editText.text.toString()
            if (messageContent.isNotEmpty()) {
                // Add the user's message to the list.
                messages.add(Message(messageContent, isUserMessage = true))
                chatAdapter.notifyItemInserted(messages.size - 1)
                recyclerView.scrollToPosition(messages.size - 1)

                // Clear the input field.
                editText.text.clear()

                // Add a hardcoded default response from the "bot".
                messages.add(Message("This is a default bot response.", isUserMessage = false))
                chatAdapter.notifyItemInserted(messages.size - 1)
                recyclerView.scrollToPosition(messages.size - 1)
            }
        }
    }
}
