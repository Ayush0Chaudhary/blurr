package com.blurr.voice.api

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.blurr.voice.MyApplication
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
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Refactored GeminiApi as a singleton object.
 * It now gets a rotated API key from ApiKeyManager for every request
 * and logs all requests and responses to a persistent file.
 */
object GeminiApi {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    val db = Firebase.firestore


    suspend fun generateContent(
        chat: List<Pair<String, List<Any>>>,
        images: List<Bitmap> = emptyList(),
        modelName: String = "gemini-2.5-flash", // Updated to a more standard model name
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
            val payload = buildPayload(chat)

            Log.d("GeminiApi", "=== GEMINI API REQUEST (Attempt ${attempts + 1}) ===")
            Log.d("GeminiApi", "Model: $modelName")
            Log.d("GeminiApi", "Payload: ${payload.toString().take(500)}...")

            try {
                val request = Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$currentApiKey")
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
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

                    val parsedResponse = parseSuccessResponse(responseBody)

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
                        responseCode = null,
                        responseTime = requestTime,
                        totalTime = totalAttemptTime,
                        responseBody = responseBody,
                        status = "pass",
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

    private fun buildPayload(chat: List<Pair<String, List<Any>>>): JSONObject {
        val contentsArray = JSONArray()
        // Loop through the entire conversation history
        chat.forEach { (role, parts) ->
            val jsonParts = JSONArray()

            // Handle text and image parts for each role
            parts.forEach { part ->
                when (part) {
                    is TextPart -> {
                        jsonParts.put(JSONObject().put("text", part.text))
                    }
                    is ImagePart -> {
                        val stream = ByteArrayOutputStream()
                        part.image.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                        val base64Image = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
                        val imagePart = JSONObject().put("inline_data", JSONObject()
                            .put("mime_type", "image/jpeg")
                            .put("data", base64Image)
                        )
                        jsonParts.put(imagePart)
                    }
                }
            }

            val contentObject = JSONObject()
                .put("role", role)
                .put("parts", jsonParts)
            contentsArray.put(contentObject)
        }
        return JSONObject().put("contents", contentsArray)
    }

    private fun parseSuccessResponse(responseBody: String): String? {
        val json = JSONObject(responseBody)
        if (!json.has("candidates")) {
            Log.w("GeminiApi", "API response has no 'candidates'. It was likely blocked. Full response: $responseBody")
            return null
        }
        return json.getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")
    }

    private fun saveLogToFile(context: Context, logEntry: String) {
        try {
            val logDir = File(context.filesDir, "gemini_logs")
            if (!logDir.exists()) {
                logDir.mkdirs()
            }
            // Use a single, rolling log file for simplicity.
            val logFile = File(logDir, "gemini_api_log.txt")

            FileWriter(logFile, true).use { writer ->
                writer.append(logEntry)
            }
            Log.d("GeminiApi", "Log entry saved to: ${logFile.absolutePath}")

        } catch (e: Exception) {
            Log.e("GeminiApi", "Failed to save log to file", e)
        }
    }
    private fun logToFirestore(logData: Map<String, Any?>) {
        // Create a unique and descriptive ID from the timestamp and prompt
        val timestamp = System.currentTimeMillis()
        val promptSnippet = (logData["prompt"] as? String)?.take(40) ?: "log"

        // Sanitize the prompt snippet to be a valid Firestore document ID
        // (removes spaces and special characters)
        val sanitizedPrompt = promptSnippet.replace(Regex("[^a-zA-Z0-9]"), "_")

        val documentId = "${timestamp}_$sanitizedPrompt"

        // Use .document(ID).set(data) instead of .add(data)
        db.collection("gemini_logs")
            .document(documentId)
            .set(logData)
            .addOnSuccessListener {
                Log.d("GeminiApi", "Log sent to Firestore with ID: $documentId")
            }
            .addOnFailureListener { e ->
                // This listener is for debugging; it won't block your app's flow
                Log.e("GeminiApi", "Error sending log to Firestore", e)
            }
    }
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