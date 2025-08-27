package com.blurr.voice

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ChatActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var editText: EditText
    private lateinit var sendButton: Button
    private val messages = mutableListOf<Message>()
    private lateinit var chatAdapter: ChatAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        recyclerView = findViewById(R.id.recyclerView)
        editText = findViewById(R.id.editText)
        sendButton = findViewById(R.id.sendButton)

        // Get the custom message from the intent
        val customMessage = intent.getStringExtra("custom_message") ?: "Hello! How can I helpy?"

        // Set up the RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(this)
        chatAdapter = ChatAdapter(messages)
        recyclerView.adapter = chatAdapter

        // Display the custom message or default message
        messages.add(Message(customMessage, isUserMessage = false))
        chatAdapter.notifyItemInserted(messages.size - 1)

        // Handle sending messages
        sendButton.setOnClickListener {
            val messageContent = editText.text.toString()
            if (messageContent.isNotEmpty()) {
                // Add the user's message
                messages.add(Message(messageContent, isUserMessage = true))
                chatAdapter.notifyItemInserted(messages.size - 1)
                recyclerView.scrollToPosition(messages.size - 1) // Scroll to the latest message

                // Clear the input field
                editText.text.clear()

                // Optionally, send a default bot response
                messages.add(Message("This is a default bot response.", isUserMessage = false))
                chatAdapter.notifyItemInserted(messages.size - 1)
                recyclerView.scrollToPosition(messages.size - 1)
            }
        }
    }
}
