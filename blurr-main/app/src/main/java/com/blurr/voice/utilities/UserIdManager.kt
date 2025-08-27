package com.blurr.voice.utilities

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID
import androidx.core.content.edit

/**
 * A manager class to handle the creation and retrieval of a persistent,
 * unique user ID for the application.
 *
 * It uses SharedPreferences to store the ID, ensuring the same user is
 * recognized across app sessions.
 *
 * @param context The application context, used to access SharedPreferences.
 */
class UserIdManager(context: Context) {

    companion object {
        // The name of our preferences file.
        private const val PREFS_NAME = "AppUserPrefs"
        // The key under which the user ID is stored.
        private const val KEY_USER_ID = "user_id"
    }

    // Initialize SharedPreferences instance.
    // MODE_PRIVATE means the file can only be accessed by the calling application.
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Retrieves the existing unique user ID. If one does not exist (i.e., it's the
     * first time the user has opened the app), it creates a new unique ID, saves it
     * for future use, and then returns it.
     *
     * @return A unique String identifier for the user.
     */
    fun getOrCreateUserId(): String {
        // Try to retrieve the existing user ID. If it's not found, 'null' is returned.
        val existingId = sharedPreferences.getString(KEY_USER_ID, null)

        return if (existingId != null) {
            // If the ID exists, we're done. Return it.
            println("UserIdManager: Existing user ID found: $existingId")
            existingId
        } else {
            // If the ID is null, this is the first launch.
            // 1. Generate a new, random, and unique ID.
            val newId = UUID.randomUUID().toString()
            println("UserIdManager: No existing ID found. Creating new ID: $newId")

            // 2. Save the new ID to SharedPreferences for future launches.
            // We use .apply() which saves the data asynchronously in the background.
            sharedPreferences.edit { putString(KEY_USER_ID, newId) }

            // 3. Return the new ID.
            newId
        }
    }

    /**
     * A helper method to demonstrate retrieving the ID without creating one.
     * @return The stored user ID, or null if it has not been created yet.
     */
    fun retrieveUserId(): String? {
        return sharedPreferences.getString(KEY_USER_ID, null)
    }
}
