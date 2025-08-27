package com.blurr.voice.agent.v1

import com.google.ai.client.generativeai.type.TextPart

abstract class BaseAgent {

    /**
     * Initialize the chat history.
     * Each pair contains:
     * - Role: "system", "user", or "assistant"
     * - Content: List of TextParts for the message
     */
    abstract fun initChat(): List<Pair<String, List<TextPart>>>

    /**
     * Generate a prompt for the LLM based on current InfoPool state.
     */
    abstract fun getPrompt(infoPool: InfoPool, config: AgentConfig): String

    /**
     * Parse the LLM response into a map of structured values.
     */
    abstract fun parseResponse(response: String): Map<String, String>
}
