package com.blurr.voice.api

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.blurr.voice.ScreenInteractionService

/**
 * A rewritten Finger class that uses the AccessibilityService for all actions,
 * requiring no root access.
 */
class Finger(private val context: Context) {

    private val TAG = "Finger (Accessibility)"

    // A helper to safely get the service instance
    private val service: ScreenInteractionService?
        get() {
            val instance = ScreenInteractionService.instance
            if (instance == null) {
                Log.e(TAG, "ScreenInteractionService is not running or not connected!")
            }
            return instance
        }

    /**
     * Starts the ChatActivity within the app using a standard Android Intent.
     */
    fun goToChatRoom(message: String) {
        Log.d(TAG, "Opening ChatActivity with message: $message")
        try {
            val intent = Intent().apply {
                // Use the app's own context to find the activity class
                setClassName(context, "com.blurr.app.ChatActivity")
                putExtra("custom_message", message)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start ChatActivity. Make sure it's defined in your AndroidManifest.xml", e)
        }
    }

    /**
     * Opens an app directly using package manager (requires QUERY_ALL_PACKAGES permission).
     * This method is intended for debugging purposes only and should be disabled in production.
     * 
     * @param packageName The package name of the app to open
     * @return true if the app was successfully launched, false otherwise
     */
    fun openApp(packageName: String): Boolean {
        Log.d(TAG, "Attempting to open app with package: $packageName")
        return try {
            val packageManager = context.packageManager
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                Log.d(TAG, "Successfully launched app: $packageName")
                true
            } else {
                Log.e(TAG, "No launch intent found for package: $packageName")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open app: $packageName", e)
            false
        }
    }

    /**
     * Launch an arbitrary intent safely.
     */
    fun launchIntent(intent: Intent): Boolean {
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start intent: $intent", e)
            false
        }
    }

    /**
     * Taps a point on the screen.
     */
    fun tap(x: Int, y: Int) {
        Log.d(TAG, "Tapping at ($x, $y)")
        service?.clickOnPoint(x.toFloat(), y.toFloat())
    }

    /**
     * Swipes between two points on the screen.
     */
    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, duration: Int = 1000) {
        Log.d(TAG, "Swiping from ($x1, $y1) to ($x2, $y2)")
        service?.swipe(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat(), duration.toLong())
    }

    /**
     * Types text i)nto the focused input field. This is now much more efficient.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    fun type(text: String) {
        Log.d(TAG, "Typing text: $text")
        service?.typeTextInFocusedField(text)
        this.enter()
    }

    /**
     * Simulates pressing the 'Enter' key.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    fun enter() {
        Log.d(TAG, "Performing 'Enter' action")
        service?.performEnter()
    }

    /**
     * Navigates back.
     */
    fun back() {
        Log.d(TAG, "Performing 'Back' action")
        service?.performBack()
    }

    /**
     * Goes to the home screen.
     */
    fun home() {
        Log.d(TAG, "Performing 'Home' action")
        service?.performHome()
    }

    /**
     * Opens the app switcher (recents).
     */
    fun switchApp() {
        Log.d(TAG, "Performing 'App Switch' action")
        service?.performRecents()
    }
    /**
     * Scrolls the screen down by a given number of pixels.
     * This performs a swipe from bottom to top.
     *
     * @param pixels The number of pixels to scroll.
     * @param duration The duration of the swipe in milliseconds.
     */
    fun scrollDown(pixels: Int, duration: Int = 500) {
        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        // Define swipe path in the middle of the screen
        val x = screenWidth / 2
        // Start swipe from 80% down the screen to avoid navigation bars
        val y1 = (screenHeight * 0.8).toInt()
        // Calculate end point, ensuring it doesn't go below 0
        val y2 = (y1 - pixels).coerceAtLeast(0)

        Log.d(TAG, "Scrolling down by $pixels pixels: swipe from ($x, $y1) to ($x, $y2)")
        swipe(x, y1, x, y2, duration)
    }

    /**
     * Scrolls the screen up by a given number of pixels.
     * This performs a swipe from top to bottom.
     *
     * @param pixels The number of pixels to scroll.
     * @param duration The duration of the swipe in milliseconds.
     */
    fun scrollUp(pixels: Int, duration: Int = 500) {
        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        // Define swipe path in the middle of the screen
        val x = screenWidth / 2
        // Start swipe from 20% down the screen to avoid status bars
        val y1 = (screenHeight * 0.2).toInt()
        // Calculate end point, ensuring it doesn't go beyond screen height
        val y2 = (y1 + pixels).coerceAtMost(screenHeight)

        Log.d(TAG, "Scrolling up by $pixels pixels: swipe from ($x, $y1) to ($x, $y2)")
        swipe(x, y1, x, y2, duration)
    }
}