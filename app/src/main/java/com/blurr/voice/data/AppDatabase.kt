/**
 * @file AppDatabase.kt
 * @brief Defines the Room database for the application.
 *
 * This file contains the `AppDatabase` class, which serves as the main access point
 * to the persisted data. It uses the Room Persistence Library to manage an SQLite
 * database for storing `Memory` entities.
 */
package com.blurr.voice.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The main Room database for the application, responsible for storing memories.
 *
 * This abstract class extends [RoomDatabase] and serves as the database holder.
 * It is annotated with [@Database] to define the entities it contains and other
 * database configurations. The database uses a singleton pattern to ensure only one
 * instance is ever created.
 *
 * @see Memory
 * @see MemoryDao
 */
@Database(entities = [Memory::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    
    /**
     * Provides an abstract method to get the Data Access Object (DAO) for memories.
     *
     * Room will generate the implementation for this method.
     *
     * @return The [MemoryDao] for database operations on the `Memory` table.
     */
    abstract fun memoryDao(): MemoryDao
    
    /**
     * Companion object to provide access to the singleton database instance.
     */
    companion object {
        /**
         * The volatile singleton instance of the [AppDatabase].
         *
         * A volatile variable is used to ensure that changes to this field are immediately
         * visible to all other threads.
         */
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        /**
         * Gets the singleton instance of the [AppDatabase].
         *
         * This function uses a synchronized block to ensure thread-safe creation of the
         * database instance. If the instance already exists, it is returned; otherwise,
         * a new database instance is created and returned.
         *
         * @param context The application context, used to create the database instance.
         * @return The singleton [AppDatabase] instance.
         */
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "blurr_memory_database"
                )
                // This migration strategy will destroy and re-create the database on a schema change.
                // It is simple but results in data loss. Not suitable for production with critical data.
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
} 