package com.blurr.voice.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import androidx.core.content.ContextCompat
import com.blurr.voice.ConversationalAgentService
import com.blurr.voice.R

class FloatingPandaButtonService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingButton: View? = null

    companion object {
        private const val TAG = "FloatingPandaButton"
        var isRunning = false
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        Log.d(TAG, "Floating Panda Button Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Floating Panda Button Service starting...")

        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Cannot show floating button: 'Draw over other apps' permission not granted.")
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            showFloatingButton()
            if (floatingButton == null) {
                Log.w(TAG, "Failed to show floating button, stopping service")
                stopSelf()
                return START_NOT_STICKY
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error showing floating button", e)
            stopSelf()
            return START_NOT_STICKY
        }

        return START_STICKY
    }

    private fun showFloatingButton() {
        if (floatingButton != null) {
            Log.d(TAG, "Floating button already showing")
            return
        }

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        try {
            // Create the button programmatically
            floatingButton = createFloatingView()
            val button = floatingButton as Button

            // Set up the button click listener
            button.setOnClickListener {
                Log.d(TAG, "Floating Panda button clicked!")
                triggerPandaActivation()
            }

            val displayMetrics = resources.displayMetrics
            val margin = (16 * displayMetrics.density).toInt() // 16dp margin

            val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                windowType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                x = margin
                y = margin
            }

            windowManager?.addView(floatingButton, params)
            Log.d(TAG, "Floating Panda button added successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding floating button", e)
            floatingButton = null
        }
    }

    private fun createFloatingView(): Button {
        return Button(this).apply {
            text = "Hey Panda"
            // Prevent text from being all caps for a softer look
            isAllCaps = false
            setTextColor(Color.WHITE)
            // Use the new pill-shaped background
            background = ContextCompat.getDrawable(context, R.drawable.floating_panda_text_background)

            // Add elevation for a floating shadow effect
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                elevation = 8f * resources.displayMetrics.density
                // Remove the default button press animation for a cleaner look
                stateListAnimator = null
            }
        }
    }


    private fun triggerPandaActivation() {
        try {
            if (!ConversationalAgentService.isRunning) {
                Log.d(TAG, "Starting ConversationalAgentService from floating button")
                val serviceIntent = Intent(this, ConversationalAgentService::class.java)
                ContextCompat.startForegroundService(this, serviceIntent)
            } else {
                Log.d(TAG, "ConversationalAgentService is already running")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting ConversationalAgentService", e)
        }
    }

    private fun hideFloatingButton() {
        floatingButton?.let { button ->
            try {
                if (button.isAttachedToWindow) {
                    windowManager?.removeView(button)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error removing floating button", e)
            }
        }
        floatingButton = null
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Floating Panda Button Service destroying...")
        hideFloatingButton()
        isRunning = false
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}