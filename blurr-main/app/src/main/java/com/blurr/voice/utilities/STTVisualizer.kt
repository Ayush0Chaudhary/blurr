package com.blurr.voice.utilities

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.util.Log
import com.blurr.voice.AudioWaveView // CHANGED: Import the new wave view

class STTVisualizer(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    // CHANGED: The view is now an AudioWaveView
    private var visualizerView: AudioWaveView? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun show() {
        // Ensure all UI operations are on the main thread
        mainHandler.post {
            if (visualizerView != null) {
                return@post // Already showing
            }
            Log.d("STTVisualizer", "Showing visualizer")

            // CHANGED: Instantiate the new wave view
            visualizerView = AudioWaveView(context)

            // CHANGED: Layout parameters are adjusted for the wave view
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                300, // Give the view a fixed height (e.g., 300 pixels)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM // Position at the very bottom
                // y = 0 // No offset from the bottom
            }

            try {
                windowManager.addView(visualizerView, params)
            } catch (e: Exception) {
                Log.e("STTVisualizer", "Failed to add view. Do you have overlay permissions?", e)
            }
        }
    }

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
     * This function works without any changes because AudioWaveView
     * also has an updateAmplitude(rmsdB: Float) method.
     */
    fun onRmsChanged(rmsdB: Float) {
        mainHandler.post {
            visualizerView?.updateAmplitude(rmsdB)
        }
    }
}