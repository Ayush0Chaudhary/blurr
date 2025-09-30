/**
 * @file OnboardingManager.kt
 * @brief Manages the state of the user onboarding process.
 *
 * This file contains the `OnboardingManager` class, which uses SharedPreferences to
 * track whether the user has completed the initial onboarding flow.
 */
package com.blurr.voice.utilities

import android.content.Context
import android.content.SharedPreferences

/**
 * A utility class to manage the state of the user onboarding process.
 *
 * This class provides simple methods to check if onboarding has been completed and to
 * set its completion status, persisting the state using [SharedPreferences].
 *
 * @param context The application context, used to access SharedPreferences.
 */
class OnboardingManager(context: Context) {

    /** The SharedPreferences instance used for storing the onboarding state. */
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("onboarding_prefs", Context.MODE_PRIVATE)

    /**
     * Companion object for constants.
     */
    companion object {
        /** The key used to store the onboarding completion status in SharedPreferences. */
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
    }

    /**
     * Checks if the user has completed the onboarding process.
     *
     * @return `true` if onboarding is marked as completed, `false` otherwise.
     */
    fun isOnboardingCompleted(): Boolean {
        return sharedPreferences.getBoolean(KEY_ONBOARDING_COMPLETED, false)
    }

    /**
     * Sets the onboarding completion status.
     *
     * @param completed `true` to mark onboarding as completed, `false` to reset it.
     */
    fun setOnboardingCompleted(completed: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply()
    }
}