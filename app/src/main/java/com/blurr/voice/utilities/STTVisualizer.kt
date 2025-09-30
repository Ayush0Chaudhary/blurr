/**
 * @file STTVisualizer.kt
 * @brief Manages the display of a real-time audio visualizer for Speech-to-Text.
 *
 * This file contains the `STTVisualizer` class, which is responsible for creating and
 * managing an overlay view that visualizes the microphone's audio input level (RMS dB)
 * during speech recognition.
 */
package com.blurr.voice.utilities

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.util.Log
import com.blurr.voice.AudioWaveView

/**
 * Manages the display of a system overlay to visualize STT audio input.
 *
 * This class uses the `WindowManager` to add and remove an [AudioWaveView] on top of
 * other applications. This provides real-time visual feedback to the user that the
 * microphone is actively listening. It requires the "Draw over other apps" permission.
 *
 * @param context The application context.
 */
class STTVisualizer(private val context: Context) {

    /** The `WindowManager` system service instance for managing overlay views. */
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    /** The custom view that displays the audio waveform. */
    private var visualizerView: AudioWaveView? = null
    /** A handler to ensure all UI operations are performed on the main thread. */
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Creates and displays the visualizer overlay at the bottom of the screen.
     *
     * This method must be called on the main thread. It will do nothing if the
     * visualizer is already visible.
     */
    fun show() {
        mainHandler.post {
            if (visualizerView != null) {
                return@post
            }
            Log.d("STTVisualizer", "Showing visualizer")

            visualizerView = AudioWaveView(context)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                300, // Fixed height for the view
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM
            }

            try {
                windowManager.addView(visualizerView, params)
            } catch (e: Exception) {
                Log.e("STTVisualizer", "Failed to add view. Do you have overlay permissions?", e)
            }
        }
    }

    /**
     * Removes the visualizer overlay from the screen.
     *
     * This method must be called on the main thread.
     */
    fun hide() {
        mainHandler.post {
            visualizerView?.let {
                if (it.isAttachedToWindow) {
                    Log.d("STTVisualizer", "Hiding visualizer")
                    windowManager.removeView(it)
                }
            }
            visualizerView = null
        }
    }

    /**
     * Updates the amplitude of the waveform in the visualizer.
     *
     * This method is called by the `STTManager` with the new RMS dB value from the
     * `SpeechRecognizer`.
     *
     * @param rmsdB The new root mean square decibel value of the audio input.
     */
    fun onRmsChanged(rmsdB: Float) {
        mainHandler.post {
            visualizerView?.updateAmplitude(rmsdB)
        }
    }
}