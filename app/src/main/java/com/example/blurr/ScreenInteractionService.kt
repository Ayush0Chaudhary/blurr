package com.example.blurr

import android.R
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.util.Xml
import android.view.Choreographer
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.example.blurr.crawler.SemanticParser
import com.example.blurr.utilities.TTSManager
import com.example.blurr.utilities.TtsVisualizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlSerializer
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.StringReader
import java.io.StringWriter
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException


// Data class to hold structured information about a parsed UI element
private data class SimplifiedElement(
    val description: String,
    val bounds: Rect,
    val center: Point,
    val isClickable: Boolean,
    val className: String
)

class ScreenInteractionService : AccessibilityService() {

    companion object {
        var instance: ScreenInteractionService? = null
        const val ACTION_SCREENSHOT_TAKEN = "com.example.accessiblity_service_test.SCREENSHOT_TAKEN"
        // A flag to easily toggle the debug taps on or off
        const val DEBUG_SHOW_TAPS = false
        // A flag to easily toggle the debug bounding boxes on or off
        const val DEBUG_SHOW_BOUNDING_BOXES = false
    }

    // Add these properties to your service/activity class
    private var rotationSpeedAnimator: ValueAnimator? = null
    private var currentRotationSpeedFactor: Float = 0f // Will be animated between 0.0f and 1.0f
    private val MAX_ROTATION_SPEED = 2.5f // Adjust this value to make the max rotation faster or slower
    private val ANIMATION_DURATION_MS = 1500L // 1.5 seconds for acceleration/deceleration

    private var windowManager: WindowManager? = null

    private var ttsVisualizer: TtsVisualizer? = null

    private var audioWaveView: AudioWaveView? = null
    private var glowingBorderView: GlowBorderView? = null



    // RENAME rotationSpeedAnimator to masterAnimator for clarity
    private var masterAnimator: ValueAnimator? = null
    // NEW: Animator to handle speed transitions
    private var speedAnimator: ValueAnimator? = null

    // We will animate this value between the speeds defined below
    private var currentRotationSpeed = 0f
    private var currentRotationAngle = 0f

    // --- DEFINE YOUR SPEEDS HERE ---
    private  val BASE_SPEED = 60.0f  // Slow speed when idle
    private  val FAST_SPEED = 10.0f  // Fast speed when speaking
    private  val SPEED_TRANSITION_DURATION_MS = 1500L // How long it takes to speed up/down


    private val executor = Executors.newSingleThreadExecutor()
    private var statusBarHeight = -1


    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        this.windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        Log.d("InteractionService", "Accessibility Service connected.")
//        setupGlowEffect()
        setupAudioWaveEffect()
//        setupWaveBorderEffect()
    }
    /**
     * Gets the package name of the app currently in the foreground.
     * @return The package name as a String, or null if not available.
     */
    fun getForegroundAppPackageName(): String? {
        // The rootInActiveWindow property holds the node info for the current screen,
        // which includes the package name.
        return rootInActiveWindow?.packageName?.toString()
    }


// In ScreenInteractionService.kt

