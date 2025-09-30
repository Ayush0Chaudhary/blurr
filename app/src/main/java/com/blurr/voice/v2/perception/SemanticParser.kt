/**
 * @file SemanticParser.kt
 * @brief Parses and simplifies the raw Android View Hierarchy XML for the agent.
 *
 * This file contains the `SemanticParser` and `XmlNode` classes, which are responsible for
 * taking the verbose XML dump from the accessibility service and transforming it into a
 * structured, simplified, and LLM-friendly representation of the UI.
 */
package com.blurr.voice.v2.perception

import android.util.Log
import kotlinx.serialization.Serializable
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

/**
 * Represents a single node in the parsed XML view hierarchy tree.
 *
 * This data class holds the attributes of an XML node, its children, and a reference to its parent.
 * It also includes several helper properties and functions for convenience, such as checking
 * if a node is interactive or semantically important.
 *
 * @property attributes A map of the node's XML attributes (e.g., "text", "resource-id", "class").
 * @property children A mutable list of child [XmlNode]s.
 * @property parent A nullable reference to the parent [XmlNode] in the tree.
 */
@Serializable
data class XmlNode(
    val attributes: MutableMap<String, String> = mutableMapOf(),
    val children: MutableList<XmlNode> = mutableListOf(),
    var parent: XmlNode? = null
) {

    override fun toString(): String {
        val text = getVisibleText().let { if (it.isNotBlank()) "text='$it'" else "" }
        val resId = attributes["resource-id"]?.let { "id='$it'" } ?: ""
        return "XmlNode($text $resId, children=${children.size})"
    }

    /**
     * A descriptive string of the node's boolean properties that are true.
     * For example: "This element is enabled, clickable, focused."
     */
    val extraInfo: String
        get() {
            val infoParts = mutableListOf<String>()
            val propertiesToCheck = listOf(
                "checkable", "checked", "clickable", "enabled", "focusable",
                "focused", "scrollable", "long-clickable", "selected"
            )

            propertiesToCheck.forEach { prop ->
                if (attributes[prop] == "true") {
                    // e.g., "long-clickable" becomes "long clickable".
                    infoParts.add(prop.replace("-", " "))
                }
            }

            return if (infoParts.isNotEmpty()) {
                "This element is ${infoParts.joinToString(", ")}."
            } else {
                ""
            }
        }

    /**
     * Determines if a node is semantically important based on its attributes.
     * An element is considered important if it has a non-empty `resource-id`, `text`, or `content-desc`.
     * @return `true` if the node is semantically important, `false` otherwise.
     */
    fun isSemanticallyImportant(): Boolean {
        return attributes["resource-id"]?.isNotBlank() == true ||
                attributes["text"]?.isNotBlank() == true ||
                attributes["content-desc"]?.isNotBlank() == true
    }

    /**
     * Determines if a node is interactive (e.g., clickable, scrollable, or an EditText).
     * @return `true` if the node is interactive, `false` otherwise.
     */
    fun isInteractive(): Boolean {
        if (attributes["enabled"] == "false") {
            return false
        }
        return attributes["clickable"] == "true" ||
                attributes["long-clickable"] == "true" ||
                attributes["checkable"] == "true" ||
                attributes["scrollable"] == "true" ||
                attributes["class"] == "android.widget.EditText" ||
                attributes["password"] == "true" ||
                attributes["focusable"] == "true"
    }

    /**
     * Returns the most relevant visible text of the node, preferring the `text` attribute
     * over `content-desc`.
     * @return The visible text, or an empty string if neither attribute is present.
     */
    fun getVisibleText(): String {
        return attributes["text"]?.takeIf { it.isNotBlank() }
            ?: attributes["content-desc"]?.takeIf { it.isNotBlank() }
            ?: ""
    }

    /**
     * Checks if the node's bounds are physically within the screen dimensions.
     * An element is considered visible if it has at least one pixel on the screen.
     * @param screenWidth The width of the device screen.
     * @param screenHeight The height of the device screen.
     * @return `true` if the node is at least partially visible, `false` otherwise.
     */
    fun isVisibleOnScreen(screenWidth: Int, screenHeight: Int): Boolean {
        val boundsStr = attributes["bounds"] ?: return false

        val regex = """\[(\d+),(\d+)\]\[(\d+),(\d+)\]""".toRegex()
        val matchResult = regex.find(boundsStr) ?: return false

        return try {
            val (left, top, right, bottom) = matchResult.destructured.toList().map { it.toInt() }

            // Element is invisible if it's entirely off-screen.
            if (right <= 0 || left >= screenWidth || bottom <= 0 || top >= screenHeight) {
                return false
            }
            // Otherwise, it's considered visible.
            true
        } catch (e: NumberFormatException) {
            false
        }
    }
}

