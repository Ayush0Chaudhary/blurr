package com.blurr.voice.overlay

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

object OverlayDispatcher {

    private val _activeContent = MutableStateFlow<Map<OverlayPosition, OverlayContent>>(emptyMap())
    val activeContent: StateFlow<Map<OverlayPosition, OverlayContent>> = _activeContent.asStateFlow()

    /**
     * Request to show an overlay.
     * Returns a unique ID that you must use to remove it later (if indefinite).
     */
    fun show(text: String, priority: OverlayPriority, duration: Long = 0L, position: OverlayPosition = OverlayPosition.BOTTOM): String {
        Log.d("OverlayDispatcher", "show: $text, $priority, $duration, $position");
        val id = UUID.randomUUID().toString()
        val newContent = OverlayContent(id, text, priority, duration, position)
        
        val currentMap = _activeContent.value.toMutableMap()
        currentMap[position] = newContent
        _activeContent.value = currentMap
        
        return id
    }

    /**
     * Remove an overlay. You must pass the ID you got when you created it.
     * This prevents TTS from accidentally removing a System Error.
     */
    fun dismiss(id: String) {
        val currentMap = _activeContent.value.toMutableMap()
        val iterator = currentMap.iterator()
        var changed = false
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value.id == id) {
                iterator.remove()
                changed = true
            }
        }
        if (changed) {
            _activeContent.value = currentMap
        }
    }

    fun clearAll() {
        _activeContent.value = emptyMap()
    }
}