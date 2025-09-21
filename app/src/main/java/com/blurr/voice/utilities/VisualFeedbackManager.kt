package com.blurr.voice.utilities

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.blurr.voice.AudioWaveView
import com.blurr.voice.R

class VisualFeedbackManager private constructor() {

    private var currentActivity: Activity? = null
    private var windowManager: WindowManager? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    // --- Components ---
    private var audioWaveView: AudioWaveView? = null
    private var ttsVisualizer: TtsVisualizer? = null
    private var transcriptionView: TextView? = null
    private var inputBoxView: View? = null

    private var speakingOverlay: View? = null

    companion object {
        private const val TAG = "VisualFeedbackManager"

        @Volatile private var INSTANCE: VisualFeedbackManager? = null

        fun getInstance(): VisualFeedbackManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: VisualFeedbackManager().also { INSTANCE = it }
            }
        }
    }

    private fun updateActivity(activity: Activity) {
        currentActivity = activity
        windowManager = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }


    // --- TTS Wave Methods ---

    fun showTtsWave() {
        mainHandler.post {
            val activity = ActivityLifecycleManager.getCurrentActivity()
            if (activity == null) {
                Log.e(TAG, "Cannot show TTS wave, no activity is available.")
                return@post
            }
            updateActivity(activity)

            if (audioWaveView != null) {
                Log.d(TAG, "Audio wave is already showing.")
                return@post
            }
            setupAudioWaveEffect()
        }
    }

    fun hideTtsWave() {
        mainHandler.post {
            if (windowManager == null || currentActivity == null) {
                Log.e(TAG, "Cannot hide TTS wave, window manager not available.")
                return@post
            }
            audioWaveView?.let {
                if (it.isAttachedToWindow) {
                    windowManager?.removeView(it)
                    Log.d(TAG, "Audio wave view removed.")
                }
            }
            audioWaveView = null

            ttsVisualizer?.stop()
            ttsVisualizer = null
            TTSManager.getInstance(currentActivity!!).utteranceListener = null
            hideSpeakingOverlay()

            Log.d(TAG, "Audio wave effect has been torn down.")
        }
    }

    private fun setupAudioWaveEffect() {
        if (windowManager == null || currentActivity == null) {
            Log.e(TAG, "Cannot setup audio wave effect, window manager not available.")
            return
        }
        // Create and add the AudioWaveView
        audioWaveView = AudioWaveView(currentActivity)
        val heightInDp = 150
        val heightInPixels = (heightInDp * currentActivity!!.resources.displayMetrics.density).toInt()
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, heightInPixels,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,

        ).apply {
            gravity = Gravity.BOTTOM
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        windowManager?.addView(audioWaveView, params)
        Log.d(TAG, "Audio wave view added.")

        // Link to TTSManager
        val ttsManager = TTSManager.getInstance(currentActivity!!)
        val audioSessionId = ttsManager.getAudioSessionId()

        if (audioSessionId == 0) {
            Log.e(TAG, "Failed to get valid audio session ID. Visualizer not started.")
            return
        }

        ttsVisualizer = TtsVisualizer(audioSessionId) { normalizedAmplitude ->
            mainHandler.post {
                audioWaveView?.setRealtimeAmplitude(normalizedAmplitude)
            }
        }

        ttsManager.utteranceListener = { isSpeaking ->
            mainHandler.post {
                if (isSpeaking) {
                    audioWaveView?.setTargetAmplitude(0.2f)
                    ttsVisualizer?.start()
                } else {
                    ttsVisualizer?.stop()
                    audioWaveView?.setTargetAmplitude(0.0f)
                }
            }
        }
        Log.d(TAG, "Audio wave effect has been set up.")
    }

    fun showSpeakingOverlay() {
        mainHandler.post {
            val activity = ActivityLifecycleManager.getCurrentActivity() ?: return@post
            updateActivity(activity)
            if (speakingOverlay != null) return@post

            speakingOverlay = View(currentActivity).apply {
                // CHANGED: Increased opacity from 80 (50%) to E6 (90%) for a more solid feel.
                // You can adjust this hex value (E6) to your liking.
                setBackgroundColor(0x80FFFFFF.toInt())
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            )

            try {
                windowManager?.addView(speakingOverlay, params)
                Log.d(TAG, "Speaking overlay added.")
            } catch (e: Exception) {
                Log.e(TAG, "Error adding speaking overlay", e)
            }
        }
    }


    fun showTranscription(initialText: String = "Listening...") {
        mainHandler.post {
            val activity = ActivityLifecycleManager.getCurrentActivity() ?: return@post
            updateActivity(activity)

            if (transcriptionView != null) {
                updateTranscription(initialText) // Update text if already shown
                return@post
            }

            transcriptionView = TextView(currentActivity).apply {
                text = initialText
                val glassBackground = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(0xDD0D0D2E.toInt(), 0xDD2A0D45.toInt())
                ).apply {
                    cornerRadius = 28f
                    setStroke(1, 0x80FFFFFF.toInt())
                }
                background = glassBackground
                setTextColor(0xFFE0E0E0.toInt())
                textSize = 16f
                setPadding(40, 24, 40, 24)
                typeface = Typeface.MONOSPACE
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                y = 250 // Position it above the wave view
            }

            try {
                windowManager?.addView(transcriptionView, params)
                Log.d(TAG, "Transcription view added.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add transcription view.", e)
                transcriptionView = null
            }
        }
    }

    fun updateTranscription(text: String) {
        mainHandler.post {
            transcriptionView?.text = text
        }
    }

    fun hideTranscription() {
        mainHandler.post {
            if (windowManager == null) return@post
            transcriptionView?.let {
                if (it.isAttachedToWindow) {
                    try {
                        windowManager?.removeView(it)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error removing transcription view.", e)
                    }
                }
            }
            transcriptionView = null
        }
    }


    @SuppressLint("ClickableViewAccessibility")
    fun showInputBox(
        onActivated: () -> Unit,
        onSubmit: (String) -> Unit,
        onOutsideTap: () -> Unit
    ) {
        mainHandler.post {
            val activity = ActivityLifecycleManager.getCurrentActivity() ?: return@post
            updateActivity(activity)

            if (inputBoxView?.isAttachedToWindow == true) {
                inputBoxView?.findViewById<EditText>(R.id.overlayInputField)?.requestFocus()
                val imm = currentActivity?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(inputBoxView?.findViewById(R.id.overlayInputField), InputMethodManager.SHOW_IMPLICIT)
                return@post
            }

            if (inputBoxView != null) {
                try { windowManager?.removeView(inputBoxView) } catch (e: Exception) {}
            }

            val inflater = LayoutInflater.from(currentActivity)
            inputBoxView = inflater.inflate(R.layout.overlay_input_box, null)

            val inputField = inputBoxView?.findViewById<EditText>(R.id.overlayInputField)
            val rootLayout = inputBoxView?.findViewById<View>(R.id.overlayRootLayout)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP
                y = (80 * currentActivity!!.resources.displayMetrics.density).toInt()
            }

            inputField?.setOnEditorActionListener { v, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    val inputText = v.text.toString().trim()
                    if (inputText.isNotEmpty()) {
                        onSubmit(inputText)
                        v.text = ""
                    } else {
                        hideInputBox()
                    }
                    true
                } else {
                    false
                }
            }

            inputField?.setOnTouchListener { _, _ ->
                onActivated()
                false
            }

            rootLayout?.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_OUTSIDE) {
                    Log.d(TAG, "Outside touch detected.")
                    onOutsideTap()
                    return@setOnTouchListener true
                }
                false
            }

            try {
                windowManager?.addView(inputBoxView, params)
                Log.d(TAG, "Input box added with initial y position: ${params.y}")
                
                inputField?.requestFocus()
                val imm = currentActivity?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(inputField, InputMethodManager.SHOW_IMPLICIT)

            } catch (e: Exception) {
                Log.e("VisualManager", "Error adding input box view", e)
            }
        }
    }

    fun hideInputBox() {
        mainHandler.post {
            if (windowManager == null || currentActivity == null) return@post
            inputBoxView?.let {
                if (it.isAttachedToWindow) {
                    val imm = currentActivity?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(it.windowToken, 0)
                    windowManager?.removeView(it)
                }
            }
            inputBoxView = null
        }
    }

    fun hideSpeakingOverlay() {
        mainHandler.post {
            if (windowManager == null) return@post
            speakingOverlay?.let {
                if (it.isAttachedToWindow) {
                    try {
                        windowManager?.removeView(it)
                        Log.d(TAG, "Speaking overlay removed.")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error removing speaking overlay", e)
                    }
                }
            }
            speakingOverlay = null
        }
    }
}