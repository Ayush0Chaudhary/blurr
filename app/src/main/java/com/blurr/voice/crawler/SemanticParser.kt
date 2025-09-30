/**
 * @file SemanticParser.kt
 * @brief Defines a parser for simplifying and interpreting raw Android UI XML.
 *
 * This file contains the `SemanticParser` class, which is a sophisticated tool for
 * converting the verbose XML output from Android's accessibility service into a clean,
 * semantically meaningful list of UI elements. It includes logic for merging descriptive
 * child nodes into their interactive parents, pruning redundant elements, and formatting
 * the output in various ways (JSON, Markdown, etc.).
 */
package com.blurr.voice.crawler

import android.content.Context
import android.graphics.Rect
import com.google.gson.GsonBuilder
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.util.regex.Pattern
import kotlin.math.max
import kotlin.math.min

/**
 * A data class that associates a [UIElement] with a simple numeric ID and its center coordinates.
 *
 * This is useful for providing a simplified reference to UI elements, for example, when
 * presenting them to a language model or user for selection.
 *
 * @property id The simple numeric identifier (1-based index).
 * @property element The full [UIElement] object.
 * @property centerX The x-coordinate of the center of the element's bounds.
 * @property centerY The y-coordinate of the center of the element's bounds.
 */
data class UIElementWithId(
    val id: Int,
    val element: UIElement,
    val centerX: Int,
    val centerY: Int
)

/**
 * A sophisticated parser that simplifies raw UI XML into a clean, semantically
 * meaningful list of [UIElement] objects.
 *
 * The parser performs several key operations:
 * 1.  **Tree Building:** Constructs a complete node tree from the raw XML string.
 * 2.  **Semantic Merging:** Traverses the tree to find non-clickable, descriptive nodes
 *     (e.g., a `TextView` next to a checkbox) and merges their text into their nearest
 *     clickable ancestor. This groups labels with their controls.
 * 3.  **Filtering & Flattening:** Traverses the merged tree to create a flat list of
 *     "important" UI elements, filtering out nodes that were merged or are outside
 *     the screen bounds.
 *
 * @param applicationContext The application context, used for debugging visualizations.
 */
class SemanticParser(private  val applicationContext: Context) {

    /**
     * An internal data class to represent the hierarchical structure of the XML UI tree.
     *
     * @property attributes A map of the XML attributes for this node.
     * @property parent A reference to the parent node in the tree.
     * @property children A list of child nodes.
     * @property mergedText A list of strings "donated" from descriptive child nodes during the merge process.
     * @property isSubsumed A flag indicating that this node's description has been merged into an ancestor.
     */
    private data class XmlNode(
        val attributes: MutableMap<String, String> = mutableMapOf(),
        var parent: XmlNode? = null,
        val children: MutableList<XmlNode> = mutableListOf(),
        var mergedText: MutableList<String> = mutableListOf(),
        var isSubsumed: Boolean = false
    ) {
        fun get(key: String): String? = attributes[key]
        fun getBool(key: String): Boolean = attributes[key]?.toBoolean() ?: false
    }

    /**
     * Parses the raw XML hierarchy and returns a simplified, semantically merged JSON string.
     *
     * This is a primary entry point for the parser.
     *
     * @param xmlString The raw XML content from the accessibility service.
     * @param screenWidth The physical width of the device screen for bounds checking.
     * @param screenHeight The physical height of the device screen for bounds checking.
     * @return A JSON string representing a list of clean [UIElement] objects.
     */
    fun parse(xmlString: String, screenWidth: Int, screenHeight: Int): String {
        val preliminaryElements = parseToUIElement(xmlString, screenWidth, screenHeight)
        visualizeCurrentScreen(applicationContext, preliminaryElements )
        val gson = GsonBuilder().setPrettyPrinting().create()
        return gson.toJson(preliminaryElements)
    }

    /**
     * Parses the raw XML and returns a mutable list of [UIElement] objects.
     *
     * This method performs the core parsing and semantic merging logic.
     *
     * @param xmlString The raw XML content from the accessibility service.
     * @param screenWidth The physical width of the device screen.
     * @param screenHeight The physical height of the device screen.
     * @return A mutable list of processed [UIElement] objects.
     */
    fun parseToUIElement(xmlString: String, screenWidth: Int, screenHeight: Int):  MutableList<UIElement> {
        val rootNode = buildTreeFromXml(xmlString)
        if (rootNode != null) {
            mergeDescriptiveChildren(rootNode)
        }
        val preliminaryElements = mutableListOf<UIElement>()
        if (rootNode != null) {
            flattenAndFilter(rootNode, preliminaryElements, screenWidth, screenHeight)
        }
        return preliminaryElements
    }

