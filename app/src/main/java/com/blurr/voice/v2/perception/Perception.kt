/**
 * @file Perception.kt
 * @brief The main coordinator for the agent's screen perception module.
 *
 * This file contains the `Perception` class, which serves as the entry point for the "SENSE"
 * part of the agent's loop. It uses various components to observe and analyze the device
 * screen to create a structured understanding of the current state.
 */
package com.blurr.voice.v2.perception

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.blurr.voice.RawScreenData
import com.blurr.voice.api.Eyes
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * The Perception module, responsible for observing the device screen and creating a
 * structured analysis of its current state.
 *
 * This class orchestrates the process of "seeing" the screen. It uses the [Eyes] component
 * to capture raw data (like the accessibility node hierarchy) and the [SemanticParser] to
 * transform that data into a structured, LLM-friendly format.
 *
 * @param eyes An instance of the [Eyes] class, which provides access to raw screen data.
 * @param semanticParser An instance of the [SemanticParser] used to process the raw XML hierarchy.
 */
@RequiresApi(Build.VERSION_CODES.R)
class Perception(
    private val eyes: Eyes,
    private val semanticParser: SemanticParser
) {

    /**
     * Analyzes the current screen to produce a comprehensive [ScreenAnalysis] object.
     *
     * This is the main entry point for the perception module. It concurrently fetches various
     * pieces of screen data (XML, keyboard status, activity name) for efficiency and then
     * synthesizes them into a single [ScreenAnalysis] object. This object contains a
     * simplified UI representation for the LLM, a map of elements for interaction, and other
     * relevant metadata.
     *
     * @param previousState An optional set of node identifiers from the previous state, which can be
     * used by the [SemanticParser] to detect new or changed UI elements.
     * @return A [ScreenAnalysis] object containing the complete state of the screen.
     */
    suspend fun analyze(previousState: Set<String>? = null): ScreenAnalysis {
        return coroutineScope {
            val rawDataDeferred = async { eyes.getRawScreenData() }
            val keyboardStatusDeferred = async { eyes.getKeyBoardStatus() }
            val currentActivity = async { eyes.getCurrentActivityName() }

            val rawData = rawDataDeferred.await() ?: RawScreenData(
                xml = "<hierarchy error=\"service not available\"/>",
                pixelsAbove = 0,
                pixelsBelow = 0,
                screenWidth = 0,
                screenHeight = 0
            )
            val isKeyboardOpen = keyboardStatusDeferred.await()
            val activityName = currentActivity.await()

            // Parse the XML from the raw data.
            Log.d("ScreenAnal", rawData.xml)
            val (uiRepresentation, elementMap) = semanticParser.toHierarchicalString(
                rawData.xml,
                previousState,
                rawData.screenWidth,
                rawData.screenHeight
            )

            val finalUiRepresentation = addScrollIndicators(uiRepresentation, rawData)

            ScreenAnalysis(
                uiRepresentation = finalUiRepresentation,
                isKeyboardOpen = isKeyboardOpen,
                activityName = activityName,
                elementMap = elementMap,
                scrollUp = rawData.pixelsAbove,
                scrollDown = rawData.pixelsBelow
            )
        }
    }

    /**
     * Adds "scroll up/down" indicators to the UI representation string.
     */
    private fun addScrollIndicators(uiString: String, rawData: RawScreenData): String {
        var representation = uiString
        val hasContentAbove = rawData.pixelsAbove > 0
        val hasContentBelow = rawData.pixelsBelow > 0

        if (representation.isNotBlank()) {
            representation = if (hasContentAbove) {
                "... ${rawData.pixelsAbove} pixels above - scroll up to see more ...\n$representation"
            } else {
                "[Start of page]\n$representation"
            }
            representation = if (hasContentBelow) {
                "$representation\n... ${rawData.pixelsBelow} pixels below - scroll down to see more ..."
            } else {
                "$representation\n[End of page]"
            }
        } else {
            representation = "The screen is empty or contains no interactive elements."
        }
        return representation
    }
}