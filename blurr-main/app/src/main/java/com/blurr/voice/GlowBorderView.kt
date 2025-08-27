package com.blurr.voice

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.toColorInt

class GlowBorderView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs) {

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 16f
        maskFilter = BlurMaskFilter(30f, BlurMaskFilter.Blur.NORMAL)
    }

    // --- THIS IS THE SECTION THAT HAS CHANGED ---

    // 1. We're back to a simple, looping array of colors.
    // The first color (Red) is repeated at the end to create a seamless loop.
    private val gradientColors = intArrayOf(
        "#FF0000".toColorInt(), // Red
        "#FF7F00".toColorInt(), // Orange
//        "#FFFF00".toColorInt(), // Yellow
//        "#00FF00".toColorInt(), // Green
        "#0000FF".toColorInt(), // Blue
//        "#4B0082".toColorInt(), // Indigo
        "#9400D3".toColorInt(), // Violet
        "#FF0000".toColorInt()  // Red again to close the loop smoothly
    )

    // 2. The 'cometPositions' array has been completely removed.

    // --- END OF THE CHANGED SECTION ---

    private val matrix = Matrix()
    private var rotationAngle = 0f

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, h)

        // Update the shader to use the full gradient.
        // We pass 'null' for the positions, which tells the gradient
        // to spread the colors evenly around the circle.
        borderPaint.shader = SweepGradient(
            w / 2f,
            h / 2f,
            gradientColors,
            null // This is the key change!
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        matrix.setRotate(rotationAngle, width / 2f, height / 2f)
        borderPaint.shader.setLocalMatrix(matrix)

        canvas.drawRoundRect(
            borderPaint.strokeWidth / 2,
            borderPaint.strokeWidth / 2,
            width.toFloat() - borderPaint.strokeWidth / 2,
            height.toFloat() - borderPaint.strokeWidth / 2,
            60f,
            60f,
            borderPaint
        )
    }

    override fun setRotation(angle: Float) {
        this.rotationAngle = angle
        invalidate()
    }

    fun setGlowAlpha(alpha: Int) {
        borderPaint.alpha = alpha.coerceIn(0, 255)
        invalidate()
    }
}