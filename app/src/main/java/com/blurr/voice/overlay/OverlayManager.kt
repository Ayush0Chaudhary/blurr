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
    private var bottomOverlayView: View? = null
    private var topOverlayView: View? = null
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
                OverlayDispatcher.activeContent.collect { contentMap ->
                    try {
                        // Update or remove views based on the map content
                        for (position in OverlayPosition.values()) {
                            val content = contentMap[position]
                            if (content != null) {
                                updateOverlayView(content)
                            } else {
                                removeOverlayView(position)
                            }
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
    private fun updateOverlayView(content: OverlayContent) {
        // Stop any pending auto-dismiss (This logic might need refinement for concurrent overlays if IDs overlap, but for now it's okay)
        // Ideally we should map runnables to IDs.
        Log.d("OverlayManager", "Updating overlay: ${content.text} at ${content.position}")
        // autoDismissRunnable?.let { mainHandler.removeCallbacks(it) } // TODO: Handle per-overlay auto-dismiss

        if (content.position == OverlayPosition.TOP) {
            if (topOverlayView == null) createView(OverlayPosition.TOP)
            (topOverlayView as? TextView)?.text = content.text
        } else {
            if (bottomOverlayView == null) createView(OverlayPosition.BOTTOM)
            (bottomOverlayView as? TextView)?.text = content.text
        }

        // Handle Auto-dismiss (e.g., for system info toasts)
        if (content.duration > 0) {
            val runnable = Runnable {
                OverlayDispatcher.dismiss(content.id)
            }
            mainHandler.postDelayed(runnable, content.duration)
        }
    }

    private fun removeOverlayView(position: OverlayPosition) {
        if (position == OverlayPosition.TOP) {
            removeView(topOverlayView)
            topOverlayView = null
        } else {
            removeView(bottomOverlayView)
            bottomOverlayView = null
        }
    }

    private fun createView(position: OverlayPosition) {
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

        val gravity = if (position == OverlayPosition.TOP) Gravity.TOP or Gravity.CENTER_HORIZONTAL else Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        val yPos = if (position == OverlayPosition.TOP) 150 else 250

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            this.gravity = gravity
            y = yPos
        }

        try {
            windowManager.addView(textView, params)
            if (position == OverlayPosition.TOP) {
                topOverlayView = textView
            } else {
                bottomOverlayView = textView
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun removeOverlayInternal() {
        removeView(bottomOverlayView)
        bottomOverlayView = null
        removeView(topOverlayView)
        topOverlayView = null
    }

    private fun removeView(view: View?) {
        view?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                // Ignore if view is not attached or already removed
            }
        }
    }
}