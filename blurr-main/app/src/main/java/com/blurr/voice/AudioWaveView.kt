package com.blurr.voice

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import androidx.core.graphics.toColorInt
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

class AudioWaveView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs) {

    // --- Configuration Constants ---
    companion object {
        // Added for converting raw audio dB to a normalized value
        private const val MIN_DB_VALUE = -60f
        private const val MAX_DB_VALUE = -5f
    }

    private val waveCount = 7
    private val minIdleAmplitude = 0.15f
    private val maxWaveHeightScale = 0.25f
    // Corrected and swapped durations for more logical behavior
    private val targetAmplitudeTransitionDuration = 500L
    private val realtimeAmplitudeTransitionDuration = 100L
    private val maxSpeedIncrease = 4.0f
    private val jitterAmount = 0.1f

    private val waveColors = intArrayOf(
        "#8A2BE2".toColorInt(), "#4169E1".toColorInt(), "#FF1493".toColorInt(),
        "#9370DB".toColorInt(), "#00BFFF".toColorInt(), "#FF69B4".toColorInt(),
        "#DA70D6".toColorInt()
    )

    private var amplitudeAnimator: ValueAnimator? = null
    private val wavePaints = mutableListOf<Paint>()
    private val wavePaths = mutableListOf<Path>()
    private val waveFrequencies: FloatArray
    private val wavePhaseShifts: FloatArray
    private val waveSpeeds: FloatArray
    private val waveAmplitudeMultipliers: FloatArray

    private var audioAmplitude = minIdleAmplitude

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)

        waveFrequencies = FloatArray(waveCount)
        wavePhaseShifts = FloatArray(waveCount)
        waveSpeeds = FloatArray(waveCount)
        waveAmplitudeMultipliers = FloatArray(waveCount)

        val blurFilter = BlurMaskFilter(15f, BlurMaskFilter.Blur.NORMAL)

        for (i in 0 until waveCount) {
            waveFrequencies[i] = Random.nextFloat() * 0.6f + 0.8f
            wavePhaseShifts[i] = Random.nextFloat() * (Math.PI * 2).toFloat()
            waveSpeeds[i] = Random.nextFloat() * 0.02f + 0.01f
            waveAmplitudeMultipliers[i] = Random.nextFloat() * 0.5f + 0.8f

            wavePaints.add(Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = waveColors[i % waveColors.size]
                alpha = 120
                maskFilter = blurFilter
            })
            wavePaths.add(Path())
        }

        ValueAnimator.ofFloat(0f, 1f).apply {
            interpolator = LinearInterpolator()
            duration = 5000
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                val speedFactor = 1.0f + (audioAmplitude * maxSpeedIncrease)
                for (i in 0 until waveCount) {
                    wavePhaseShifts[i] += (waveSpeeds[i] * speedFactor)
                }
                invalidate()
            }
            start()
        }
    }

    /**
     * NEW: Call this from your audio processing code with the raw dB value.
     * It normalizes the value and updates the wave animation in real-time.
     * @param rmsdB The Root Mean Square decibel level of the current audio buffer.
     */
    fun updateAmplitude(rmsdB: Float) {
        // Normalize the decibel level to a 0.0 to 1.0 range
        val normalizedAmplitude = ((rmsdB - MIN_DB_VALUE) / (MAX_DB_VALUE - MIN_DB_VALUE)).coerceIn(0f, 2.0f)

        // Call the existing method to update the wave's appearance
        setRealtimeAmplitude(normalizedAmplitude)
    }


    /**
     * Instantly sets the amplitude for real-time visualization.
     * @param amplitude The raw amplitude from the visualizer (0.0 to 1.0).
     */
    fun setRealtimeAmplitude(amplitude: Float) {
        val scaledAmplitude = amplitude.pow(1.5f).coerceIn(0.0f, 1.0f)
        val targetAmplitude = minIdleAmplitude + (scaledAmplitude * maxWaveHeightScale)
        amplitudeAnimator?.cancel()
        amplitudeAnimator = ValueAnimator.ofFloat(audioAmplitude, targetAmplitude).apply {
            duration = realtimeAmplitudeTransitionDuration // Use short duration for responsiveness
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animation ->
                audioAmplitude = animation.animatedValue as Float
            }
            start()
        }
    }

    /**
     * Smoothly animates the wave's amplitude to a new target level.
     * Used for non-realtime effects like a "power up" sequence.
     * @param target The target amplitude level (0.0f for idle, 1.0f for full).
     */
    fun setTargetAmplitude(target: Float) {
        val targetAmplitude = minIdleAmplitude + (target * maxWaveHeightScale)
        amplitudeAnimator?.cancel()
        amplitudeAnimator = ValueAnimator.ofFloat(audioAmplitude, targetAmplitude).apply {
            duration = targetAmplitudeTransitionDuration // Use longer duration for smooth transitions
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animation ->
                audioAmplitude = animation.animatedValue as Float
            }
            start()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        for(i in 0 until waveCount) {
            val paint = wavePaints[i]
            val color = waveColors[i % waveColors.size]
            paint.shader = LinearGradient(
                0f, h / 2f, 0f, h.toFloat(),
                color, Color.TRANSPARENT, Shader.TileMode.CLAMP
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        for (i in 0 until waveCount) {
            wavePaths[i].reset()
            wavePaths[i].moveTo(0f, height.toFloat())
            val waveMaxHeight = height * audioAmplitude * waveAmplitudeMultipliers[i]
            val currentJitter = (Random.nextFloat() - 0.5f) * waveMaxHeight * jitterAmount
            for (x in 0..width step 5) {
                val sineInput = (x * (Math.PI * 2 / width) * waveFrequencies[i]) + wavePhaseShifts[i]
                val sineOutput = (sin(sineInput) * 0.5f + 0.5f)
                val y = height - (waveMaxHeight * sineOutput) + currentJitter
                wavePaths[i].lineTo(x.toFloat(), y.toFloat())
            }
            wavePaths[i].lineTo(width.toFloat(), height.toFloat())
            wavePaths[i].close()
            canvas.drawPath(wavePaths[i], wavePaints[i])
        }
    }
}