package com.blurr.voice.agent.v1

import android.content.Context

/**
 * Centralized configuration for the agent system.
 * This class holds all configuration parameters that need to be accessible
 * across different agent components.
 */
data class AgentConfig(
    val visionMode: VisionMode,
    val apiKey: String,
    val maxIterations: Int = 200000,
    val maxConsecutiveFailures: Int = 100,
    val maxRepetitiveActions: Int = 100,
    val errorThreshold: Int = 5,
    val context: Context,
    val enableDirectAppOpening: Boolean = false // Debug flag for direct app opening
) {
    val isXmlMode: Boolean
        get() = visionMode == VisionMode.XML
    
    val isScreenshotMode: Boolean
        get() = visionMode == VisionMode.SCREENSHOT
}

enum class VisionMode(val displayName: String, val description: String) {
    XML("XML Mode", "Uses UI structure data (faster, less resource intensive)"),
    SCREENSHOT("Screenshot Mode", "Uses actual screenshots (more accurate, higher resource usage)")
}

/**
 * Factory class to create AgentConfig instances
 */
object AgentConfigFactory {
    fun create(
        context: Context,
        visionMode: String,
        apiKey: String,
        maxIterations: Int = 200000,
        maxConsecutiveFailures: Int = 3,
        maxRepetitiveActions: Int = 3,
        errorThreshold: Int = 2,
        enableDirectAppOpening: Boolean = false // Default to false for production safety
    ): AgentConfig {
        val mode = when (visionMode.uppercase()) {
            "XML" -> VisionMode.XML
            "SCREENSHOT" -> VisionMode.SCREENSHOT
            else -> VisionMode.XML // Default to XML mode
        }
        
        return AgentConfig(
            visionMode = mode,
            apiKey = apiKey,
            maxIterations = maxIterations,
            maxConsecutiveFailures = maxConsecutiveFailures,
            maxRepetitiveActions = maxRepetitiveActions,
            errorThreshold = errorThreshold,
            context = context,
            enableDirectAppOpening = enableDirectAppOpening
        )
    }
} 