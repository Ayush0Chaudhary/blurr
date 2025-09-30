/**
 * @file MemoryExtractor.kt
 * @brief Defines a service for extracting key facts from conversations to be stored as memories.
 *
 * This file contains the `MemoryExtractor` object, which uses a Large Language Model (LLM)
 * to analyze conversation history and identify significant, long-term information about the user.
 */
package com.blurr.voice.data

import android.util.Log
//import com.blurr.voice.api.GeminiApi
import com.google.ai.client.generativeai.type.TextPart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * An object responsible for extracting and storing memories from conversations using an LLM.
 *
 * This service analyzes conversation history to identify and save lasting facts about the user,
 * helping to build a personalized experience over time.
 */
object MemoryExtractor {
    
    /**
     * The prompt template sent to the LLM for memory extraction.
     *
     * This prompt instructs the model to act as a memory extraction agent, focusing on
     * significant personal details while ignoring transient information. It also includes
     * placeholders for the conversation history and a list of already known memories to
     * prevent duplication.
     */
    private val memoryExtractionPrompt = """
        You are a memory extraction agent. Analyze the following conversation and extract key, lasting facts about the user which are supposed to be known by perfect friend to understand the user better.
        
        Focus on:
        - Personal details (family, relationships, preferences, life events)
        - Significant experiences or traumas or hobbies
        - Important dates, locations, or circumstances
        - Long-term preferences or goals or habits etc
        Ignore:
        - Fleeting emotions or temporary states
        - Generic statements or hypothetical scenarios
        - Technical details or app-specific information
        
        IMPORTANT: Do NOT extract memories that are semantically equivalent to the following already known memories:
        {used_memories}
        
        Format each memory as a clear, concise sentence that captures the essential fact.
        If no significant memories are found, return "NO_MEMORIES".
        
        Conversation:
        {conversation}
        
        Extracted Memories (one per line):
    """.trimIndent()
    
    /**
     * Analyzes a conversation, extracts key memories, and stores them via the [MemoryManager].
     *
     * This is a fire-and-forget operation that runs asynchronously on an IO thread to avoid
     * blocking the main conversation flow. It formats the conversation, sends it to the LLM,
     * parses the response, and then stores each extracted memory.
     *
     * **Note:** This function is currently commented out and disabled in the source code.
     *
     * @param conversationHistory The list of conversation turns to analyze.
     * @param memoryManager The [MemoryManager] instance used to store the extracted memories.
     * @param usedMemories A set of memories that have already been used in the current context,
     *                     provided to the LLM to avoid extracting duplicate information.
     */
//    suspend fun extractAndStoreMemories(
//        conversationHistory: List<Pair<String, List<Any>>>,
//        memoryManager: MemoryManager,
//        usedMemories: Set<String> = emptySet()
//    ) {
//        withContext(Dispatchers.IO) {
//            try {
//                Log.d("MemoryExtractor", "Starting memory extraction from conversation")
//                Log.d("MemoryExtractor", "Used memories count: ${usedMemories.size}")
//
//                val conversationText = formatConversationForExtraction(conversationHistory)
//
//                val usedMemoriesText = if (usedMemories.isNotEmpty()) {
//                    usedMemories.joinToString("\n") { "- $it" }
//                } else {
//                    "None"
//                }
//
//                val extractionPrompt = memoryExtractionPrompt
//                    .replace("{conversation}", conversationText)
//                    .replace("{used_memories}", usedMemoriesText)
//
//                val extractionChat = listOf(
//                    "user" to listOf(TextPart(extractionPrompt))
//                )
//
//                val extractionResponse = GeminiApi.generateContent(extractionChat)
//
//                if (extractionResponse != null) {
//                    Log.d("MemoryExtractor", "Memory extraction response: ${extractionResponse.take(200)}...")
//
//                    val memories = parseExtractedMemories(extractionResponse)
//
//                    if (memories.isNotEmpty()) {
//                        Log.d("MemoryExtractor", "Extracted ${memories.size} memories")
//
//                        memories.forEach { memory ->
//                            try {
//                                val success = memoryManager.addMemory(memory)
//                                if (success) {
//                                    Log.d("MemoryExtractor", "Successfully stored memory: $memory")
//                                } else {
//                                    Log.e("MemoryExtractor", "Failed to store memory: $memory")
//                                }
//                            } catch (e: Exception) {
//                                Log.e("MemoryExtractor", "Error storing memory: $memory", e)
//                            }
//                        }
//                    } else {
//                        Log.d("MemoryExtractor", "No significant memories found in conversation")
//                    }
//                } else {
//                    Log.e("MemoryExtractor", "Failed to get memory extraction response")
//                }
//
//            } catch (e: Exception) {
//                Log.e("MemoryExtractor", "Error during memory extraction", e)
//            }
//        }
//    }
//
    /**
     * Formats the conversation history into a plain text string for LLM analysis.
     *
     * @param conversationHistory The conversation history to format.
     * @return A single string with each turn on a new line, prefixed by the role.
     */
    private fun formatConversationForExtraction(conversationHistory: List<Pair<String, List<Any>>>): String {
        return conversationHistory.joinToString("\n") { (role, parts) ->
            val textParts = parts.filterIsInstance<TextPart>()
            val text = textParts.joinToString(" ") { it.text }
            "$role: $text"
        }
    }
    
    /**
     * Parses the raw string response from the LLM to extract a list of memories.
     *
     * This function cleans the response by splitting it into lines and filtering out
     * empty lines, "NO_MEMORIES" responses, and other artifacts.
     *
     * @param response The raw text response from the LLM.
     * @return A list of trimmed, non-empty memory strings.
     */
    private fun parseExtractedMemories(response: String): List<String> {
        return try {
            response.lines()
                .filter { it.isNotBlank() }
                .filter { !it.equals("NO_MEMORIES", ignoreCase = true) }
                .filter { !it.startsWith("Extracted Memories") }
                .filter { !it.startsWith("Memories:") }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.e("MemoryExtractor", "Error parsing extracted memories", e)
            emptyList()
        }
    }
} 