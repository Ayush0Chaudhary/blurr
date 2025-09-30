/**
 * @file Finger.kt
 * @brief Defines the Finger class, which provides an API for simulating user touch interactions and device navigation.
 *
 * This file contains the implementation of the Finger class, which acts as a high-level interface
 * for performing actions like tapping, swiping, typing, and navigating the Android OS. It relies
 * on the [ScreenInteractionService] (an Accessibility Service) to execute these actions without
 * requiring root access.
 */
package com.blurr.voice.api

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.blurr.voice.ScreenInteractionService

/**
 * Simulates user "finger" interactions with the device screen and system.
 *
 * This class provides a set of methods to programmatically control the device,
 * abstracting the underlying calls to the [ScreenInteractionService]. It allows the agent
 * to perform gestures, type text, and navigate the system UI.
 *
 * @param context The Android application context, used for operations like launching intents.
 */
class Finger(private val context: Context) {

    private val TAG = "Finger (Accessibility)"

    /**
     * A private computed property to safely get the singleton instance of the [ScreenInteractionService].
     * Logs an error if the service is not available.
     */
    private val service: ScreenInteractionService?
        get() {
            val instance = ScreenInteractionService.instance
            if (instance == null) {
                Log.e(TAG, "ScreenInteractionService is not running or not connected!")
            }
            return instance
        }

    /**
     * Starts the ChatActivity within the app, passing a custom message.
     *
     * @param message The message to be passed to the ChatActivity via an intent extra.
     */
    fun goToChatRoom(message: String) {
        Log.d(TAG, "Opening ChatActivity with message: $message")
        try {
            val intent = Intent().apply {
                // Use the app's own context to find the activity class
                setClassName(context, "com.blurr.voice.ChatActivity")
                putExtra("custom_message", message)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start ChatActivity. Make sure it's defined in your AndroidManifest.xml", e)
        }
    }

    /**
     * Opens an application using its package name.
     *
     * This method uses the package manager to find and launch the main intent for the given package.
     * Note: This may require the `QUERY_ALL_PACKAGES` permission on newer Android versions.
     *
     * @param packageName The package name of the app to open (e.g., "com.android.chrome").
     * @return `true` if the app was successfully launched, `false` otherwise.
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
     * Launches an arbitrary [Intent] safely.
     *
     * Adds the `FLAG_ACTIVITY_NEW_TASK` flag before starting the activity.
     *
     * @param intent The intent to launch.
     * @return `true` if the intent was launched successfully, `false` otherwise.
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
     * Taps a specific coordinate on the screen.
     *
     * @param x The x-coordinate of the tap location.
     * @param y The y-coordinate of the tap location.
     */
    fun tap(x: Int, y: Int) {
        Log.d(TAG, "Tapping at ($x, $y)")
        service?.clickOnPoint(x.toFloat(), y.toFloat())
    }

    /**
     * Performs a long press (press and hold) at a specific coordinate on the screen.
     *
     * @param x The x-coordinate of the long press location.
     * @param y The y-coordinate of the long press location.
     */
    fun longPress(x: Int, y: Int) {
        Log.d(TAG, "Long pressing at ($x, $y)")
        service?.longClickOnPoint(x.toFloat(), y.toFloat())
    }

    /**
     * Swipes from a starting coordinate to an ending coordinate.
     *
     * @param x1 The starting x-coordinate.
     * @param y1 The starting y-coordinate.
     * @param x2 The ending x-coordinate.
     * @param y2 The ending y-coordinate.
     * @param duration The duration of the swipe gesture in milliseconds.
     */
    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, duration: Int = 1000) {
        Log.d(TAG, "Swiping from ($x1, $y1) to ($x2, $y2)")
        service?.swipe(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat(), duration.toLong())
    }

    /**
     * Types the given text into the currently focused input field.
     *
     * After typing, this method automatically triggers an 'Enter' action.
     * Requires Android R (API level 30) or higher.
     *
     * @param text The text to be typed.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    fun type(text: String) {
        Log.d(TAG, "Typing text: $text")
        service?.typeTextInFocusedField(text)
        this.enter()
    }

    /**
     * Simulates pressing the 'Enter' key on the keyboard.
     *
     * This is useful for submitting forms or adding new lines in a text field.
     * Requires Android R (API level 30) or higher.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    fun enter() {
        Log.d(TAG, "Performing 'Enter' action")
        service?.performEnter()
    }

    /**
     * Triggers the global 'Back' navigation action.
     */
    fun back() {
        Log.d(TAG, "Performing 'Back' action")
        service?.performBack()
    }

    /**
     * Triggers the global 'Home' action, returning to the device's home screen.
     */
    fun home() {
        Log.d(TAG, "Performing 'Home' action")
        service?.performHome()
    }

    /**
     * Opens the recent apps switcher.
     */
    fun switchApp() {
        Log.d(TAG, "Performing 'App Switch' action")
        service?.performRecents()
    }

    /**
     * Scrolls the content of the screen upwards by a specified amount, revealing content below.
     *
     * This is achieved by performing a swipe gesture from bottom to top.
     *
     * @param pixels The vertical distance in pixels to scroll.
     * @param duration The duration of the scroll gesture in milliseconds.
     */
    fun scrollUp(pixels: Int, duration: Int = 500) {
        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        // Define swipe path in the middle of the screen
        val x = screenWidth / 2
        // Start swipe from 80% down the screen to avoid navigation bars
        val y1 = (screenHeight * 0.8).toInt()
        // Calculate end point, ensuring it doesn't go below 0
        val y2 = (y1 - pixels).coerceAtLeast(0)

        Log.d(TAG, "Scrolling content up by $pixels pixels: swipe from ($x, $y1) to ($x, $y2)")
        swipe(x, y1, x, y2, duration)
    }

    /**
     * Scrolls the content of the screen downwards by a specified amount, revealing content above.
     *
     * This is achieved by performing a swipe gesture from top to bottom.
     *
     * @param pixels The vertical distance in pixels to scroll.
     * @param duration The duration of the scroll gesture in milliseconds.
     */
    fun scrollDown(pixels: Int, duration: Int = 500) {
        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        // Define swipe path in the middle of the screen
        val x = screenWidth / 2
        // Start swipe from 20% down the screen to avoid status bars
        val y1 = (screenHeight * 0.2).toInt()
        // Calculate end point, ensuring it doesn't go beyond screen height
        val y2 = (y1 + pixels).coerceAtMost(screenHeight)

        Log.d(TAG, "Scrolling content down by $pixels pixels: swipe from ($x, $y1) to ($x, $y2)")
        swipe(x, y1, x, y2, duration)
    }
}