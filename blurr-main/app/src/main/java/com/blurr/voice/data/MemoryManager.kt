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
 * Manager class for handling memory operations with embeddings
 */
class MemoryManager(private val context: Context) {
    
    private val database = AppDatabase.getDatabase(context)
    private val memoryDao = database.memoryDao()
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    /**
     * Add a new memory with embedding, checking for duplicates first
     */
    suspend fun addMemory(originalText: String, checkDuplicates: Boolean = true): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("MemoryManager", "Adding memory: ${originalText.take(100)}...")
                
                if (checkDuplicates) {
                    // Check for similar existing memories first
                    val similarMemories = findSimilarMemories(originalText, similarityThreshold = 0.85f)
                    if (similarMemories.isNotEmpty()) {
                        Log.d("MemoryManager", "Found ${similarMemories.size} similar memories, skipping duplicate")
                        return@withContext true // Return true since we're avoiding a duplicate
                    }
                }
                
                // Generate embedding for the text
                val embedding = EmbeddingService.generateEmbedding(
                    text = originalText,
                    taskType = "RETRIEVAL_DOCUMENT"
                )
                
                if (embedding == null) {
                    Log.e("MemoryManager", "Failed to generate embedding for text")
                    return@withContext false
                }
                
                // Convert embedding to JSON string for storage
                val embeddingJson = JSONArray(embedding).toString()
                
                // Create memory entity
                val memory = Memory(
                    originalText = originalText,
                    embedding = embeddingJson
                )
                
                // Save to database
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
     * Fire-and-forget version of addMemory that is not tied to an Activity scope.
     * Uses an internal SupervisorJob so it won't be cancelled when a caller finishes.
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
     * Search for relevant memories based on a query
     */
    suspend fun searchMemories(query: String, topK: Int = 3): List<String> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("MemoryManager", "Searching memories for query: ${query.take(100)}...")
                
                // Generate embedding for the query
                val queryEmbedding = EmbeddingService.generateEmbedding(
                    text = query,
                    taskType = "RETRIEVAL_QUERY"
                )
                
                if (queryEmbedding == null) {
                    Log.e("MemoryManager", "Failed to generate embedding for query")
                    return@withContext emptyList()
                }
                
                // Get all memories from database
                val allMemories = memoryDao.getAllMemoriesList()
                
                if (allMemories.isEmpty()) {
                    Log.d("MemoryManager", "No memories found in database")
                    return@withContext emptyList()
                }
                
                // Calculate similarities and find top matches
                val similarities = allMemories.map { memory ->
                    val memoryEmbedding = parseEmbeddingFromJson(memory.embedding)
                    val similarity = calculateCosineSimilarity(queryEmbedding, memoryEmbedding)
                    Pair(memory.originalText, similarity)
                }.sortedByDescending { it.second }
                
                // Return top K memories
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
     * Get relevant memories for a task and format them for prompt augmentation
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
            // If no relevant memories, just return the original task
            taskDescription
        }
    }
    
    /**
     * Get memory count
     */
    suspend fun getMemoryCount(): Int {
        return withContext(Dispatchers.IO) {
            memoryDao.getMemoryCount()
        }
    }
    
    /**
     * Get all memories as a list
     */
    suspend fun getAllMemoriesList(): List<Memory> {
        return withContext(Dispatchers.IO) {
            memoryDao.getAllMemoriesList()
        }
    }
    
    /**
     * Delete all memories
     */
    suspend fun clearAllMemories() {
        withContext(Dispatchers.IO) {
            memoryDao.deleteAllMemories()
            Log.d("MemoryManager", "All memories cleared")
        }
    }
    
    /**
     * Delete a specific memory by ID
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
     * Find memories similar to the given text
     */
    suspend fun findSimilarMemories(text: String, similarityThreshold: Float = 0.8f): List<String> {
        return withContext(Dispatchers.IO) {
            try {
                // Generate embedding for the query text
                val queryEmbedding = EmbeddingService.generateEmbedding(
                    text = text,
                    taskType = "RETRIEVAL_QUERY"
                )
                
                if (queryEmbedding == null) {
                    Log.e("MemoryManager", "Failed to generate embedding for similarity check")
                    return@withContext emptyList()
                }
                
                // Get all memories from database
                val allMemories = memoryDao.getAllMemoriesList()
                
                if (allMemories.isEmpty()) {
                    return@withContext emptyList()
                }
                
                // Calculate similarities and find similar memories
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
     * Parse embedding from JSON string
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
     * Calculate cosine similarity between two vectors
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
    
    companion object {
        private var instance: MemoryManager? = null
        
        fun getInstance(context: Context = MyApplication.appContext): MemoryManager {
            return instance ?: synchronized(this) {
                instance ?: MemoryManager(context).also { instance = it }
            }
        }
    }
} 