// REPLACE your existing 'setupGlowEffect' with this new version
    /**
     * Shows the border, starts the animation engine at base speed,
     * and sets up a listener to change speed when TTS is active.
     */
    private fun setupGlowEffect() {
        // 1. Show the border on screen
        showGlowingBorder()

        // 2. Start the animation engine
        initializeRotationEngine()

        // 3. Set the initial speed to the slow, base speed
        setSpeed(BASE_SPEED)

        // 4. Get the TTS manager instance
        val ttsManager = TTSManager.getInstance(this)

        val mainHandler = Handler(Looper.getMainLooper())

// ...

        ttsManager.utteranceListener = { isSpeaking ->
            // Post the logic to the main thread
            mainHandler.post {
                if (isSpeaking) {
                    Log.d("GlowEffect", "TTS Speaking: Setting FAST_SPEED")
                    setSpeed(FAST_SPEED)
                } else {
                    Log.d("GlowEffect", "TTS Stopped: Setting BASE_SPEED")
                    setSpeed(BASE_SPEED)
                }
            }
        }
    }

    /**
     * Smoothly animates the rotation speed to a new target value.
     * @param targetSpeed The speed to transition to (e.g., BASE_SPEED or FAST_SPEED).
     */
    private fun setSpeed(targetSpeed: Float) {
        speedAnimator?.cancel() // Cancel any ongoing speed change
        speedAnimator = ValueAnimator.ofFloat(currentRotationSpeed, targetSpeed).apply {
            duration = SPEED_TRANSITION_DURATION_MS
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                // This updates the speed value on every frame of the transition
                currentRotationSpeed = animator.animatedValue as Float
            }
            start()
        }
    }
    /**
     * NEW: Creates and displays the glowing border overlay.
     */
    private fun showGlowingBorder() {
        if (!Settings.canDrawOverlays(this)) {
            Log.w("InteractionService", "Cannot show glowing border: 'Draw over other apps' permission not granted.")
            return
        }
        if (glowingBorderView != null) return // Avoid adding multiple views

        glowingBorderView = GlowBorderView(this)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, // Use accessibility overlay
            // Flags to make the overlay non-interactive (lets touches pass through)
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        // UI operations must be on the main thread
        Handler(Looper.getMainLooper()).post {
            windowManager?.addView(glowingBorderView, params)
            Log.d("InteractionService", "Glowing border added.")
        }
    }

    /**
     * NEW: Animates the glow based on voice amplitude.
     * @param amplitude Normalized voice volume (0.0f to 1.0f).
     */
    /**
     * Animates the glow based on voice amplitude and animated rotation speed.
     * @param amplitude Normalized voice volume (0.0f to 1.0f).
     */
    private fun updateGlowAnimation(amplitude: Float) {
        glowingBorderView?.let { view ->
            // 1. Alpha pulsing based on volume (this logic remains the same)
            val targetAlpha = (80 + 175 * amplitude).toInt()
            view.setGlowAlpha(targetAlpha)

            // 2. Rotation based on the smoothly animated speed factor
            // The rotation amount is now driven by our ValueAnimator, not the amplitude
            val rotationAmount = currentRotationSpeedFactor * MAX_ROTATION_SPEED
            currentRotationAngle = (currentRotationAngle + rotationAmount) % 360
            view.setRotation(currentRotationAngle)
        }
    }

    /**
     * Starts the master animation loop. This loop runs forever and uses the
     * 'currentRotationSpeed' variable to update the border's angle.
     */
    private fun initializeRotationEngine() {
        masterAnimator?.cancel()
        masterAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 10000L // Duration doesn't matter much, it's just a continuous ticker
            interpolator = android.view.animation.LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE

            addUpdateListener {
                // This is the core engine, running on every frame
                currentRotationAngle = (currentRotationAngle + currentRotationSpeed) % 360
                glowingBorderView?.setRotation(currentRotationAngle)
            }
            start()
        }
    }

    /**
     * NEW: Hides and cleans up the glowing border view.
     */
    private fun hideGlowingBorder() {
        Handler(Looper.getMainLooper()).post {
            glowingBorderView?.let { windowManager?.removeView(it) }
            glowingBorderView = null
        }
    }


    /**
     * Shows a temporary visual indicator on the screen for debugging taps.
     */
    private fun showDebugTap(tapX: Float, tapY: Float) {
        if (!Settings.canDrawOverlays(this)) {
            Log.w("InteractionService", "Cannot show debug tap: 'Draw over other apps' permission not granted.")
            return
        }

        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val overlayView = ImageView(this)

        val tapIndicator = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0x80FF0000.toInt()) // Semi-transparent red
            setSize(100, 100)
            setStroke(4, 0xFFFF0000.toInt()) // Solid red border
        }
        overlayView.setImageDrawable(tapIndicator)

        val params = WindowManager.LayoutParams(
            100, 100,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = tapX.toInt() - 50
            y = tapY.toInt() - 50
        }

        Handler(Looper.getMainLooper()).post {
            try {
                windowManager.addView(overlayView, params)
                Handler(Looper.getMainLooper()).postDelayed({
                    if (overlayView.isAttachedToWindow) windowManager.removeView(overlayView)
                }, 500L)
            } catch (e: Exception) {
                Log.e("InteractionService", "Failed to add debug tap view", e)
            }
        }
    }

    /**
     * Draws labeled bounding boxes for each simplified element on the screen.
     */
    private fun drawDebugBoundingBoxes(elements: List<SimplifiedElement>) {
        if (!Settings.canDrawOverlays(this)) {
            Log.w("InteractionService", "Cannot draw bounding boxes: 'Draw over other apps' permission not granted.")
            return
        }

        // Calculate status bar height once
        if (statusBarHeight < 0) {
            val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
            statusBarHeight = if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
        }

        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val viewsToRemove = mutableListOf<View>()
        val mainHandler = Handler(Looper.getMainLooper())

        mainHandler.post {
            elements.forEach { element ->
                try {
                    // Create the border view
                    val boxView = View(this).apply {
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            val color = if (element.isClickable) 0xFF00FF00.toInt() else 0xFFFFFF00.toInt()
                            setStroke(4, color)
                        }
                    }
                    val boxParams = WindowManager.LayoutParams(
                        element.bounds.width(), element.bounds.height(),
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                        PixelFormat.TRANSLUCENT
                    ).apply {
                        gravity = Gravity.TOP or Gravity.START
                        x = element.bounds.left
                        // CORRECTED: Subtract status bar height for accurate positioning
                        y = element.bounds.top - statusBarHeight
                    }
                    windowManager.addView(boxView, boxParams)
                    viewsToRemove.add(boxView)

                    // Create the label view
                    val labelView = TextView(this).apply {
                        text = element.description
                        setBackgroundColor(0xAA000000.toInt())
                        setTextColor(0xFFFFFFFF.toInt())
                        textSize = 10f
                        setPadding(4, 2, 4, 2)
                    }
                    val labelParams = WindowManager.LayoutParams(
                        WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                        PixelFormat.TRANSLUCENT
                    ).apply {
                        gravity = Gravity.TOP or Gravity.START
                        x = element.bounds.left
                        // CORRECTED: Subtract status bar height and offset from the top
                        y = (element.bounds.top - 35).coerceAtLeast(0) - statusBarHeight
                    }
                    windowManager.addView(labelView, labelParams)
                    viewsToRemove.add(labelView)

                } catch (e: Exception) {
                    Log.e("InteractionService", "Failed to add debug bounding box view for element: ${element.description}", e)
                }
            }

            mainHandler.postDelayed({
                viewsToRemove.forEach { view ->
                    if (view.isAttachedToWindow) windowManager.removeView(view)
                }
            }, 10000L)
        }
    }


    /**
     * UPDATED: Parses the raw XML into a de-duplicated, structured list of simplified elements.
     */
    private fun parseXmlToSimplifiedElements(xmlString: String): List<SimplifiedElement> {
        val allElements = mutableListOf<SimplifiedElement>()
        try {
            val parser: XmlPullParser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(StringReader(xmlString))

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "node") {
                    val boundsString = parser.getAttributeValue(null, "bounds")
                    val bounds = try {
                        val numbers = boundsString?.replace(Regex("[\\[\\]]"), ",")?.split(",")?.filter { it.isNotEmpty() }
                        if (numbers?.size == 4) Rect(numbers[0].toInt(), numbers[1].toInt(), numbers[2].toInt(), numbers[3].toInt()) else Rect()
                    } catch (e: Exception) { Rect() }

                    if (bounds.width() <= 0 || bounds.height() <= 0) {
                        eventType = parser.next()
                        continue
                    }

                    val isClickable = parser.getAttributeValue(null, "clickable") == "true"
                    val text = parser.getAttributeValue(null, "text")
                    val contentDesc = parser.getAttributeValue(null, "content-desc")
                    val resourceId = parser.getAttributeValue(null, "resource-id")
                    val className = parser.getAttributeValue(null, "class") ?: "Element"

                    if (isClickable || !text.isNullOrEmpty() || (contentDesc != null && contentDesc != "null" && contentDesc.isNotEmpty())) {
                        val description = when {
                            !contentDesc.isNullOrEmpty() && contentDesc != "null" -> contentDesc
                            !text.isNullOrEmpty() -> text
                            !resourceId.isNullOrEmpty() -> resourceId.substringAfterLast('/')
                            else -> ""
                        }
                        if (description.isNotEmpty()) {
                            val center = Point(bounds.centerX(), bounds.centerY())
                            allElements.add(SimplifiedElement(description, bounds, center, isClickable, className))
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.e("InteractionService", "Error parsing XML for simplified elements", e)
        }

//        // --- De-duplication Logic ---
//        val filteredElements = mutableListOf<SimplifiedElement>()
//        val claimedAreas = mutableListOf<Rect>()
//
//        // Process larger elements first to claim their space
//        allElements.sortedByDescending { it.bounds.width() * it.bounds.height() }.forEach { element ->
//            // Check if the element's center is already within a claimed area
//            val isContained = claimedAreas.any { claimedRect ->
//                claimedRect.contains(element.center.x, element.center.y)
//            }
//
//            if (!isContained) {
//                filteredElements.add(element)
//                // Only clickable containers should claim space to prevent them from hiding their children
//                if (element.isClickable) {
//                    claimedAreas.add(element.bounds)
//                }
//            }
//        }

        // Return the filtered list, sorted by top-to-bottom, left-to-right position
//        return filteredElements.sortedWith(compareBy({ it.bounds.top }, { it.bounds.left }))
        return allElements
    }


    /**
     * Formats the structured list of elements into a single string for the LLM.
     */
    private fun formatElementsForLlm(elements: List<SimplifiedElement>): String {
        if (elements.isEmpty()) {
            return "No interactable or textual elements found on the screen."
        }
        val elementStrings = elements.map {
            val action = if (it.isClickable) "Action: Clickable" else "Action: Not-Clickable (Text only)"
            val elementType = it.className.substringAfterLast('.')
            // Use the center point in the output string
            "- $elementType: \"${it.description}\" | $action | Center: (${it.center.x}, ${it.center.y})"
        }
        return "Interactable Screen Elements:\n" + elementStrings.joinToString("\n")
    }

    suspend fun dumpWindowHierarchy(): String {
        return withContext(Dispatchers.Default) {
            val rootNode = rootInActiveWindow ?: run {
                Log.e("InteractionService", "Root node is null, cannot dump hierarchy.")
                return@withContext "Error: UI hierarchy is not available."
            }

            val stringWriter = StringWriter()
            try {
                val serializer: XmlSerializer = Xml.newSerializer()
                serializer.setOutput(stringWriter)
                serializer.startDocument("UTF-8", true)
                serializer.startTag(null, "hierarchy")
                dumpNode(rootNode, serializer, 0)
                serializer.endTag(null, "hierarchy")
                serializer.endDocument()

                val rawXml = stringWriter.toString()
                logLongString("rawXml", rawXml)

                // Get screen dimensions
                val screenWidth: Int
                val screenHeight: Int
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val windowMetrics = windowManager?.currentWindowMetrics
                    val insets = windowMetrics?.windowInsets?.getInsetsIgnoringVisibility(android.view.WindowInsets.Type.systemBars())
                    screenWidth = windowMetrics?.bounds?.width() ?: 0
                    screenHeight = windowMetrics?.bounds?.height() ?: 0
                } else {
                    val display = windowManager?.defaultDisplay
                    val size = Point()
                    display?.getSize(size)
                    screenWidth = size.x
                    screenHeight = size.y
                }



//                val semanticParser = SemanticParser(this@ScreenInteractionService)
//                val simplifiedJson = semanticParser.parse(rawXml, screenWidth, screenHeight)
//                Log.d("APPMAP", "Screen Width: $screenWidth, Screen Height: $screenHeight\n$simplifiedJson")
//                // 1. Parse the raw XML into a structured list.
                val simplifiedElements = parseXmlToSimplifiedElements(rawXml)
                println("SIZEEEE : " + simplifiedElements.size)
                // 2. If debug mode is on, draw the bounding boxes.
                if (DEBUG_SHOW_BOUNDING_BOXES) {
                    drawDebugBoundingBoxes(simplifiedElements)
                }

                // 3. Format the structured list into the final string for the LLM.
                return@withContext formatElementsForLlm(simplifiedElements)

            } catch (e: Exception) {
                Log.e("InteractionService", "Error dumping or transforming UI hierarchy", e)
                return@withContext "Error processing UI."
            }
        }
    }

    private fun dumpNode(node: android.view.accessibility.AccessibilityNodeInfo?, serializer: XmlSerializer, index: Int) {
        if (node == null) return

        serializer.startTag(null, "node")

        // Add common attributes to the XML node
        serializer.attribute(null, "index", index.toString())
        serializer.attribute(null, "text", node.text?.toString() ?: "")
        serializer.attribute(null, "resource-id", node.viewIdResourceName ?: "")
        serializer.attribute(null, "class", node.className?.toString() ?: "")
        serializer.attribute(null, "package", node.packageName?.toString() ?: "")
        serializer.attribute(null, "content-desc", node.contentDescription?.toString() ?: "")
        serializer.attribute(null, "checkable", node.isCheckable.toString())
        serializer.attribute(null, "checked", node.isChecked.toString())
        serializer.attribute(null, "clickable", node.isClickable.toString())
        serializer.attribute(null, "enabled", node.isEnabled.toString())
        serializer.attribute(null, "focusable", node.isFocusable.toString())
        serializer.attribute(null, "focused", node.isFocused.toString())
        serializer.attribute(null, "scrollable", node.isScrollable.toString())
        serializer.attribute(null, "long-clickable", node.isLongClickable.toString())
        serializer.attribute(null, "password", node.isPassword.toString())
        serializer.attribute(null, "selected", node.isSelected.toString())

        val bounds = android.graphics.Rect()
        node.getBoundsInScreen(bounds)
        serializer.attribute(null, "bounds", bounds.toShortString())

        // Recursively dump children
        for (i in 0 until node.childCount) {
            dumpNode(node.getChild(i), serializer, i)
        }

        serializer.endTag(null, "node")
    }


    fun logLongString(tag: String, message: String) {
        val maxLogSize = 2000 // Split into chunks of 2000 characters
        for (i in 0..message.length / maxLogSize) {
            val start = i * maxLogSize
            var end = (i + 1) * maxLogSize
            end = if (end > message.length) message.length else end
            Log.d(tag, message.substring(start, end))
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We are triggering actions proactively, so we don't need to react to events.
    }

    override fun onInterrupt() {
        Log.e("InteractionService", "Accessibility Service interrupted.")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        hideGlowingBorder()
        Log.d("InteractionService", "Accessibility Service destroyed.")
    }

    /**
     * NEW: Programmatically checks if there is a focused and editable input field
     * ready to receive text. This is the most reliable way to know if typing is possible.
     * @return True if typing is possible, false otherwise.
     */
    fun isTypingAvailable(): Boolean {
        val focusedNode = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        // A field is available for typing if it's focused, editable, and enabled.
        return focusedNode != null && focusedNode.isEditable && focusedNode.isEnabled
    }

    fun clickOnPoint(x: Float, y: Float) {
        // Show visual feedback for the tap if the debug flag is enabled
        if (DEBUG_SHOW_TAPS) {
            showDebugTap(x, y)
        }

        val path = Path().apply {
            moveTo(x, y)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 10))
            .build()

        dispatchGesture(gesture, null, null)
    }

    /**
     * Performs a swipe gesture on the screen.
     * @param duration The time in milliseconds the swipe should take.
     */
    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, duration: Long) {
        val path = Path()
        path.moveTo(x1, y1)
        path.lineTo(x2, y2)
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()

        dispatchGesture(gesture, null, null)
    }

    /**
     * Types the given text into the currently focused editable field.
     */
    fun typeTextInFocusedField(textToType: String) {
        // Find the node that currently has input focus
        val focusedNode = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)

        if (focusedNode != null && focusedNode.isEditable) {
            val arguments = Bundle()
            // To append text rather than replacing it, we get existing text first
            val existingText =  ""
            val newText = existingText.toString() + textToType

            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText)
            focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        } else {
            Log.e("InteractionService", "Could not find a focused editable field to type in.")
        }
    }

    /**
     * Triggers the 'Back' button action.
     */
    fun performBack() {
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    /**
     * Triggers the 'Home' button action.
     */
    fun performHome() {
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    /**
     * Triggers the 'App Switch' (Recents) action.
     */
    fun performRecents() {
        performGlobalAction(GLOBAL_ACTION_RECENTS)
    }

    /**
     * Simulates an 'Enter' key press on the focused element.
     */
    fun performEnter() {
        val focusedNode = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focusedNode != null) {
            // A simple click often works for 'Enter' on buttons or submitting forms
            focusedNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } else {
            Log.e("InteractionService", "Could not find a focused node to 'Enter' on.")
        }
    }
    /**
     * Asynchronously captures a screenshot from an AccessibilityService in a safe and reliable way.
     * This function follows the "Strict Librarian" rule: it always closes the screenshot resource
     * after use to prevent leaks and allow subsequent screenshots to succeed.
     *
     * @return A nullable Bitmap. Returns the screenshot Bitmap on success, or null if any part of the process fails.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    suspend fun captureScreenshot(): Bitmap? {
        // A top-level try-catch block ensures that no matter what fails inside the coroutine,
        // the app will not crash. It will log the error and return null.
        return try {
            // suspendCancellableCoroutine is the standard way to wrap a modern callback-based
            // Android API into a clean, suspendable coroutine.
            suspendCancellableCoroutine { continuation ->
                // The executor ensures the result callbacks happen on the main UI thread,
                // which is a requirement for many UI-related APIs.
                val executor = ContextCompat.getMainExecutor(this)

                // STEP 1: Ask the "Librarian" (Android OS) to check out the "book" (Screenshot).
                takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    executor,
                    object : TakeScreenshotCallback {

                        // This block is called if the system successfully grants us the screenshot buffer.
                        override fun onSuccess(screenshotResult: ScreenshotResult) {
                            // The HardwareBuffer is the actual low-level resource. It's the "special book".
                            val hardwareBuffer = screenshotResult.hardwareBuffer

                            if (hardwareBuffer == null) {
                                // If, for some reason, the buffer is null even on success, fail gracefully.
                                continuation.resumeWithException(Exception("Screenshot hardware buffer was null."))
                                return
                            }

                            // STEP 2: "Photocopy the book" by wrapping the HardwareBuffer into a standard Bitmap.
                            // We make a mutable copy so we can work with it after closing the original buffer.
                            val bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, screenshotResult.colorSpace)
                                ?.copy(Bitmap.Config.ARGB_8888, false)

                            // STEP 3: THIS IS THE MOST IMPORTANT STEP.
                            // "Return the book to the librarian." We immediately close the original HardwareBuffer
                            // to release the system resource. This allows the *next* screenshot call to succeed.
                            hardwareBuffer.close()

                            // STEP 4: Give the "photocopy" (the Bitmap) back to our agent.
                            if (bitmap != null) {
                                // If the bitmap was created successfully, resume the coroutine with the result.
                                continuation.resume(bitmap)
                            } else {
                                // If bitmap creation failed, resume with an error.
                                continuation.resumeWithException(Exception("Failed to wrap hardware buffer into a Bitmap."))
                            }
                        }

                        // This block is called if the "Librarian" denies our request for any reason.
                        override fun onFailure(errorCode: Int) {
                            // We don't crash the app. We just tell the coroutine that it failed,
                            // which will be caught by our top-level try-catch block.
                            continuation.resumeWithException(Exception("Screenshot failed with error code: $errorCode"))
                        }
                    }
                )
            }
        } catch (e: Exception) {
            // Any exception from resumeWithException will be caught here.
            // We log the full error with its stack trace for easy debugging.
            Log.e("ScreenshotUtil", "Screenshot capture failed", e)
            // We return null to the caller, signaling that the operation did not succeed.
            null
        }
    }

    private fun saveBitmapToFile(bitmap: Bitmap, file: File) {
        try {
            file.parentFile?.mkdirs()

            val fos: OutputStream = FileOutputStream(file)
            fos.use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                Log.d("InteractionService", "Screenshot saved to ${file.absolutePath}")
            }
        } catch (e: Exception) {
            Log.e("InteractionService", "Failed to save bitmap to file.", e)
        }
    }



    /**
     * A private recursive helper to find and collect interactable nodes.
     */
    private fun findInteractableNodesRecursive(
        node: android.view.accessibility.AccessibilityNodeInfo?,
        list: MutableList<InteractableElement>
    ) {
        if (node == null) return
//
        // =================================================================
        // THIS IS THE CORE LOGIC: Check if the node is interactable
        // =================================================================
//      val isInteractable = (node.isClickable || node.isLongClickable || node.isScrollable || node.isFocusable || node.isLongClickable || node.is) && node.isEnabled

        val bounds = android.graphics.Rect()
        node.getBoundsInScreen(bounds)

        // We only care about elements that are actually visible on screen
        if (!bounds.isEmpty) {
            list.add(
                InteractableElement(
                    text = node.text?.toString(),
                    contentDescription = node.contentDescription?.toString(),
                    resourceId = node.viewIdResourceName,
                    className = node.className?.toString(),
                    bounds = bounds,
                    node = node // Keep the original node reference to perform actions
                )
            )
        }

        // Continue searching through the children
        for (i in 0 until node.childCount) {
            findInteractableNodesRecursive(node.getChild(i), list)
        }
    }


    // Rename the update method to be more descriptive

    /**
     * NEW: Creates and displays the AudioWaveView at the bottom of the screen.
     */
    private fun showAudioWave() {
        if (audioWaveView != null) return // Already showing

        audioWaveView = AudioWaveView(this)

        // Convert 150dp to pixels for the view's height
        val heightInDp = 150
        val heightInPixels = (heightInDp * resources.displayMetrics.density).toInt()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, // Full width
            heightInPixels, // Fixed height
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            // --- 3. Anchor the view to the bottom ---
            gravity = Gravity.BOTTOM
//            y = 200 // Moves the view 200 pixels up from the bottom

        }

        Handler(Looper.getMainLooper()).post {
            windowManager?.addView(audioWaveView, params)
            Log.d("InteractionService", "Audio wave view added.")
        }
    }

    /**
     * NEW: Hides the wave view.
     */
    private fun hideAudioWave() {
        Handler(Looper.getMainLooper()).post {
            audioWaveView?.let { windowManager?.removeView(it) }
            audioWaveView = null
        }
    }