/**
 * Parses an Android View Hierarchy XML dump, filters it for relevance, and converts it
 * into a structured, LLM-friendly string format.
 *
 * This class is the core of the semantic parsing process. It builds a tree from the raw XML,
 * prunes nodes that are not visible or important, and then generates a custom string representation
 * that assigns numeric IDs to interactive elements.
 */
class SemanticParser {

    private val interactiveNodeMap = mutableMapOf<Int, XmlNode>()
    private var interactiveElementCounter = 0
    private var screenWidth: Int = 0
    private var screenHeight: Int = 0

    /**
     * Parses the XML tree into a custom string format, highlighting interactive elements.
     *
     * This is the primary public method. It implements the following rules:
     * - Only interactive elements get a numeric index like `[1]`.
     * - Child elements are indented with tabs (`\t`).
     * - Elements marked with `*` are new compared to the `previousNodes` set.
     * - Non-interactive elements are only shown if they contain text.
     *
     * @param xmlString The raw XML dump of the screen hierarchy.
     * @param previousNodes A set of unique identifiers for nodes from a previous screen state, used to detect new elements. A good identifier is "text|resource-id|class".
     * @param screenWidth The width of the device screen.
     * @param screenHeight The height of the device screen.
     * @return A [Pair] containing the formatted UI string and a map of numeric IDs to their corresponding [XmlNode]s.
     */
    fun toHierarchicalString(xmlString: String, previousNodes: Set<String>? = null, screenWidth: Int, screenHeight: Int): Pair<String,  Map<Int, XmlNode>> {
        val rootNode = buildTreeFromXml(xmlString) ?: return Pair("", emptyMap())
        this.screenWidth = screenWidth
        this.screenHeight = screenHeight

        // Prune the tree to remove noise before generating the string.
        val prunedChildren = rootNode.children.flatMap { prune(it) }
        rootNode.children.clear()
        rootNode.children.addAll(prunedChildren)

        // Reset state for the new parse.
        interactiveNodeMap.clear()
        interactiveElementCounter = 0
        val stringBuilder = StringBuilder()

        // Recursively build the string starting from the children of the root.
        rootNode.children.forEach { child ->
            buildHierarchicalStringRecursive(child, 0, stringBuilder, previousNodes ?: emptySet())
        }

        return Pair(stringBuilder.toString(), interactiveNodeMap)
    }

    /**
     * Returns the center coordinates of an interactive element given its numeric index.
     * This function should only be called after `toHierarchicalString` has been run.
     *
     * @param index The numeric index of the element (e.g., `1` from `[1] ...`).
     * @return A `Pair(x, y)` representing the center coordinates, or `null` if the index is invalid or the element has no bounds.
     */
    fun getCenterOfElement(index: Int): Pair<Int, Int>? {
        val node = interactiveNodeMap[index] ?: return null
        val bounds = node.attributes["bounds"] ?: return null

        val regex = """\[(\d+),(\d+)\]\[(\d+),(\d+)\]""".toRegex()
        val matchResult = regex.find(bounds) ?: return null

        return try {
            val (left, top, right, bottom) = matchResult.destructured.toList().map { it.toInt() }
            val centerX = (left + right) / 2
            val centerY = (top + bottom) / 2
            Pair(centerX, centerY)
        } catch (e: NumberFormatException) {
            null // Return null if coordinates are not valid integers.
        }
    }

    /**
     * A legacy method to parse and filter the XML string, returning a filtered XML string.
     *
     * @param xmlString The raw XML dump of the screen hierarchy.
     * @return A filtered XML string containing only the essential nodes.
     */
    fun parseAndFilter(xmlString: String): String {
        val rootNode = buildTreeFromXml(xmlString) ?: return "<hierarchy/>"

        val newChildren = rootNode.children.flatMap { prune(it) }
        rootNode.children.clear()
        rootNode.children.addAll(newChildren)

        return toXmlString(rootNode)
    }

