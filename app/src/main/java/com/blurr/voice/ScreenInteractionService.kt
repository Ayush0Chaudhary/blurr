/**
 * @file ScreenInteractionService.kt
 * @brief The core AccessibilityService that allows the agent to see and interact with the screen.
 *
 * This file defines `ScreenInteractionService`, which is the fundamental component enabling the
 * agent's perception and action capabilities. It runs as a background service with accessibility
 * permissions, allowing it to read the view hierarchy, capture screenshots, and dispatch gestures
 * to control the device.
 */
package com.blurr.voice

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
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
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.RequiresApi
import com.blurr.voice.utilities.TTSManager
import com.blurr.voice.utilities.TtsVisualizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlSerializer
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.StringWriter
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * A data class to hold a simplified representation of a UI element.
 * (Currently unused in the main flow but kept for potential future use).
 */
private data class SimplifiedElement(
    val description: String,
    val bounds: Rect,
    val center: Point,
    val isClickable: Boolean,
    val className: String
)

/**
 * A data class holding the complete raw data captured from the screen at a single point in time.
 *
 * @property xml The raw XML dump of the view hierarchy.
 * @property pixelsAbove The number of pixels of scrollable content available above the visible area.
 * @property pixelsBelow The number of pixels of scrollable content available below the visible area.
 * @property screenWidth The total width of the screen in pixels.
 * @property screenHeight The total height of the screen in pixels.
 */
data class RawScreenData(
    val xml: String,
    val pixelsAbove: Int,
    val pixelsBelow: Int,
    val screenWidth: Int,
    val screenHeight: Int
)

/**
 * The core `AccessibilityService` that acts as the agent's "eyes" and "hands".
 *
 * This service has two primary responsibilities:
 * 1.  **Perception**: Reading the screen's view hierarchy (`AccessibilityNodeInfo`) and converting
 *     it into a structured XML format. It can also capture screenshots.
 * 2.  **Interaction**: Executing gestures on the screen, such as taps, swipes, and long presses,
 *     as well as performing global actions like "back" and "home".
 *
 * It uses a singleton-like pattern with a static `instance` for easy access from other parts of the app.
 */
class ScreenInteractionService : AccessibilityService() {

    companion object {
        /** A static reference to the running service instance. */
        var instance: ScreenInteractionService? = null
        const val DEBUG_SHOW_TAPS = false
        const val DEBUG_SHOW_BOUNDING_BOXES = false
    }

    private var windowManager: WindowManager? = null
    private var ttsVisualizer: TtsVisualizer? = null
    private var audioWaveView: AudioWaveView? = null
    private var glowingBorderView: GlowBorderView? = null
    private var statusBarHeight = -1
    private var currentActivityName: String? = null

