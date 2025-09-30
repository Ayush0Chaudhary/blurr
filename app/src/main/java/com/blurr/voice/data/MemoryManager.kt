/**
 * @file MemoryManager.kt
 * @brief Provides a high-level manager for memory operations, combining database access and embedding services.
 *
 * This file contains the `MemoryManager` class, which acts as a facade for all memory-related
 * operations. It orchestrates the use of `EmbeddingService` to generate vector representations
 * of text and `MemoryDao` to persist and retrieve `Memory` entities from the database.
 */
package com.blurr.voice.data

import android.content.Context
import android.util.Log
import com.blurr.voice.api.EmbeddingService
import com.blurr.voice.MyApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * A manager class for handling all memory-related operations, including storage, retrieval,
 * and similarity-based searching.
 *
 * This class abstracts the complexities of generating embeddings and interacting with the
 * database. It provides a clean API for other parts of the application to manage memories.
 *
 * @param context The application context, used to get the database instance.
 */
class MemoryManager(private val context: Context) {
    
    /** The Room database instance. */
    private val database = AppDatabase.getDatabase(context)
    /** The Data Access Object for memories. */
    private val memoryDao = database.memoryDao()
    /** A dedicated coroutine scope for fire-and-forget background operations. */
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    /**
     * Adds a new memory to the database after generating its embedding.
     *
     * This function can optionally check for duplicate or highly similar memories before
     * insertion to avoid redundant data.
     *
     * @param originalText The text content of the memory to be added.
     * @param checkDuplicates If true, performs a similarity search to prevent adding duplicate memories.
     * @return `true` if the memory was added successfully or if a duplicate was detected and skipped,
     *         `false` if an error occurred (e.g., embedding generation failed).
     */
    suspend fun addMemory(originalText: String, checkDuplicates: Boolean = true): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("MemoryManager", "Adding memory: ${originalText.take(100)}...")
                
                if (checkDuplicates) {
                    val similarMemories = findSimilarMemories(originalText, similarityThreshold = 0.85f)
                    if (similarMemories.isNotEmpty()) {
                        Log.d("MemoryManager", "Found ${similarMemories.size} similar memories, skipping duplicate")
                        return@withContext true
                    }
                }
                
                val embedding = EmbeddingService.generateEmbedding(
                    text = originalText,
                    taskType = "RETRIEVAL_DOCUMENT"
                )
                
                if (embedding == null) {
                    Log.e("MemoryManager", "Failed to generate embedding for text")
                    return@withContext false
                }
                
                val embeddingJson = JSONArray(embedding).toString()
                
                val memory = Memory(
                    originalText = originalText,
                    embedding = embeddingJson
                )
                
