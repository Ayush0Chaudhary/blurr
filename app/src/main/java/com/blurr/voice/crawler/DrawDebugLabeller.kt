/**
 * @file DrawDebugLabeller.kt
 * @brief Provides utilities for drawing debug overlays on the screen.
 *
 * This file contains the `DebugOverlayDrawer` class, which can draw labeled bounding boxes over
 * other applications to visually represent UI elements. It also includes the `BoundsParser` helper
 * object for parsing element coordinates. This is a powerful tool for debugging UI perception
 * and automation logic.
 */
package com.blurr.voice.crawler

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import java.util.regex.Pattern


/**
 * A helper object to parse a bounds string (e.g., "[0,0][100,100]") into a [Rect] object.
 */
object BoundsParser {
    /** The regex pattern to capture the four integer coordinates from the bounds string. */
    private val PATTERN = Pattern.compile("\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]")

    /**
     * Parses a string of coordinates into a [Rect].
     *
     * @param boundsString The string to parse, expected in the format "[left,top][right,bottom]".
     * @return A [Rect] object if parsing is successful, otherwise null.
     */
    fun parse(boundsString: String?): Rect? {
        if (boundsString == null) return null
        val matcher = PATTERN.matcher(boundsString)
        return if (matcher.matches()) {
            val left = matcher.group(1)?.toIntOrNull() ?: 0
            val top = matcher.group(2)?.toIntOrNull() ?: 0
            val right = matcher.group(3)?.toIntOrNull() ?: 0
            val bottom = matcher.group(4)?.toIntOrNull() ?: 0
            Rect(left, top, right, bottom)
        } else {
            null
        }
    }
}

/**
 * A utility class for drawing temporary, labeled bounding boxes over other applications.
 *
 * This class is designed for debugging agent perception by visualizing the location and
 * identity of UI elements as seen by the system. It requires the "Draw over other apps"
 * permission to function.
 *
 * @param context The application context, required to access system services like [WindowManager].
 */
class DebugOverlayDrawer(private val context: Context) {

    /** The WindowManager service used to add and remove overlay views. */
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    /** A handler associated with the main thread to ensure all UI operations are performed there. */
    private val mainHandler = Handler(Looper.getMainLooper())
    /** The height of the status bar, cached for accurate positioning of overlays. */
    private var statusBarHeight = -1

    /**
     * Draws labeled bounding boxes for a list of [UIElement] objects on the screen.
     *
     * The boxes and labels are added as system overlays and will automatically be removed
     * after a specified duration. This function checks for the necessary overlay permission
     * before attempting to draw.
     *
     * @param elements The list of [UIElement] objects to visualize.
     * @param durationMs The time in milliseconds for the overlays to remain on screen.
     */
    fun drawLabeledBoxes(elements: List<UIElement>, durationMs: Long = 5000L) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            Log.w("DebugOverlayDrawer", "Cannot draw bounding boxes: 'Draw over other apps' permission not granted.")
            Log.w("DebugOverlayDrawer", "Please request permission using Settings.ACTION_MANAGE_OVERLAY_PERMISSION.")
            return
        }

        if (statusBarHeight < 0) {
            val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
            statusBarHeight = if (resourceId > 0) context.resources.getDimensionPixelSize(resourceId) else 0
        }

        val viewsToRemove = mutableListOf<View>()

        mainHandler.post {
            for (element in elements) {
                val bounds = BoundsParser.parse(element.bounds) ?: continue

                try {
                    val boxView = createBoxView(element)
                    val boxParams = createBoxLayoutParams(bounds)
                    windowManager.addView(boxView, boxParams)
                    viewsToRemove.add(boxView)

                    val labelView = createLabelView(element)
                    val labelParams = createLabelLayoutParams(bounds)
                    windowManager.addView(labelView, labelParams)
                    viewsToRemove.add(labelView)

                } catch (e: Exception) {
                    Log.e("DebugOverlayDrawer", "Failed to add debug view for element: ${element.text}", e)
                }
            }

            mainHandler.postDelayed({
                viewsToRemove.forEach { view ->
                    if (view.isAttachedToWindow) {
                        windowManager.removeView(view)
                    }
                }
            }, durationMs)
        }
    }

    /**
     * Creates a simple [View] to serve as the colored bounding box.
     *
     * The box is green for clickable elements and yellow for non-clickable elements.
     *
     * @param element The [UIElement] for which to create the box.
     * @return A [View] styled as a colored border.
     */
    private fun createBoxView(element: UIElement): View {
        return View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                val color = if (element.is_clickable) 0xFF00FF00.toInt() else 0xFFFFFF00.toInt()
                setStroke(6, color) // 6 pixel thick border
            }
        }
    }

    /**
     * Creates a [TextView] to serve as the label for a bounding box.
     *
     * The label text is determined by the element's text, content description, or resource ID.
     *
     * @param element The [UIElement] for which to create the label.
     * @return A configured [TextView] ready to be added as an overlay.
     */
    private fun createLabelView(element: UIElement): TextView {
        val description = when {
            !element.text.isNullOrBlank() -> element.text
            !element.content_description.isNullOrBlank() -> element.content_description
            else -> element.resource_id ?: "No ID"
        }

        return TextView(context).apply {
            text = description
            setBackgroundColor(Color.parseColor("#BB000000")) // Semi-transparent black
            setTextColor(Color.WHITE)
            textSize = 10f
            setPadding(6, 4, 6, 4)
        }
    }

    /**
     * Creates the [WindowManager.LayoutParams] for a bounding box view.
     *
     * These parameters define the size, position, and behavior of the overlay window.
     * The flags used make the overlay non-interactive.
     *
     * @param bounds The [Rect] defining the position and size of the box.
     * @return A configured [WindowManager.LayoutParams] object.
     */
    private fun createBoxLayoutParams(bounds: Rect): WindowManager.LayoutParams {
        val params = WindowManager.LayoutParams(
            bounds.width(),
            bounds.height(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = bounds.left
        params.y = bounds.top - statusBarHeight
        return params
    }

    /**
     * Creates the [WindowManager.LayoutParams] for a label view.
     *
     * These parameters position the label just above its corresponding bounding box.
     *
     * @param bounds The [Rect] of the corresponding bounding box, used for positioning.
     * @return A configured [WindowManager.LayoutParams] object.
     */
    private fun createLabelLayoutParams(bounds: Rect): WindowManager.LayoutParams {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = bounds.left
        params.y = (bounds.top - 35).coerceAtLeast(0) - statusBarHeight
        return params
    }
}

/*
--- HOW TO USE THIS CODE ---

1.  **Add Permission to AndroidManifest.xml:**
    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />

2.  **Check and Request Permission in your Activity/Fragment:**
    You must ask the user to grant the overlay permission before calling the drawer.

    ```kotlin
    private const val OVERLAY_PERMISSION_REQUEST_CODE = 123

    private fun checkAndRequestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE)
        }
    }
    ```

3.  **How your Agent would use it:**
    After your `XmlToAppMapParser` generates the list of `UIElement` objects:

    ```kotlin
    // In your agent's logic...
    fun visualizeCurrentScreen(context: Context, elements: List<UIElement>) {
        // Create an instance of the drawer
        val overlayDrawer = DebugOverlayDrawer(context)

        // Call the function to draw the boxes. They will disappear automatically.
        overlayDrawer.drawLabeledBoxes(elements)
    }
    ```
*/
