/**
 * @file IntentRegistry.kt
 * @brief Manages the discovery and registration of available AppIntents.
 *
 * This file contains the `IntentRegistry` singleton object, which serves as a central
 * repository for all `AppIntent` implementations within the application. It allows for
 * finding and listing intents that the agent can use.
 */
package com.blurr.voice.intents

import android.content.Context
import android.util.Log
import com.blurr.voice.intents.impl.DialIntent
import com.blurr.voice.intents.impl.EmailComposeIntent
import com.blurr.voice.intents.impl.ShareTextIntent
import com.blurr.voice.intents.impl.ViewUrlIntent

/**
 * A singleton object that discovers and manages all available [AppIntent] implementations.
 *
 * This registry is responsible for providing a list of all known intents and allowing
 * lookup by name. It uses a simple manual registration process in its `init` block.
 *
 * Convention: For organization, all intent implementations should be placed under the
 * `com.blurr.voice.intents.impl` package.
 */
object IntentRegistry {
    private const val TAG = "IntentRegistry"

    /** A map to store the registered intents, with the intent name as the key. */
    private val discovered: MutableMap<String, AppIntent> = linkedMapOf()
    /** A flag to ensure the registry is initialized only once. */
    @Volatile private var initialized = false

    /**
     * Initializes the registry by manually registering all known [AppIntent] implementations.
     *
     * This method is synchronized to prevent race conditions during initialization. It should
     * be called once before any other methods are used.
     *
     * @param context The application context (currently unused but kept for future compatibility).
     */
    @Synchronized
    @Suppress("UNUSED_PARAMETER")
    fun init(context: Context) {
        if (initialized) return
        register(DialIntent())
        register(ViewUrlIntent())
        register(ShareTextIntent())
        register(EmailComposeIntent())
        initialized = true
    }

    /**
     * Registers a single [AppIntent] instance with the registry.
     *
     * If an intent with the same name is already registered, it will be overwritten, and a
     * warning will be logged.
     *
     * @param intent The instance of the [AppIntent] to register.
     */
    fun register(intent: AppIntent) {
        val key = intent.name.trim()
        if (discovered.containsKey(key)) {
            Log.w(TAG, "Duplicate intent registration for name: ${intent.name}; overriding")
        }
        discovered[key] = intent
    }

    /**
     * Returns a list of all registered intents.
     *
     * It ensures the registry is initialized before returning the list.
     *
     * @param context The application context, used for initialization if needed.
     * @return A list of all registered [AppIntent] objects.
     */
    fun listIntents(context: Context): List<AppIntent> {
        if (!initialized) init(context)
        return discovered.values.toList()
    }

    /**
     * Finds a registered intent by its unique name.
     *
     * The lookup is case-sensitive first for an exact match, then falls back to a
     * case-insensitive search. It ensures the registry is initialized before performing the search.
     *
     * @param context The application context, used for initialization if needed.
     * @param name The name of the intent to find.
     * @return The corresponding [AppIntent] if found, otherwise null.
     */
    fun findByName(context: Context, name: String): AppIntent? {
        if (!initialized) init(context)
        // Exact match first
        discovered[name]?.let { return it }
        // Fallback to case-insensitive match
        return discovered.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
    }
}

