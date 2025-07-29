package com.example.blurr.utilities

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.example.blurr.api.GeminiApi
import com.google.ai.client.generativeai.type.ImagePart
import com.google.ai.client.generativeai.type.TextPart
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit


fun encodeImageBase64(file: File): String {
    val imageBytes = file.readBytes()
    return Base64.encodeToString(imageBytes, Base64.NO_WRAP)
}

fun addResponse(
    role: String,
    prompt: String,
    chatHistory: List<Pair<String, List<Any>>>,
    imageBitmap: Bitmap? = null // MODIFIED: Accepts a Bitmap directly
): List<Pair<String, List<Any>>> {
    val updatedChat = chatHistory.toMutableList()

    val messageParts = mutableListOf<Any>()
    messageParts.add(TextPart(prompt))

    if (imageBitmap != null) {
        messageParts.add(ImagePart(imageBitmap))
    }

    updatedChat.add(Pair(role, messageParts))
    return updatedChat
}

fun addResponsePrePost(
    role: String,
    prompt: String,
    chatHistory: List<Pair<String, List<Any>>>,
    imageBefore: Bitmap? = null, // MODIFIED: Accepts a Bitmap
    imageAfter: Bitmap? = null  // MODIFIED: Accepts a Bitmap
): List<Pair<String, List<Any>>> {
    val updatedChat = chatHistory.toMutableList()
    val messageParts = mutableListOf<Any>()

    messageParts.add(TextPart(prompt))

    // Attach "before" image directly if available
    imageBefore?.let {
        messageParts.add(ImagePart(it))
    }

    // Attach "after" image directly if available
    imageAfter?.let {
        messageParts.add(ImagePart(it))
    }

    updatedChat.add(Pair(role, messageParts))
    return updatedChat
}

suspend fun getReasoningModelApiResponse(
    chat: List<Pair<String, List<Any>>>,
    apiKey: String,
    agentState: com.example.blurr.agent.InfoPool? = null // NEW: Optional agent state parameter
): String? { // Return nullable String
    return GeminiApi.generateContent(chat) // MODIFIED: Pass agent state
}

/**
 * Enhanced version of getReasoningModelApiResponse that includes agent state context.
 * This function provides the LLM with comprehensive context about the agent's current state,
 * including task progress, action history, and error information.
 * 
 * @param chat The conversation history
 * @param apiKey The API key for the LLM service
 * @param agentState The current state of the agent (InfoPool)
 * @return The LLM response or null if failed
 */
suspend fun getReasoningModelApiResponseWithState(
    chat: List<Pair<String, List<Any>>>,
    apiKey: String,
    agentState: com.example.blurr.agent.InfoPool
): String? {
    return GeminiApi.generateContent(chat)
}

/**
 * Example function showing how to use agent state for better context-aware responses.
 * This demonstrates how the agent state can improve LLM responses by providing:
 * - Current task and progress information
 * - Recent action history and outcomes
 * - Error context and important notes
 * - Memory and tips for better decision making
 */
suspend fun exampleUsageWithAgentState(
    userInstruction: String,
    agentState: com.example.blurr.agent.InfoPool
): String? {
    // Create a simple chat with the user instruction
    val chat = listOf(
        "user" to listOf(com.google.ai.client.generativeai.type.TextPart(userInstruction))
    )
    
    // Call the API with agent state for better context
    return getReasoningModelApiResponseWithState(chat, "", agentState)
}


suspend fun inferenceChat(
    chat: List<Pair<String, List<Any>>>,
    apiKey: String,
    modelName: String = "gemini-2.0-flash",
    maxRetry: Int = 5
): String {
    val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS) // crucial for image input
        .build()

    val contentsArray = JSONArray()

    for ((role, parts) in chat) {
        if (role == "system") {
            parts.forEach { println(it) }
            break
        }

        val jsonParts = JSONArray()
        for (part in parts) {
            when (part) {
                is TextPart -> {
                    jsonParts.put(JSONObject().put("text", part.text))
                }
                is ImagePart -> {
                    val stream = ByteArrayOutputStream()
                    part.image.compress(Bitmap.CompressFormat.JPEG, 100, stream)
                    val base64Image = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)

                    val inlineImageJson = JSONObject().put("inline_data", JSONObject()
                        .put("mime_type", "image/jpeg")
                        .put("data", base64Image)
                    )
                    jsonParts.put(inlineImageJson)
                }
                else -> throw IllegalArgumentException("Unsupported part type: ${part::class.java}")
            }
        }
        contentsArray.put(JSONObject().put("role", role).put("parts", jsonParts))
    }

    val payload = JSONObject().put("contents", contentsArray)
    val mediaType = "application/json".toMediaType()
    var attempts = 0

    while (attempts < maxRetry) {
        try {
            val requestBody = payload.toString().toRequestBody(mediaType)
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    println("\n⚠️ HTTP ${response.code} error: ${response.message}")
                    println("Response Body:\n$responseBody")
                    throw Exception("Gemini API error ${response.code}: ${response.message}")
                }

                val json = JSONObject(responseBody)
                return json.getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
            }
        } catch (e: SocketTimeoutException) {
            println("⏱️ Timeout on attempt ${attempts + 1}, retrying...")
        } catch (e: Exception) {
            println("❌ Error in inferenceChat: ${e.message}")
            e.printStackTrace()
        }

        attempts++
        delay(2000L)
    }

    throw Exception("Failed after $maxRetry retries")
}
