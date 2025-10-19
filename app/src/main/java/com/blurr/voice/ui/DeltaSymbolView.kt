package com.blurr.voice.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.content.ContextCompat
import com.blurr.voice.R
import kotlin.math.sqrt

class DeltaSymbolView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var glowAnimator: ValueAnimator? = null
    // 1. Define stroke widths based on your request (0.3dp and 0.6dp)
    private val thinStrokeWidthPx = 0.75f * resources.displayMetrics.density
    private val thickStrokeWidthPx = 2f * resources.displayMetrics.density

    // Create three separate paint objects for each side
    private val leftPaint = createStrokePaint(thinStrokeWidthPx)
    private val bottomPaint = createStrokePaint(thinStrokeWidthPx)
    private val rightPaint = createStrokePaint(thickStrokeWidthPx)
    private val allPaints = listOf(leftPaint, bottomPaint, rightPaint)

    // Create three separate paths for each side
    private val leftPath = Path()
    private val bottomPath = Path()
    private val rightPath = Path()
    private val allPaths = listOf(leftPath, bottomPath, rightPath)

    private var currentGlowRadius = MIN_GLOW_RADIUS

    private companion object {
        const val ANIMATION_DURATION = 1500L
        const val MIN_GLOW_RADIUS = 10f
        const val MAX_GLOW_RADIUS = 30f
        const val START_SCALE = 1.0f
        const val END_SCALE = 1.05f
        // 2. Define a fixed side length for the equilateral triangle in DP
        const val SIDE_LENGTH_DP = 250f
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    private fun createStrokePaint(strokeWidth: Float): Paint {
        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth
            color = ContextCompat.getColor(context, R.color.delta_idle)
            // Use ROUND caps to make the corners where paths meet look better
            strokeCap = Paint.Cap.ROUND
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        // 3. Calculate triangle dimensions based on the fixed DP size
        val sideLengthPx = SIDE_LENGTH_DP * resources.displayMetrics.density
        val triangleHeight = sideLengthPx * sqrt(3f) / 2f

        // Center the fixed-size triangle within the view
        val centerX = w / 2f
        val centerY = h / 2f

        val topPoint = Pair(centerX, centerY - triangleHeight / 2f)
        val bottomLeftPoint = Pair(centerX - sideLengthPx / 2f, centerY + triangleHeight / 2f)
        val bottomRightPoint = Pair(centerX + sideLengthPx / 2f, centerY + triangleHeight / 2f)

        // Update the path for the left side
        leftPath.reset()
        leftPath.moveTo(topPoint.first, topPoint.second)
        leftPath.lineTo(bottomLeftPoint.first, bottomLeftPoint.second)

        // Update the path for the bottom side
        bottomPath.reset()
        bottomPath.moveTo(bottomLeftPoint.first, bottomLeftPoint.second)
        bottomPath.lineTo(bottomRightPoint.first, bottomRightPoint.second)

        // Update the path for the right side
        rightPath.reset()
        rightPath.moveTo(bottomRightPoint.first, bottomRightPoint.second)
        rightPath.lineTo(topPoint.first, topPoint.second)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // 4. Draw each side with its corresponding path and paint
        canvas.drawPath(leftPath, leftPaint)
        canvas.drawPath(bottomPath, bottomPaint)
        canvas.drawPath(rightPath, rightPaint)
    }

    fun setColor(color: Int) {
        allPaints.forEach { it.color = color }
        if (glowAnimator?.isRunning == true) {
            allPaints.forEach { it.setShadowLayer(currentGlowRadius, 0f, 0f, color) }
        }
        invalidate()
    }

    fun startGlow() {
        if (glowAnimator?.isRunning == true) {
            return
        }

        glowAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ANIMATION_DURATION
            interpolator = AccelerateDecelerateInterpolator()
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE

            addUpdateListener { animator ->
                val fraction = animator.animatedValue as Float
                val glowRadius = MIN_GLOW_RADIUS + (MAX_GLOW_RADIUS - MIN_GLOW_RADIUS) * fraction
                currentGlowRadius = glowRadius

                // Animate glow and scale on all three paints
                allPaints.forEach { it.setShadowLayer(glowRadius, 0f, 0f, it.color) }


                invalidate()
            }
        }
        glowAnimator?.start()
    }

    fun stopGlow() {
        glowAnimator?.cancel()
        glowAnimator = null

        // Reset all three paints
        allPaints.forEach { it.clearShadowLayer() }
        scaleX = 1.0f
        scaleY = 1.0f
        currentGlowRadius = MIN_GLOW_RADIUS
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopGlow()
    }
}

