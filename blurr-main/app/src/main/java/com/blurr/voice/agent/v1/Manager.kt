package com.blurr.voice.agent.v1

import com.google.ai.client.generativeai.type.TextPart

class Manager : BaseAgent() {

    override fun initChat(): List<Pair<String, List<TextPart>>> {
        val systemPromptv1 = """
            You are a helpful AI assistant for operating mobile phones. Your goal is to track progress and devise high-level plans to achieve the user's requests. Think as if you are a human user operating the phone.
        """.trimIndent()
        return listOf("user" to listOf(TextPart(systemPromptv1)))
    }

    override fun getPrompt(infoPool: InfoPool, config: AgentConfig): String {
        val sb = StringBuilder()
        sb.appendLine("### User Instruction ###")
        sb.appendLine(infoPool.instruction)
        sb.appendLine()

        if (infoPool.recalledMemories.isNotEmpty()) {
            sb.appendLine(" ### Memories about User relevant to this Instruction ###")
            sb.appendLine(infoPool.recalledMemories)
            sb.appendLine()
        }

        if (infoPool.plan.isEmpty()) {
            if (config.isXmlMode) {
                if (infoPool.perceptionInfosPreMarkdown.isNotEmpty()) {
                    sb.appendLine("### Visible Screen Elements ###")
                    sb.appendLine("The following UI elements are currently visible on the screen:")
                    sb.appendLine()
                    sb.appendLine(infoPool.perceptionInfosPreMarkdown)
                    sb.appendLine()
                } else if (infoPool.perceptionInfosPreXML.isNotEmpty()) {
                    sb.appendLine("### Visible Screen Elements ###")
                    sb.appendLine("The following UI elements are currently visible on the screen in XML format:")
                    sb.appendLine(infoPool.perceptionInfosPreXML)
                    sb.appendLine()
                }
            }
            sb.appendLine("---")
            sb.appendLine("Think step by step and make an high-level plan to achieve the user's instruction. If the request is complex, break it down into subgoals. If the request involves exploration, include concrete subgoals to quantify the investigation steps. The screenshot displays the starting state of the phone.\n\n")

            sb.appendLine("---")
            sb.appendLine("Provide your output in the following format which contains three parts:\n")
            sb.appendLine("### Thought ###")
            sb.appendLine("A detailed explanation of your rationale for the plan and subgoals.\n")
            sb.appendLine("### Plan ###")
            sb.appendLine("1. first subgoal")
            sb.appendLine("2. second subgoal")
            sb.appendLine("...\n")
            sb.appendLine("### Current Subgoal ###")
            sb.appendLine("The first subgoal you should work on.\n")
        }
        else {
            sb.appendLine("### Last Action Analysis ###")
            sb.appendLine("You must first analyze the result of the last action before planning the next one.")
            sb.appendLine("- Last Action Performed: ${infoPool.lastAction}")
            sb.appendLine("- Expected Outcome: ${infoPool.lastSummary}")
            sb.appendLine()
            
            if (config.isXmlMode) {
                if (infoPool.perceptionInfosPreMarkdown.isNotEmpty()) {
                    sb.appendLine("### Screen State Before Last Action ###")
                    sb.appendLine("The following UI elements were visible before the last action:")
                    sb.appendLine()
                    sb.appendLine(infoPool.perceptionInfosPreMarkdown)
                    sb.appendLine()
                }
                
                if (infoPool.perceptionInfosPostMarkdown.isNotEmpty()) {
                    sb.appendLine("### Screen State After Last Action ###")
                    sb.appendLine("The following UI elements are now visible after the last action:")
                    sb.appendLine()
                    sb.appendLine(infoPool.perceptionInfosPostMarkdown)
                    sb.appendLine()
                } else {
                    sb.appendLine("### Screen State After Last Action ###")
                    sb.appendLine("The current screen content provided in 'Visible Screen Elements' is the result of the last action.")
                    sb.appendLine()
                }
            } else {
                sb.appendLine("### Screen State Before Last Action (XML) ###")
                sb.appendLine(infoPool.reflectionPreActionXML.ifEmpty { "N/A" })
                sb.appendLine()
                sb.appendLine("### Screen State After Last Action (XML) ###")
                sb.appendLine("The current screen content provided in 'Visible Screen Elements' is the result of the last action.")
                sb.appendLine()
            }

            sb.appendLine("---")
            sb.appendLine("### Current Plan & Progress ###")
            sb.appendLine("Current Plan: ${infoPool.plan}")
            sb.appendLine("Previous Subgoal: ${infoPool.prevSubgoal}")
            sb.appendLine("Previous Progress Status: ${infoPool.progressStatus}")
            sb.appendLine()




            if (infoPool.errorFlagPlan) {
                sb.appendLine("### Potentially Stuck! ###\n\n")
                sb.appendLine("You have encountered several failed attempts. Here are some logs:\n")

                val k = infoPool.errToManagerThresh
                val lastActions = infoPool.actionHistory.takeLast(k)
                val lastSummaries = infoPool.summaryHistory.takeLast(k)
                val lastErrors = infoPool.errorDescriptions.takeLast(k)
                for (i in lastActions.indices) {
                    sb.appendLine("- Attempt: Action: ${lastActions[i]} | Description: ${lastSummaries[i]} | Outcome: Failed | Feedback: ${lastErrors[i]}\n")
                }
            }
            sb.appendLine("\n\n")

            sb.appendLine("\nProvide your output in the following format, which contains six parts:\n")
            sb.appendLine("### Outcome ###")
            sb.appendLine("Analyze the last action based on the XML changes. Choose ONE: 'A' (Successful), 'B' (Failed, wrong page/state), 'C' (Failed, no change).")
            sb.appendLine()
            sb.appendLine("### Error Description ###")
            sb.appendLine("If the action failed (B or C), explain why. Otherwise, write 'None'.")
            sb.appendLine()
            sb.appendLine("### Progress Status ###")
            sb.appendLine("If successful, briefly update the overall task progress. If failed, you can copy the previous status or describe the stuck state.")
            sb.appendLine()
            sb.appendLine("### Thought ###")
            sb.appendLine("Provide a detailed explanation for your reflection (the 'why' behind the outcome) and the rationale for the next plan/subgoal.")
            sb.appendLine()
            sb.appendLine("### Plan ###")
            sb.appendLine("The high-level plan. Update it if necessary, otherwise copy the existing plan.")
            sb.appendLine()
            sb.appendLine("### Current Subgoal ###")
            sb.appendLine("The next subgoal to work on. If all subgoals are completed, write 'Finished'.")
        }


        return sb.toString()
    }

    override fun parseResponse(response: String): Map<String, String> {
        val outcome = extractSection(response, "### Outcome ###", "### Error Description ###")
        val errorDescription = extractSection(response, "### Error Description ###", "### Progress Status ###")
        val progressStatus = extractSection(response, "### Progress Status ###", "### Thought ###")
        val thought = extractSection(response, "### Thought ###", "### Plan ###")
        val plan = extractSection(response, "### Plan ###", "### Current Subgoal ###")
        val currentSubgoal = extractSection(response, "### Current Subgoal ###", null)

        return mapOf(
            "outcome" to outcome,
            "error_description" to errorDescription,
            "progress_status" to progressStatus,
            "thought" to thought,
            "plan" to plan,
            "current_subgoal" to currentSubgoal
        )
    }

    private fun extractSection(text: String, start: String, end: String?): String {
        val startIndex = text.indexOf(start)
        if (startIndex == -1) return ""

        val from = startIndex + start.length
        val to = end?.let { text.indexOf(it, from) } ?: text.length

        if (to == -1 || from >= text.length || from > to) return ""
        return text.substring(from, to).trim()
    }

}