    /**
     * Recursively builds the hierarchical string representation.
     */
    private fun buildHierarchicalStringRecursive(
        node: XmlNode,
        indentLevel: Int,
        builder: StringBuilder,
        previousNodes: Set<String>
    ) {
        val indent = "\t".repeat(indentLevel)

        // A unique key to identify a node across different hierarchy snapshots.
        val nodeKey = "${node.getVisibleText()}|${node.attributes["resource-id"]}|${node.attributes["class"]}"
        val isNew = !previousNodes.contains(nodeKey) && node.isSemanticallyImportant()

        if (node.isInteractive()) {
            interactiveElementCounter++
            interactiveNodeMap[interactiveElementCounter] = node

            val newMarker = if (isNew) "* " else ""
            val text = node.getVisibleText().replace("\n", " ")
            val resourceId = node.attributes["resource-id"] ?: ""
            val extraInfo = node.extraInfo
            val className = (node.attributes["class"] ?: "").removePrefix("android.")

            builder.append("$indent$newMarker[$interactiveElementCounter] ")
                .append("text:\"$text\" ")
                .append("<$resourceId> ")
                .append("<$extraInfo> ")
                .append("<$className>\n")

        } else {
            // Only print non-interactive elements if they contain text.
            val text = node.getVisibleText()
            if (text.isNotBlank()) {
                val newMarker = if (isNew) "* " else ""
                builder.append("$indent$newMarker${text.replace("\n", " ")}\n")
            }
        }

        // Recurse for children.
        node.children.forEach { child ->
            buildHierarchicalStringRecursive(child, indentLevel + 1, builder, previousNodes)
        }
    }

    /**
     * Recursively prunes the UI tree to remove noise.
     *
     * It removes nodes that are not visible or semantically important, and it promotes their
     * children to maintain the hierarchy. This simplifies the tree for the LLM.
     *
     * @param node The current node to process.
     * @return A list of nodes to be kept. If the current node is kept, it's a single-element
     * list. If it's removed, the list contains its children that are to be promoted.
     */
    private fun prune(node: XmlNode): List<XmlNode> {
        val newChildren = node.children.flatMap { prune(it) }
        node.children.clear()
        node.children.addAll(newChildren)
        newChildren.forEach { it.parent = node }

        if (!node.isVisibleOnScreen(screenWidth, screenHeight)) {
            return node.children
        }

        return if (node.isSemanticallyImportant() || node.isInteractive() || node.children.isNotEmpty()) {
            // Keep this node.
            listOf(node)
        } else {
            // This node is not important. Discard it and promote its children.
            node.children
        }
    }

    /**
     * Traverses the raw XML string using [XmlPullParser] and builds a tree of [XmlNode] objects.
     * @param xmlString The raw XML from the accessibility service.
     * @return The root [XmlNode] of the parsed tree, or null on failure.
     */
    private fun buildTreeFromXml(xmlString: String): XmlNode? {
        val cleanedXml = xmlString.replace('\u00A0', ' ')

        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(StringReader(cleanedXml))

        var root: XmlNode? = null
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
                        } else {
                            val parent = nodeStack.lastOrNull()
                            parent?.children?.add(newNode)
                            newNode.parent = parent
                        }
                        nodeStack.addLast(newNode)
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "node") {
                        nodeStack.removeLastOrNull()
                    }
                }
            }
            eventType = parser.next()
        }
        return root
    }

    /**
     * Converts a pruned tree of [XmlNode]s back to a formatted XML string.
     * Used by the legacy `parseAndFilter` method.
     */
    private fun toXmlString(root: XmlNode): String {
        val stringBuilder = StringBuilder()
        stringBuilder.append("<hierarchy")
        root.attributes.forEach { (key, value) ->
            stringBuilder.append(" ").append(key).append("=\"").append(escapeXml(value)).append("\"")
        }
        stringBuilder.appendLine(">")

        root.children.forEach { child ->
            buildXmlStringRecursive(child, stringBuilder, 1)
        }

        stringBuilder.appendLine("</hierarchy>")
        return stringBuilder.toString()
    }

    /**
     * Recursively builds the XML string for a node and its children.
     */
    private fun buildXmlStringRecursive(node: XmlNode, builder: StringBuilder, indentLevel: Int) {
        val indent = "  ".repeat(indentLevel)
        builder.append(indent).append("<node")

        node.attributes.forEach { (key, value) ->
            builder.append(" ").append(key).append("=\"").append(escapeXml(value)).append("\"")
        }

        if (node.children.isEmpty()) {
            builder.appendLine("/>")
        } else {
            builder.appendLine(">")
            for (child in node.children) {
                buildXmlStringRecursive(child, builder, indentLevel + 1)
            }
            builder.append(indent).appendLine("</node>")
        }
    }

    /**
     * Escapes special characters in a string for safe inclusion in XML.
     */
    private fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}