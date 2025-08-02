package com.blurr.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Memory entity
 */
@Dao
interface MemoryDao {
    
    @Insert
    suspend fun insertMemory(memory: Memory): Long
    
    @Query("SELECT * FROM memories ORDER BY id DESC")
    fun getAllMemories(): Flow<List<Memory>>
    
    @Query("SELECT * FROM memories")
    suspend fun getAllMemoriesList(): List<Memory>
    
    @Query("SELECT * FROM memories WHERE id = :id")
    suspend fun getMemoryById(id: Long): Memory?
    
    @Delete
    suspend fun deleteMemory(memory: Memory)
    
    @Query("DELETE FROM memories")
    suspend fun deleteAllMemories()
    
    @Query("SELECT COUNT(*) FROM memories")
    suspend fun getMemoryCount(): Int
} 