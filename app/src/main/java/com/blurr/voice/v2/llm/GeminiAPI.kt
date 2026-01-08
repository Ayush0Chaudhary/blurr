package com.blurr.voice.v2.llm

import android.util.Log
import com.blurr.voice.BuildConfig
import com.blurr.voice.utilities.ApiKeyManager
import com.blurr.voice.v2.AgentOutput
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.Content
import com.google.ai.client.generativeai.type.GenerationConfig
import com.google.ai.client.generativeai.type.RequestOptions
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import com.blurr.voice.v2.logging.TaskLogger
import android.content.Context
import kotlinx.serialization.encodeToString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
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
    private val context: Context,
    private val maxRetry: Int = 3
) {

    companion object {
        private const val TAG = "GeminiV2Api"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val proxyUrl: String = BuildConfig.GCLOUD_PROXY_URL
    private val proxyKey: String = BuildConfig.GCLOUD_PROXY_URL_KEY

    private val httpClient = OkHttpClient()

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    // Cache for GenerativeModel instances to avoid repeated initializations.
    private val modelCache = ConcurrentHashMap<String, GenerativeModel>()



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

        // Log the task
        try {
            val input = jsonParser.encodeToString(messages)
            TaskLogger.log(context, input, jsonString)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log task: ${e.message}")
        }

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
     * AUTOMATIC DISPATCHER: Checks internal config and decides whether to use
     * the secure proxy or a direct API call.
     */
    private suspend fun performApiCall(messages: List<GeminiMessage>): String {
        return if (!proxyUrl.isNullOrBlank() && !proxyKey.isNullOrBlank()) {
            Log.i(TAG, "Proxy config found. Using secure Cloud Function.")
            performProxyApiCall(messages)
        } else {
            Log.i(TAG, "Proxy config not found. Using direct Gemini SDK call (Fallback).")
            performDirectApiCall(messages)
        }
    }

    /**
     * PROXY MODE: Performs the API call through the secure Google Cloud Function.
     */
    private suspend fun performProxyApiCall(messages: List<GeminiMessage>): String {
        val proxyMessages = messages.map {
            ProxyRequestMessage(
                role = it.role.name.lowercase(),
                parts = it.parts.filterIsInstance<TextPart>().map { part -> ProxyRequestPart(part.text) }
            )
        }
        val requestPayload = ProxyRequestBody(modelName, proxyMessages)
        val jsonBody = jsonParser.encodeToString(ProxyRequestBody.serializer(), requestPayload)

        val request = Request.Builder()
            .url(proxyUrl)
            .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
            .addHeader("Content-Type", "application/json")
            .addHeader("X-API-Key", proxyKey)
            .build()

        httpClient.newCall(request).execute().use { response ->
            val responseBodyString = response.body?.string()
            if (!response.isSuccessful || responseBodyString.isNullOrBlank()) {
                val errorMsg = "Proxy API call failed with code: ${response.code}, body: $responseBodyString"
                Log.e(TAG, errorMsg)
                throw IOException(errorMsg)
            }
            Log.d(TAG, "Successfully received response from proxy.")
            return responseBodyString
        }
    }

    /**
     * DIRECT MODE: Performs the API call using the embedded Google AI SDK.
     */
    private suspend fun performDirectApiCall(messages: List<GeminiMessage>): String {
        val apiKey = apiKeyManager.getNextKey()
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
                        if(part.text.startsWith("<agent_history>") || part.text.startsWith("Memory:")) {
                            Log.d("GEMINIAPITEMP_INPUT", part.text)
                        }
                    }
                    // Handle other part types like images here if needed in the future.
                }
            }
        }
    }

    /**
     * WORKAROUND: Generates content using a direct REST API call to enable Google Search grounding.
     * This should be used for queries requiring real-time information until the Kotlin SDK
     * officially supports the search tool.
     *
     * @param prompt The user's text prompt.
     * @return The generated text content as a String, or null on failure.
     */
    suspend fun generateGroundedContent(prompt: String): String? {
        val apiKey = apiKeyManager.getNextKey() // Reuse your existing key manager

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent"

        // 1. Manually construct the JSON body to include the "google_search" tool
        val jsonBody = """
        {
          "contents": [
            {
              "parts": [
                {"text": "$prompt"}
              ]
            }
          ],
          "tools": [
            {
              "google_search": {}
            }
          ]
        }
    """.trimIndent()

        val requestBody = jsonBody.toRequestBody(mediaType)

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("x-goog-api-key", apiKey)
            .build()

        return try {
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful || responseBody == null) {
                Log.e(TAG, "Grounded API call failed with code: ${response.code}, body: $responseBody")
                return null
            }

            // 2. Parse the JSON response to extract the model's text output
            val text = JSONObject(responseBody)
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")

            Log.d(TAG, "Successfully received grounded response.")
            text

        } catch (e: Exception) {
            Log.e(TAG, "Exception during grounded API call", e)
            null
        }
    }

}

@Serializable
private data class ProxyRequestPart(val text: String)

@Serializable
private data class ProxyRequestMessage(val role: String, val parts: List<ProxyRequestPart>)

@Serializable
private data class ProxyRequestBody(val modelName: String, val messages: List<ProxyRequestMessage>)


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