package com.example.blurr.agent

import com.google.ai.client.generativeai.type.TextPart

class ActionReflector : BaseAgent() {

    override fun initChat(): List<Pair<String, List<TextPart>>> {
        val systemPrompt = """
            You are an intelligent AI agent responsible for analyzing the results of mobile device actions and determining their success or failure.
            
            Your core responsibilities:
            1. Compare the before and after states of the device screen
            2. Determine if the action achieved its intended outcome
            3. Update progress status for successful actions
            4. Provide detailed error analysis for failed actions
            
            Analysis approach:
            - For XML mode: Focus on structural changes in UI hierarchy, element properties, and navigation state
            - For Screenshot mode: Focus on visual changes, text content, button states, and screen transitions
            
            Be thorough but concise in your analysis. Consider the context of the user's overall goal when evaluating success.
        """.trimIndent()
        
        return listOf("user" to listOf(TextPart(systemPrompt)))
    }

    override fun getPrompt(infoPool: InfoPool, config: AgentConfig): String {
        val sb = StringBuilder()

        sb.appendLine("### User Instruction ###")
        sb.appendLine(infoPool.instruction)
        sb.appendLine()

        sb.appendLine("### Progress Status ###")
        sb.appendLine(infoPool.progressStatus.ifEmpty { "No progress yet." })
        sb.appendLine()

        sb.appendLine("### Current Subgoal ###")
        sb.appendLine(infoPool.currentSubgoal)
        sb.appendLine()

        sb.appendLine("---")

        if (infoPool.tips.isNotEmpty()){
            sb.appendLine("### Tips ###")
            sb.appendLine("From previous experience interacting with the device, you have collected the following tips that might be useful for analyzing the results of actions:\n")
            sb.appendLine(infoPool.tips)
        }

        // Vision mode specific instructions
        if (config.isXmlMode) {
            sb.appendLine("You are analyzing UI changes using XML structure data. Focus on structural changes in the UI hierarchy.")
            sb.appendLine("Screen dimensions: ${infoPool.width} x ${infoPool.height} pixels")
            sb.appendLine()
        } else {
            sb.appendLine("You are analyzing visual changes using screenshots. Focus on visual and textual changes.")
            sb.appendLine("Screen dimensions: ${infoPool.width} x ${infoPool.height} pixels")
            sb.appendLine()
        }

        // Screen Information Before Action
        sb.appendLine("### Screen Information Before the Action ###")
        if (config.isXmlMode) {
            if (infoPool.perceptionInfosPreXML.isNotEmpty()) {
                sb.appendLine("### UI Structure (XML) ###")
                sb.appendLine("The following UI elements were visible on the screen in XML format:")
                sb.appendLine(infoPool.perceptionInfosPreXML)
                sb.appendLine()
            } else {
                sb.appendLine("No XML data available for pre-action state.")
            }
        } else {
            if (infoPool.perceptionInfosPre.isNotEmpty()) {
                sb.appendLine("### Extracted UI Elements ###")
                sb.appendLine("The following UI elements were detected (format: element_text; coordinates):")
                infoPool.perceptionInfosPre.forEach {
                    if (it.text.isNotBlank() && it.coordinates != Pair(0, 0)) {
                        sb.appendLine("${it.text}; ${it.coordinates}")
                    }
                }
                sb.appendLine()
            } else {
                sb.appendLine("No visual elements detected in pre-action screenshot.")
            }
        }
        
        sb.appendLine("Keyboard status before action: ${if (infoPool.keyboardPre) "Active - typing enabled" else "Inactive - typing disabled"}")
        sb.appendLine()

        // Screen Information After Action
        sb.appendLine("### Screen Information After the Action ###")
        if (config.isXmlMode) {
            if (infoPool.perceptionInfosPostXML.isNotEmpty()) {
                sb.appendLine("### UI Structure (XML) ###")
                sb.appendLine("The following UI elements are now visible on the screen in XML format:")
                sb.appendLine(infoPool.perceptionInfosPostXML)
                sb.appendLine()
            } else {
                sb.appendLine("No XML data available for post-action state.")
            }
        } else {
            if (infoPool.perceptionInfosPost.isNotEmpty()) {
                sb.appendLine("### Extracted UI Elements ###")
                sb.appendLine("The following UI elements are now detected (format: element_text; coordinates):")
                infoPool.perceptionInfosPost.forEach {
                    if (it.text.isNotBlank() && it.coordinates != Pair(0, 0)) {
                        sb.appendLine("${it.text}; ${it.coordinates}")
                    }
                }
                sb.appendLine()
            } else {
                sb.appendLine("No visual elements detected in post-action screenshot.")
            }
        }
        
        sb.appendLine("Keyboard status after action: ${if (infoPool.keyboardPost) "Active - typing enabled" else "Inactive - typing disabled"}")
        sb.appendLine()

        // Action Details
        sb.appendLine("---")
        sb.appendLine("### Latest Action ###")
        sb.appendLine("Action performed: ${infoPool.lastAction}")
        sb.appendLine("Expected outcome: ${infoPool.lastSummary}")
        sb.appendLine()

        // Analysis Instructions
        sb.appendLine("---")
        sb.appendLine("Carefully analyze the changes between the before and after states to determine if the action was successful.")
        sb.appendLine()
        
        if (config.isXmlMode) {
            sb.appendLine("Focus on structural changes in the UI hierarchy, new elements, removed elements, or changes in element properties.")
        } else {
            sb.appendLine("Focus on visual changes, new text elements, button states, navigation changes, or content updates.")
        }
        
        sb.appendLine()
        sb.appendLine("Special considerations:")
        sb.appendLine("- For 'Swipe' actions: New content appearing usually indicates success")
        sb.appendLine("- For 'Tap' actions: UI state changes or navigation typically indicate success")
        sb.appendLine("- For 'Type' actions: Text input or form completion indicates success")
        sb.appendLine("- For 'Back' actions: Returning to previous screen indicates success")
        sb.appendLine()
        
        // Add mode-specific analysis hints
        sb.appendLine(getModeSpecificHints(config))
        sb.appendLine()

        // Output Format
        sb.appendLine("Provide your analysis in the following format:")
        sb.appendLine()
        sb.appendLine("### Outcome ###")
        sb.appendLine("Choose from the following options. Give your answer as \"A\", \"B\" or \"C\":")
        sb.appendLine("A: Successful or Partially Successful. The result of the last action meets the expectation.")
        sb.appendLine("B: Failed. The last action results in a wrong page. I need to return to the previous state.")
        sb.appendLine("C: Failed. The last action produces no changes.")
        sb.appendLine()
        sb.appendLine("### Error Description ###")
        sb.appendLine("If the action failed, provide a detailed description of the error and the potential reason causing this failure. If the action succeeded, put \"None\" here.")
        sb.appendLine()
        sb.appendLine("### Progress Status ###")
        sb.appendLine("If the action was successful or partially successful, update the progress status. If the action failed, copy the previous progress status.")

        sb.appendLine("### Tips ###\n")
        sb.appendLine("Important tips (2-3 tips) that you encountered while analyzing the results of actions and could be useful later. tips should be concise (1 or 2 statements).\n")
        sb.appendLine("tip-title: first tip concise description\n")
        sb.appendLine("tip-title: second tip concise description\n")
        sb.appendLine("...\n")

        return sb.toString()
    }

