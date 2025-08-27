package com.blurr.voice.v2.perception

import android.os.Build
import androidx.annotation.RequiresApi
import com.blurr.voice.RawScreenData
import com.blurr.voice.api.Eyes
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async

/**
 * The Perception module is responsible for observing the device screen and
 * creating a structured analysis of the current state.
 *
 * @param eyes An instance of the Eyes class to see the screen (XML, screenshot).
 * @param semanticParser An instance of the SemanticParser to make sense of the XML.
 */
@RequiresApi(Build.VERSION_CODES.R)
class Perception(
    private val eyes: Eyes,
    private val semanticParser: SemanticParser
) {

    /**
     * Analyzes the current screen to produce a comprehensive ScreenAnalysis object.
     * This is the main entry point for this module.
     *
     * It performs multiple observation actions concurrently for efficiency.
     *
     * @param previousState An optional set of node identifiers from the previous state,
     * used to detect new UI elements.
     * @return A ScreenAnalysis object containing the complete state of the screen.
     */
    suspend fun analyze(previousState: Set<String>? = null): ScreenAnalysis {
        return coroutineScope {
        val screenshotDeferred = async { eyes.openEyes() }
        val rawDataDeferred = async { eyes.getRawScreenData() }
        val keyboardStatusDeferred = async { eyes.getKeyBoardStatus() }
        val currentActivity = async { eyes.getCurrentActivityName() }
        val screenshot = screenshotDeferred.await()
        val rawData = rawDataDeferred.await() ?: RawScreenData(
            "<hierarchy error=\"service not available\"/>", 0, 0, 0,0
        )
        val isKeyboardOpen = keyboardStatusDeferred.await()

        // Assume you have a way to get this
        val activityName = currentActivity.await()

        // Parse the XML from the raw data

        val parseResult = semanticParser.toHierarchicalString(rawData.xml, previousState, rawData.screenWidth, rawData.screenHeight)
        var uiRepresentation = parseResult.first
        val elementMap = parseResult.second

        val hasContentAbove = rawData.pixelsAbove > 0
        val hasContentBelow = rawData.pixelsBelow > 0

        if (uiRepresentation.isNotBlank()) {
            if (hasContentAbove) {
                uiRepresentation = "... ${rawData.pixelsAbove} pixels above - scroll up to see more ...\n$uiRepresentation"
            } else {
                uiRepresentation = "[Start of page]\n$uiRepresentation"
            }
            if (hasContentBelow) {
                uiRepresentation = "$uiRepresentation\n... ${rawData.pixelsBelow} pixels below - scroll down to see more ..."
            } else {
                uiRepresentation = "$uiRepresentation\n[End of page]"
            }
        } else {
            uiRepresentation = "The screen is empty or contains no interactive elements."
        }

        ScreenAnalysis(
            uiRepresentation = uiRepresentation, // The newly formatted string
            isKeyboardOpen = isKeyboardOpen,
            activityName = activityName,
            elementMap = elementMap,
            scrollUp = rawData.pixelsAbove, // Store the raw numbers
            scrollDown = rawData.pixelsBelow  // Store the raw numbers
        )
    }
    }
}