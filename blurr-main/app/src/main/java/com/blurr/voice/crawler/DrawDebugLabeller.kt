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
 * A helper object to parse the bounds string from the XML into a Rect object.
 */
object BoundsParser {
    // Regex to capture the four numbers from a string like "[x1,y1][x2,y2]"
    private val PATTERN = Pattern.compile("\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]")

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
 * A utility class for drawing temporary, labeled bounding boxes over other apps.
 * This is extremely useful for debugging agent perception.
 */
class DebugOverlayDrawer(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var statusBarHeight = -1

    /**
     * Draws labeled bounding boxes for each UIElement on the screen.
     * The boxes and labels will automatically disappear after a short duration.
     *
     * @param elements The list of UIElements to visualize.
     * @param durationMs The time in milliseconds for the boxes to remain on screen.
     */
    fun drawLabeledBoxes(elements: List<UIElement>, durationMs: Long = 5000L) {
        // This check is crucial. The function will not work without the overlay permission.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            Log.w("DebugOverlayDrawer", "Cannot draw bounding boxes: 'Draw over other apps' permission not granted.")
            Log.w("DebugOverlayDrawer", "Please request permission using Settings.ACTION_MANAGE_OVERLAY_PERMISSION.")
            return
        }

        // Calculate status bar height once for accurate positioning.
        if (statusBarHeight < 0) {
            val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
            statusBarHeight = if (resourceId > 0) context.resources.getDimensionPixelSize(resourceId) else 0
        }

        val viewsToRemove = mutableListOf<View>()

        // All UI operations must be done on the main thread.
        mainHandler.post {
            for (element in elements) {
                val bounds = BoundsParser.parse(element.bounds) ?: continue // Skip if bounds are invalid

                try {
                    // --- Create and add the Bounding Box View ---
                    val boxView = createBoxView(element)
                    val boxParams = createBoxLayoutParams(bounds)
                    windowManager.addView(boxView, boxParams)
                    viewsToRemove.add(boxView)

                    // --- Create and add the Label View ---
                    val labelView = createLabelView(element)
                    val labelParams = createLabelLayoutParams(bounds)
                    windowManager.addView(labelView, labelParams)
                    viewsToRemove.add(labelView)

                } catch (e: Exception) {
                    Log.e("DebugOverlayDrawer", "Failed to add debug view for element: ${element.text}", e)
                }
            }

            // Schedule the removal of all created views after the specified duration.
            mainHandler.postDelayed({
                viewsToRemove.forEach { view ->
                    // Check if the view is still attached to the window before trying to remove it.
                    if (view.isAttachedToWindow) {
                        windowManager.removeView(view)
                    }
                }
            }, durationMs)
        }
    }

    private fun createBoxView(element: UIElement): View {
        return View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                // Green for clickable elements, Yellow for non-clickable but visible elements.
                val color = if (element.is_clickable) 0xFF00FF00.toInt() else 0xFFFFFF00.toInt()
                setStroke(6, color) // 6 pixel thick border
            }
        }
    }

    private fun createLabelView(element: UIElement): TextView {
        // Create a descriptive label, preferring text over content description.
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

    private fun createBoxLayoutParams(bounds: Rect): WindowManager.LayoutParams {
        val params = WindowManager.LayoutParams(
            bounds.width(),
            bounds.height(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // These flags make the overlay non-interactive.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = bounds.left
        // Adjust for status bar height to align perfectly with screen elements.
        params.y = bounds.top - statusBarHeight
        return params
    }

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
        // Place the label just above the bounding box, ensuring it doesn't go off-screen.
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
