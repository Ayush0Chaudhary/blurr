/**
 * @file ScreenAnalysis.kt
 * @brief Defines the data structure for holding a complete analysis of the device screen.
 *
 * This file contains the `ScreenAnalysis` data class, which is the primary output of the
 * `Perception` module.
 */
package com.blurr.voice.v2.perception

import kotlinx.serialization.Serializable

/**
 * A data class that holds a complete, structured analysis of the screen at a single point in time.
 * This is the primary data structure returned by the [Perception] module.
 *
 * @property uiRepresentation A clean, LLM-friendly string describing the visible UI elements.
 * @property isKeyboardOpen `true` if the software keyboard is likely visible on the screen.
 * @property activityName The name of the current foreground activity, providing context.
 * @property elementMap A map from an integer ID (e.g., the `[1]` in the `uiRepresentation`) to the
 *                      actual [XmlNode] object. This allows the `ActionExecutor` to look up an
 *                      element's properties (like its bounds) for interaction.
 * @property scrollUp The number of pixels of content available by scrolling up. A value greater than 0
 *                    indicates that there is more content off-screen in the upward direction.
 * @property scrollDown The number of pixels of content available by scrolling down. A value greater than 0
 *                      indicates that there is more content off-screen in the downward direction.
 */
@Serializable
data class ScreenAnalysis(
    val uiRepresentation: String,
    val isKeyboardOpen: Boolean,
    val activityName: String,
    val elementMap: Map<Int, XmlNode>,
    val scrollUp: Int?,
    val scrollDown: Int?
)
