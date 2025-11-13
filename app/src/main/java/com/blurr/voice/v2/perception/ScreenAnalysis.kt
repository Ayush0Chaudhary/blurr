package com.blurr.voice.v2.perception

import android.graphics.Bitmap
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.serialization.Serializable

/**
 * A data class that holds a complete analysis of the screen at a single point in time.
 * This is the primary output of the Perception module.
 *
 * @param uiRepresentation A clean, LLM-friendly string describing the UI elements.
 * @param screenshot A bitmap of the current screen, used for vision models.
 * @param isKeyboardOpen True if the software keyboard is likely visible.
 * @param activityName The name of the current foreground activity for context.
 * @param elementMap A map from the integer ID `[1]` in the uiRepresentation to the
 * actual XmlNode object, allowing the ActionExecutor to find center coordinates.
 */
data class ScreenAnalysis(
    val uiRepresentation: String,
    val isKeyboardOpen: Boolean,
    val activityName: String,
    val elementMap: Map<Int, AccessibilityNodeInfo>,
    val scrollUp: Int?,
    val scrollDown: Int?
)
