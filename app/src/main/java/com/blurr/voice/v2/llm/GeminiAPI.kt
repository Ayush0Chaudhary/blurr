package com.blurr.voice.v2.llm

import android.util.Log
import com.blurr.voice.BuildConfig
import com.blurr.voice.utilities.ApiKeyManager
import com.blurr.voice.v2.AgentOutput
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

/**
 * Updated API Client for Cerebras (via Cloud Function Proxy).
 * Forces Llama 3.3 model to prevent 404 errors.
 */
class GeminiApi(
    // Even if DI passes "gemini-2.5-flash", we will ignore it in the proxy call below
    private val modelName: String = "llama-3.3-70b",
    private val apiKeyManager: ApiKeyManager,
    private val maxRetry: Int = 3
) {

    companion object {
        private const val TAG = "CerebrasApi"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        // Force the correct Cerebras model ID here
        private const val CEREBRAS_MODEL_ID = "llama-3.3-70b"
    }

    private val proxyUrl: String = BuildConfig.GCLOUD_PROXY_URL
    private val proxyKey: String = BuildConfig.GCLOUD_PROXY_URL_KEY

    private val httpClient = OkHttpClient.Builder()
        .callTimeout(60.seconds)
        .readTimeout(60.seconds)
        .build()

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
    }

    suspend fun generateAgentOutput(messages: List<GeminiMessage>): AgentOutput? {
        val jsonString = retryWithBackoff(times = maxRetry) {
            performProxyApiCall(messages)
        } ?: return null

        return try {
            // Log the raw response for debugging
            Log.d(TAG, "Raw Response: $jsonString")
            jsonParser.decodeFromString<AgentOutput>(jsonString)
        } catch (e: Exception) {
            Log.e(TAG, "JSON Parsing Failed. Raw: $jsonString", e)
            null
        }
    }

    private suspend fun performProxyApiCall(messages: List<GeminiMessage>): String {
        // 1. Convert internal messages to Cerebras/OpenAI format
        val cerebrasMessages = messages.mapIndexed { index, msg ->
            // Map Roles: MODEL -> assistant, USER -> user (or system if first)
            val role = when (msg.role) {
                MessageRole.MODEL -> "assistant"
                MessageRole.USER -> if (index == 0) "system" else "user"
                MessageRole.TOOL -> "user"
            }

            // Flatten Content: Cerebras text-only models expect a simple string
            val content = msg.parts.filterIsInstance<TextPart>()
                .joinToString("\n") { it.text }

            CerebrasMessage(role, content)
        }

        // 2. Create Payload
        // IMPORTANT: We use CEREBRAS_MODEL_ID constant to override any injected model name
        val requestPayload = CerebrasRequest(
            model = CEREBRAS_MODEL_ID,
            messages = cerebrasMessages
        )

        val jsonBody = jsonParser.encodeToString(requestPayload)

        // 3. Send Request
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

            // The Cloud Function returns just the content string (AgentOutput JSON)
            return responseBodyString
        }
    }
}

// --- Data Classes for Cerebras Format ---

@Serializable
private data class CerebrasRequest(
    val model: String,
    val messages: List<CerebrasMessage>,
    val temperature: Double = 0.7,
    val stream: Boolean = false
)

@Serializable
private data class CerebrasMessage(
    val role: String,
    val content: String
)

// --- Retry Logic ---

private suspend fun <T> retryWithBackoff(
    times: Int,
    initialDelay: Long = 1000L,
    maxDelay: Long = 10000L,
    factor: Double = 2.0,
    block: suspend () -> T
): T? {
    var currentDelay = initialDelay
    repeat(times) { attempt ->
        try {
            return block()
        } catch (e: Exception) {
            Log.e("CerebrasApi", "Attempt ${attempt + 1}/$times failed: ${e.message}")
            if (attempt == times - 1) return null
            delay(currentDelay)
            currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
        }
    }
    return null
}