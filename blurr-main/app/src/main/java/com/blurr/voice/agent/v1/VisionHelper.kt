package com.blurr.voice.agent.v1

import android.graphics.Bitmap
import android.util.Log
import com.blurr.voice.utilities.addResponse
import com.blurr.voice.utilities.addResponsePrePost

/**
 * Helper class for vision mode operations.
 * Centralizes the logic for handling different vision modes.
 */
object VisionHelper {
    
    /**
     * Creates a chat response based on the vision mode.
     * For XML mode: text only
     * For Screenshot mode: text + screenshot
     */
    fun createChatResponse(
        role: String,
        prompt: String,
        chatHistory: List<Pair<String, List<Any>>>,
        config: AgentConfig,
        screenshot: Bitmap? = null
    ): List<Pair<String, List<Any>>> {
        return if (config.isXmlMode) {
            addResponse(role, prompt, chatHistory)
        } else {
            addResponse(role, prompt, chatHistory, screenshot)
        }
    }
    
    /**
     * Creates a chat response for pre/post action comparison.
     * For XML mode: text only
     * For Screenshot mode: text + before and after screenshots
     */
    fun createPrePostChatResponse(
        role: String,
        prompt: String,
        chatHistory: List<Pair<String, List<Any>>>,
        config: AgentConfig,
        beforeScreenshot: Bitmap? = null,
        afterScreenshot: Bitmap? = null
    ): List<Pair<String, List<Any>>> {
        return if (config.isXmlMode) {
            addResponse(role, prompt, chatHistory)
        } else {
            addResponsePrePost(role, prompt, chatHistory, beforeScreenshot, afterScreenshot)
        }
    }
    
    /**
     * Gets the appropriate prompt based on vision mode.
     * This can be used to customize prompts based on the mode.
     */
    fun getModeSpecificPrompt(basePrompt: String, config: AgentConfig): String {
        return when (config.visionMode) {
            VisionMode.XML -> "$basePrompt\n\nNote: Using XML mode for UI analysis."
            VisionMode.SCREENSHOT -> "$basePrompt\n\nNote: Using screenshot mode for visual analysis."
        }
    }
    
    /**
     * Logs vision mode information for debugging.
     */
    fun logVisionModeInfo(config: AgentConfig) {
        Log.d("VisionHelper", "Vision Mode: ${config.visionMode.displayName}")
        Log.d("VisionHelper", "Description: ${config.visionMode.description}")
    }
} 