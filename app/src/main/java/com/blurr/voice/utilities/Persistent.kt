/**
 * @file Persistent.kt
 * @brief Provides utility functions for saving and loading data to and from files.
 *
 * This file contains the `Persistent` class, which offers simple methods for file I/O,
 * specifically for handling "tips" text files and saving debug bitmaps to public storage.
 */
package com.blurr.voice.utilities

import android.graphics.Bitmap
import android.os.Environment
import android.util.Log

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A utility class for handling persistent file storage operations.
 */
class Persistent {

    /**
     * Saves a string of tips to a specified file.
     *
     * @param file The [File] object to write to.
     * @param tips The string content to be saved.
     */
    fun saveTipsToFile(file: File, tips: String) {
        file.writeText(tips)
    }

    /**
     * Loads a string of tips from a specified file.
     *
     * @param file The [File] object to read from.
     * @return The content of the file as a string, or an empty string if the file does not exist.
     */
    fun loadTipsFromFile(file: File): String {
        return if (file.exists()) file.readText() else ""
    }

    /**
     * Saves a [Bitmap] image to the public "Pictures/ScreenAgent" directory for debugging purposes.
     *
     * The saved file will be named with a timestamp (e.g., "SS_20230101_120000.png").
     *
     * @param bitmap The [Bitmap] to be saved.
     */
    fun saveBitmapForDebugging(bitmap: Bitmap) {
        val publicPicturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val screenshotDir = File(publicPicturesDir, "ScreenAgent")
        screenshotDir.mkdirs()
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(screenshotDir, "SS_$timestamp.png")
        try {
            val fos = java.io.FileOutputStream(file)
            fos.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            Log.d("MainActivity", "Debug screenshot saved to ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to save debug screenshot", e)
        }
    }
}