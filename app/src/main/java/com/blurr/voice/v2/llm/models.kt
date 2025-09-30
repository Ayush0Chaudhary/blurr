/**
 * @file models.kt
 * @brief Defines the core data models for representing messages in a conversation with the Gemini LLM.
 *
 * This file contains the serializable data classes and enums used to construct a valid
 * conversation history for the Gemini API.
 */
package com.blurr.voice.v2.llm

import kotlinx.serialization.Serializable

/**
 * Represents the role of the entity that created a message.
 */
enum class MessageRole {
    /** A message from the end-user. */
    USER,
    /** A message from the language model. */
    MODEL,
    /** A message containing the result of a tool (function) call. */
    TOOL
}

/**
 * A sealed interface representing a single part of a message's content.
 * A message can be composed of multiple parts (e.g., text and images).
 */
@Serializable
sealed interface ContentPart

/**
 * A content part containing simple text.
 * @property text The text content.
 */
@Serializable
data class TextPart(val text: String) : ContentPart

/**
 * Represents a single message in the conversation history sent to or received from the LLM.
 *
 * A message consists of a role and one or more content parts.
 *
 * @property role The role of the message author ([MessageRole.USER], [MessageRole.MODEL], or [MessageRole.TOOL]).
 * @property parts A list of [ContentPart]s that make up the message.
 * @property toolCode An optional identifier for a tool call, used for messages with the [MessageRole.TOOL] role.
 */
@Serializable
data class GeminiMessage(
    val role: MessageRole,
    val parts: List<ContentPart>,
    val toolCode: String? = null
) {
    /**
     * A convenience constructor for creating a simple text message from the user.
     * @param text The user's text input.
     */
    constructor(text: String) : this(
        role = MessageRole.USER,
        parts = listOf(TextPart(text))
    )
}