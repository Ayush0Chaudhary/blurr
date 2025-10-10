package com.blurr.voice.utilities

import android.content.Context
import androidx.core.content.ContextCompat
import com.blurr.voice.R

/**
 * Utility class for mapping PandaState values to their corresponding colors
 * and providing state-related information for the delta symbol.
 */
object DeltaStateColorMapper {

    /**
     * Data class representing the visual state of the delta symbol
     */
    data class DeltaVisualState(
        val state: PandaState,
        val color: Int,
        val statusText: String,
        val colorHex: String
    )

    /**
     * Get the color resource ID for a given PandaState
     */
    fun getColorResourceId(state: PandaState): Int {
        return when (state) {
            PandaState.IDLE -> R.color.delta_idle
            PandaState.LISTENING -> R.color.delta_listening
            PandaState.PROCESSING -> R.color.delta_processing
            PandaState.SPEAKING -> R.color.delta_speaking
            PandaState.ERROR -> R.color.delta_error
        }
    }

    /**
     * Get the resolved color value for a given PandaState
     */
    fun getColor(context: Context, state: PandaState): Int {
        val colorResId = getColorResourceId(state)
        return ContextCompat.getColor(context, colorResId)
    }

    /**
     * Get the status text for a given PandaState
     */
    fun getStatusText(state: PandaState): String {
        return when (state) {
            PandaState.IDLE -> "Ready"
            PandaState.LISTENING -> "Listening..."
            PandaState.PROCESSING -> "Processing..."
            PandaState.SPEAKING -> "Speaking..."
            PandaState.ERROR -> "Error"
        }
    }

    /**
     * Get the hex color string for a given PandaState (for debugging/logging)
     */
    fun getColorHex(context: Context, state: PandaState): String {
        val color = getColor(context, state)
        return String.format("#%08X", color)
    }

    /**
     * Get complete visual state information for a given PandaState
     */
    fun getDeltaVisualState(context: Context, state: PandaState): DeltaVisualState {
        return DeltaVisualState(
            state = state,
            color = getColor(context, state),
            statusText = getStatusText(state),
            colorHex = getColorHex(context, state)
        )
    }

    /**
     * Get all available states with their visual information
     */
    fun getAllStates(context: Context): List<DeltaVisualState> {
        return PandaState.values().map { state ->
            getDeltaVisualState(context, state)
        }
    }

    /**
     * Check if a state represents an active operation (not idle or error)
     */
    fun isActiveState(state: PandaState): Boolean {
        return when (state) {
            PandaState.LISTENING, PandaState.PROCESSING, PandaState.SPEAKING -> true
            PandaState.IDLE, PandaState.ERROR -> false
        }
    }

    /**
     * Check if a state represents an error condition
     */
    fun isErrorState(state: PandaState): Boolean {
        return state == PandaState.ERROR
    }

    /**
     * Get the priority of a state for determining which state to display
     * when multiple conditions might be true. Higher numbers = higher priority.
     */
    fun getStatePriority(state: PandaState): Int {
        return when (state) {
            PandaState.ERROR -> 5      // Highest priority
            PandaState.SPEAKING -> 4   // High priority
            PandaState.LISTENING -> 3  // Medium-high priority
            PandaState.PROCESSING -> 2 // Medium priority
            PandaState.IDLE -> 1       // Lowest priority
        }
    }
}