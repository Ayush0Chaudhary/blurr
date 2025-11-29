package com.blurr.voice.overlay

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

object OverlayDispatcher {

    private val _activeContent = MutableStateFlow<OverlayContent?>(null)
    val activeContent: StateFlow<OverlayContent?> = _activeContent.asStateFlow()

    /**
     * Request to show an overlay.
     * Returns a unique ID that you must use to remove it later (if indefinite).
     */
    fun show(text: String, priority: OverlayPriority, duration: Long = 0L): String {
        val current = _activeContent.value
        Log.d("OverlayDispatcher", "show: $text, $priority, $duration");
        val id = UUID.randomUUID().toString()
        val newContent = OverlayContent(id, text, priority, duration)
        _activeContent.value = newContent
        return id
    }

    /**
     * Remove an overlay. You must pass the ID you got when you created it.
     * This prevents TTS from accidentally removing a System Error.
     */
    fun dismiss(id: String) {
        val current = _activeContent.value
        if (current != null && current.id == id) {
            _activeContent.value = null
        }
    }

    fun clearAll() {
        _activeContent.value = null
    }
}