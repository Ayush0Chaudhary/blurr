package com.blurr.voice.v2.llm

import kotlinx.serialization.Serializable

/**
 * Represents the role of the entity creating the message.
 * The 'tool' role is for responses from function calls.
 */
enum class MessageRole {
    USER,
    MODEL,
    TOOL
}

/**
 * A sealed interface representing a part of a message.
 * For now, we only support text parts.
 */
@Serializable
sealed interface ContentPart

@Serializable
data class TextPart(val text: String) : ContentPart

/**
 * Represents a single message in the conversation history.
 * A message consists of a role and one or more content parts.
 *
 * @param role The role of the message author (USER, MODEL, or TOOL).
 * @param parts A list of content parts that make up the message. For this version, it will contain one or more TextPart objects.
 * @param toolCode An optional identifier for the tool call, used for TOOL role messages.
 */
@Serializable
data class GeminiMessage(
    val role: MessageRole,
    val parts: List<ContentPart>,
    val toolCode: String? = null
) {
    // Convenience constructor for simple text messages from the USER
    constructor(text: String) : this(
        role = MessageRole.USER,
        parts = listOf(TextPart(text))
    )
}