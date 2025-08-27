package com.blurr.voice.agent.v1

import com.google.ai.client.generativeai.type.TextPart

class Judge {

    // Initializes the chat with a system prompt
    fun initChat(): List<Pair<String, List<TextPart>>> {
        val systemPrompt = """
            You are a Judge. The user will specify content they want to avoid on their phone. Your role is to decide whether the content is appropriate based on the user's preferences.
            
            For example: If the user states they want to avoid adult content, and you detect the user has opened such content, you must reject it. If no violation is found, you should accept the content.
            NOTE: DON'T BE PROACTIVE, ONLY REJECT WHEN YOU HAVE THE EVIDENCE.
        """.trimIndent()

        return listOf("user" to listOf(TextPart(systemPrompt)))
    }

    // Generates the prompt based on user request and screen content
    fun getPrompt(inst: String, perceptionInfoXML: String, ssPresent: Boolean): String {
        val sb = StringBuilder()

        // Provide clear instructions for the system
        sb.appendLine("### User Request: Content to be Avoided ###")
        sb.appendLine(inst)
        sb.appendLine()

        if(ssPresent){
            sb.appendLine("I have included a screenshot too")
        }
        sb.appendLine("### Current Screen Content (XML Representation) ###")
        sb.appendLine(perceptionInfoXML)
        sb.appendLine("\n\n-----\n")

        sb.appendLine("Provide your output in the following format containing three parts:\n")
        sb.appendLine("### Judgement ###")
        sb.appendLine("Choose from the following options. Give your answer as \\\"A\\\", \\\"B\\\" or \\\"C\\\":\\n\"")
        sb.appendLine("A: Accept the content – the content is suitable for the user.")
        sb.appendLine("B: Reject the content – the content is inappropriate based on the user's preferences.")
        sb.appendLine("C: Uncertain – the decision is unclear; a screenshot of the phone’s screen is needed for further review.")

        sb.appendLine("### Reasoning ###")
//        sb.appendLine("Provide a detailed rationale for your decision based on the user's preferences and the current screen content.")
        sb.appendLine("Provide a accurate rationale for your decision based on the user's preferences and the current screen content. Try to keep it light hearted and if possible funny. phrase it as if you are talking to the user")

        sb.appendLine("\n----\n")
        sb.appendLine("NOTE: Only include the necessary information. Avoid adding any extra details.")

        // Output the generated prompt for the judge
        return sb.toString()
    }


     fun parseResponse(response: String): Map<String, String> {
        val outcome = extractSection(response, "### Judgement ###", "### Reasoning ###")
        val errorDescription = extractSection(response, "### Reasoning ###", null)
        return mapOf(
            "judgement" to outcome,
            "reason" to errorDescription,
        )
    }

    private fun extractSection(text: String, start: String, end: String?): String {
        val startIndex = text.indexOf(start)
        if (startIndex == -1) return ""
        val from = startIndex + start.length
        val to = end?.let { text.indexOf(it, from) } ?: text.length
        return text.substring(from, to).trim()
    }
}
