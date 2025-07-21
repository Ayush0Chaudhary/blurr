package com.example.blurr.agent

import com.google.ai.client.generativeai.type.TextPart

class ClarificationAgent : BaseAgent() {

    override fun initChat(): List<Pair<String, List<TextPart>>> {
        val systemPrompt = """
            Think as if you are a human user operating the phone.
            You will be given a task to perform, based on that you have to find what is missing in the instruction that is important to fulfill the task.
        """.trimIndent()

        return listOf("user" to listOf(TextPart(systemPrompt)))
    }

    override fun getPrompt(infoPool: InfoPool, config: AgentConfig): String {
        return """
              You are a helpful AI assistant that analyzes user instructions and determines if they need clarification.
            Your goal is to identify unclear or ambiguous instructions and generate specific questions to clarify them.
            
            You should ask clarifying questions when:
            1. The instruction lacks specific details needed for execution
            2. There are multiple possible interpretations
            3. Required information is missing (names, apps, preferences, etc.)
            4. The instruction is too vague to execute properly
            
            Examples of unclear instructions that need clarification:
            - "Message my brother happy birthday" (needs: brother's name, messaging app)
            - "Open my photos" (needs: which photo app, what to do with photos)
            - "Call someone" (needs: who to call)
            - "Set an alarm" (needs: time, label, repeat settings)
            
            Examples of clear instructions that don't need clarification:
            - "Open WhatsApp"
            - "Go to home screen"
            - "Take a screenshot"
            - "Open Settings app"
            
            The agent can do speak, listen, see screen, tap screen, and basically use the phone as normal human would
        
            
            
            ### User Instruction ###
            ${infoPool.instruction}
            
            ### Task ###
            Analyze this instruction and determine if it needs clarification.
            
            If the instruction is clear and specific enough to execute without additional information, respond with "CLEAR".
            
            If the instruction needs clarification, provide a list of specific questions that would help clarify the missing information. Focus on practical details needed for execution.
            
            ### Response Format ###
            If the instruction is clear, respond with:
            ```
            STATUS: CLEAR
            QUESTIONS: NONE
            ```
            
            If the instruction needs clarification, respond with:
            ```
            STATUS: NEEDS_CLARIFICATION
            QUESTIONS:
            1. [First clarifying question]
            2. [Second clarifying question]
            3. [Third clarifying question]
            ```
            
            Keep questions specific, practical, and focused on information needed for task execution.
        """.trimIndent()
    }

    override fun parseResponse(response: String): Map<String, String> {
        val status = extractSection(response, "STATUS:", "QUESTIONS:")
        val questions = extractSection(response, "QUESTIONS:", null)
        
        return mapOf(
            "status" to status.trim(),
            "questions" to questions.trim()
        )
    }

    private fun extractSection(text: String, start: String, end: String?): String {
        val startIndex = text.indexOf(start)
        if (startIndex == -1) return ""
        val from = startIndex + start.length
        val to = end?.let { text.indexOf(it, from) } ?: text.length
        if (from >= text.length || from > to) return ""
        return text.substring(from, to).trim()
    }

    fun parseQuestions(questionsText: String): List<String> {
        if (questionsText.isBlank()) return emptyList()
        
        return questionsText.lines()
            .filter { it.trim().isNotEmpty() }
            .map { line ->
                // Remove numbering (1., 2., etc.) and clean up
                line.replace(Regex("^\\d+\\.\\s*"), "").trim()
            }
            .filter { it.isNotEmpty() }
    }
} 