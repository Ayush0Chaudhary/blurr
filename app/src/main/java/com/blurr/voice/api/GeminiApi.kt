/**
 * @file GeminiApi.kt
 * @brief Manages all interactions with the Google Gemini API, including proxying, logging, and error handling.
 *
 * This file contains the implementation of the `GeminiApi` singleton object. It is the central point
 * for making generative AI requests. It includes features like API key rotation, request retries,
 * detailed local file logging, and remote logging to Firestore. All requests are routed through a
 * specified proxy server.
 */
package com.blurr.voice.api

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.blurr.voice.BuildConfig
import com.blurr.voice.MyApplication
import com.blurr.voice.utilities.ApiKeyManager
import com.google.ai.client.generativeai.type.ImagePart
import com.google.ai.client.generativeai.type.TextPart
import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.blurr.voice.utilities.NetworkConnectivityManager
import com.blurr.voice.utilities.NetworkNotifier
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * A singleton object responsible for making requests to the Google Gemini API.
 *
 * This object handles the entire lifecycle of a Gemini API call, including:
 * - Building the request payload.
 * - Routing the request through a proxy.
 * - Managing API key rotation via [ApiKeyManager].
 * - Implementing a retry mechanism for transient errors.
 * - Performing comprehensive logging of requests and responses to both local files and Firestore.
 * - Checking for network connectivity before making a request.
 */
object GeminiApi {
    /** The URL of the proxy server to which all Gemini API requests are sent. */
    private val proxyUrl: String = BuildConfig.GCLOUD_PROXY_URL
    /** The API key for authenticating with the proxy server. */
    private val proxyKey: String = BuildConfig.GCLOUD_PROXY_URL_KEY

    /** The OkHttpClient instance used for all API requests, configured with timeouts. */
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    /** The Firestore database instance for remote logging. */
    val db = Firebase.firestore


