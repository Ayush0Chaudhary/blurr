package com.blurr.voice

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.animation.ValueAnimator
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
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
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.blurr.voice.utilities.TTSManager
import com.blurr.voice.utilities.TtsVisualizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlSerializer
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.StringReader
import java.io.StringWriter
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private data class SimplifiedElement(
    val description: String,
    val bounds: Rect,
    val center: Point,
    val isClickable: Boolean,
    val className: String
)

data class RawScreenData(
//    val xml: String,
    val rootNode: AccessibilityNodeInfo?,
    val pixelsAbove: Int,
    val pixelsBelow: Int,
    val screenWidth: Int,
    val screenHeight: Int
)

class ScreenInteractionService : AccessibilityService() {

    companion object {
        var instance: ScreenInteractionService? = null

        const val DEBUG_SHOW_TAPS = true

        const val DEBUG_SHOW_BOUNDING_BOXES = false
    }

    private var windowManager: WindowManager? = null

    private var ttsVisualizer: TtsVisualizer? = null

    private var audioWaveView: AudioWaveView? = null
    private var glowingBorderView: GlowBorderView? = null

    private var statusBarHeight = -1

    private var currentActivityName: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        this.windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        Log.d("InteractionService", "Accessibility Service connected.")
//        setupGlowEffect()
//        setupAudioWaveEffect()
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
    fun showDebugTap(tapX: Float, tapY: Float) {
//        if (!Settings.canDrawOverlays(this)) {
//            Log.w("InteractionService", "Cannot show debug tap: 'Draw over other apps' permission not granted.")
//            return
//        }

        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
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
    /**
     * Returns the raw XML hierarchy string.
     * Used as a "Signature" to detect screen changes.
     */
    fun getWindowHierarchySignature(): String {
        val rootNode = rootInActiveWindow ?: return "null_root"

        val stringWriter = StringWriter()
        try {
            val serializer: XmlSerializer = Xml.newSerializer()
            serializer.setOutput(stringWriter)
            serializer.startDocument("UTF-8", true)
            serializer.startTag(null, "hierarchy")

            // Reuse your existing dumpNode function
            dumpNode(rootNode, serializer, 0)

            serializer.endTag(null, "hierarchy")
            serializer.endDocument()

            return stringWriter.toString()
        } catch (e: Exception) {
            Log.e("InteractionService", "Error generating signature", e)
            return "error_generating_signature"
        }
    }

    suspend fun getAllScreenAnalysisData(): RawScreenData {
        val (screenWidth, screenHeight) = getScreenDimensions()
        val maxRetries = 5
        val retryDelay = 200L // Reduced delay for snappiness

        for (attempt in 1..maxRetries) {
            // 1. Get all interactive windows, not just the focused one
            val allWindows = windows

            // 2. Find the main application window.
            // Logic: Look for TYPE_APPLICATION. If multiple, pick the largest one.
            // This ignores your overlay (which is likely TYPE_APPLICATION_OVERLAY) or smaller dialogs.
            val targetWindow = allWindows
                .filter { it.type == TYPE_APPLICATION }
                .maxByOrNull {
                    val bounds = Rect()
                    it.getBoundsInScreen(bounds)
                    bounds.width() * bounds.height()
                }

            val rootNode = targetWindow?.root ?: rootInActiveWindow

            if (rootNode != null) {
                // Log what we found to debug
                Log.d("InteractionService", "Analyzing window: ${rootNode.packageName}")

                val (pixelsAbove, pixelsBelow) = findScrollableNodeAndGetInfo(rootNode)
                return RawScreenData(rootNode, pixelsAbove, pixelsBelow, screenWidth, screenHeight)
            }

            if (attempt < maxRetries) {
                delay(retryDelay)
            }
        }

        Log.e("InteractionService", "Failed to get any valid root node after $maxRetries attempts.")
        return RawScreenData(null, 0, 0, screenWidth, screenHeight)
    }
    /**
     * Shows a thin, white border around the entire screen for 300ms.
     * This serves as a non-intrusive visual feedback mechanism.
     */
    private fun showScreenFlash() {
        // All UI operations must be on the main thread
        val mainHandler = Handler(Looper.getMainLooper())
        mainHandler.post {

            // --- Improved Check 1: Check if windowManager is null ---
            // Get a local non-null reference. If wm is null, the service may not
            // be fully connected yet.
            val wm = windowManager
            if (wm == null) {
                Log.e("InteractionService", "showScreenFlash failed: WindowManager is null.")
                return@post
            }

            // --- Improved Check 2: Check for overlay permission ---
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                Log.w("InteractionService", "Cannot show screen flash: 'Draw over other apps' permission not granted.")
                // You could add a Toast here for debugging if you want
                // Toast.makeText(this, "Screen flash permission missing", Toast.LENGTH_SHORT).show()
                return@post
            }

            // 1. Create the View
            val borderView = View(this)

            // 2. Create the border drawable
            val borderDrawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.TRANSPARENT)
                setStroke(8, Color.WHITE) // 8px stroke
            }
            borderView.background = borderDrawable