                val id = memoryDao.insertMemory(memory)
                Log.d("MemoryManager", "Successfully added memory with ID: $id")
                return@withContext true
                
            } catch (e: Exception) {
                Log.e("MemoryManager", "Error adding memory $e", e)
                return@withContext false
            }
        }
    }

    /**
     * Adds a memory in a "fire-and-forget" manner.
     *
     * This function launches the `addMemory` operation in a separate coroutine scope that
     * is not tied to the caller's lifecycle, making it suitable for background tasks.
     *
     * @param originalText The text content of the memory to be added.
     * @param checkDuplicates If true, performs a similarity search before adding.
     */
    fun addMemoryFireAndForget(originalText: String, checkDuplicates: Boolean = true) {
        ioScope.launch {
            try {
                val result = addMemory(originalText, checkDuplicates)
                Log.d("MemoryManager", "Fire-and-forget addMemory result=$result")
            } catch (e: Exception) {
                Log.e("MemoryManager", "Fire-and-forget addMemory error", e)
            }
        }
    }
    
    /**
     * Searches for the most relevant memories based on a text query.
     *
     * It generates an embedding for the query and then performs a cosine similarity search
     * against all stored memories.
     *
     * @param query The search query.
     * @param topK The maximum number of most similar memories to return.
     * @return A list of strings, where each string is the original text of a relevant memory.
     */
    suspend fun searchMemories(query: String, topK: Int = 3): List<String> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("MemoryManager", "Searching memories for query: ${query.take(100)}...")
                
                val queryEmbedding = EmbeddingService.generateEmbedding(
                    text = query,
                    taskType = "RETRIEVAL_QUERY"
                )
                
                if (queryEmbedding == null) {
                    Log.e("MemoryManager", "Failed to generate embedding for query")
                    return@withContext emptyList()
                }
                
                val allMemories = memoryDao.getAllMemoriesList()
                
                if (allMemories.isEmpty()) {
                    Log.d("MemoryManager", "No memories found in database")
                    return@withContext emptyList()
                }
                
                val similarities = allMemories.map { memory ->
                    val memoryEmbedding = parseEmbeddingFromJson(memory.embedding)
                    val similarity = calculateCosineSimilarity(queryEmbedding, memoryEmbedding)
                    Pair(memory.originalText, similarity)
                }.sortedByDescending { it.second }
                
                val topMemories = similarities.take(topK).map { it.first }
                Log.d("MemoryManager", "Found ${topMemories.size} relevant memories")
                return@withContext topMemories
                
            } catch (e: Exception) {
                Log.e("MemoryManager", "Error searching memories", e)
                return@withContext emptyList()
            }
        }
    }
    
    /**
     * Retrieves relevant memories and formats them into a string suitable for augmenting an LLM prompt.
     *
     * @param taskDescription The description of the current task, used as a query to find memories.
     * @return A string containing the formatted memories prepended to the original task description,
     *         or just the original task description if no relevant memories are found.
     */
    suspend fun getRelevantMemories(taskDescription: String): String {
        val relevantMemories = searchMemories(taskDescription, topK = 3)
        
        return if (relevantMemories.isNotEmpty()) {
            buildString {
                appendLine("--- Relevant Information ---")
                relevantMemories.forEach { memory ->
                    appendLine("- $memory")
                }
                appendLine()
                appendLine("--- My Task ---")
                appendLine(taskDescription)
            }
        } else {
            taskDescription
        }
    }
    
    /**
     * Gets the total number of memories in the database.
     * @return The total count of memories.
     */
    suspend fun getMemoryCount(): Int {
        return withContext(Dispatchers.IO) {
            memoryDao.getMemoryCount()
        }
    }
    
    /**
     * Retrieves all memories from the database as a list of [Memory] objects.
     * @return A list of all memories.
     */
    suspend fun getAllMemoriesList(): List<Memory> {
        return withContext(Dispatchers.IO) {
            memoryDao.getAllMemoriesList()
        }
    }
    
    /**
     * Deletes all memories from the database.
     */
    suspend fun clearAllMemories() {
        withContext(Dispatchers.IO) {
            memoryDao.deleteAllMemories()
            Log.d("MemoryManager", "All memories cleared")
        }
    }
    
    /**
     * Deletes a specific memory from the database by its ID.
     * @param id The ID of the memory to delete.
     * @return `true` if deletion was successful, `false` otherwise.
     */
    suspend fun deleteMemoryById(id: Long): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                memoryDao.deleteMemoryById(id)
                Log.d("MemoryManager", "Successfully deleted memory with ID: $id")
                true
            } catch (e: Exception) {
                Log.e("MemoryManager", "Error deleting memory with ID: $id", e)
                false
            }
        }
    }
    
    /**
     * Finds memories that are semantically similar to a given text.
     *
     * @param text The text to compare against.
     * @param similarityThreshold The cosine similarity score (0.0 to 1.0) above which a memory is considered similar.
     * @return A list of original text strings for memories that meet the similarity threshold.
     */
    suspend fun findSimilarMemories(text: String, similarityThreshold: Float = 0.8f): List<String> {
        return withContext(Dispatchers.IO) {
            try {
                val queryEmbedding = EmbeddingService.generateEmbedding(
                    text = text,
                    taskType = "RETRIEVAL_QUERY"
                )
                
                if (queryEmbedding == null) {
                    Log.e("MemoryManager", "Failed to generate embedding for similarity check")
                    return@withContext emptyList()
                }
                
                val allMemories = memoryDao.getAllMemoriesList()
                
                if (allMemories.isEmpty()) {
                    return@withContext emptyList()
                }
                
                val similarMemories = allMemories.mapNotNull { memory ->
                    val memoryEmbedding = parseEmbeddingFromJson(memory.embedding)
                    val similarity = calculateCosineSimilarity(queryEmbedding, memoryEmbedding)
                    
                    if (similarity >= similarityThreshold) {
                        Log.d("MemoryManager", "Found similar memory (similarity: $similarity): ${memory.originalText.take(50)}...")
                        memory.originalText
                    } else {
                        null
                    }
                }
                
                Log.d("MemoryManager", "Found ${similarMemories.size} similar memories with threshold $similarityThreshold")
                return@withContext similarMemories
                
            } catch (e: Exception) {
                Log.e("MemoryManager", "Error finding similar memories", e)
                return@withContext emptyList()
            }
        }
    }
    
    /**
     * Parses an embedding vector from its JSON string representation.
     * @param embeddingJson The JSON string to parse.
     * @return A list of floats representing the vector, or an empty list if parsing fails.
     */
    private fun parseEmbeddingFromJson(embeddingJson: String): List<Float> {
        return try {
            val jsonArray = JSONArray(embeddingJson)
            (0 until jsonArray.length()).map { i ->
                jsonArray.getDouble(i).toFloat()
            }
        } catch (e: Exception) {
            Log.e("MemoryManager", "Error parsing embedding JSON", e)
            emptyList()
        }
    }
    
    /**
     * Calculates the cosine similarity between two vectors.
     * @param vector1 The first vector.
     * @param vector2 The second vector.
     * @return The cosine similarity score, a float between 0.0 and 1.0. Returns 0f if vectors
     *         have different dimensions or if the denominator is zero.
     */
    private fun calculateCosineSimilarity(vector1: List<Float>, vector2: List<Float>): Float {
        if (vector1.size != vector2.size) {
            Log.w("MemoryManager", "Vector dimensions don't match: ${vector1.size} vs ${vector2.size}")
            return 0f
        }
        
        var dotProduct = 0f
        var norm1 = 0f
        var norm2 = 0f
        
        for (i in vector1.indices) {
            dotProduct += vector1[i] * vector2[i]
            norm1 += vector1[i] * vector1[i]
            norm2 += vector2[i] * vector2[i]
        }
        
        val denominator = kotlin.math.sqrt(norm1) * kotlin.math.sqrt(norm2)
        return if (denominator > 0) dotProduct / denominator else 0f
    }
    
    /**
     * Companion object for implementing the singleton pattern for the MemoryManager.
     */
    companion object {
        private var instance: MemoryManager? = null
        
        /**
         * Gets the singleton instance of the [MemoryManager].
         *
         * @param context The application context. Defaults to the global application context.
         * @return The singleton [MemoryManager] instance.
         */
        fun getInstance(context: Context = MyApplication.appContext): MemoryManager {
            return instance ?: synchronized(this) {
                instance ?: MemoryManager(context).also { instance = it }
            }
        }
    }
} 