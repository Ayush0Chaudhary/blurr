/**
 * @file FloatingPandaButtonService.kt
 * @brief A service to display a persistent floating button for activating the agent.
 *
 * This service is responsible for creating and managing a floating button overlay that appears
 * on top of other applications. This button provides a manual way to trigger the
 * [ConversationalAgentService] and serves as a fallback when wake word detection is
 * unavailable or has failed.
 */
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

/**
 * A [Service] that displays a floating button over other apps.
 *
 * This service requires the "Draw over other apps" permission. When started, it adds a
 * custom-styled button to the screen using the [WindowManager]. Tapping this button
 * starts the [ConversationalAgentService].
 */
class FloatingPandaButtonService : Service() {

    /** The WindowManager service used to add and remove the floating view. */
    private var windowManager: WindowManager? = null
    /** The floating button view instance. */
    private var floatingButton: View? = null

    /**
     * Companion object for constants and static properties.
     */
    companion object {
        private const val TAG = "FloatingPandaButton"
        /** A static flag to indicate if the service is currently running. */
        var isRunning = false
    }

    /**
     * Called when the service is first created.
     */
    override fun onCreate() {
        super.onCreate()
        isRunning = true
        Log.d(TAG, "Floating Panda Button Service created")
    }

    /**
     * Called every time the service is started.
     *
     * This method checks for the necessary overlay permission and then calls
     * [showFloatingButton] to display the button.
     *
     * @return [START_STICKY] to indicate that the service should be restarted if it's killed.
     */
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

    /**
     * Creates and displays the floating button on the screen.
     *
     * This method initializes the [WindowManager], creates the button view using
     * [createFloatingView], sets up its layout parameters, and adds it to the window.
     */
    private fun showFloatingButton() {
        if (floatingButton != null) {
            Log.d(TAG, "Floating button already showing")
            return
        }

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        try {
            floatingButton = createFloatingView()
            val button = floatingButton as Button

            button.setOnClickListener {
                Log.d(TAG, "Floating Panda button clicked!")
                triggerPandaActivation()
            }

            val displayMetrics = resources.displayMetrics
            val margin = (16 * displayMetrics.density).toInt()

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

    /**
     * Creates and styles the floating button view.
     *
     * @return A styled [Button] instance.
     */
    private fun createFloatingView(): Button {
        return Button(this).apply {
            text = "Hey Panda"
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = ContextCompat.getDrawable(context, R.drawable.floating_panda_text_background)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                elevation = 8f * resources.displayMetrics.density
                stateListAnimator = null
            }
        }
    }


    /**
     * Triggers the activation of the main conversational agent service.
     */
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

    /**
     * Removes the floating button from the screen.
     */
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

    /**
     * Called when the service is being destroyed.
     * Ensures the floating button is removed.
     */
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Floating Panda Button Service destroying...")
        hideFloatingButton()
        isRunning = false
    }

    /**
     * This service does not support binding, so this method returns null.
     */
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}