            // --- FIX: Use the correct window type based on Android version ---
            val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            // 3. Define the layout parameters
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                windowType, // Use the dynamically-set windowType
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )

            // --- Improved Error Handling ---
            try {
                // 4. Add the view
                wm.addView(borderView, params) // Use the non-null reference

                // 5. Schedule removal
                mainHandler.postDelayed({
                    // Check if view is still attached before trying to remove
                    if (borderView.isAttachedToWindow) {
                        try {
                            wm.removeView(borderView)
                        } catch (e: Exception) {
                            // This can happen if the service is destroyed
                            // during the 500ms delay
                            Log.e("InteractionService", "Failed to remove screen flash view.", e)
                        }
                    }
                }, 500L) // The flash duration

            } catch (e: WindowManager.BadTokenException) {
                // This is the most likely error you were facing
                Log.e("InteractionService", "Failed to add screen flash view. Is the window type ($windowType) correct for this Android version? (This is a 'BadTokenException')", e)
            } catch (e: IllegalStateException) {
                // This happens if you accidentally try to add the view twice
                Log.e("InteractionService", "Failed to add screen flash view. Was it already added? (This is an 'IllegalStateException')", e)
            } catch (e: SecurityException) {
                // This can happen if permissions are revoked while the service is running
                Log.e("InteractionService", "Failed to add screen flash view. Permission issue? (This is a 'SecurityException')", e)
            } catch (e: Exception) {
                // Catch-all for any other unexpected errors
                Log.e("InteractionService", "Failed to add screen flash view with an unknown exception.", e)
            }
        }
    }
    suspend fun dumpWindowHierarchy(pureXML: Boolean = false): String {
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
//                logLongString("rawXml", rawXml)

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


//                 val semanticParser = SemanticParser()
//                 val simplifiedJson = semanticParser.toHierarchicalString(rawXml)
//                // 1. Parse the raw XML into a structured list.
                val simplifiedElements = parseXmlToSimplifiedElements(rawXml)
                println("SIZEEEE : " + simplifiedElements.size)
                // 2. If debug mode is on, draw the bounding boxes.
                if (DEBUG_SHOW_BOUNDING_BOXES) {
                    drawDebugBoundingBoxes(simplifiedElements)
                }

                try {
                    showScreenFlash()
                } catch (e: Exception) {
                    Log.e("InteractionService", "Failed to trigger screen flash", e)
                }

                if (pureXML) {
                    return@withContext rawXml
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

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString()
            val className = event.className?.toString()

            if (!packageName.isNullOrBlank() && !className.isNullOrBlank()) {
                this.currentActivityName = ComponentName(packageName, className).flattenToString()
                Log.d("AccessibilityService", "Current Activity Updated: $currentActivityName")
            }
        }
    }

    fun getCurrentActivityName(): String {
        return this.currentActivityName ?: "Unknown"
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
     * Performs a long press gesture at a specific point on the screen.
     * @param x The x-coordinate of the long press.
     * @param y The y-coordinate of the long press.
     */
    fun longClickOnPoint(x: Float, y: Float) {
        // Show visual feedback for the tap if the debug flag is enabled
        if (DEBUG_SHOW_TAPS) {
            showDebugTap(x, y)
        }

        val path = Path().apply {
            moveTo(x, y)
        }
        // A long press is essentially a tap that is held down.
        // 600ms is a common duration for a long press.
        val longPressStroke = GestureDescription.StrokeDescription(path, 0, 2000L)

        val gesture = GestureDescription.Builder()
            .addStroke(longPressStroke)
            .build()

        dispatchGesture(gesture, null, null)
    }

    /**
     * Scrolls the screen down by a given number of pixels with more precision.
     * This performs a swipe from bottom to top with a calculated duration
     * to maintain a slow, constant velocity and minimize the "fling" effect.
     *
     * @param pixels The number of pixels to scroll.
     * @param pixelsPerSecond The desired velocity of the swipe. Lower is more precise.
     */
    fun scrollDownPrecisely(pixels: Int, pixelsPerSecond: Int = 1000) {
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        // Define swipe path in the middle of the screen
        val x = screenWidth / 2
        // Start swipe from 80% down the screen to avoid navigation bars
        val y1 = (screenHeight * 0.8).toInt()
        // Calculate end point, ensuring it doesn't go below 0
        val y2 = (y1 - pixels).coerceAtLeast(0)

        // Calculate duration based on distance to maintain a constant, slow velocity
        // duration (ms) = (distance (px) / velocity (px/s)) * 1000 (ms/s)
        val distance = y1 - y2
        if (distance <= 0) {
            Log.w("Scroll", "Scroll distance is zero or negative. Aborting.")
            return
        }

        val duration = (distance.toFloat() / pixelsPerSecond * 1000).toInt()

        Log.d("Scroll", "Scrolling down by $pixels pixels: swipe from ($x, $y1) to ($x, $y2) over $duration ms")
        swipe(x.toFloat(), y1.toFloat(), x.toFloat(), y2.toFloat(), duration.toLong())
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

    @RequiresApi(Build.VERSION_CODES.R)
    fun performEnter() {
        val rootNode: AccessibilityNodeInfo? = rootInActiveWindow
        if (rootNode == null) {
            Log.e("InteractionService", "Cannot perform Enter: rootInActiveWindow is null.")
            return
        }

        val focusedNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focusedNode == null) {
            Log.w("InteractionService", "Could not find a focused input node to perform 'Enter' on.")
            return
        }

        try {
            val supportedActions = focusedNode.actionList

            // --- Step 1: Attempt the primary, correct method ---
            val imeAction = AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER
            if (supportedActions.contains(imeAction)) {
                Log.d("InteractionService", "Attempting primary action: ACTION_IME_ENTER")
                val success = focusedNode.performAction(imeAction.id)
                if (success) {
                    Log.d("InteractionService", "Successfully performed ACTION_IME_ENTER.")
                    return // Action succeeded, we are done.
                }
                // If it failed, we'll proceed to the fallback.
                Log.w("InteractionService", "ACTION_IME_ENTER was supported but failed to execute. Proceeding to fallback.")
            }

            // --- Step 2: Attempt the fallback method ---
            Log.w("InteractionService", "ACTION_IME_ENTER not available or failed. Trying ACTION_CLICK as a fallback.")
            val clickAction = AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK
            if (supportedActions.contains(clickAction)) {
                val success = focusedNode.performAction(clickAction.id)
                if (success) {
                    Log.d("InteractionService", "Fallback ACTION_CLICK succeeded.")
                } else {
                    Log.e("InteractionService", "Fallback ACTION_CLICK also failed.")
                }
            } else {
                Log.e("InteractionService", "No supported 'Enter' or 'Click' action was found on the focused node.")
            }

        } catch (e: Exception) {
            Log.e("InteractionService", "Exception while trying to perform Enter action", e)
        } finally {
            // IMPORTANT: Always recycle the node you found to prevent memory leaks.
            focusedNode.recycle()
        }
    }

    /**
     * Traverses the node tree to find the primary scrollable container.
     * A simple heuristic is to find the largest scrollable node on screen.
     */
    private fun findScrollableNodeAndGetInfo(rootNode: AccessibilityNodeInfo?): Pair<Int, Int> {
        if (rootNode == null) return Pair(0, 0)

        val queue: ArrayDeque<AccessibilityNodeInfo> = ArrayDeque()
        queue.addLast(rootNode)

        var bestNode: AccessibilityNodeInfo? = null
        var maxNodeSize = -1

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()

            if (node.isScrollable) {
                val rect = android.graphics.Rect()
                node.getBoundsInScreen(rect)
                val size = rect.width() * rect.height()
                if (size > maxNodeSize) {
                    maxNodeSize = size
                    bestNode = node
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.addLast(it) }
            }
        }

        var pixelsAbove = 0
        var pixelsBelow = 0

        bestNode?.let {
            val rangeInfo = it.rangeInfo
            if (rangeInfo != null) {
                // Use RangeInfo if available (common in RecyclerViews)
                pixelsAbove = (rangeInfo.current - rangeInfo.min).toInt()
                pixelsBelow = (rangeInfo.max - rangeInfo.current).toInt()
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Fallback for Android R+ (common in ScrollViews)
                // Note: getMaxScrollY() might not always be available.
                pixelsAbove = 10
                pixelsBelow = (5).coerceAtLeast(0)
            }
            // Recycle the node we found to be safe
            it.recycle()
        }

        return Pair(pixelsAbove, pixelsBelow)
    }

    private fun getScreenDimensions(): Pair<Int, Int> {
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Use modern API for Android 11+ (API 30+)
            val metrics = windowManager.currentWindowMetrics
            val width = metrics.bounds.width()
            val height = metrics.bounds.height()
            Pair(width, height)
        } else {
            // Use legacy API for older Android versions (API 24-29)
            val display = windowManager.defaultDisplay
            val displayMetrics = android.util.DisplayMetrics()
            display.getRealMetrics(displayMetrics)
            val width = displayMetrics.widthPixels
            val height = displayMetrics.heightPixels
            Pair(width, height)
        }
    }

    suspend fun getScreenAnalysisData(): RawScreenData {
        val (screenWidth, screenHeight) = getScreenDimensions()
        val maxRetries = 5
        val retryDelay = 800L

        for (attempt in 1..maxRetries) {
            // Attempt to get the root node in each iteration.
            val rootNode = rootInActiveWindow

            if (rootNode != null) {
                Log.d("InteractionService", "Got rootInActiveWindow on attempt $attempt.")

                val (pixelsAbove, pixelsBelow) = findScrollableNodeAndGetInfo(rootNode)

                return RawScreenData(rootNode, pixelsAbove, pixelsBelow, screenWidth, screenHeight) // MODIFIED
            }

            if (attempt < maxRetries) {
                Log.d("InteractionService", "rootInActiveWindow is null on attempt $attempt. Retrying in ${retryDelay}ms...")
                delay(retryDelay)
            }
        }

        Log.e("InteractionService", "Failed to get rootInActiveWindow after $maxRetries attempts.")
        return RawScreenData(null, 0, 0, screenWidth, screenHeight) // MODIFIED
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
        node: AccessibilityNodeInfo?,
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
     * Connects the TTS audio output to the wave view for real-time visualization.
     */
    fun showAndSetupAudioWave() {
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
    fun hideAudioWave() {
        Handler(Looper.getMainLooper()).post {
            audioWaveView?.let {
                if (it.isAttachedToWindow) {
                    windowManager?.removeView(it)
                    Log.d("InteractionService", "Audio wave view removed.")
                }
            }
            audioWaveView = null

            // Clean up visualizer and listener to prevent leaks
            ttsVisualizer?.stop()
            ttsVisualizer = null
            TTSManager.getInstance(this).utteranceListener = null
            Log.d("InteractionService", "Audio wave effect has been torn down.")
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
