package com.blurr.voice.utilities

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.blurr.voice.R
import com.blurr.voice.services.EnhancedWakeWordService
import kotlinx.coroutines.launch

class WakeWordManager(
    private val context: Context,
    private val permissionLauncher: ActivityResultLauncher<String>
) {

    fun handleWakeWordButtonClick(wakeWordButton: TextView) {
        if (EnhancedWakeWordService.isRunning) {
            stopWakeWordService()
        } else {
            startWakeWordService()
        }
        updateButtonState(wakeWordButton)
    }

    private fun startWakeWordService() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            val serviceIntent = Intent(context, EnhancedWakeWordService::class.java).apply {
                putExtra(EnhancedWakeWordService.EXTRA_USE_PORCUPINE, true)
            }
            ContextCompat.startForegroundService(context, serviceIntent)
            Toast.makeText(context, context.getString(R.string.wake_word_enabled, "Porcupine"), Toast.LENGTH_SHORT).show()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun stopWakeWordService() {
        context.stopService(Intent(context, EnhancedWakeWordService::class.java))
        Toast.makeText(context, context.getString(R.string.wake_word_disabled), Toast.LENGTH_SHORT).show()
    }

    fun updateButtonState(wakeWordButton: TextView) {
        wakeWordButton.text = if (EnhancedWakeWordService.isRunning) {
            context.getString(R.string.wake_word_disabled)
        } else {
            context.getString(R.string.enable_wake_word)
        }
    }
}