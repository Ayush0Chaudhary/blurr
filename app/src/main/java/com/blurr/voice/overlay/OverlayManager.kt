package com.blurr.voice.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException

class OverlayManager private constructor(context: Context) {

    private val applicationContext = context.applicationContext
    private val windowManager by lazy { applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val clientCount = AtomicInteger(0)
    private var overlayView: View? = null
    private var observeJob: Job? = null
    private var autoDismissRunnable: Runnable? = null

    companion object {
        @Volatile private var INSTANCE: OverlayManager? = null
        fun getInstance(context: Context): OverlayManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: OverlayManager(context).also { INSTANCE = it }
            }
        }
    }
    /**
     * Called by anyone who wants the overlay to be alive.
     * Increments the user count.
     */
    @Synchronized // prevent race conditions
    fun startObserving() {
        val currentCount = clientCount.incrementAndGet()
        Log.d("OverlayManager", "Client added. Total clients: $currentCount")

        // Only start the job if it's not already running
        if (observeJob?.isActive == true) return

        observeJob = scope.launch {
            try {
                OverlayDispatcher.activeContent.collect { content ->
                    try {
                        if (content == null) {
                            removeOverlayInternal()
                        } else {
                            updateOverlay(content)
                        }
                    } catch (e: Exception) {
                        Log.e("OverlayManager", "UI Update Error", e)
                    }
                }
            } catch (e: CancellationException) {
                Log.d("OverlayManager", "Observer cancelled normally.")
            } catch (e: Exception) {
                Log.e("OverlayManager", "Fatal Observer Error", e)
            }
        }
    }

    /**
     * Called when a component is done with the overlay.
     * Decrements the user count. Only kills the overlay if count reaches 0.
     */
    @Synchronized
    fun stopObserving() {
        val remainingClients = clientCount.decrementAndGet()
        Log.d("OverlayManager", "Client removed. Remaining clients: $remainingClients")

        if (remainingClients <= 0) {
            // Only actually stop if NOBODY needs it anymore
            Log.d("OverlayManager", "No clients left. Stopping observer.")
            clientCount.set(0) // Safety reset
            observeJob?.cancel()
            observeJob = null // Clear the reference so it can restart later
            removeOverlayInternal()
        } else {
            Log.d("OverlayManager", "Observer kept alive for other clients.")
        }
    }
    private fun updateOverlay(content: OverlayContent) {
        // Stop any pending auto-dismiss
        Log.d("OverlayManager", "Stopping auto-dismiss")
        autoDismissRunnable?.let { mainHandler.removeCallbacks(it) }
        Log.d("OverlayManager", content.text)

        // If view doesn't exist, create it
        if (overlayView == null) {
            createView()
        }

        // Update the text
        (overlayView as? TextView)?.text = content.text

        // Handle Auto-dismiss (e.g., for system info toasts)
        if (content.duration > 0) {
            autoDismissRunnable = Runnable {
                OverlayDispatcher.dismiss(content.id)
            }
            mainHandler.postDelayed(autoDismissRunnable!!, content.duration)
        }
    }

    private fun createView() {
        val textView = TextView(applicationContext).apply {
            // Styling... (Same as your original code)
            background = GradientDrawable().apply {
                setColor(0xCC000000.toInt())
                cornerRadius = 24f
            }
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 16f
            setPadding(24, 16, 24, 16)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 250
        }

        try {
            windowManager.addView(textView, params)
            overlayView = textView
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun removeOverlayInternal() {
        overlayView?.let {
            if (it.isAttachedToWindow) {
                try {
                    windowManager.removeView(it)
                } catch (e: Exception) {}
            }
        }
        overlayView = null
    }
}