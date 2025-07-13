package com.example.blurr.agent

import com.google.ai.client.generativeai.type.TextPart

class Manager : BaseAgent() {

    override fun initChat(): List<Pair<String, List<TextPart>>> {
        val systemPromptv1 = """
            You are a helpful AI assistant for operating mobile phones. Your goal is to track progress and devise high-level plans to achieve the user's requests. Think as if you are a human user operating the phone.
            NOTE: Use browsers for general search
        """.trimIndent()
        return listOf("user" to listOf(TextPart(systemPromptv1)))
    }

    override fun getPrompt(infoPool: InfoPool, config: AgentConfig): String {
        val sb = StringBuilder()
        sb.appendLine("### User Instruction ###")
        sb.appendLine(infoPool.instruction)
        sb.appendLine()

        if (infoPool.tips.isNotEmpty()) {
            sb.appendLine("### Tips ###")
            sb.appendLine("The following tips contain valuable insights. use them to make better plans:")
            sb.appendLine(infoPool.tips)
            sb.appendLine()
        }

        if (infoPool.recalledMemories.isNotEmpty()) {
            sb.appendLine(" ### Memories about User relevant to this Instruction ###")
            sb.appendLine(infoPool.recalledMemories)
            sb.appendLine()
        }

        if (infoPool.perceptionInfosPre.isNotEmpty() && !config.isXmlMode ) {
            sb.appendLine("### Visible Screen Elements ###")
            sb.appendLine("The following UI elements are currently visible on the screen:")
            infoPool.perceptionInfosPre.forEach { element ->
                sb.appendLine("- Text: \"${element.text}\" at position ${element.coordinates}")
            }
            sb.appendLine()
        }
        if (infoPool.perceptionInfosPreXML.isNotEmpty() && config.isXmlMode) {
            sb.appendLine("### Visible Screen Elements ###")
            sb.appendLine("The following UI elements are currently visible on the screen in XML format:")
            sb.appendLine(infoPool.perceptionInfosPreXML)
            sb.appendLine()
        }
        if (infoPool.plan.isEmpty()) {
            sb.appendLine("---")
            sb.appendLine("Think step by step and make an high-level plan to achieve the user's instruction. If the request is complex, break it down into subgoals. If the request involves exploration, include concrete subgoals to quantify the investigation steps. The screenshot displays the starting state of the phone.\n\n")

            sb.appendLine("---")
            sb.appendLine("Provide your output in the following format which contains four parts:\n")
            sb.appendLine("### Thought ###")
            sb.appendLine("A detailed explanation of your rationale for the plan and subgoals.\n")
            sb.appendLine("### Plan ###")
            sb.appendLine("1. first subgoal")
            sb.appendLine("2. second subgoal")
            sb.appendLine("...\n")
            sb.appendLine("### Current Subgoal ###")
            sb.appendLine("The first subgoal you should work on.\n")
            sb.appendLine("### Tips ###\n")
            sb.appendLine("Important tips (2-3 tips) that you encountered while making the plan and could be useful later. tips should be concise (1 or 2 statements).\n")
            sb.appendLine("tip-title: first tip concise description\n")
            sb.appendLine("tip-title: second tip concise description\n")
            sb.appendLine("...\n")
        }
        else {
            sb.appendLine("### Current Plan ###")
            sb.appendLine("${infoPool.plan}\n")
            sb.appendLine("### Previous Subgoal ###")
            sb.appendLine("${infoPool.currentSubgoal}\n")
            sb.appendLine("### Progress Status ###")
            sb.appendLine("${ infoPool.progressStatus }\n")
            sb.appendLine("### Important Notes ###")
            if (infoPool.importantNotes.isNotEmpty()){
                infoPool.importantNotes.forEach { element ->
                    sb.appendLine("- $element ")
                }
                sb.appendLine("\n")
            }else{
                sb.appendLine("No important notes\n\n")
            }

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
            sb.appendLine("The sections above provide an overview of the plan you are following, the current subgoal you are working on, the overall progress made, and any important notes you have recorded. The screenshot displays the current state of the phone.\n")
            sb.appendLine("Carefully assess the current status to determine if the task has been fully completed. If the user's request involves exploration, ensure you have conducted sufficient investigation. If you are confident that no further actions are required, mark the task as \"Finished\" in your output. If the task is not finished, outline the next steps. If you are stuck with errors, think step by step about whether the overall plan needs to be revised to address the error.\n")
            sb.appendLine("NOTE: If the current situation prevents proceeding with the original plan or requires clarification from the user, make reasonable assumptions and revise the plan accordingly. Act as though you are the user in such cases.\n\n")



            sb.appendLine("---\n")
            sb.appendLine("Provide your output in the following format, which contains three parts:\n\n")
            sb.appendLine("### Thought ###")
            sb.appendLine("Provide a detailed explanation of your rationale for the plan and subgoals.\n")
            sb.appendLine("### Plan ###")
            sb.appendLine("If an update is required for the high-level plan, provide the updated plan here. Otherwise, keep the current plan and copy it here.\n")
            sb.appendLine("### Current Subgoal ###")
            sb.appendLine("The next subgoal to work on. If the previous subgoal is not yet complete, copy it here. If all subgoals are completed, write \"Finished\".")
            sb.appendLine("### Tips ###\n")
            sb.appendLine("Important tips (2-3 tips) that you encountered while making the plan and could be useful later. tips should be concise (1 or 2 statements).\n")
            sb.appendLine("tip-title: first tip concise description\n")
            sb.appendLine("tip-title: second tip concise description\n")
            sb.appendLine("...\n")
        }


        return sb.toString()
    }

    override fun parseResponse(response: String): Map<String, String> {
        val thought = extractSection(response, "### Thought ###", "### Plan ###")
        val plan = extractSection(response, "### Plan ###", "### Current Subgoal ###")
        val currentSubgoal = extractSection(response, "### Current Subgoal ###", "### Tips ###")
        val tips = extractSection(response, "### Tips ###", null)

        return mapOf(
            "thought" to thought,
            "plan" to plan,
            "current_subgoal" to currentSubgoal,
            "tips" to tips
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