    override fun parseResponse(response: String): Map<String, String> {
        val outcome = extractSection(response, "### Outcome ###", "### Error Description ###")
        val errorDescription = extractSection(response, "### Error Description ###", "### Progress Status ###")
        val progressStatus = extractSection(response, "### Progress Status ###", "### Tips ###")
        val tips = extractSection(response, "### Tips ###", null)

        return mapOf(
            "outcome" to outcome,
            "error_description" to errorDescription,
            "progress_status" to progressStatus,
            "tips" to tips
        )
    }

    /**
     * Extracts a section from the response text between specified markers
     */
    private fun extractSection(text: String, start: String, end: String?): String {
        val startIndex = text.indexOf(start)
        if (startIndex == -1) return ""
        val from = startIndex + start.length
        val to = end?.let { text.indexOf(it, from) } ?: text.length
        return text.substring(from, to).trim()
    }

    /**
     * Provides mode-specific analysis hints
     */
    fun getModeSpecificHints(config: AgentConfig): String {
        return when (config.visionMode) {
            VisionMode.XML -> """
                XML Mode Analysis Hints:
                - Look for changes in element hierarchy (new nodes, removed nodes)
                - Check for changes in element properties (enabled/disabled, visible/hidden)
                - Focus on navigation state changes (activity changes, fragment changes)
                - Pay attention to text content changes in text elements
                - Consider changes in element IDs or resource IDs
            """.trimIndent()
            
            VisionMode.SCREENSHOT -> """
                Screenshot Mode Analysis Hints:
                - Look for visual changes in UI elements
                - Check for new text content or removed text
                - Focus on button state changes (enabled/disabled, pressed/released)
                - Pay attention to navigation indicators (back buttons, titles)
                - Consider changes in screen layout or content positioning
            """.trimIndent()
        }
    }
}
