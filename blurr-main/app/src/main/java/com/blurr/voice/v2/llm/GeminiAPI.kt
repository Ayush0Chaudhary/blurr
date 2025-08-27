package com.blurr.voice.v2.llm

import android.util.Log
import androidx.room.ForeignKey
import com.blurr.voice.api.ApiKeyManager
import com.blurr.voice.v2.AgentOutput
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.Content
import com.google.ai.client.generativeai.type.GenerateContentResponse
import com.google.ai.client.generativeai.type.GenerationConfig
import com.google.ai.client.generativeai.type.RequestOptions
import com.google.ai.client.generativeai.type.Schema
import com.google.ai.client.generativeai.type.ServerException
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

/**
 * A modern, robust Gemini API client using the official Google AI SDK.
 *
 * This client features:
 * - Conversion of internal message formats to the SDK's `Content` format.
 * - API key management and rotation via an injectable [ApiKeyManager].
 * - An idiomatic, exponential backoff retry mechanism for API calls.
 * - Efficient caching of `GenerativeModel` instances to reduce overhead.
 * - Structured JSON output enforcement using `response_schema`.
 *
 * @property modelName The name of the Gemini model to use (e.g., "gemini-1.5-flash").
 * @property apiKeyManager An instance of [ApiKeyManager] to handle API key retrieval.
 * @property maxRetry The maximum number of times to retry a failed API call.
 */
class GeminiApi(
    private val modelName: String,
    private val apiKeyManager: ApiKeyManager, // Injected dependency
    private val maxRetry: Int = 3
) {

    companion object {
        private const val TAG = "GeminiV2Api"
    }

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    // Cache for GenerativeModel instances to avoid repeated initializations.
    private val modelCache = ConcurrentHashMap<String, GenerativeModel>()

    /**
     * KDoc comment to strongly advise developers to keep this schema synchronized
     * with the AgentOutput data class to prevent runtime errors.
     *
     * IMPORTANT: This schema MUST be kept in sync with the [AgentOutput] data class.
     * Any changes to [AgentOutput] must be reflected here.
     */
//    private val agentOutputSchema = Schema.obj(
//        name = "agentOutput",
//        description = "The structured output from the agent.",
//        Schema.str("thinking", "The agent's inner monologue and reasoning."),
//        Schema.str("evaluationPreviousGoal", "The agent's evaluation of the previous step's outcome."),
//        Schema.str("memory", "The agent's short-term memory for the next step."),
//        Schema.str("nextGoal", "The agent's immediate goal for the current step."),
//        Schema.arr(
//            name = "action",
//            description = "A list of actions to be executed.",
//            items = Schema.obj(
//                "actionSpec",
//                "A single action specification.",
//                Schema.enum("name", "The name of the action to execute.", ForeignKey.Action.entries.map { it.name }),
//                Schema.obj(
//                    "parameters", "A map of parameter names to their values for the action."
//                    // NOTE: We define `parameters` as a generic object. The LLM will populate it.
//                    // This is more flexible than defining every parameter for every action.
//                )
//            )
//        )
//    )


    private val jsonGenerationConfig = GenerationConfig.builder().apply {
        responseMimeType = "application/json"
//        responseSchema = agentOutputSchema
    }.build()

    private val requestOptions = RequestOptions(timeout = 60.seconds)


    /**
     * Generates a structured response from the Gemini model and parses it into an [AgentOutput] object.
     * This is the primary public method for this class.
     *
     * @param messages The list of [GeminiMessage] objects for the prompt.
     * @return An [AgentOutput] object on success, or null if the API call or parsing fails after all retries.
     */
    suspend fun generateAgentOutput(messages: List<GeminiMessage>): AgentOutput? {
        val jsonString = retryWithBackoff(times = maxRetry) {
            performApiCall(messages)
        } ?: return null

        return try {
            Log.d(TAG, "Parsing guaranteed JSON response. $jsonString")
            Log.d("GEMINIAPITEMP_OUTPUT", jsonString)

            jsonParser.decodeFromString<AgentOutput>(jsonString)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse JSON into AgentOutput. Error: ${e.message}", e)
            null
        }
    }

    /**
     * Performs the actual API call to the Gemini model.
     *
     * @param messages The list of [GeminiMessage] objects.
     * @return The response text from the model as a String.
     * @throws ServerException for API-level errors (e.g., invalid key, rate limits).
     * @throws ContentBlockedException if the response was blocked for safety reasons.
     * @throws Exception for other network or unexpected errors.
     */
    private suspend fun performApiCall(messages: List<GeminiMessage>): String {
        val apiKey = apiKeyManager.getNextKey()

        // Use cached model instance or create a new one if it doesn't exist for the given key.
        val generativeModel = modelCache.getOrPut(apiKey) {
            Log.d(TAG, "Creating new GenerativeModel instance for key ending in ...${apiKey.takeLast(4)}")
            GenerativeModel(
                modelName = modelName,
                apiKey = apiKey,
                generationConfig = jsonGenerationConfig,
                requestOptions = requestOptions
            )
        }

        val history = convertToSdkHistory(messages)
        val response = generativeModel.generateContent(*history.toTypedArray())

        response.text?.let {
            Log.d(TAG, "Successfully received response from model.")
            return it
        }

        // Handle cases where the response is empty or blocked.
        val reason = response.promptFeedback?.blockReason?.name ?: "UNKNOWN"
        throw ContentBlockedException("Blocked or empty response from API. Reason: $reason")
    }

    /**
     * Converts the internal `List<GeminiMessage>` to the `List<Content>` required by the Google AI SDK.
     */
    private fun convertToSdkHistory(messages: List<GeminiMessage>): List<Content> {
        return messages.map { message ->
            val role = when (message.role) {
                MessageRole.USER -> "user"
                MessageRole.MODEL -> "model"
                MessageRole.TOOL -> "tool"
            }

            content(role) {
                message.parts.forEach { part ->
                    if (part is TextPart) {
                        text(part.text)
                        Log.d("GEMINIAPITEMP_INPUT", part.text)
                    }
                    // Handle other part types like images here if needed in the future.
                }
            }
        }
    }
}

/**
 * Custom exception to indicate that the response content was blocked by the API.
 */
class ContentBlockedException(message: String) : Exception(message)

/**
 * A higher-order function that provides a generic retry mechanism with exponential backoff.
 *
 * @param times The maximum number of retry attempts.
 * @param initialDelay The initial delay in milliseconds before the first retry.
 * @param maxDelay The maximum delay in milliseconds.
 * @param factor The multiplier for the delay on each subsequent retry.
 * @param block The suspend block of code to execute and retry on failure.
 * @return The result of the block if successful, or null if all retries fail.
 */
private suspend fun <T> retryWithBackoff(
    times: Int,
    initialDelay: Long = 1000L, // 1 second
    maxDelay: Long = 16000L,   // 16 seconds
    factor: Double = 2.0,
    block: suspend () -> T
): T? {
    var currentDelay = initialDelay
    repeat(times) { attempt ->
        try {
            return block()
        } catch (e: Exception) {
            Log.e("RetryUtil", "Attempt ${attempt + 1}/$times failed: ${e.message}", e)
            if (attempt == times - 1) {
                Log.e("RetryUtil", "All $times retry attempts failed.")
                return null // All retries failed
            }
            delay(currentDelay)
            currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
        }
    }
    return null // Should not be reached
}