// --- REPLACE your old setupAudioWaveEffect with this new, simpler version ---
    /**
     * Connects the TTS speaking state to the wave view for smooth animations.
     */

    // --- REPLACED with the new reactive implementation ---
    /**
     * Connects the TTS audio output to the wave view for real-time visualization.
     */
    private fun setupAudioWaveEffect() {
        showAudioWave()
        val ttsManager = TTSManager.getInstance(this)
        val audioSessionId = ttsManager.getAudioSessionId()

        if (audioSessionId == 0) {
            Log.e("InteractionService", "Failed to get valid audio session ID. Visualizer not started.")
            return
        }

        // Create the visualizer and link it to the AudioWaveView
        ttsVisualizer = TtsVisualizer(audioSessionId) { normalizedAmplitude ->
            Handler(Looper.getMainLooper()).post {
                audioWaveView?.setRealtimeAmplitude(normalizedAmplitude)
            }
        }

        // Use the utterance listener to start and stop the visualizer
        ttsManager.utteranceListener = { isSpeaking ->
            Handler(Looper.getMainLooper()).post {
                if (isSpeaking) {
                    audioWaveView?.setTargetAmplitude(0.2f)
                    ttsVisualizer?.start()
                } else {
                    ttsVisualizer?.stop()
                    audioWaveView?.setTargetAmplitude(0.0f)
                }
            }
        }
    }


}

data class InteractableElement(
    val text: String?,
    val contentDescription: String?,
    val resourceId: String?,
    val className: String?,
    val bounds: android.graphics.Rect,
    // We can also hold a reference to the original node if needed for performing actions
    val node: android.view.accessibility.AccessibilityNodeInfo
) {
    // A helper to get the center coordinates, useful for tapping
    fun getCenter(): android.graphics.Point {
        return android.graphics.Point(bounds.centerX(), bounds.centerY())
    }
}
