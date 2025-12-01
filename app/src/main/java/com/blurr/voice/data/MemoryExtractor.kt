package com.blurr.voice.data

import android.content.Context
import android.util.Log
import com.blurr.voice.api.GeminiApi
import com.google.ai.client.generativeai.type.TextPart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.cactus.ChatMessage

/**
 * Extracts and stores memories from conversations using LLM-based memory extraction
 */
object MemoryExtractor {
    
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
     * Extracts memories from a conversation and stores them asynchronously
     * This is a fire-and-forget operation that doesn't block the conversation flow
     * 
     * @param conversationHistory The conversation to extract memories from
     * @param memoryManager The memory manager instance
     * @param scope The coroutine scope for async operations
     * @param usedMemories Set of memories already used in this conversation to avoid duplicates
     */
    /**
     * Extracts memories from a conversation and stores them asynchronously
     * This is a fire-and-forget operation that doesn't block the conversation flow
     * 
     * @param context The context to access preferences
     * @param conversationHistory The conversation to extract memories from
     * @param memoryManager The memory manager instance
     * @param usedMemories Set of memories already used in this conversation to avoid duplicates
     */
    suspend fun extractAndStoreMemories(
        context: Context,
        conversationHistory: List<Pair<String, List<Any>>>,
        memoryManager: MemoryManager,
        usedMemories: Set<String> = emptySet()
    ) {
        withContext(Dispatchers.IO) {
            try {
                Log.d("MemoryExtractor", "Starting memory extraction from conversation")
                Log.d("MemoryExtractor", "Used memories count: ${usedMemories.size}")

                // Convert conversation to text format for analysis
                val conversationText = formatConversationForExtraction(conversationHistory)

                // Format used memories for the prompt
                val usedMemoriesText = if (usedMemories.isNotEmpty()) {
                    usedMemories.joinToString("\n") { "- $it" }
                } else {
                    "None"
                }

                // Create the extraction prompt with used memories
                val extractionPrompt = memoryExtractionPrompt
                    .replace("{conversation}", conversationText)
                    .replace("{used_memories}", usedMemoriesText)

                // Check preference for local model
                val prefs = context.getSharedPreferences("BlurrSettings", Context.MODE_PRIVATE)
                val useLocalModel = prefs.getBoolean("use_local_model", false)

                val extractionResponse = if (useLocalModel) {
                    Log.d("MemoryExtractor", "Using local CactusLM for memory extraction")
                    CactusEmbeddingManager.generateCompletion(
                        listOf(ChatMessage(role = "user", content = extractionPrompt))
                    )
                } else {
                    // Call LLM for memory extraction (Remote)
                    val extractionChat = listOf(
                        "user" to listOf(TextPart(extractionPrompt))
                    )
                    GeminiApi.generateContent(extractionChat)
                }

                if (extractionResponse != null) {
                    Log.d("MemoryExtractor", "Memory extraction response: ${extractionResponse.take(200)}...")

                    // Parse the extracted memories
                    val memories = parseExtractedMemories(extractionResponse)

                    if (memories.isNotEmpty()) {
                        Log.d("MemoryExtractor", "Extracted ${memories.size} memories")

                        // Store each memory asynchronously (no need for string filtering since LLM handles it)
                        memories.forEach { memory ->
                            try {
                                val success = memoryManager.addMemory(memory)
                                if (success) {
                                    Log.d("MemoryExtractor", "Successfully stored memory: $memory")
                                } else {
                                    Log.e("MemoryExtractor", "Failed to store memory: $memory")
                                }
                            } catch (e: Exception) {
                                Log.e("MemoryExtractor", "Error storing memory: $memory", e)
                            }
                        }
                    } else {
                        Log.d("MemoryExtractor", "No significant memories found in conversation")
                    }
                } else {
                    Log.e("MemoryExtractor", "Failed to get memory extraction response")
                }

            } catch (e: Exception) {
                Log.e("MemoryExtractor", "Error during memory extraction", e)
            }
        }
    }
//
    /**
     * Formats conversation history for memory extraction analysis
     */
    private fun formatConversationForExtraction(conversationHistory: List<Pair<String, List<Any>>>): String {
        return conversationHistory.joinToString("\n") { (role, parts) ->
            val textParts = parts.filterIsInstance<TextPart>()
            val text = textParts.joinToString(" ") { it.text }
            "$role: $text"
        }
    }
    
    /**
     * Parses the LLM response to extract individual memories
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