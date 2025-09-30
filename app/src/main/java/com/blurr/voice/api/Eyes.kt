/**
 * @file Eyes.kt
 * @brief Defines the Eyes class, which acts as an interface for perceiving the device screen.
 *
 * This file contains the implementation of the Eyes class. It uses the ScreenInteractionService
 * (an Accessibility Service) to capture screenshots, dump the UI hierarchy in various formats,
 * and retrieve information about the current state of the screen and foreground application.
 */
package com.blurr.voice.api

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.blurr.voice.RawScreenData
import com.blurr.voice.ScreenInteractionService

/**
 * Represents the "eyes" of the agent, providing capabilities to "see" the device screen.
 *
 * This class serves as a high-level API for interacting with the [ScreenInteractionService].
 * It abstracts the details of the accessibility service, offering simple methods to capture
 * the screen content and structure.
 *
 * @param context The Android application context. Although not used directly in the methods,
 *                it's kept as a constructor parameter for potential future use cases like
 *                accessing app-specific resources or directories.
 */
class Eyes(context: Context) {

    /**
     * Captures the current screen content as a bitmap image.
     *
     * This function relies on the [ScreenInteractionService] to take a screenshot.
     * It requires at least Android R (API level 30).
     *
     * @return A [Bitmap] object of the screenshot, or null if the accessibility service
     *         is not running or the screenshot fails.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    suspend fun openEyes(): Bitmap? {
        val service = ScreenInteractionService.instance
        if (service == null) {
            Log.e("Eyes", "Accessibility Service is not running!")
            return null
        }
        return service.captureScreenshot()
    }

    /**
     * Dumps the current UI layout to a raw XML string.
     *
     * This method retrieves the complete window hierarchy as an XML structure, which is useful
     * for machine parsing and analysis.
     *
     * @return A [String] containing the raw XML hierarchy, or a default empty hierarchy
     *         tag (`"<hierarchy/>"`) if the service is not available.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun openPureXMLEyes(): String {
        val service = ScreenInteractionService.instance
        if (service == null) {
            Log.e("AccessibilityController", "Accessibility Service is not running!")
            return "<hierarchy/>"
        }
        Log.d("AccessibilityController", "Requesting pure XML UI layout dump...")
        return service.dumpWindowHierarchy(true)
    }

    /**
     * Dumps the current UI layout in a simplified, human-readable markdown format.
     *
     * This method retrieves a processed version of the window hierarchy, often better suited
     * for analysis by LLMs or for debugging purposes.
     *
     * @return A [String] containing the simplified UI hierarchy, or a default empty hierarchy
     *         tag (`"<hierarchy/>"`) if the service is not available.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun openXMLEyes(): String {
        val service = ScreenInteractionService.instance
        if (service == null) {
            Log.e("AccessibilityController", "Accessibility Service is not running!")
            return "<hierarchy/>"
        }
        Log.d("AccessibilityController", "Requesting simplified UI layout dump...")
        return service.dumpWindowHierarchy()
    }

    /**
     * Checks if the on-screen keyboard is currently visible and available for typing.
     *
     * @return `true` if the keyboard is available, `false` otherwise or if the service
     *         is not running.
     */
    fun getKeyBoardStatus(): Boolean {
        val service = ScreenInteractionService.instance
        if (service == null) {
            Log.e("AccessibilityController", "Accessibility Service is not running!")
            return false
        }
        return service.isTypingAvailable()
    }

    /**
     * Retrieves a comprehensive set of raw data about the current screen.
     *
     * This function efficiently gathers the screen's XML hierarchy and scrollability
     * information in a single call to the accessibility service.
     *
     * @return A [RawScreenData] object containing the screen information, or a default
     *         empty object if the service is not running.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    suspend fun getRawScreenData(): RawScreenData? {
        val service = ScreenInteractionService.instance
        if (service == null) {
            Log.e("AccessibilityController", "Accessibility Service is not running!")
            return RawScreenData("", 0,0, 0, 0)
        }
        return service.getScreenAnalysisData()
    }

    /**
     * Gets the package name of the current foreground application.
     *
     * This is useful for identifying the context in which the agent is operating.
     *
     * @return A [String] representing the package name (e.g., "com.whatsapp"), or "Unknown"
     *         if the service is not running or the name cannot be determined.
     */
    fun getCurrentActivityName(): String {
        val service = ScreenInteractionService.instance
        if (service == null) {
            Log.e("AccessibilityController", "Accessibility Service is not running!")
            return "Unknown"
        }
        return service.getCurrentActivityName()
    }
}