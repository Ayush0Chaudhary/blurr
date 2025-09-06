package com.blurr.voice.intents

import android.content.Context
import android.util.Log

/**
 * Discovers and manages AppIntent implementations.
 * Convention: Put intent implementations under package com.blurr.voice.intents.impl
 */
object IntentRegistry {
    private const val TAG = "IntentRegistry"

    private val discovered: MutableMap<String, AppIntent> = linkedMapOf()
    @Volatile private var initialized = false

    @Synchronized
    @Suppress("UNUSED_PARAMETER")
    fun init(context: Context) {
        register(com.blurr.voice.intents.impl.DialIntent())
        initialized = true
    }
    fun register(intent: AppIntent) {
        val key = intent.name.trim()
        if (discovered.containsKey(key)) {
            Log.w(TAG, "Duplicate intent registration for name: ${intent.name}; overriding")
        }
        discovered[key] = intent
    }

    fun listIntents(context: Context): List<AppIntent> {
        if (!initialized) init(context)
        return discovered.values.toList()
    }

    fun findByName(context: Context, name: String): AppIntent? {
        if (!initialized) init(context)
        // exact match first, then case-insensitive
        discovered[name]?.let { return it }
        return discovered.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
    }
}

