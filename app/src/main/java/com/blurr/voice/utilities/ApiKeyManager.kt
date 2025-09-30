/**
 * @file ApiKeyManager.kt
 * @brief Provides a thread-safe manager for rotating through a list of API keys.
 *
 * This file contains the `ApiKeyManager` singleton object, which is responsible for
 * managing and distributing API keys in a round-robin fashion. This is useful for
 * distributing API load across multiple keys.
 */
package com.blurr.voice.utilities

import com.blurr.voice.BuildConfig
import java.util.concurrent.atomic.AtomicInteger

/**
 * A thread-safe, singleton object to manage and rotate a list of API keys.
 *
 * This manager loads a comma-separated list of API keys from the `BuildConfig` and provides
 * a method (`getNextKey`) to retrieve them one by one in a circular sequence.
 * This ensures that different parts of the app can request a key without needing to
 * manage the rotation logic themselves.
 */
object ApiKeyManager {

    /**
     * The list of API keys loaded from `BuildConfig.GEMINI_API_KEYS`.
     * The string is split by commas to create the list.
     */
    private val apiKeys: List<String> = if (BuildConfig.GEMINI_API_KEYS.isNotEmpty()) {
        BuildConfig.GEMINI_API_KEYS.split(",")
    } else {
        emptyList()
    }

    /**
     * An atomic integer to keep track of the current index in the `apiKeys` list,
     * ensuring thread-safe increments.
     */
    private val currentIndex = AtomicInteger(0)

    /**
     * Gets the next API key from the list in a circular, round-robin fashion.
     *
     * This method is thread-safe. It will throw an [IllegalStateException] if the list
     * of API keys is empty.
     *
     * @return The next API key as a String.
     * @throws IllegalStateException if no API keys are configured.
     */
    fun getNextKey(): String {
        if (apiKeys.isEmpty()) {
            throw IllegalStateException("API key list is empty. Please add keys to local.properties.")
        }
        val index = currentIndex.getAndIncrement() % apiKeys.size
        return apiKeys[index]
    }
}