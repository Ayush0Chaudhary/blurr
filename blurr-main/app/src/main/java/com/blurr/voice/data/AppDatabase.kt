package com.blurr.voice.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room database for storing memories
 */
@Database(entities = [Memory::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun memoryDao(): MemoryDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "blurr_memory_database"
                )
                .fallbackToDestructiveMigration() // This will recreate the database if schema changes
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
} 