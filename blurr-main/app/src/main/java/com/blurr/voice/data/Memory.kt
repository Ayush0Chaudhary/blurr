package com.blurr.voice.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Memory entity for storing user information with embeddings
 */
@Entity(tableName = "memories")
data class Memory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val originalText: String,
    val embedding: String, // Stored as JSON string of numbers
    val timestamp: Long = System.currentTimeMillis()
) 