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
 * Data class to store UI elements with their numeric IDs and coordinates
 */
data class UIElementWithId(
    val id: Int,
    val element: UIElement,
    val centerX: Int,
    val centerY: Int
)

/**
 * A sophisticated parser that simplifies the raw UI XML into a clean,
 * semantically meaningful list of UI elements. It merges descriptive child nodes
 * into their interactive parents and then prunes nested interactive elements.
 */
class SemanticParser(private  val applicationContext: Context) {

    // Internal data class to represent the XML tree structure.
    private data class XmlNode(
        val attributes: MutableMap<String, String> = mutableMapOf(),
        var parent: XmlNode? = null,
        val children: MutableList<XmlNode> = mutableListOf(),
        // --- Fields for merging logic ---
        var mergedText: MutableList<String> = mutableListOf(),
        var isSubsumed: Boolean = false
    ) {
        // Helper to get an attribute
        fun get(key: String): String? = attributes[key]
        fun getBool(key: String): Boolean = attributes[key]?.toBoolean() ?: false
    }

    /**
     * Parses the raw XML hierarchy and returns a simplified, semantically merged JSON string.
     *
     * @param xmlString The raw XML content from the accessibility service.
     * @param screenWidth The physical width of the device screen.
     * @param screenHeight The physical height of the device screen.
     * @return A JSON string representing a list of clean UIElements.
     */
    fun parse(xmlString: String, screenWidth: Int, screenHeight: Int): String {
        // Step 1: Build the complete tree from the XML.
        val rootNode = buildTreeFromXml(xmlString)

        // Step 2: Perform the semantic merge (upward text propagation).
        if (rootNode != null) {
            mergeDescriptiveChildren(rootNode)
        }

        // Step 3: Flatten the tree to a preliminary list of important elements.
        val preliminaryElements = mutableListOf<UIElement>()
        if (rootNode != null) {
            flattenAndFilter(rootNode, preliminaryElements, screenWidth, screenHeight)
        }

        visualizeCurrentScreen(applicationContext, preliminaryElements )

        // Step 5: Serialize the final list to a pretty JSON string.
        val gson = GsonBuilder().setPrettyPrinting().create()
        return gson.toJson(preliminaryElements)
    }

    fun parseToUIElement(xmlString: String, screenWidth: Int, screenHeight: Int):  MutableList<UIElement> {
        // Step 1: Build the complete tree from the XML.
        val rootNode = buildTreeFromXml(xmlString)

        // Step 2: Perform the semantic merge (upward text propagation).
        if (rootNode != null) {
            mergeDescriptiveChildren(rootNode)
        }

        // Step 3: Flatten the tree to a preliminary list of important elements.
        val preliminaryElements = mutableListOf<UIElement>()
        if (rootNode != null) {
            flattenAndFilter(rootNode, preliminaryElements, screenWidth, screenHeight)
        }
        return preliminaryElements

    }

    /**
     * Converts UI elements to markdown format with numeric IDs for easy reference.
     *
     * @param elements List of UI elements to convert
     * @return A markdown string with numbered elements
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
            
            // Add details about the element
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
     * Helper function to get a human-readable description of an element
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
     * Parses the raw XML hierarchy and returns both JSON and markdown formats.
     *
     * @param xmlString The raw XML content from the accessibility service.
     * @param screenWidth The physical width of the device screen.
     * @param screenHeight The physical height of the device screen.
     * @return A pair containing (JSON string, markdown string)
     */
    fun parseWithMarkdown(xmlString: String, screenWidth: Int, screenHeight: Int): Pair<String, String> {
        // Step 1: Build the complete tree from the XML.
        val rootNode = buildTreeFromXml(xmlString)

        // Step 2: Perform the semantic merge (upward text propagation).
        if (rootNode != null) {
            mergeDescriptiveChildren(rootNode)
        }

        // Step 3: Flatten the tree to a preliminary list of important elements.
        val preliminaryElements = mutableListOf<UIElement>()
        if (rootNode != null) {
            flattenAndFilter(rootNode, preliminaryElements, screenWidth, screenHeight)
        }

        visualizeCurrentScreen(applicationContext, preliminaryElements)

        // Step 4: Generate both JSON and markdown formats
        val gson = GsonBuilder().setPrettyPrinting().create()
        val jsonString = gson.toJson(preliminaryElements)
        val markdownString = elementsToMarkdown(preliminaryElements)
        
        return Pair(jsonString, markdownString)
    }

