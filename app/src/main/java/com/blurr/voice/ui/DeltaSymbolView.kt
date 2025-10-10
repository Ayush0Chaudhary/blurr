package com.blurr.voice.ui

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.blurr.voice.R
import com.blurr.voice.utilities.DeltaStateColorMapper
import com.blurr.voice.utilities.DeltaSymbolAnimator
import com.blurr.voice.utilities.PandaState

/**
 * Custom view that displays the delta symbol with status text and handles
 * state-based color animations.
 */
class DeltaSymbolView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "DeltaSymbolView"
    }

    private val deltaImageView: ImageView
    private val statusTextView: TextView
    private val animator: DeltaSymbolAnimator

    private var currentState: PandaState = PandaState.IDLE

    init {
        orientation = VERTICAL
        gravity = android.view.Gravity.CENTER_HORIZONTAL

        // Create and configure the delta symbol ImageView
        deltaImageView = ImageView(context).apply {
            layoutParams = LayoutParams(
                resources.getDimensionPixelSize(R.dimen.delta_symbol_size),
                resources.getDimensionPixelSize(R.dimen.delta_symbol_size)
            )
            setImageResource(R.drawable.ic_delta_large)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }

        // Create and configure the status text TextView
        statusTextView = TextView(context).apply {
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = resources.getDimensionPixelSize(R.dimen.status_text_margin_top)
            }
            textSize = 16f
            setTextColor(ContextCompat.getColor(context, R.color.white))
            gravity = android.view.Gravity.CENTER
            text = DeltaStateColorMapper.getStatusText(PandaState.IDLE)
        }

        // Add views to the layout
        addView(deltaImageView)
        addView(statusTextView)

        // Initialize animator
        animator = DeltaSymbolAnimator(context)

        // Set initial state
        setStateImmediate(PandaState.IDLE)
    }

    /**
     * Update the delta symbol to reflect the given state with animation
     */
    fun setState(state: PandaState, animate: Boolean = true) {
        if (state == currentState) {
            Log.d(TAG, "State unchanged: $state")
            return
        }

        Log.d(TAG, "Updating state from $currentState to $state")
        currentState = state

        // Update status text
        statusTextView.text = DeltaStateColorMapper.getStatusText(state)

        // Update color with or without animation
        if (animate) {
            animator.animateToState(deltaImageView, state)
        } else {
            animator.setStateColor(deltaImageView, state)
        }
    }

    /**
     * Set the state immediately without animation
     */
    fun setStateImmediate(state: PandaState) {
        setState(state, animate = false)
    }

    /**
     * Get the current state
     */
    fun getCurrentState(): PandaState = currentState

    /**
     * Get the current visual state information
     */
    fun getCurrentVisualState(): DeltaStateColorMapper.DeltaVisualState {
        return DeltaStateColorMapper.getDeltaVisualState(context, currentState)
    }

    /**
     * Set custom status text (overrides default state text)
     */
    fun setCustomStatusText(text: String) {
        statusTextView.text = text
    }

    /**
     * Reset status text to the default for current state
     */
    fun resetStatusText() {
        statusTextView.text = DeltaStateColorMapper.getStatusText(currentState)
    }

    /**
     * Clean up resources when the view is detached
     */
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator.cleanup()
    }

    /**
     * Get the delta ImageView for direct access if needed
     */
    fun getDeltaImageView(): ImageView = deltaImageView

    /**
     * Get the status TextView for direct access if needed
     */
    fun getStatusTextView(): TextView = statusTextView
}