    /**
     * The main function to generate content from the Gemini API.
     *
     * It orchestrates the process of building a request, sending it through the proxy,
     * handling retries, and logging the outcome.
     *
     * @param chat The conversation history, represented as a list of pairs where the first
     *             element is the role ("user" or "model") and the second is a list of parts
     *             (e.g., [TextPart], [ImagePart]).
     * @param images A list of [Bitmap] images to be included in the request. Note: The current
     *               proxy implementation ignores images.
     * @param modelName The name of the Gemini model to use for the request (e.g., "gemini-1.5-flash-latest").
     * @param maxRetry The maximum number of times to retry the request upon failure.
     * @param context The Android context, used for network connectivity checks and file logging.
     * @return A string containing the generated content from the model, or null if the request
     *         fails after all retries or if there is no network connection.
     */
    suspend fun generateContent(
        chat: List<Pair<String, List<Any>>>,
        images: List<Bitmap> = emptyList(),
        modelName: String = "gemini-1.5-flash-latest",
        maxRetry: Int = 4,
        context: Context? = null
    ): String? {
        // Network check before making any calls
        try {
            val appCtx = context ?: MyApplication.appContext
            val isOnline = NetworkConnectivityManager(appCtx).isNetworkAvailable()
            if (!isOnline) {
                Log.e("GeminiApi", "No internet connection. Skipping generateContent call.")
                NetworkNotifier.notifyOffline()
                return null
            }
        } catch (e: Exception) {
            Log.e("GeminiApi", "Network check failed, assuming offline. ${e.message}")
            return null
        }
        // Extract the last user prompt text for logging purposes.
        val lastUserPrompt = chat.lastOrNull { it.first == "user" }
            ?.second
            ?.filterIsInstance<TextPart>()
            ?.joinToString(separator = "\n") { it.text } ?: "No text prompt found"

        var attempts = 0
        while (attempts < maxRetry) {
            // Get a new API key for each attempt
            val currentApiKey = ApiKeyManager.getNextKey()
            Log.d("GeminiApi", "=== GEMINI API REQUEST (Attempt ${attempts + 1}) ===")
            Log.d("GeminiApi", "Using API key ending in: ...${currentApiKey.takeLast(4)}")
            Log.d("GeminiApi", "Model: $modelName")

            val attemptStartTime = System.currentTimeMillis()
            // IMPORTANT: Define payload here so it's accessible in the catch block for logging.
            val payload = buildPayload(chat, modelName)

            Log.d("GeminiApi", "Payload: ${payload.toString().take(500)}...")

            try {
                val request = Request.Builder()
                    .url(proxyUrl)
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .addHeader("Content-Type", "application/json")
                    .addHeader("X-API-Key", proxyKey)
                    .build()

                val requestStartTime = System.currentTimeMillis()
                client.newCall(request).execute().use { response ->
                    val responseEndTime = System.currentTimeMillis()
                    val requestTime = responseEndTime - requestStartTime
                    val totalAttemptTime = responseEndTime - attemptStartTime
                    val responseBody = response.body?.string()

                    Log.d("GeminiApi", "=== GEMINI API RESPONSE (Attempt ${attempts + 1}) ===")
                    Log.d("GeminiApi", "HTTP Status: ${response.code}")
                    Log.d("GeminiApi", "Request time: ${requestTime}ms")

                    if (!response.isSuccessful || responseBody.isNullOrEmpty()) {
                        Log.e("GeminiApi", "API call failed with HTTP ${response.code}. Response: $responseBody")
                        throw Exception("API Error ${response.code}: $responseBody")
                    }

                    // The proxy is expected to return the direct text response.
                    val parsedResponse = responseBody

                    val logEntry = createLogEntry(
                        attempt = attempts + 1,
                        modelName = modelName,
                        prompt = lastUserPrompt,
                        imagesCount = images.size,
                        payload = payload.toString(),
                        responseCode = response.code,
                        responseBody = responseBody,
                        responseTime = requestTime,
                        totalTime = totalAttemptTime
                    )
                    saveLogToFile(MyApplication.appContext, logEntry)
                    val logData = createLogDataMap(
                        attempt = attempts + 1,
                        modelName = modelName,
                        prompt = lastUserPrompt,
                        imagesCount = images.size,
                        responseCode = response.code,
                        responseTime = requestTime,
                        totalTime = totalAttemptTime,
                        responseBody = responseBody,
                        status = "pass"
                    )
                    logToFirestore(logData)


                    return parsedResponse
                }
            } catch (e: Exception) {
                val attemptEndTime = System.currentTimeMillis()
                val totalAttemptTime = attemptEndTime - attemptStartTime

                Log.e("GeminiApi", "=== GEMINI API ERROR (Attempt ${attempts + 1}) ===", e)

                // Save the error log entry to a file.
                val logEntry = createLogEntry(
                    attempt = attempts + 1,
                    modelName = modelName,
                    prompt = lastUserPrompt,
                    imagesCount = images.size,
                    payload = payload.toString(), // Log the payload that caused the error
                    responseCode = null,
                    responseBody = null,
                    responseTime = 0,
                    totalTime = totalAttemptTime,
                    error = e.message
                )
                saveLogToFile(MyApplication.appContext, logEntry)
                val logData = createLogDataMap(
                    attempt = attempts + 1,
                    modelName = modelName,
                    prompt = lastUserPrompt,
                    imagesCount = images.size,
                    responseCode = null,
                    responseTime = 0,
                    totalTime = totalAttemptTime,
                    status = "error",
                    responseBody = null,
                    error = e.message
                )
                logToFirestore(logData)

                attempts++
                if (attempts < maxRetry) {
                    val delayTime = 1000L * attempts
                    Log.d("GeminiApi", "Retrying in ${delayTime}ms...")
                    delay(delayTime)
                } else {
                    Log.e("GeminiApi", "Request failed after all ${maxRetry} retries.")
                    return null
                }
            }
        }
        return null
    }

    /**
     * Builds the JSON payload for the proxy server.
     *
     * This function constructs a [JSONObject] with the specified model name and a "messages"
     * array, formatted according to the proxy's requirements. It converts the abstract
     * chat history into a concrete JSON structure.
     *
     * Note: This implementation currently ignores [ImagePart]s, as the proxy format
     * does not support them.
     *
     * @param chat The conversation history.
     * @param modelName The name of the model to be included in the payload.
     * @return A [JSONObject] representing the request payload.
     */
    private fun buildPayload(chat: List<Pair<String, List<Any>>>, modelName: String): JSONObject {
        val rootObject = JSONObject()
        rootObject.put("modelName", modelName)

        val messagesArray = JSONArray()
        chat.forEach { (role, parts) ->
            val messageObject = JSONObject()
            messageObject.put("role", role.lowercase())

            val jsonParts = JSONArray()
            parts.forEach { part ->
                when (part) {
                    is TextPart -> {
                        val partObject = JSONObject().put("text", part.text)
                        jsonParts.put(partObject)
                    }
                    is ImagePart -> {
                        Log.w("GeminiApi", "ImagePart found but skipped. The proxy payload format does not support images.")
                    }
                }
            }

            if (jsonParts.length() > 0) {
                messageObject.put("parts", jsonParts)
                messagesArray.put(messageObject)
            }
        }

        rootObject.put("messages", messagesArray)
        return rootObject
    }

