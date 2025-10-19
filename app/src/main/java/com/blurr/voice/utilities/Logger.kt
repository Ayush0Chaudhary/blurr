package com.blurr.voice.utilities

import android.util.Log
import com.blurr.voice.BuildConfig

object Logger {
    private const val DEFAULT_TAG = "BlurrVoice"
    private const val UI_DEBUG_TAG = "PandaUI-Debug"

    // Enable logging based on build configuration
    private val isLoggingEnabled = BuildConfig.ENABLE_LOGGING

    fun d(tag: String = DEFAULT_TAG, message: String) {
        if (isLoggingEnabled) {
            Log.d(tag, message)
        }
    }

    fun i(tag: String = DEFAULT_TAG, message: String) {
        if (isLoggingEnabled) {
            Log.i(tag, message)
        }
    }

    fun w(tag: String = DEFAULT_TAG, message: String) {
        if (isLoggingEnabled) {
            Log.w(tag, message)
        }
    }

    fun e(tag: String = DEFAULT_TAG, message: String, throwable: Throwable? = null) {
        if (isLoggingEnabled) {
            if (throwable != null) {
                Log.e(tag, message, throwable)
            } else {
                Log.e(tag, message)
            }
        }
    }

    fun v(tag: String = DEFAULT_TAG, message: String) {
        if (isLoggingEnabled) {
            Log.v(tag, message)
        }
    }

    fun wtf(tag: String = DEFAULT_TAG, message: String, throwable: Throwable? = null) {
        if (isLoggingEnabled) {
            if (throwable != null) {
                Log.wtf(tag, message, throwable)
            } else {
                Log.wtf(tag, message)
            }
        }
    }

    /**
     * Special logger for UI debugging to keep logs clean and easy to filter.
     */
    fun ui(message: String) {
        if (isLoggingEnabled) {
            Log.d(UI_DEBUG_TAG, message)
        }
    }
}