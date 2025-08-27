package com.blurr.voice.crawler

/**
 * Data classes that represent the final JSON structure of our AppMap.
 * Using these makes the code type-safe and easy to serialize into JSON.
 */
data class AppMap(
    val app_metadata: AppMetadata,
    val screens: List<Screen>
)

/**
 * A simplified data class representing a clickable element sent to the LLM for analysis.
 */
private data class ElementForLlm(
    val id: Int,
    val resource_id: String?,
    val text: String?,
    val content_description: String?,
    val class_name: String?
)

data class AppMetadata(
    val package_name: String,
    val version_name: String = "1.0.0", // Placeholder, should be retrieved from PackageManager
    val version_code: Int = 1,       // Placeholder
    val screen_title: String         // The user-facing title of the screen
)

data class Screen(
    val screen_id: String, // e.g., a hash of the element structure or activity name
    val ui_elements: List<UIElement>
)

data class UIElement(
    val resource_id: String?,
    var text: String?,
    var content_description: String?,
    val class_name: String?,
    val bounds: String?,
    val is_clickable: Boolean,
    val is_long_clickable: Boolean,
    val is_password: Boolean,
    @Transient var isPruned: Boolean = false // Helper field for pruning, excluded from JSON
)