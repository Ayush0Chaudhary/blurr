package com.blurr.voice.v2.perception

import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.serialization.Serializable
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

/**
 * Represents a node in the XML view hierarchy.
 *
 * This data class holds the attributes of the XML node, its children, and a reference to its parent.
 * It also includes several helper properties and functions for convenience.
 *
 * @property attributes A map of the node's XML attributes (e.g., "text", "resource-id").
 * @property children A list of child [XmlNode]s.
 * @property parent A nullable reference to the parent [XmlNode].
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
     * Example: "This element is enabled, clickable, focused."
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
                    // e.g., "long-clickable" becomes "long clickable"
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
     * An element is important if it has a non-empty `resource-id`, `text`, or `content-desc`.
     */
    fun isSemanticallyImportant(): Boolean {
        return attributes["resource-id"]?.isNotBlank() == true ||
                attributes["text"]?.isNotBlank() == true ||
                attributes["content-desc"]?.isNotBlank() == true
    }

    /**
     * Determines if a node is interactive (clickable or long-clickable).
     */
    fun isInteractive(): Boolean {
        if (attributes["enabled"] == "false") {
            return false
        }
        if (attributes["clickable"] == "true" ||
            attributes["long-clickable"] == "true" ||
            attributes["checkable"] == "true" ||
            attributes["scrollable"] == "true" ||
            attributes["class"] == "android.widget.EditText" ||
            attributes["password"] == "true" ||
            attributes["focusable"] == "true") {
            return true
        }

        return false
    }

    /**
     * Returns the visible text of the node, preferring `text` over `content-desc`.
     * Returns an empty string if neither is present.
     */
    fun getVisibleText(): String {
        return attributes["text"]?.takeIf { it.isNotBlank() }
            ?: attributes["content-desc"]?.takeIf { it.isNotBlank() }
            ?: ""
    }

    /**
     * Checks if the node's bounds are physically within the screen dimensions.
     * An element is considered visible if it has at least one pixel on the screen.
     */
    fun isVisibleOnScreen(screenWidth: Int, screenHeight: Int): Boolean {
        val boundsStr = attributes["bounds"] ?: return false

        val regex = """\[(\d+),(\d+)\]\[(\d+),(\d+)\]""".toRegex()
        val matchResult = regex.find(boundsStr) ?: return false

        return try {
            val (left, top, right, bottom) = matchResult.destructured.toList().map { it.toInt() }

            // Element is invisible if it's entirely off-screen
            if (right <= 0 || left >= screenWidth || bottom <= 0 || top >= screenHeight) {
                return false
            }

            // If it's not entirely off-screen, it's visible
            true
        } catch (e: NumberFormatException) {
            false
        }
    }
}

/**
 * Parses an Android View Hierarchy XML dump.
 *
 * This class can filter the XML to keep only semantically important nodes and can also
 * generate a custom string representation of the hierarchy that focuses on interactive elements.
 * It allows for retrieving the coordinates of these interactive elements by their generated index.
 */
class SemanticParser {

    // Stores a map of Integer index -> XmlNode for interactive elements from the last parse.
    private val interactiveNodeMap = mutableMapOf<Int, AccessibilityNodeInfo>()
    private var interactiveElementCounter = 0
    private var screenWidth: Int = 0
    private var screenHeight: Int = 0

    fun parseNodeTree(
        rootNode: AccessibilityNodeInfo,
        previousNodes: Set<String>?,
        screenWidth: Int,
        screenHeight: Int
    ): Pair<String, Map<Int, AccessibilityNodeInfo>> {
        this.screenWidth = screenWidth
        this.screenHeight = screenHeight

        interactiveNodeMap.clear()
        interactiveElementCounter = 0
        val stringBuilder = StringBuilder()

        // Recursively build the string starting from the root node.
        buildStringFromNodeRecursive(rootNode, 0, stringBuilder, previousNodes ?: emptySet())

        return Pair(stringBuilder.toString(), interactiveNodeMap)
    }

    private fun getExtraInfo(node: AccessibilityNodeInfo): String {
        val infoParts = mutableListOf<String>()
        if (node.isCheckable) infoParts.add("checkable")
        if (node.isChecked) infoParts.add("checked")
        if (node.isClickable) infoParts.add("clickable")
        if (node.isEnabled) infoParts.add("enabled")
        if (node.isFocusable) infoParts.add("focusable")
        if (node.isFocused) infoParts.add("focused")
        if (node.isScrollable) infoParts.add("scrollable")
        if (node.isLongClickable) infoParts.add("long clickable")
        if (node.isSelected) infoParts.add("selected")

        return if (infoParts.isNotEmpty()) {
            "This element is ${infoParts.joinToString(", ")}."
        } else {
            ""
        }
    }


