/**
 * @file GlowBorderView.kt
 * @brief A custom view that renders an animated, glowing, multi-colored border.
 *
 * This file contains the `GlowBorderView` class, a decorative component used to draw attention
 * or indicate an active state.
 */
package com.blurr.voice

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.toColorInt

/**
 * A custom `View` that draws a glowing, multi-colored border around its bounds.
 *
 * This view uses a `SweepGradient` to create a "rainbow" effect that flows around the
 * border of a rounded rectangle. A `BlurMaskFilter` is applied to the paint to give the
 * border a soft, glowing appearance. The gradient's rotation can be updated externally,
 * typically via a `ValueAnimator`, to create a continuous spinning animation.
 */
class GlowBorderView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs) {

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 16f
        maskFilter = BlurMaskFilter(30f, BlurMaskFilter.Blur.NORMAL)
    }

    // A looping array of colors for the gradient. The first color is repeated at the end
    // to ensure a seamless transition when the gradient rotates.
    private val gradientColors = intArrayOf(
        "#FF0000".toColorInt(), // Red
        "#FF7F00".toColorInt(), // Orange
        "#0000FF".toColorInt(), // Blue
        "#9400D3".toColorInt(), // Violet
        "#FF0000".toColorInt()  // Red again to close the loop smoothly
    )

    private val matrix = Matrix()
    private var rotationAngle = 0f

    init {
        // Software layer is needed for the BlurMaskFilter to work correctly.
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    /**
     * Called when the view's size changes. This is the ideal place to create the
     * `SweepGradient` shader, as it depends on the view's center coordinates.
     */
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, h)

        // Passing 'null' for the positions tells the gradient to spread the colors evenly.
        borderPaint.shader = SweepGradient(
            w / 2f,
            h / 2f,
            gradientColors,
            null
        )
    }

    /**
     * The core drawing method. It applies the current rotation to the gradient's matrix
     * and then draws the glowing, rounded-rectangle border onto the canvas.
     */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        matrix.setRotate(rotationAngle, width / 2f, height / 2f)
        borderPaint.shader.setLocalMatrix(matrix)

        canvas.drawRoundRect(
            borderPaint.strokeWidth / 2,
            borderPaint.strokeWidth / 2,
            width.toFloat() - borderPaint.strokeWidth / 2,
            height.toFloat() - borderPaint.strokeWidth / 2,
            60f, // cornerRadiusX
            60f, // cornerRadiusY
            borderPaint
        )
    }

    /**
     * Sets the rotation angle of the gradient.
     * Call this method repeatedly (e.g., from a `ValueAnimator`) to create a spinning animation.
     * @param angle The new rotation angle in degrees.
     */
    override fun setRotation(angle: Float) {
        this.rotationAngle = angle
        invalidate() // Trigger a redraw with the new rotation.
    }

    /**
     * Sets the alpha (transparency) of the glow effect.
     * @param alpha The alpha value, from 0 (fully transparent) to 255 (fully opaque).
     */
    fun setGlowAlpha(alpha: Int) {
        borderPaint.alpha = alpha.coerceIn(0, 255)
        invalidate()
    }
}