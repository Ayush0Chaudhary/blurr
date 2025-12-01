package com.blurr.voice.api

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import java.io.File
import android.util.Log
import androidx.annotation.RequiresApi
import com.blurr.voice.RawScreenData
import com.blurr.voice.ScreenInteractionService

class Eyes(context: Context) {

    // This now points to the public directory where your screenshots will be saved.
    private val publicPicturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)

    // This will now point to the specific screenshot file after it's created.
    private var latestScreenshotFile: File? = null

    // The path for the XML file can remain internal as you don't need to view it manually.
    private val xmlFile: File = File(context.filesDir, "window_dump.xml")

    /**
     * Takes a screenshot and saves it to the public Pictures/ScreenAgent directory.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    suspend fun openEyes(): Bitmap? {
        val service = ScreenInteractionService.instance
        if (service == null) {
            Log.e("Eyes", "Accessibility Service is not running!")
            return null
        }
        // Directly call the suspend function on the service
        return service.captureScreenshot()
    }

    /**
     * Dumps the current UI layout to an XML file using the Accessibility Service.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun openPureXMLEyes(): String {
        val service = ScreenInteractionService.instance
        if (service == null) {
            Log.e("AccessibilityController", "Accessibility Service is not running!")
            return "<hierarchy/>"
        }
        Log.d("AccessibilityController", "Requesting UI layout dump...")
        return service.dumpWindowHierarchy(true)
    }

    /**
     * Dumps the current UI layout in a more readable markdown format.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun openXMLEyes(): String {
        val service = ScreenInteractionService.instance
        if (service == null) {
            Log.e("AccessibilityController", "Accessibility Service is not running!")
            return "<hierarchy/>"
        }
        Log.d("AccessibilityController", "Requesting UI layout dump...")
        return service.dumpWindowHierarchy()
    }

    fun getKeyBoardStatus(): Boolean {
        val service = ScreenInteractionService.instance
        if (service == null) {
            Log.e("AccessibilityController", "Accessibility Service is not running!")
            return false
        }
        return service.isTypingAvailable()
    }

    /**
     * Gets all raw screen data (XML, scroll info) in a single, efficient call.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    suspend fun getRawScreenData(): RawScreenData? {
        val service = ScreenInteractionService.instance
        if (service == null) {
            Log.e("AccessibilityController", "Accessibility Service is not running!")
            return RawScreenData(null, 0,0, 0, 0)
        }
        return service.getScreenAnalysisData()
    }


    @RequiresApi(Build.VERSION_CODES.R)
    suspend fun getAllRawScreenData(): RawScreenData? {
        val service = ScreenInteractionService.instance
        if (service == null) {
            Log.e("AccessibilityController", "Accessibility Service is not running!")
            return RawScreenData(null, 0,0, 0, 0)
        }
        return service.getAllScreenAnalysisData()
    }

    /**
     * Gets the package name of the current foreground activity.
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