    private fun buildStringFromNodeRecursive(
        node: AccessibilityNodeInfo,
        indentLevel: Int,
        builder: StringBuilder,
        previousNodes: Set<String>
    ) {
        // 1. Pruning/Visibility Check
        // We only process nodes visible to the user.
        if (!node.isVisibleToUser) {
            return
        }

        // 2. Get Node Properties
        val text = node.text?.toString() ?: ""
        val contentDesc = node.contentDescription?.toString() ?: ""
        val visibleText = if (text.isNotBlank()) text else contentDesc
        val resourceId = node.viewIdResourceName ?: ""
        val className = node.className?.toString() ?: ""

        // 3. Check Importance & Interactivity
        val isSemanticallyImportant = resourceId.isNotBlank() || visibleText.isNotBlank()
        // Check if node is interactive
        val isInteractive = (node.isClickable ||
                node.isLongClickable ||
                node.isCheckable ||
                node.isScrollable ||
                node.isEditable ||
                node.isFocusable) && node.isEnabled

        // We will print the node if it's interactive OR semantically important
        val shouldPrintNode = isInteractive || isSemanticallyImportant

        if (shouldPrintNode) {
            val nodeKey = "$visibleText|$resourceId|$className"
            val isNew = !previousNodes.contains(nodeKey) && isSemanticallyImportant
            val indent = "\t".repeat(indentLevel)

            if (isInteractive) {
                interactiveElementCounter++
                interactiveNodeMap[interactiveElementCounter] = node // Store the actual node

                val newMarker = if (isNew) "* " else ""
                val extraInfo = getExtraInfo(node) // Helper function
                val simpleClassName = className.removePrefix("android.widget.")

                // Format: [1] text:"<text>" <resource-id> <ExtraInfo> <class_name>
                builder.append("$indent$newMarker[$interactiveElementCounter] ")
                    .append("text:\"${visibleText.replace("\n", " ")}\" ")
                    .append("<$resourceId> ")
                    .append("<$extraInfo> ")
                    .append("<$simpleClassName>\n")

            } else {
                // Just text
                val newMarker = if (isNew) "* " else ""
                builder.append("$indent$newMarker${visibleText.replace("\n", " ")}\n")
            }
        }

        // 4. Recurse for children
        // If we printed this node, children are indented.
        // If we *didn't* print this node (e.g., invisible container),
        // its children are processed at the *same* indent level (this simulates child promotion).
        val nextIndent = if (shouldPrintNode) indentLevel + 1 else indentLevel
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                buildStringFromNodeRecursive(child, nextIndent, builder, previousNodes)
            }
        }
    }
    // --- Original Public API (Maintained) ---

    /**
     * The main public method to parse and filter the XML string, returning a filtered XML.
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
     * Recursively prunes the tree. It removes nodes that are not semantically important
     * and promotes their children to maintain the hierarchy.
     *
     * @param node The current node to process.
     * @return A list of nodes to be kept. If the current node is kept, it's a single-element
     * list. If it's removed, it's the list of its promoted children.
     */
    private fun prune(node: XmlNode): List<XmlNode> {
        // 1. Recursively process children and collect the promoted results.
        val newChildren = node.children.flatMap { prune(it) }
        node.children.clear()
        node.children.addAll(newChildren)
        newChildren.forEach { it.parent = node }
        if (!node.isVisibleOnScreen(screenWidth, screenHeight)) {
            return node.children
        }
        // 2. Decide what to do with the current node.
        return if (node.isSemanticallyImportant() || node.isInteractive() || node.children.isNotEmpty()) {
            // Keep this node. Its (potentially promoted) children are already attached.
            listOf(node)
        } else {
            // This node is not important and has no important descendants.
            // Discard it and promote its children up to its parent.
            node.children
        }
    }

    /**
     * Traverses the raw XML string and builds a tree of [XmlNode] objects.
     */
    private fun buildTreeFromXml(xmlString: String): XmlNode? {
        // Replace non-breaking spaces with regular spaces to prevent parsing issues.
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
                    // We only care about tags named "node"
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
     * Converts the final tree back to a formatted XML string.
     */
    private fun toXmlString(root: XmlNode): String {
        val stringBuilder = StringBuilder()
        // The root itself is usually <hierarchy>, let's start with its attributes
        stringBuilder.append("<hierarchy")
        root.attributes.forEach { (key, value) ->
            stringBuilder.append(" ").append(key).append("=\"").append(escapeXml(value)).append("\"")
        }
        stringBuilder.appendLine(">")

        // Recursively build string for all children of the root.
        root.children.forEach { child ->
            buildXmlStringRecursive(child, stringBuilder, 1)
        }

        stringBuilder.appendLine("</hierarchy>")
        return stringBuilder.toString()
    }

    private fun buildXmlStringRecursive(node: XmlNode, builder: StringBuilder, indentLevel: Int) {
        val indent = "  ".repeat(indentLevel)
        builder.append(indent).append("<node")

        // Append attributes
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

    private fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}