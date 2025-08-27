package com.blurr.voice.utilities

import android.graphics.Bitmap
import android.os.Environment
import android.util.Log

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Persistent {


    fun saveTipsToFile(file: File, tips: String) {
        file.writeText(tips)
    }
    fun loadTipsFromFile(file: File): String {
        return if (file.exists()) file.readText() else ""
    }

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