    /**
     * Called by the system when the service is first connected (i.e., when it's enabled in settings).
     */
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        this.windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        Log.d("InteractionService", "Accessibility Service connected.")
    }

    /**
     * Gets the package name of the app currently in the foreground.
     * @return The package name as a String, or null if not available.
     */
    fun getForegroundAppPackageName(): String? {
        return rootInActiveWindow?.packageName?.toString()
    }

    /**
     * Hides and cleans up the glowing border view overlay.
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
            setColor(0x80FF0000.toInt())
            setSize(100, 100)
            setStroke(4, 0xFFFF0000.toInt())
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
     * Draws labeled bounding boxes for each element on the screen for debugging.
     */
    private fun drawDebugBoundingBoxes(elements: List<SimplifiedElement>) {
        if (!Settings.canDrawOverlays(this)) {
            Log.w("InteractionService", "Cannot draw bounding boxes: 'Draw over other apps' permission not granted.")
            return
        }

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
                        y = element.bounds.top - statusBarHeight
                    }
                    windowManager.addView(boxView, boxParams)
                    viewsToRemove.add(boxView)

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
     * Parses the raw XML into a structured list of simplified elements. (Legacy/Unused).
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
        return allElements
    }

    /**
     * Formats the list of simplified elements into a single string for the LLM. (Legacy/Unused).
     */
    private fun formatElementsForLlm(elements: List<SimplifiedElement>): String {
        if (elements.isEmpty()) {
            return "No interactable or textual elements found on the screen."
        }
        val elementStrings = elements.map {
            val action = if (it.isClickable) "Action: Clickable" else "Action: Not-Clickable (Text only)"
            val elementType = it.className.substringAfterLast('.')
            "- $elementType: \"${it.description}\" | $action | Center: (${it.center.x}, ${it.center.y})"
        }
        return "Interactable Screen Elements:\n" + elementStrings.joinToString("\n")
    }

    /**
     * Shows a temporary white border flash on the screen as non-intrusive feedback.
     */
    private fun showScreenFlash() {
        val mainHandler = Handler(Looper.getMainLooper())
        mainHandler.post {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                Log.w("InteractionService", "Cannot show screen flash: 'Draw over other apps' permission not granted.")
                return@post
            }

            val borderView = View(this)
            val borderDrawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.TRANSPARENT)
                setStroke(8, Color.WHITE)
            }
            borderView.background = borderDrawable

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )

            try {
                windowManager?.addView(borderView, params)
                mainHandler.postDelayed({
                    if (borderView.isAttachedToWindow) {
                        windowManager?.removeView(borderView)
                    }
                }, 500L)

            } catch (e: Exception) {
                Log.e("InteractionService", "Failed to add screen flash view", e)
            }
        }
    }

    /**
     * Captures the current view hierarchy and serializes it to an XML string.
     * @param pureXML If true, returns the raw XML. If false, returns a simplified format. (Legacy flag)
     * @return A string containing the UI hierarchy.
     */
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

                val screenWidth: Int
                val screenHeight: Int
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val windowMetrics = windowManager?.currentWindowMetrics
                    screenWidth = windowMetrics?.bounds?.width() ?: 0
                    screenHeight = windowMetrics?.bounds?.height() ?: 0
                } else {
                    val display = windowManager?.defaultDisplay
                    val size = Point()
                    display?.getSize(size)
                    screenWidth = size.x
                    screenHeight = size.y
                }

                val simplifiedElements = parseXmlToSimplifiedElements(rawXml)
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
                return@withContext formatElementsForLlm(simplifiedElements)

            } catch (e: Exception) {
                Log.e("InteractionService", "Error dumping or transforming UI hierarchy", e)
                return@withContext "Error processing UI."
            }
        }
    }

    /**
     * A recursive helper function to traverse the `AccessibilityNodeInfo` tree and build an XML string.
     */
    private fun dumpNode(node: android.view.accessibility.AccessibilityNodeInfo?, serializer: XmlSerializer, index: Int) {
        if (node == null) return

        serializer.startTag(null, "node")

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

        for (i in 0 until node.childCount) {
            dumpNode(node.getChild(i), serializer, i)
        }

        serializer.endTag(null, "node")
    }

    /**
     * Utility to log a long string in chunks.
     */
    fun logLongString(tag: String, message: String) {
        val maxLogSize = 2000
        for (i in 0..message.length / maxLogSize) {
            val start = i * maxLogSize
            var end = (i + 1) * maxLogSize
            end = if (end > message.length) message.length else end
            Log.d(tag, message.substring(start, end))
        }
    }

    /**
     * Listens for accessibility events to track the current foreground activity.
     */
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

    /**
     * Returns the name of the current foreground activity.
     */
    fun getCurrentActivityName(): String {
        return this.currentActivityName ?: "Unknown"
    }

    /**
     * Called by the system when the service is interrupted.
     */
    override fun onInterrupt() {
        Log.e("InteractionService", "Accessibility Service interrupted.")
    }

    /**
     * Called by the system when the service is being destroyed.
     */
    override fun onDestroy() {
        super.onDestroy()
        instance = null
        hideGlowingBorder()
        Log.d("InteractionService", "Accessibility Service destroyed.")
    }

    /**
     * Programmatically checks if there is a focused and editable input field
     * ready to receive text. This is the most reliable way to know if typing is possible.
     * @return True if typing is possible, false otherwise.
     */
    fun isTypingAvailable(): Boolean {
        val focusedNode = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        return focusedNode != null && focusedNode.isEditable && focusedNode.isEnabled
    }

    /**
     * Performs a click gesture at a specific point on the screen.
     * @param x The x-coordinate of the click.
     * @param y The y-coordinate of the click.
     */
    fun clickOnPoint(x: Float, y: Float) {
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
        if (DEBUG_SHOW_TAPS) {
            showDebugTap(x, y)
        }

        val path = Path().apply {
            moveTo(x, y)
        }
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

        val x = screenWidth / 2
        val y1 = (screenHeight * 0.8).toInt()
        val y2 = (y1 - pixels).coerceAtLeast(0)

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
        val focusedNode = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)

        if (focusedNode != null && focusedNode.isEditable) {
            val arguments = Bundle()
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
     * Attempts to perform an 'Enter' or 'Click' action on the currently focused input field.
     * This is useful for submitting forms or search queries.
     */
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

            val imeAction = AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER
            if (supportedActions.contains(imeAction)) {
                Log.d("InteractionService", "Attempting primary action: ACTION_IME_ENTER")
                val success = focusedNode.performAction(imeAction.id)
                if (success) {
                    Log.d("InteractionService", "Successfully performed ACTION_IME_ENTER.")
                    return
                }
                Log.w("InteractionService", "ACTION_IME_ENTER was supported but failed to execute. Proceeding to fallback.")
            }

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
            focusedNode.recycle()
        }
    }

    /**
     * Traverses the node tree to find the primary scrollable container and determine how much
     * content is available above and below the visible area.
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
                pixelsAbove = (rangeInfo.current - rangeInfo.min).toInt()
                pixelsBelow = (rangeInfo.max - rangeInfo.current).toInt()
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                pixelsAbove = 10
                pixelsBelow = (5).coerceAtLeast(0)
            }
            it.recycle()
        }

        return Pair(pixelsAbove, pixelsBelow)
    }

    /**
     * Gets the current screen dimensions.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun getScreenDimensions(): Pair<Int, Int> {
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val metrics = windowManager.currentWindowMetrics
        val width = metrics.bounds.width()
        val height = metrics.bounds.height()
        return Pair(width, height)
    }

    /**
     * The primary perception method. It captures all raw data about the current screen state,
     * including the XML hierarchy and scroll information. It includes a retry mechanism to handle
     * cases where the accessibility service might be slow to provide the root node.
     * @return A [RawScreenData] object containing the complete screen state.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    suspend fun getScreenAnalysisData(): RawScreenData {
        val (screenWidth, screenHeight) = getScreenDimensions()
        val maxRetries = 5
        val retryDelay = 800L

        for (attempt in 1..maxRetries) {
            val rootNode = rootInActiveWindow

            if (rootNode != null) {
                Log.d("InteractionService", "Got rootInActiveWindow on attempt $attempt.")

                val (pixelsAbove, pixelsBelow) = findScrollableNodeAndGetInfo(rootNode)
                val xmlString = dumpWindowHierarchy(true)
                return RawScreenData(xmlString, pixelsAbove, pixelsBelow, screenWidth, screenHeight)
            }

            if (attempt < maxRetries) {
                Log.d("InteractionService", "rootInActiveWindow is null on attempt $attempt. Retrying in ${retryDelay}ms...")
                delay(retryDelay)
            }
        }

        Log.e("InteractionService", "Failed to get rootInActiveWindow after $maxRetries attempts.")
        return RawScreenData("<hierarchy/>", 0, 0, screenWidth, screenHeight)
    }

    /**
     * Asynchronously captures a screenshot from the device.
     * This function safely handles the screenshot buffer, ensuring it is closed properly
     * to prevent resource leaks.
     *
     * @return A nullable [Bitmap] of the screenshot, or null if the capture fails.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    suspend fun captureScreenshot(): Bitmap? {
        return try {
            suspendCancellableCoroutine { continuation ->
                val executor = ContextCompat.getMainExecutor(this)
                takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    executor,
                    object : TakeScreenshotCallback {
                        override fun onSuccess(screenshotResult: ScreenshotResult) {
                            val hardwareBuffer = screenshotResult.hardwareBuffer
                            if (hardwareBuffer == null) {
                                continuation.resumeWithException(Exception("Screenshot hardware buffer was null."))
                                return
                            }
                            val bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, screenshotResult.colorSpace)
                                ?.copy(Bitmap.Config.ARGB_8888, false)
                            hardwareBuffer.close()
                            if (bitmap != null) {
                                continuation.resume(bitmap)
                            } else {
                                continuation.resumeWithException(Exception("Failed to wrap hardware buffer into a Bitmap."))
                            }
                        }
                        override fun onFailure(errorCode: Int) {
                            continuation.resumeWithException(Exception("Screenshot failed with error code: $errorCode"))
                        }
                    }
                )
            }
        } catch (e: Exception) {
            Log.e("ScreenshotUtil", "Screenshot capture failed", e)
            null
        }
    }

    /**
     * Saves a bitmap to a local file. (Helper/Debug function).
     */
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
     * A private recursive helper to find and collect interactable nodes. (Legacy/Unused).
     */
    private fun findInteractableNodesRecursive(
        node: AccessibilityNodeInfo?,
        list: MutableList<InteractableElement>
    ) {
        if (node == null) return

        val bounds = android.graphics.Rect()
        node.getBoundsInScreen(bounds)

        if (!bounds.isEmpty) {
            list.add(
                InteractableElement(
                    text = node.text?.toString(),
                    contentDescription = node.contentDescription?.toString(),
                    resourceId = node.viewIdResourceName,
                    className = node.className?.toString(),
                    bounds = bounds,
                    node = node
                )
            )
        }

        for (i in 0 until node.childCount) {
            findInteractableNodesRecursive(node.getChild(i), list)
        }
    }

    /**
     * Creates and displays the `AudioWaveView` overlay at the bottom of the screen.
     */
    private fun showAudioWave() {
        if (audioWaveView != null) return

        audioWaveView = AudioWaveView(this)

        val heightInDp = 150
        val heightInPixels = (heightInDp * resources.displayMetrics.density).toInt()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            heightInPixels,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
        }

        Handler(Looper.getMainLooper()).post {
            windowManager?.addView(audioWaveView, params)
            Log.d("InteractionService", "Audio wave view added.")
        }
    }

    /**
     * Connects the TTS audio output to the `AudioWaveView` for real-time visualization.
     */
    fun showAndSetupAudioWave() {
        showAudioWave()
        val ttsManager = TTSManager.getInstance(this)
        val audioSessionId = ttsManager.getAudioSessionId()

        if (audioSessionId == 0) {
            Log.e("InteractionService", "Failed to get valid audio session ID. Visualizer not started.")
            return
        }

        ttsVisualizer = TtsVisualizer(audioSessionId) { normalizedAmplitude ->
            Handler(Looper.getMainLooper()).post {
                audioWaveView?.setRealtimeAmplitude(normalizedAmplitude)
            }
        }

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

    /**
     * Hides the `AudioWaveView` overlay and cleans up related resources.
     */
    fun hideAudioWave() {
        Handler(Looper.getMainLooper()).post {
            audioWaveView?.let {
                if (it.isAttachedToWindow) {
                    windowManager?.removeView(it)
                    Log.d("InteractionService", "Audio wave view removed.")
                }
            }
            audioWaveView = null

            ttsVisualizer?.stop()
            ttsVisualizer = null
            TTSManager.getInstance(this).utteranceListener = null
            Log.d("InteractionService", "Audio wave effect has been torn down.")
        }
    }
}

/**
 * A data class representing an interactable element on the screen. (Legacy/Unused).
 */
data class InteractableElement(
    val text: String?,
    val contentDescription: String?,
    val resourceId: String?,
    val className: String?,
    val bounds: android.graphics.Rect,
    val node: android.view.accessibility.AccessibilityNodeInfo
) {
    /** A helper to get the center coordinates, useful for tapping. */
    fun getCenter(): android.graphics.Point {
        return android.graphics.Point(bounds.centerX(), bounds.centerY())
    }
}