    /**
     * Converts a list of UI elements into a human-readable markdown format.
     *
     * Each element is assigned a numeric ID for easy reference, and its key properties
     * are listed.
     *
     * @param elements The list of [UIElement] objects to format.
     * @return A markdown string representing the list of elements.
     */
    fun elementsToMarkdown(elements: List<UIElement>): String {
        if (elements.isEmpty()) {
            return "No interactive elements found on screen."
        }

        val markdown = StringBuilder()
        markdown.appendLine("# Screen Elements")
        markdown.appendLine()
        markdown.appendLine("The following elements are available for interaction:")
        markdown.appendLine()

        elements.forEachIndexed { index, element ->
            val elementId = index + 1
            markdown.appendLine("## $elementId. ${getElementDescription(element)}")
            
            val details = mutableListOf<String>()
            if (!element.text.isNullOrBlank()) {
                details.add("**Text:** ${element.text}")
            }
            if (!element.content_description.isNullOrBlank()) {
                details.add("**Description:** ${element.content_description}")
            }
            if (!element.class_name.isNullOrBlank()) {
                details.add("**Type:** ${element.class_name}")
            }
            if (!element.resource_id.isNullOrBlank()) {
                details.add("**ID:** ${element.resource_id}")
            }
            if (element.is_clickable) {
                details.add("**Action:** Clickable")
            }
            if (element.is_long_clickable) {
                details.add("**Action:** Long-clickable")
            }
            if (element.is_password) {
                details.add("**Type:** Password field")
            }
            
            if (details.isNotEmpty()) {
                markdown.appendLine(details.joinToString(" | "))
            }
            
            markdown.appendLine()
        }

        return markdown.toString()
    }

    /**
     * Generates a simple, human-readable description for a UI element.
     *
     * It prioritizes text, then content description, then resource ID, and finally class name.
     *
     * @param element The [UIElement] to describe.
     * @return A descriptive string for the element.
     */
    private fun getElementDescription(element: UIElement): String {
        val text = element.text
        val contentDesc = element.content_description
        val resourceId = element.resource_id
        val className = element.class_name
        
        return when {
            !text.isNullOrBlank() -> text
            !contentDesc.isNullOrBlank() -> contentDesc
            !resourceId.isNullOrBlank() -> resourceId
            else -> className ?: "Unknown element"
        }
    }

    /**
     * Parses the raw XML and returns both a JSON and a markdown representation.
     *
     * This is a convenience method that combines the results of `parse` and `elementsToMarkdown`.
     *
     * @param xmlString The raw XML content.
     * @param screenWidth The screen width.
     * @param screenHeight The screen height.
     * @return A [Pair] containing the JSON string as the first element and the markdown string as the second.
     */
    fun parseWithMarkdown(xmlString: String, screenWidth: Int, screenHeight: Int): Pair<String, String> {
        val preliminaryElements = parseToUIElement(xmlString, screenWidth, screenHeight)
        visualizeCurrentScreen(applicationContext, preliminaryElements)
        val gson = GsonBuilder().setPrettyPrinting().create()
        val jsonString = gson.toJson(preliminaryElements)
        val markdownString = elementsToMarkdown(preliminaryElements)
        
        return Pair(jsonString, markdownString)
    }

    /**
     * Parses the raw XML and returns a list of elements with simplified numeric IDs and center coordinates.
     *
     * @param xmlString The raw XML content.
     * @param screenWidth The screen width.
     * @param screenHeight The screen height.
     * @return A list of [UIElementWithId] objects.
     */
    fun parseWithIds(xmlString: String, screenWidth: Int, screenHeight: Int): List<UIElementWithId> {
        val preliminaryElements = parseToUIElement(xmlString, screenWidth, screenHeight)
        visualizeCurrentScreen(applicationContext, preliminaryElements)

        return preliminaryElements.mapIndexed { index, element ->
            val bounds = parseBounds(element.bounds)
            val centerX = bounds?.let { (it.left + it.right) / 2 } ?: 0
            val centerY = bounds?.let { (it.top + it.bottom) / 2 } ?: 0
            
            UIElementWithId(
                id = index + 1,
                element = element,
                centerX = centerX,
                centerY = centerY
            )
        }
    }

    /**
     * Gets the center coordinates of an element given its numeric ID.
     *
     * @param elementId The 1-based numeric ID of the element.
     * @param elementsWithIds The list of [UIElementWithId] to search within.
     * @return A [Pair] of (x, y) coordinates, or null if the element ID is not found.
     */
    fun getElementCoordinates(elementId: Int, elementsWithIds: List<UIElementWithId>): Pair<Int, Int>? {
        val element = elementsWithIds.find { it.id == elementId }
        return element?.let { Pair(it.centerX, it.centerY) }
    }


