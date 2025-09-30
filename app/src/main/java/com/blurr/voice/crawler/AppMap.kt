/**
 * @file AppMap.kt
 * @brief Defines the data structures for representing the application's UI map.
 *
 * This file contains a set of data classes that model the hierarchical structure of an
 * application's user interface. This "AppMap" is used to represent screens and their
 * interactive elements in a structured, serializable format, which can be used for
 * UI analysis and automation.
 */
package com.blurr.voice.crawler

/**
 * The root data class representing a complete map of a specific application state.
 *
 * This class combines metadata about the application with a list of screens that were
 * part of the UI crawl or analysis.
 *
 * @property app_metadata Metadata about the application, such as package name and version.
 * @property screens A list of [Screen] objects captured during the UI analysis.
 */
data class AppMap(
    val app_metadata: AppMetadata,
    val screens: List<Screen>
)

/**
 * A simplified data class representing a clickable element, intended for submission to an LLM.
 *
 * This class holds a minimal set of identifiers for a UI element, which can be used by a
 * language model to understand the element's purpose and context without being overloaded
 * with detailed layout information.
 *
 * @property id A unique identifier for the element within its screen context.
 * @property resource_id The Android resource ID of the element, if available.
 * @property text The visible text of the element, if any.
 * @property content_description The content description (accessibility label) of the element.
 * @property class_name The Android widget class name (e.g., "android.widget.Button").
 */
private data class ElementForLlm(
    val id: Int,
    val resource_id: String?,
    val text: String?,
    val content_description: String?,
    val class_name: String?
)

/**
 * Contains metadata about the application being mapped.
 *
 * @property package_name The unique package name of the application (e.g., "com.example.app").
 * @property version_name The user-facing version name (e.g., "1.0.0").
 * @property version_code The internal version code.
 * @property screen_title The title of the screen as it appears to the user.
 */
data class AppMetadata(
    val package_name: String,
    val version_name: String = "1.0.0",
    val version_code: Int = 1,
    val screen_title: String
)

/**
 * Represents a single screen or UI state within the application.
 *
 * @property screen_id A unique identifier for this screen, which could be an activity name
 *                     or a hash of the UI structure.
 * @property ui_elements A list of all the [UIElement] objects present on this screen.
 */
data class Screen(
    val screen_id: String,
    val ui_elements: List<UIElement>
)

/**
 * Represents a single, distinct element within a user interface.
 *
 * This class captures the key properties of a UI widget, such as its identifiers, text,
 * state, and position.
 *
 * @property resource_id The Android resource ID string (e.g., "com.example.app:id/button_save").
 * @property text The visible text content of the element.
 * @property content_description The accessibility content description, which describes the
 *                               element's purpose.
 * @property class_name The fully qualified name of the widget's class (e.g., "android.widget.Button").
 * @property bounds The on-screen coordinates of the element, typically in "[left,top][right,bottom]" format.
 * @property is_clickable Indicates whether the element can be clicked.
 * @property is_long_clickable Indicates whether the element can be long-pressed.
 * @property is_password Indicates whether the element is a password input field.
 * @property isPruned A transient helper field used during processing to mark if the element
 *                    has been pruned from a list. It is not included in JSON serialization.
 */
data class UIElement(
    val resource_id: String?,
    var text: String?,
    var content_description: String?,
    val class_name: String?,
    val bounds: String?,
    val is_clickable: Boolean,
    val is_long_clickable: Boolean,
    val is_password: Boolean,
    @Transient var isPruned: Boolean = false
)