    /**
     * Saves a detailed log entry to a local file for debugging purposes.
     *
     * The log is appended to `gemini_api_log.txt` in the app's internal files directory.
     *
     * @param context The application context, used to access the file system.
     * @param logEntry The formatted string to be written to the log file.
     */
    private fun saveLogToFile(context: Context, logEntry: String) {
        try {
            val logDir = File(context.filesDir, "gemini_logs")
            if (!logDir.exists()) {
                logDir.mkdirs()
            }
            val logFile = File(logDir, "gemini_api_log.txt")

            FileWriter(logFile, true).use { writer ->
                writer.append(logEntry)
            }
            Log.d("GeminiApi", "Log entry saved to: ${logFile.absolutePath}")

        } catch (e: Exception) {
            Log.e("GeminiApi", "Failed to save log to file", e)
        }
    }

    /**
     * Logs a summary of the API call to a Firestore collection for remote monitoring.
     *
     * @param logData A map containing the key-value pairs to be logged.
     */
    private fun logToFirestore(logData: Map<String, Any?>) {
        val timestamp = System.currentTimeMillis()
        val promptSnippet = (logData["prompt"] as? String)?.take(40) ?: "log"
        val sanitizedPrompt = promptSnippet.replace(Regex("[^a-zA-Z0-9]"), "_")
        val documentId = "${timestamp}_$sanitizedPrompt"

        db.collection("gemini_logs")
            .document(documentId)
            .set(logData)
            .addOnSuccessListener {
                Log.d("GeminiApi", "Log sent to Firestore with ID: $documentId")
            }
            .addOnFailureListener { e ->
                Log.e("GeminiApi", "Error sending log to Firestore", e)
            }
    }

    /**
     * Creates a formatted, multi-line string for a detailed log entry.
     *
     * This is used to generate the content for the local log file.
     *
     * @return A formatted string containing all the provided log details.
     */
    private fun createLogEntry(
        attempt: Int,
        modelName: String,
        prompt: String,
        imagesCount: Int,
        payload: String,
        responseCode: Int?,
        responseBody: String?,
        responseTime: Long,
        totalTime: Long,
        error: String? = null
    ): String {
        return buildString {
            appendLine("=== GEMINI API DEBUG LOG ===")
            appendLine("Timestamp: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())}")
            appendLine("Attempt: $attempt")
            appendLine("Model: $modelName")
            appendLine("Images count: $imagesCount")
            appendLine("Prompt length: ${prompt.length}")
            appendLine("Prompt: $prompt")
            appendLine("Payload: $payload")
            appendLine("Response code: $responseCode")
            appendLine("Response time: ${responseTime}ms")
            appendLine("Total time: ${totalTime}ms")
            if (error != null) {
                appendLine("Error: $error")
            } else {
                appendLine("Response body: $responseBody")
            }
            appendLine("=== END LOG ===")
        }
    }

    /**
     * Creates a map of data for logging to Firestore.
     *
     * @return A map containing key-value pairs summarizing the API call.
     */
    private fun createLogDataMap(
        attempt: Int,
        modelName: String,
        prompt: String,
        imagesCount: Int,
        responseCode: Int?,
        responseTime: Long,
        totalTime: Long,
        status: String,
        responseBody: String?,
        error: String? = null
    ): Map<String, Any?> {
        return mapOf(
            "timestamp" to FieldValue.serverTimestamp(), // Use server time
            "status" to status,
            "attempt" to attempt,
            "model" to modelName,
            "prompt" to prompt,
            "imagesCount" to imagesCount,
            "responseCode" to responseCode,
            "responseTimeMs" to responseTime,
            "totalTimeMs" to totalTime,
            "llmReply" to responseBody,
            "error" to error
        )
    }
}