    /**
     * Triggers the debug overlay to visualize the parsed elements.
     *
     * Note: The drawing is commented out by default to prevent it from running in production.
     *
     * @param context The application context.
     * @param elements The list of [UIElement] objects to visualize.
     */
    fun visualizeCurrentScreen(context: Context, elements: List<UIElement>) {
        val overlayDrawer = DebugOverlayDrawer(context)
        // overlayDrawer.drawLabeledBoxes(elements)
    }
    /**
     * Traverses the XML from the accessibility service and builds a tree of [XmlNode] objects.
     *
     * @param xmlString The raw XML hierarchy.
     * @return The root [XmlNode] of the parsed tree, or null if parsing fails.
     */
    private fun buildTreeFromXml(xmlString: String): XmlNode? {
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xmlString))

        var root: XmlNode? = null
        var currentNode: XmlNode? = null
        val nodeStack = ArrayDeque<XmlNode>()

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == "node") {
                        val newNode = XmlNode()
                        for (i in 0 until parser.attributeCount) {
                            newNode.attributes[parser.getAttributeName(i)] = parser.getAttributeValue(i)
                        }

                        if (root == null) {
                            root = newNode
                            currentNode = newNode
                        } else {
                            currentNode?.children?.add(newNode)
                            newNode.parent = currentNode
                        }
                        nodeStack.addLast(newNode)
                        currentNode = newNode
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "node") {
                        nodeStack.removeLastOrNull()
                        currentNode = nodeStack.lastOrNull()
                    }
                }
            }
            eventType = parser.next()
        }
        return root
    }

    /**
     * Performs a depth-first traversal to merge descriptive text from non-clickable children
     * into their nearest clickable ancestor.
     *
     * This is the core of the semantic analysis, grouping labels with their corresponding inputs.
     *
     * @param node The current [XmlNode] in the traversal.
     */
    private fun mergeDescriptiveChildren(node: XmlNode) {
        for (child in node.children) {
            mergeDescriptiveChildren(child)
        }

        val text = node.get("text")
        val contentDesc = node.get("content-desc")
        val hasDescriptiveText = !text.isNullOrBlank() || !contentDesc.isNullOrBlank()

        if (hasDescriptiveText && !node.getBool("clickable")) {
            var ancestor = node.parent
            while (ancestor != null) {
                if (ancestor.getBool("clickable")) {
                    val description = if (!text.isNullOrBlank()) text else contentDesc!!
                    ancestor.mergedText.add(description)
                    node.isSubsumed = true
                    break
                }
                ancestor = ancestor.parent
            }
        }
    }

    /**
     * Traverses the merged tree and creates the final, flat list of [UIElement] objects.
     *
     * It filters out nodes that were subsumed during the merge process or are outside the
     * visible screen bounds.
     *
     * @param node The current [XmlNode] in the traversal.
     * @param finalElements The list to which the final, filtered elements will be added.
     * @param screenWidth The width of the screen.
     * @param screenHeight The height of the screen.
     */
    private fun flattenAndFilter(node: XmlNode, finalElements: MutableList<UIElement>, screenWidth: Int, screenHeight: Int) {
        val isImportant = node.getBool("clickable") ||
                (!node.get("text").isNullOrBlank() && !node.isSubsumed) ||
                (!node.get("content-desc").isNullOrBlank() && !node.isSubsumed)

        if (isImportant && !node.isSubsumed) {
            val bounds = parseBounds(node.get("bounds"))
            if (bounds != null &&
               bounds.left >= 0 && bounds.right <= screenWidth &&
                bounds.top >= 0 && bounds.bottom <= screenHeight  &&
                bounds.left != bounds.right &&
                bounds.top != bounds.bottom
            ) {
                val combinedText = mutableListOf<String>()
                node.get("text")?.takeIf { it.isNotBlank() }?.let { combinedText.add(it) }
                combinedText.addAll(node.mergedText)

                finalElements.add(
                    UIElement(
                        resource_id = node.get("resource-id"),
                        text = combinedText.joinToString(" | "),
                        content_description = node.get("content-desc"),
                        class_name = node.get("class"),
                        bounds = node.get("bounds"),
                        is_clickable = node.getBool("clickable"),
                        is_long_clickable = node.getBool("long-clickable"),
                        is_password = node.getBool("password")
                    )
                )
            }
        }

        for (child in node.children) {
            flattenAndFilter(child, finalElements, screenWidth, screenHeight)
        }
    }

    /**
     * A robust helper to parse a bounds string (e.g., "[0,0][100,100]") into a [Rect].
     *
     * This version includes validation to handle malformed coordinates (e.g., left > right)
     * by correcting them.
     *
     * @param boundsString The string to parse.
     * @return A corrected [Rect] object, or null if parsing fails.
     */
    private fun parseBounds(boundsString: String?): Rect? {
        if (boundsString == null) return null
        val pattern = Pattern.compile("\\[(-?\\d+),(-?\\d+)\\]\\[(-?\\d+),(-?\\d+)\\]")
        val matcher = pattern.matcher(boundsString)
        return if (matcher.matches()) {
            try {
                val left = matcher.group(1).toInt()
                val top = matcher.group(2).toInt()
                val right = matcher.group(3).toInt()
                val bottom = matcher.group(4).toInt()

                val fixedLeft = min(left, right)
                val fixedTop = min(top, bottom)
                val fixedRight = max(left, right)
                val fixedBottom = max(top, bottom)

                Rect(fixedLeft, fixedTop, fixedRight, fixedBottom)
            } catch (e: NumberFormatException) {
                null
            }
        } else {
            null
        }
    }
}
