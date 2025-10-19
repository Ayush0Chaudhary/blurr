package com.blurr.voice.utilities

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import androidx.core.view.ViewCompat
import com.blurr.voice.AudioWaveView
import com.blurr.voice.R
import com.blurr.voice.ui.SmallDeltaGlowView

class VisualFeedbackManager private constructor(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    // --- Components ---
    private var audioWaveView: AudioWaveView? = null
    private var ttsVisualizer: TtsVisualizer? = null
    private var transcriptionView: TextView? = null
    private var inputBoxView: View? = null
    private var thinkingIndicatorView: View? = null
    private var smallDeltaGlowView: SmallDeltaGlowView? = null
    private val pandaStateManager by lazy { PandaStateManager.getInstance(context) }
    private val stateChangeListener: (PandaState) -> Unit
    private var speakingOverlay: View? = null

    init {
        stateChangeListener = { newState ->
            updateSmallDeltaVisuals(newState)
        }
        pandaStateManager.addStateChangeListener(stateChangeListener)

    }

    companion object {
        private const val TAG = "VisualFeedbackManager"

        @Volatile private var INSTANCE: VisualFeedbackManager? = null

        fun getInstance(context: Context): VisualFeedbackManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: VisualFeedbackManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    /**
     * Check if the app has permission to draw overlays
     */
    private fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
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
            hideSpeakingOverlay()

            Log.d(TAG, "Audio wave effect has been torn down.")
        }
    }

    private fun setupAudioWaveEffect() {
        // Check if we have overlay permission before attempting to add views
        if (!hasOverlayPermission()) {
            Log.e(TAG, "Cannot setup audio wave effect: SYSTEM_ALERT_WINDOW permission not granted")
            return
        }

        try {
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
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up audio wave effect", e)
            // Clean up if something went wrong
            audioWaveView = null
            ttsVisualizer = null
        }
    }

    fun showSpeakingOverlay() {
        mainHandler.post {
            if (speakingOverlay != null) return@post

            if (!hasOverlayPermission()) {
                Log.e(TAG, "Cannot show speaking overlay: SYSTEM_ALERT_WINDOW permission not granted")
                return@post
            }

            speakingOverlay = View(context).apply {
                // Reduced opacity from 80 (50%) to 40 (25%) for a more subtle overlay
                setBackgroundColor(0x40FFFFFF.toInt())
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            )

            try {
                windowManager.addView(speakingOverlay, params)
                Log.d(TAG, "Speaking overlay added.")
            } catch (e: Exception) {
                Log.e(TAG, "Error adding speaking overlay", e)
                speakingOverlay = null
            }
        }
    }


    fun showTranscription(initialText: String = "Listening...") {
        if (transcriptionView != null) {
            updateTranscription(initialText) // Update text if already shown
            return
        }

        mainHandler.post {
            if (!hasOverlayPermission()) {
                Log.e(TAG, "Cannot show transcription: SYSTEM_ALERT_WINDOW permission not granted")
                return@post
            }

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
        onSubmit: (String) -> Unit,
        onOutsideTap: () -> Unit
    ) {
        // This method creates an overlay input box that appears over other apps
        // Key fix: Proper keyboard positioning using WindowInsetsCompat to prevent
        // the input box from being hidden behind the keyboard when it appears
        mainHandler.post {
            if (inputBoxView?.isAttachedToWindow == true) {
                // If already showing, just ensure focus
                inputBoxView?.findViewById<EditText>(R.id.overlayInputField)?.requestFocus()
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(inputBoxView?.findViewById(R.id.overlayInputField), InputMethodManager.SHOW_IMPLICIT)
                return@post
            }

            if (!hasOverlayPermission()) {
                Log.e(TAG, "Cannot show input box: SYSTEM_ALERT_WINDOW permission not granted")
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
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP
                // Top margin with sufficient space
                y = (80 * context.resources.displayMetrics.density).toInt() // 80dp top margin
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
                    Log.d(TAG, "Outside touch detected.")
                    onOutsideTap() // Use the new callback
                    return@setOnTouchListener true
                }
                false
            }

            try {
                windowManager.addView(inputBoxView, params)
                Log.d(TAG, "Input box added with initial y position: ${params.y}")
                
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
    fun hideSpeakingOverlay() {
        mainHandler.post {
            speakingOverlay?.let {
                if (it.isAttachedToWindow) {
                    try {
                        windowManager.removeView(it)
                        Log.d(TAG, "Speaking overlay removed.")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error removing speaking overlay", e)
                    }
                }
            }
            speakingOverlay = null
        }
    }
    // --- Thinking indicator (replace existing methods with these) ---
    fun showThinkingIndicator(initialText: String = "Thinking...") {
        if (thinkingIndicatorView != null) {
            updateThinking(initialText)
            return
        }

        mainHandler.post {
            if (!hasOverlayPermission()) {
                Log.e(TAG, "Cannot show thinking indicator: SYSTEM_ALERT_WINDOW permission not granted")
                return@post
            }

            // If a previous instance exists on window manager, try to remove it silently
            thinkingIndicatorView?.let {
                try { if (it.isAttachedToWindow) windowManager.removeView(it) } catch (_: Exception) {}
            }

            // Build a TextView similar to the transcription view so it looks consistent
            val textView = TextView(context).apply {
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
                // Optional: higher elevation on supported API levels
                ViewCompat.setElevation(this, 12f)
            }

            thinkingIndicatorView = textView

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                // Place it above the wave and near center (adjust y as needed)
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                y = 320 // tweak dp offset by multiplying with density if you want to use dp
                // If you want the same y calculation in dp:
                // y = (320 * context.resources.displayMetrics.density).toInt()
            }

            try {
                windowManager.addView(textView, params)
                Log.d(TAG, "Thinking indicator added.")
            } catch (e: Exception) {
                Log.e(TAG, "Error adding thinking indicator", e)
                thinkingIndicatorView = null
            }
        }
    }

    fun updateThinking(text: String) {
        mainHandler.post {
            (thinkingIndicatorView as? TextView)?.text = text
        }
    }

    fun hideThinkingIndicator() {
        mainHandler.post {
            thinkingIndicatorView?.let { view ->
                if (view.isAttachedToWindow) {
                    try {
                        windowManager.removeView(view)
                        Log.d(TAG, "Thinking indicator removed.")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error removing thinking indicator", e)
                    }
                }
            }
            thinkingIndicatorView = null
        }
    }

    fun showSmallDeltaGlow() {
        mainHandler.post {
            if (smallDeltaGlowView?.isAttachedToWindow == true) return@post

            if (!hasOverlayPermission()) {
                Log.e(TAG, "Cannot show small delta glow: SYSTEM_ALERT_WINDOW permission not granted")
                return@post
            }

            smallDeltaGlowView = SmallDeltaGlowView(context)
            val sizeInDp = 64
            val sizeInPixels = (sizeInDp * context.resources.displayMetrics.density).toInt()
            val marginBottomInDp = 48
            val marginBottomInPixels = (marginBottomInDp * context.resources.displayMetrics.density).toInt()

            val params = WindowManager.LayoutParams(
                sizeInPixels, sizeInPixels,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                y = marginBottomInPixels
            }

            try {
                windowManager.addView(smallDeltaGlowView, params)
                // Set the initial color and glow based on the *current* state
                updateSmallDeltaVisuals(pandaStateManager.getCurrentState())
                Log.d(TAG, "Small delta glow view added.")
            } catch (e: Exception) {
                Log.e(TAG, "Error adding small delta glow view", e)
                smallDeltaGlowView = null
            }
        }
    }

    fun hideSmallDeltaGlow() {
        mainHandler.post {
            smallDeltaGlowView?.let {
                it.stopGlow()
                if (it.isAttachedToWindow) {
                    try {
                        windowManager.removeView(it)
                        Log.d(TAG, "Small delta glow view removed.")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error removing small delta glow view", e)
                    }
                }
            }
            smallDeltaGlowView = null
        }
    }

    /**
     * A new private method to update the small delta's appearance based on the app state.
     */
    private fun updateSmallDeltaVisuals(state: PandaState) {
        // Run on the main thread and only if the view exists
        mainHandler.post {
            smallDeltaGlowView?.let { view ->
                // 1. Get the color for the new state
                val color = DeltaStateColorMapper.getColor(context, state)
                view.setColor(color)

                // 2. Start or stop the glow based on whether the state is "active"
                if (DeltaStateColorMapper.isActiveState(state)) {
                    view.startGlow()
                } else {
                    view.stopGlow()
                }
            }
        }
    }


}