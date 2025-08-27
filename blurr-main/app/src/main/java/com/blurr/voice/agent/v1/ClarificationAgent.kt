package com.blurr.voice.agent.v1

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
            - "Book a uber ride for me" (needs: Destination, type of uber car)
            
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

    /**
     * Enhanced version of getPrompt that includes conversation history for better context-aware clarification decisions.
     * This allows the agent to consider previous conversation context when determining what clarifying questions to ask.
     */
    fun getPromptWithHistory(infoPool: InfoPool, config: AgentConfig, conversationHistory: List<Pair<String, List<Any>>>): String {
        val conversationContext = buildConversationContext(conversationHistory)
        
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
            - "Book a uber ride for me" (needs: Destination, type of uber car)
            
            Examples of clear instructions that don't need clarification:
            - "Open WhatsApp"
            - "Go to home screen"
            - "Take a screenshot"
            - "Open Settings app"
            
            The agent can do speak, listen, see screen, tap screen, and basically use the phone as normal human would
        
            ### Conversation History ###
            $conversationContext
            
            ### User Instruction ###
            ${infoPool.instruction}
            
            ### Task ###
            Analyze this instruction in the context of the conversation history and determine if it needs clarification.
            
            Consider the conversation history when making your decision:
            - If information was already provided in previous messages, don't ask for it again
            - If the user is referring to something mentioned earlier, use that context
            - If this is a follow-up to a previous request, consider what was already established
            
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

    /**
     * Builds a readable conversation context from the conversation history.
     * Extracts text content from the conversation history for context-aware clarification.
     */
    private fun buildConversationContext(conversationHistory: List<Pair<String, List<Any>>>): String {
        if (conversationHistory.isEmpty()) {
            return "No previous conversation context available."
        }

        val contextBuilder = StringBuilder()
        contextBuilder.appendLine("Previous conversation:")
        
        conversationHistory.forEachIndexed { index, (role, messageParts) ->
            // Extract text content from message parts
            val textContent = messageParts
                .filterIsInstance<TextPart>()
                .joinToString(" ") { it.text }
                .trim()
            
            if (textContent.isNotEmpty()) {
                contextBuilder.appendLine("${index + 1}. $role: $textContent")
            }
        }
        
        return contextBuilder.toString().trim()
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