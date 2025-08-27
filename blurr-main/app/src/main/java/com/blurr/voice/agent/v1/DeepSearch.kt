package com.blurr.voice.agent.v1

import com.blurr.voice.BuildConfig
import com.blurr.voice.api.GeminiApi
import com.blurr.voice.api.TavilyApi

/**
 * A sophisticated research agent that uses a two-step LLM process.
 * 1. Pre-Search Analysis: Determines the best search strategy and parameters.
 * 2. Search & Summarization: Executes an advanced search and synthesizes the results.
 */
class DeepSearch {

    private val geminiApi = GeminiApi // Assumes this is a singleton now
    private val tavilyApi = TavilyApi(BuildConfig.TAVILY_API)

    /**
     * The main public function. Orchestrates the entire intelligent search process.
     * @param instruction The user's raw voice or text command.
     * @return The final, synthesized answer, or an error message.
     */
//    suspend fun execute(instruction: String): String {
//        return withContext(Dispatchers.IO) {
//            try {
//                // STEP 1: PRE-SEARCH ANALYSIS (LLM Call #1)
//                // Ask Gemini to determine the best way to search for this query.
//                Log.d("DeepSearch", "Step 1: Performing Pre-Search Analysis for: '$instruction'")
//                val searchStrategyJson = geminiApi.generateContent(
//                    prompt = getPreSearchAnalysisPrompt(instruction),
//                    modelName = "gemini-1.5-flash-latest" // Good for structured JSON output
//                )
//
//                if (searchStrategyJson == null) {
//                    Log.e("DeepSearch", "Pre-search analysis failed to return a strategy.")
//                    return@withContext "I couldn't figure out how to search for that."
//                }
//
//                val sanitizedJson = searchStrategyJson
//                    .replace("```json", "")
//                    .replace("```", "")
//                    .trim()
//
//                val searchParams = JSONObject(sanitizedJson)
//
//                val isSearchNeeded = searchParams.optBoolean("is_search_needed", false)
//
//                // If the LLM decides no search is needed, return its reasoning.
//                if (!isSearchNeeded) {
//
//                    return@withContext "NO-SEARCH"
//                }
//
//
//                // STEP 2: ADVANCED TAVILY SEARCH
//                // Execute the search using the optimized parameters from the LLM.
//                Log.d("DeepSearch", "Step 2: Executing Advanced Search with parameters: $searchParams")
//                val tavilyJsonResult = tavilyApi.search(searchParams)
//
//
//                // STEP 3: FINAL SUMMARIZATION (LLM Call #2)
//                // Feed the rich results back to Gemini to get a final answer.
//                Log.d("DeepSearch", "Step 3: Summarizing search results.")
//                val finalAnswer = geminiApi.generateContent(
//                    prompt = getSummarizationPrompt(instruction, tavilyJsonResult)
//                )
//
//                finalAnswer ?: "I found some information but had trouble summarizing it."
//
//            } catch (e: Exception) {
//                Log.e("DeepSearch", "Deep search execution failed", e)
//                "I'm sorry, I encountered an error while trying to find information."
//            }
//        }
//    }

    /**
     * Creates the prompt that asks the LLM to act as a research strategist.
     */
    private fun getPreSearchAnalysisPrompt(instruction: String): String {
        return """
            You are an expert research strategist. Your job is to analyze a user's request and create the optimal search plan for the Tavily Search API.

            You MUST respond in a valid JSON format.

            Analyze the user's instruction. Based on its content and intent, decide if a web search is necessary.
            - If no search is needed (e.g., the user is saying "hello" or giving a command like "stop"), respond with `{"is_search_needed": false, "reason": "A search is not required for this command."}`.
            - If a search is needed, determine the best parameters.

            Your JSON response should include:
            - "is_search_needed": (boolean) true if a search is required.
            - "query": (string) The optimized search query. Keep it concise (under 100 words).
            - "search_depth": (string) "advanced" for complex, specific questions, or "basic" for general ones.
            - "include_answer": (boolean) Set to true if the user is asking a direct question that could have a factual answer.
            - "topic": (string) Set to "news" if the query is about recent events (politics, sports, etc.). Otherwise, use "general".

            User's Instruction: "$instruction"

            JSON Response:
        """.trimIndent()
    }

    /**
     * Creates the prompt that asks the LLM to synthesize the Tavily results into a final answer.
     */
    private fun getSummarizationPrompt(originalQuery: String, tavilyJsonResult: String): String {
        return """
            You are a helpful summarization assistant. Your task is to provide a direct, comprehensive, and natural language answer to the user's original question.
            Use the provided JSON data from a search engine as your primary source of truth.
            Synthesize the information from the 'results' and the direct 'answer' field (if present) into a single, well-structured response.
            Do not just repeat the data; explain it.

            User's Original Question: "$originalQuery"

            JSON Search Results:
            $tavilyJsonResult

            Your Comprehensive Answer:
        """.trimIndent()
    }
}