    /**
     * Parses the raw XML hierarchy and returns elements with IDs and coordinates.
     *
     * @param xmlString The raw XML content from the accessibility service.
     * @param screenWidth The physical width of the device screen.
     * @param screenHeight The physical height of the device screen.
     * @return A list of UIElementWithId containing elements with their IDs and coordinates
     */
    fun parseWithIds(xmlString: String, screenWidth: Int, screenHeight: Int): List<UIElementWithId> {
        // Step 1: Build the complete tree from the XML.
        val rootNode = buildTreeFromXml(xmlString)

        // Step 2: Perform the semantic merge (upward text propagation).
        if (rootNode != null) {
            mergeDescriptiveChildren(rootNode)
        }

        // Step 3: Flatten the tree to a preliminary list of important elements.
        val preliminaryElements = mutableListOf<UIElement>()
        if (rootNode != null) {
            flattenAndFilter(rootNode, preliminaryElements, screenWidth, screenHeight)
        }

        visualizeCurrentScreen(applicationContext, preliminaryElements)

        // Step 4: Convert to UIElementWithId with coordinates
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
     * Gets the coordinates of an element by its numeric ID.
     *
     * @param elementId The numeric ID of the element (1-based)
     * @param elementsWithIds List of elements with IDs
     * @return Pair of (x, y) coordinates, or null if element not found
     */
    fun getElementCoordinates(elementId: Int, elementsWithIds: List<UIElementWithId>): Pair<Int, Int>? {
        val element = elementsWithIds.find { it.id == elementId }
        return element?.let { Pair(it.centerX, it.centerY) }
    }


    fun visualizeCurrentScreen(context: Context, elements: List<UIElement>) {
        // Create an instance of the drawer
        val overlayDrawer = DebugOverlayDrawer(context)

        // Call the function to draw the boxes. They will disappear automatically.
//        overlayDrawer.drawLabeledBoxes(elements)
    }
    /**
     * Traverses the XML and builds a tree of XmlNode objects, preserving the hierarchy.
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
     * Performs a depth-first traversal. When it finds a descriptive but non-clickable node,
     * it walks up the tree to find a clickable ancestor and "donates" its text to it.
     */
    private fun mergeDescriptiveChildren(node: XmlNode) {
        // First, recurse to the deepest children.
        for (child in node.children) {
            mergeDescriptiveChildren(child)
        }

        // Now, process the current node.
        val text = node.get("text")
        val contentDesc = node.get("content-desc")
        val hasDescriptiveText = !text.isNullOrBlank() || !contentDesc.isNullOrBlank()

        if (hasDescriptiveText && !node.getBool("clickable")) {
            // This node is descriptive but not clickable. Find an interactive ancestor.
            var ancestor = node.parent
            while (ancestor != null) {
                if (ancestor.getBool("clickable")) {
                    // Found a clickable ancestor. Donate text and mark this node for pruning.
                    val description = if (!text.isNullOrBlank()) text else contentDesc!!
                    ancestor.mergedText.add(description)
                    node.isSubsumed = true
                    break // Stop the upward walk
                }
                ancestor = ancestor.parent
            }
        }
    }

    /**
     * Traverses the merged tree and creates the final, flat list of UIElement objects,
     * filtering out subsumed and unimportant nodes.
     */
    private fun flattenAndFilter(node: XmlNode, finalElements: MutableList<UIElement>, screenWidth: Int, screenHeight: Int) {
        // A node is considered important if it's clickable OR has text that wasn't merged away.
        val isImportant = node.getBool("clickable") ||
                (!node.get("text").isNullOrBlank() && !node.isSubsumed) ||
                (!node.get("content-desc").isNullOrBlank() && !node.isSubsumed)

        if (isImportant && !node.isSubsumed) {
            // Add a check to ensure the element is within the visible screen bounds.
            val bounds = parseBounds(node.get("bounds"))
            if (bounds != null &&
//                [2160,690][1080,878]
               bounds.left >= 0 && bounds.right <= screenWidth && // Horizontal check
                bounds.top >= 0 && bounds.bottom <= screenHeight  && // Vertical check
                bounds.left != bounds.right && // none zero widht
                bounds.top != bounds.bottom  // none zero hieght
            ) {
                // Combine original text with any text merged from children.
                val combinedText = mutableListOf<String>()
                node.get("text")?.takeIf { it.isNotBlank() }?.let { combinedText.add(it) }
                combinedText.addAll(node.mergedText)

                finalElements.add(
                    UIElement(
                        resource_id = node.get("resource-id"),
                        text = combinedText.joinToString(" | "), // Join merged texts
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

        // Continue traversal.
        for (child in node.children) {
            flattenAndFilter(child, finalElements, screenWidth, screenHeight)
        }
    }

    /**
     * Helper to parse a bounds string "[x1,y1][x2,y2]" into an Android Rect object.
     * Now includes validation to ensure the rectangle is well-formed.
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

                // NEW: Fix reversed coordinates by taking the min and max of the values.
                val fixedLeft = min(left, right)
                val fixedTop = min(top, bottom)
                val fixedRight = max(left, right)
                val fixedBottom = max(top, bottom)

                Rect(fixedLeft, fixedTop, fixedRight, fixedBottom)
            } catch (e: NumberFormatException) {
                // Handle cases where parsing to Int fails
                null
            }
        } else {
            null
        }
    }
}
