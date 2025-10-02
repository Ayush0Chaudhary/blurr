package com.blurr.voice.api

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
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
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

object GeminiApi {
    private val proxyUrl: String = BuildConfig.GCLOUD_PROXY_URL
    private val proxyKey: String = BuildConfig.GCLOUD_PROXY_URL_KEY

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    val db = Firebase.firestore

    suspend fun generateContent(
        chat: List<Pair<String, List<Any>>>,
        images: List<Bitmap> = emptyList(),
        modelName: String = "gemini-1.5-flash",
        maxRetry: Int = 4,
        context: Context? = null
    ): String? {
        val appCtx = context ?: MyApplication.appContext
        try {
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

        val lastUserPrompt = chat.lastOrNull { it.first == "user" }
            ?.second
            ?.filterIsInstance<TextPart>()
            ?.joinToString(separator = "\n") { it.text } ?: "No text prompt found"

        val apiKeyManager = ApiKeyManager.getInstance(appCtx)
        val useCustomKeys = apiKeyManager.useCustomKeys()

        var attempts = 0
        while (attempts < maxRetry) {
            val attemptStartTime = System.currentTimeMillis()
            var responseBody: String?
            var payload: JSONObject
            var request: Request

            try {
                if (useCustomKeys) {
                    // --- Direct API Call Logic ---
                    val apiKey = apiKeyManager.getNextKey()
                    if (apiKey.isNullOrEmpty()) {
                        Log.e("GeminiApi", "Custom key is selected but no key is available.")
                        return null
                    }
                    Log.d("GeminiApi", "Using DIRECT API call with user-provided key.")
                    payload = buildDirectPayload(chat, images)
                    request = Request.Builder()
                        .url("https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey")
                        .post(payload.toString().toRequestBody("application/json".toMediaType()))
                        .addHeader("Content-Type", "application/json")
                        .build()
                } else {
                    // --- Proxy Call Logic ---
                    Log.d("GeminiApi", "Using PROXY API call.")
                    payload = buildProxyPayload(chat, modelName)
                    request = Request.Builder()
                        .url(proxyUrl)
                        .post(payload.toString().toRequestBody("application/json".toMediaType()))
                        .addHeader("Content-Type", "application/json")
                        .addHeader("X-API-Key", proxyKey)
                        .build()
                }

                Log.d("GeminiApi", "=== GEMINI API REQUEST (Attempt ${attempts + 1}) ===")
                Log.d("GeminiApi", "Model: $modelName")
                Log.d("GeminiApi", "Payload: ${payload.toString().take(500)}...")

                val requestStartTime = System.currentTimeMillis()
                client.newCall(request).execute().use { response ->
                    val responseEndTime = System.currentTimeMillis()
                    val requestTime = responseEndTime - requestStartTime
                    responseBody = response.body?.string()

                    Log.d("GeminiApi", "=== GEMINI API RESPONSE (Attempt ${attempts + 1}) ===")
                    Log.d("GeminiApi", "HTTP Status: ${response.code}")
                    Log.d("GeminiApi", "Request time: ${requestTime}ms")

                    if (!response.isSuccessful || responseBody.isNullOrEmpty()) {
                        throw Exception("API Error ${response.code}: $responseBody")
                    }

                    val parsedResponse = parseSuccessResponse(responseBody!!)
                    logToFirestore(createLogDataMap(attempts + 1, modelName, lastUserPrompt, images.size, response.code, requestTime, responseEndTime - attemptStartTime, "pass", parsedResponse, null))
                    return parsedResponse
                }
            } catch (e: Exception) {
                val attemptEndTime = System.currentTimeMillis()
                Log.e("GeminiApi", "=== GEMINI API ERROR (Attempt ${attempts + 1}) ===", e)
                logToFirestore(createLogDataMap(attempts + 1, modelName, lastUserPrompt, images.size, null, 0, attemptEndTime - attemptStartTime, "error", null, e.message))

                attempts++
                if (attempts < maxRetry) {
                    val delayTime = 1000L * attempts
                    Log.d("GeminiApi", "Retrying in ${delayTime}ms...")
                    delay(delayTime)
                } else {
                    Log.e("GeminiApi", "Request failed after all $maxRetry retries.")
                    return null
                }
            }
        }
        return null
    }

    private fun buildProxyPayload(chat: List<Pair<String, List<Any>>>, modelName: String): JSONObject {
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
                    is ImagePart -> Log.w("GeminiApi", "ImagePart found but skipped for proxy call.")
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

    private fun buildDirectPayload(chat: List<Pair<String, List<Any>>>, images: List<Bitmap>): JSONObject {
        val rootObject = JSONObject()
        val contentsArray = JSONArray()
        chat.forEach { (role, parts) ->
            val contentObject = JSONObject()
            contentObject.put("role", role.lowercase())
            val jsonParts = JSONArray()
            parts.forEach { part ->
                if (part is TextPart) {
                    jsonParts.put(JSONObject().put("text", part.text))
                }
            }
            contentObject.put("parts", jsonParts)
            contentsArray.put(contentObject)
        }

        if (images.isNotEmpty()) {
            var lastUserContent: JSONObject? = null
            for (i in (contentsArray.length() - 1) downTo 0) {
                val content = contentsArray.getJSONObject(i)
                if (content.getString("role") == "user") {
                    lastUserContent = content
                    break
                }
            }
            if (lastUserContent != null) {
                val partsArray = lastUserContent.getJSONArray("parts")
                images.forEach { image ->
                    val base64Image = bitmapToBase64(image)
                    val imagePart = JSONObject().put("inline_data",
                        JSONObject()
                            .put("mime_type", "image/jpeg")
                            .put("data", base64Image)
                    )
                    partsArray.put(imagePart)
                }
            }
        }
        rootObject.put("contents", contentsArray)
        return rootObject
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    private fun parseSuccessResponse(responseBody: String): String? {
        return try {
            val json = JSONObject(responseBody)
            if (json.has("text")) {
                return json.getString("text")
            }
            if (!json.has("candidates")) {
                Log.w("GeminiApi", "API response has no 'candidates'. It was likely blocked. Full response: $responseBody")
                if (json.has("error")) {
                    Log.e("GeminiApi", "Proxy returned an error: ${json.getString("error")}")
                }
                return null
            }
            val candidates = json.getJSONArray("candidates")
            if (candidates.length() == 0) return null
            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.getJSONObject("content")
            val parts = content.getJSONArray("parts")
            if (parts.length() == 0) return null
            parts.getJSONObject(0).getString("text")
        } catch (e: Exception) {
            Log.e("GeminiApi", "Failed to parse successful response: $responseBody", e)
            responseBody
        }
    }

    private fun logToFirestore(logData: Map<String, Any?>) {
        val timestamp = System.currentTimeMillis()
        val promptSnippet = (logData["prompt"] as? String)?.take(40) ?: "log"
        val sanitizedPrompt = promptSnippet.replace(Regex("[^a-zA-Z0-9]"), "_")
        val documentId = "${timestamp}_$sanitizedPrompt"

        db.collection("gemini_logs")
            .document(documentId)
            .set(logData)
            .addOnSuccessListener { Log.d("GeminiApi", "Log sent to Firestore with ID: $documentId") }
            .addOnFailureListener { e -> Log.e("GeminiApi", "Error sending log to Firestore", e) }
    }

    private fun createLogDataMap(
        attempt: Int, modelName: String, prompt: String, imagesCount: Int,
        responseCode: Int?, responseTime: Long, totalTime: Long, status: String,
        responseBody: String?, error: String?
    ): Map<String, Any?> {
        return mapOf(
            "timestamp" to FieldValue.serverTimestamp(),
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