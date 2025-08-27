package com.blurr.voice.api

import com.blurr.voice.BuildConfig
import java.util.concurrent.atomic.AtomicInteger

/**
 * A thread-safe, singleton object to manage and rotate a list of API keys.
 * This ensures that every part of the app gets the next key in the sequence.
 */
object ApiKeyManager {

    private val apiKeys: List<String> = if (BuildConfig.GEMINI_API_KEYS.isNotEmpty()) {
        BuildConfig.GEMINI_API_KEYS.split(",")
    } else {
        emptyList()
    }

    private val currentIndex = AtomicInteger(0)

    /**
     * Gets the next API key from the list in a circular, round-robin fashion.
     * @return The next API key as a String.
     */
    fun getNextKey(): String {
        if (apiKeys.isEmpty()) {
            throw IllegalStateException("API key list is empty. Please add keys to ApiKeyManager.")
        }
        // Get the current index, then increment it for the next call.
        // The modulo operator (%) makes it loop back to 0 when it reaches the end.
        val index = currentIndex.getAndIncrement() % apiKeys.size
        return apiKeys[index]
    }
}