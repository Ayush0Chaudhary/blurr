package com.blurr.voice.utilities

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.drawable.VectorDrawable
import android.util.Log
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.blurr.voice.R

/**
 * Utility class for animating the delta symbol with smooth color transitions
 * based on PandaState changes.
 */
class DeltaSymbolAnimator(private val context: Context) {

    companion object {
        private const val TAG = "DeltaSymbolAnimator"
        private const val ANIMATION_DURATION = 300L // 300ms for smooth transitions
    }

    private var currentAnimator: ValueAnimator? = null
    private var currentColor: Int = ContextCompat.getColor(context, R.color.delta_idle)

    /**
     * Animate the delta symbol to the color associated with the given state
     */
    fun animateToState(imageView: ImageView, state: PandaState) {
        val targetColor = getColorForState(state)
        animateColorTransition(imageView, currentColor, targetColor)
        currentColor = targetColor
    }

    /**
     * Set the delta symbol color immediately without animation
     */
    fun setStateColor(imageView: ImageView, state: PandaState) {
        val color = getColorForState(state)
        applyColorToImageView(imageView, color)
        currentColor = color
    }

    /**
     * Get the color associated with a specific PandaState
     */
    private fun getColorForState(state: PandaState): Int {
        return when (state) {
            PandaState.IDLE -> ContextCompat.getColor(context, R.color.delta_idle)
            PandaState.LISTENING -> ContextCompat.getColor(context, R.color.delta_listening)
            PandaState.PROCESSING -> ContextCompat.getColor(context, R.color.delta_processing)
            PandaState.SPEAKING -> ContextCompat.getColor(context, R.color.delta_speaking)
            PandaState.ERROR -> ContextCompat.getColor(context, R.color.delta_error)
        }
    }

    /**
     * Animate smooth color transition between two colors
     */
    private fun animateColorTransition(imageView: ImageView, fromColor: Int, toColor: Int) {
        // Cancel any existing animation
        currentAnimator?.cancel()

        if (fromColor == toColor) {
            Log.d(TAG, "Colors are the same, skipping animation")
            return
        }

        Log.d(TAG, "Animating color transition from ${Integer.toHexString(fromColor)} to ${Integer.toHexString(toColor)}")

        currentAnimator = ValueAnimator.ofObject(ArgbEvaluator(), fromColor, toColor).apply {
            duration = ANIMATION_DURATION
            
            addUpdateListener { animator ->
                val animatedColor = animator.animatedValue as Int
                applyColorToImageView(imageView, animatedColor)
            }
            
            start()
        }
    }

    /**
     * Apply color to the ImageView's drawable
     */
    private fun applyColorToImageView(imageView: ImageView, color: Int) {
        try {
            val drawable = imageView.drawable
            if (drawable is VectorDrawable) {
                drawable.setTint(color)
            } else {
                Log.w(TAG, "ImageView drawable is not a VectorDrawable, cannot apply tint")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error applying color to ImageView", e)
        }
    }

    /**
     * Clean up resources and cancel any running animations
     */
    fun cleanup() {
        currentAnimator?.cancel()
        currentAnimator = null
    }

    /**
     * Get the current color being displayed
     */
    fun getCurrentColor(): Int = currentColor
}