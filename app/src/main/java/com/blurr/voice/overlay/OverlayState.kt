package com.blurr.voice.overlay

enum class OverlayPriority(val level: Int) {
    CAPTION(1),
    TASKS(1),
}

enum class OverlayPosition {
    TOP,
    BOTTOM
}

data class OverlayContent(
    val id: String,
    val text: String,
    val priority: OverlayPriority,
    val duration: Long = 0L,
    val position: OverlayPosition = OverlayPosition.BOTTOM
)