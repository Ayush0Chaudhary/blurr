package com.blurr.voice.utilities

import android.annotation.SuppressLint
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

class VisualFeedbackManager private constructor(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    // --- Components ---
    private var audioWaveView: AudioWaveView? = null
    private var ttsVisualizer: TtsVisualizer? = null
    private var transcriptionView: TextView? = null
    private var inputBoxView: View? = null


    companion object {
        private const val TAG = "VisualFeedbackManager"

        @Volatile private var INSTANCE: VisualFeedbackManager? = null

        fun getInstance(context: Context): VisualFeedbackManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: VisualFeedbackManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    // --- TTS Wave Methods ---

    fun showTtsWave() {
        mainHandler.post {
            if (audioWaveView != null) {
                Log.d(TAG, "Audio wave is already showing.")
                return@post
            }
            setupAudioWaveEffect()
        }
    }

    fun hideTtsWave() {
        mainHandler.post {
            audioWaveView?.let {
                if (it.isAttachedToWindow) {
                    windowManager.removeView(it)
                    Log.d(TAG, "Audio wave view removed.")
                }
            }
            audioWaveView = null

            ttsVisualizer?.stop()
            ttsVisualizer = null
            TTSManager.getInstance(context).utteranceListener = null
            Log.d(TAG, "Audio wave effect has been torn down.")
        }
    }

    private fun setupAudioWaveEffect() {
        // Create and add the AudioWaveView
        audioWaveView = AudioWaveView(context)
        val heightInDp = 150
        val heightInPixels = (heightInDp * context.resources.displayMetrics.density).toInt()
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, heightInPixels,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,

        ).apply {
            gravity = Gravity.BOTTOM
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        windowManager.addView(audioWaveView, params)
        Log.d(TAG, "Audio wave view added.")

        // Link to TTSManager
        val ttsManager = TTSManager.getInstance(context)
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

    fun showTranscription(initialText: String = "Listening...") {
        if (transcriptionView != null) {
            updateTranscription(initialText) // Update text if already shown
            return
        }

        mainHandler.post {
            transcriptionView = TextView(context).apply {
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
                windowManager.addView(transcriptionView, params)
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
            transcriptionView?.let {
                if (it.isAttachedToWindow) {
                    try {
                        windowManager.removeView(it)
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
        onSubmit: (String) -> Unit
    ) {
        mainHandler.post {
            if (inputBoxView?.isAttachedToWindow == true) {
                // If already showing, just ensure focus
                inputBoxView?.findViewById<EditText>(R.id.overlayInputField)?.requestFocus()
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(inputBoxView?.findViewById(R.id.overlayInputField), InputMethodManager.SHOW_IMPLICIT)
                return@post
            }

            if (inputBoxView != null) {
                try { windowManager.removeView(inputBoxView) } catch (e: Exception) {}
            }

            val inflater = LayoutInflater.from(context)
            inputBoxView = inflater.inflate(R.layout.overlay_input_box, null)

            val inputField = inputBoxView?.findViewById<EditText>(R.id.overlayInputField)
            val rootLayout = inputBoxView?.findViewById<View>(R.id.overlayRootLayout)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                // You need it to be focusable to receive key events.
                // The absence of FLAG_NOT_FOCUSABLE makes it focusable by default.
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM
            }

            ViewCompat.setOnApplyWindowInsetsListener(inputBoxView!!) { view, insets ->
                val imeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
                val navigationBarHeight = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom

                // The view's vertical position (y) should be the keyboard's height
                // minus the navigation bar height, as the view is already avoiding the nav bar.
                params.y = imeHeight - navigationBarHeight

                // Apply the updated layout parameters
                if (view.isAttachedToWindow) {
                    windowManager.updateViewLayout(view, params)
                }

                WindowInsetsCompat.CONSUMED // Consume the insets
            }

            inputField?.setOnEditorActionListener { v, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    val inputText = v.text.toString().trim()
                    if (inputText.isNotEmpty()) {
                        onSubmit(inputText)
                        v.text = ""
                    } else {
                        // If empty, just hide the box
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
                    hideInputBox()
                    return@setOnTouchListener true
                }
                false
            }

            try {
                windowManager.addView(inputBoxView, params)
                // **IMPROVEMENT**: Explicitly request focus and show the keyboard
                inputField?.requestFocus()
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(inputField, InputMethodManager.SHOW_IMPLICIT)

            } catch (e: Exception) {
                Log.e("VisualManager", "Error adding input box view", e)
            }
        }
    }
    // --- REPLACE the hideInputBox method with this simplified version ---
    fun hideInputBox() {
        mainHandler.post {
            inputBoxView?.let {
                if (it.isAttachedToWindow) {
                    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(it.windowToken, 0)
                    windowManager.removeView(it)
                }
            }
            inputBoxView = null
        }
    }
}