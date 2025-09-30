/**
 * @file LLMHelperFunctions.kt
 * @brief Provides helper functions for interacting with the LLM and managing chat history.
 *
 * This file contains utility functions that simplify common tasks related to constructing
 * prompts, managing conversation history, and making API calls to the Gemini model.
 */
package com.blurr.voice.utilities

import android.graphics.Bitmap
import com.blurr.voice.api.GeminiApi
import com.google.ai.client.generativeai.type.ImagePart
import com.google.ai.client.generativeai.type.TextPart

/**
 * Adds a new response (from either the user or the model) to the chat history.
 *
 * This function takes a role, a text prompt, and an optional image, and appends them
 * as a new turn to the provided chat history.
 *
 * @param role The role of the speaker, typically "user" or "model".
 * @param prompt The text content of the message.
 * @param chatHistory The current conversation history.
 * @param imageBitmap An optional [Bitmap] image to include with the message.
 * @return A new list representing the updated chat history.
 */
fun addResponse(
    role: String,
    prompt: String,
    chatHistory: List<Pair<String, List<Any>>>,
    imageBitmap: Bitmap? = null
): List<Pair<String, List<Any>>> {
    val updatedChat = chatHistory.toMutableList()

    val messageParts = mutableListOf<Any>()
    messageParts.add(TextPart(prompt))

    if (imageBitmap != null) {
        messageParts.add(ImagePart(imageBitmap))
    }

    updatedChat.add(Pair(role, messageParts))
    return updatedChat
}

/**
 * Adds a new response to the chat history with "before" and "after" images.
 *
 * This is a specialized version of [addResponse] for use cases where it's necessary
 * to show the state of the screen before and after an action.
 *
 * @param role The role of the speaker.
 * @param prompt The text content of the message.
 * @param chatHistory The current conversation history.
 * @param imageBefore An optional [Bitmap] representing the state before an action.
 * @param imageAfter An optional [Bitmap] representing the state after an action.
 * @return A new list representing the updated chat history.
 */
fun addResponsePrePost(
    role: String,
    prompt: String,
    chatHistory: List<Pair<String, List<Any>>>,
    imageBefore: Bitmap? = null,
    imageAfter: Bitmap? = null
): List<Pair<String, List<Any>>> {
    val updatedChat = chatHistory.toMutableList()
    val messageParts = mutableListOf<Any>()

    messageParts.add(TextPart(prompt))

    imageBefore?.let {
        messageParts.add(ImagePart(it))
    }

    imageAfter?.let {
        messageParts.add(ImagePart(it))
    }

    updatedChat.add(Pair(role, messageParts))
    return updatedChat
}

/**
 * A wrapper function to get a response from the Gemini API.
 *
 * @param chat The complete chat history to be sent to the model.
 * @return The string response from the Gemini API, or null if an error occurred.
 */
suspend fun getReasoningModelApiResponse(
    chat: List<Pair<String, List<Any>>>,
): String? {
    return GeminiApi.generateContent(chat)
}

