/**
 * @file MemoryDao.kt
 * @brief Defines the Data Access Object (DAO) for the Memory entity.
 *
 * This file contains the `MemoryDao` interface, which provides the methods that the rest of
 * the app uses to interact with data in the "memories" table. Room generates the implementation
 * of this DAO at compile time.
 */
package com.blurr.voice.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) for the [Memory] entity.
 *
 * This interface defines the database interactions for the `memories` table, including
 * insertion, retrieval, deletion, and counting of memory records. All methods are
 * suspend functions or return a [Flow] to ensure they are called off the main thread.
 */
@Dao
interface MemoryDao {
    
    /**
     * Inserts a new memory into the database.
     *
     * @param memory The [Memory] object to insert.
     * @return The row ID of the newly inserted memory.
     */
    @Insert
    suspend fun insertMemory(memory: Memory): Long
    
    /**
     * Retrieves all memories from the database, ordered by timestamp in descending order.
     *
     * This method returns a [Flow], which allows the UI to observe changes to the data
     * and automatically update when the database content changes.
     *
     * @return A [Flow] emitting a list of all [Memory] objects.
     */
    @Query("SELECT * FROM memories ORDER BY timestamp DESC")
    fun getAllMemories(): Flow<List<Memory>>
    
    /**
     * Retrieves all memories from the database as a simple list.
     *
     * This is a one-shot query that gets a snapshot of all memories at the time of the call.
     *
     * @return A [List] of all [Memory] objects.
     */
    @Query("SELECT * FROM memories ORDER BY timestamp DESC")
    suspend fun getAllMemoriesList(): List<Memory>
    
    /**
     * Retrieves a single memory by its unique ID.
     *
     * @param id The ID of the memory to retrieve.
     * @return The [Memory] object if found, otherwise null.
     */
    @Query("SELECT * FROM memories WHERE id = :id")
    suspend fun getMemoryById(id: Long): Memory?
    
    /**
     * Deletes a specific memory from the database.
     *
     * @param memory The [Memory] object to delete.
     */
    @Delete
    suspend fun deleteMemory(memory: Memory)
    
    /**
     * Deletes a memory from the database by its unique ID.
     *
     * @param id The ID of the memory to delete.
     */
    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun deleteMemoryById(id: Long)
    
    /**
     * Deletes all memories from the database.
     *
     * Use this function with caution as it will permanently remove all stored memories.
     */
    @Query("DELETE FROM memories")
    suspend fun deleteAllMemories()
    
    /**
     * Gets the total number of memories currently stored in the database.
     *
     * @return An integer representing the total count of memories.
     */
    @Query("SELECT COUNT(*) FROM memories")
    suspend fun getMemoryCount(): Int
} 