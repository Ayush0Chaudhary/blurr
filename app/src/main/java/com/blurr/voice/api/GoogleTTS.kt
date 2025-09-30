/**
 * @file GoogleTTS.kt
 * @brief Provides an interface for Google's Text-to-Speech (TTS) API.
 *
 * This file contains the necessary components to interact with the Google Cloud TTS service.
 * It defines the available voices in the [TTSVoice] enum and provides the [GoogleTts] object
 * for making API requests to synthesize speech from text.
 */
package com.blurr.voice.api

import android.util.Base64
import android.util.Log
import com.blurr.voice.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import com.blurr.voice.MyApplication
import com.blurr.voice.utilities.NetworkConnectivityManager
import com.blurr.voice.utilities.NetworkNotifier

/**
 * Enumerates the available high-definition voice options for Google's Text-to-Speech service.
 *
 * Each enum entry represents a specific "Chirp3-HD" voice, providing its display name,
 * the official voice name used in API requests, and a brief description.
 *
 * @property displayName A user-friendly name for the voice.
 * @property voiceName The identifier for the voice required by the Google Cloud TTS API.
 * @property description A short description of the voice's characteristics.
 */
enum class TTSVoice(val displayName: String, val voiceName: String, val description: String) {
    CHIRP_ACHERNAR("Achernar", "en-US-Chirp3-HD-Achernar", "High-definition female voice."),
    CHIRP_ACHIRD("Achird", "en-US-Chirp3-HD-Achird", "High-definition male voice."),
    CHIRP_ALGENIB("Algenib", "en-US-Chirp3-HD-Algenib", "High-definition male voice."),
    CHIRP_ALGIEBA("Algieba", "en-US-Chirp3-HD-Algieba", "High-definition male voice."),
    CHIRP_ALNILAM("Alnilam", "en-US-Chirp3-HD-Alnilam", "High-definition male voice."),
    CHIRP_AOEDE("Aoede", "en-US-Chirp3-HD-Aoede", "High-definition female voice."),
    CHIRP_AUTONOE("Autonoe", "en-US-Chirp3-HD-Autonoe", "High-definition female voice."),
    CHIRP_CALLIRRHOE("Callirrhoe", "en-US-Chirp3-HD-Callirrhoe", "High-definition female voice."),
    CHIRP_CHARON("Charon", "en-US-Chirp3-HD-Charon", "High-definition male voice."),
    CHIRP_DESPINA("Despina", "en-US-Chirp3-HD-Despina", "High-definition female voice."),
    CHIRP_ENCELADUS("Enceladus", "en-US-Chirp3-HD-Enceladus", "High-definition male voice."),
    CHIRP_ERINOME("Erinome", "en-US-Chirp3-HD-Erinome", "High-definition female voice."),
    CHIRP_FENRIR("Fenrir", "en-US-Chirp3-HD-Fenrir", "High-definition male voice."),
    CHIRP_GACRUX("Gacrux", "en-US-Chirp3-HD-Gacrux", "High-definition female voice."),
    CHIRP_IAPETUS("Iapetus", "en-US-Chirp3-HD-Iapetus", "High-definition male voice."),
    CHIRP_KORE("Kore", "en-US-Chirp3-HD-Kore", "High-definition female voice."),
    CHIRP_LAOMEDEIA("Laomedeia", "en-US-Chirp3-HD-Laomedeia", "High-definition female voice."),
    CHIRP_LEDA("Leda", "en-US-Chirp3-HD-Leda", "High-definition female voice."),
    CHIRP_ORUS("Orus", "en-US-Chirp3-HD-Orus", "High-definition male voice."),
    CHIRP_PUCK("Puck", "en-US-Chirp3-HD-Puck", "High-definition male voice."),
    CHIRP_PULCHERRIMA("Pulcherrima", "en-US-Chirp3-HD-Pulcherrima", "High-definition female voice."),
    CHIRP_RASALGETHI("Rasalgethi", "en-US-Chirp3-HD-Rasalgethi", "High-definition male voice."),
    CHIRP_SADACHBIA("Sadachbia", "en-US-Chirp3-HD-Sadachbia", "High-definition male voice."),
    CHIRP_SADALTAGER("Sadaltager", "en-US-Chirp3-HD-Sadaltager", "High-definition male voice."),
    CHIRP_SCHEDAR("Schedar", "en-US-Chirp3-HD-Schedar", "High-definition male voice."),
    CHIRP_SULAFAT("Sulafat", "en-US-Chirp3-HD-Sulafat", "High-definition female voice."),
    CHIRP_UMBRIEL("Umbriel", "en-US-Chirp3-HD-Umbriel", "High-definition male voice."),
    CHIRP_VINDEMIATRIX("Vindemiatrix", "en-US-Chirp3-HD-Vindemiatrix", "High-definition female voice."),
    CHIRP_ZEPHYR("Zephyr", "en-US-Chirp3-HD-Zephyr", "High-definition female voice."),
    CHIRP_ZUBENELGENUBI("Zubenelgenubi", "en-US-Chirp3-HD-Zubenelgenubi", "High-definition male voice.")
}

/**
 * A singleton object that handles communication with the Google Cloud Text-to-Speech API.
 *
 * This client is responsible for constructing and sending synthesis requests and processing
 * the audio response.
 */
object GoogleTts {
    /** The API key for the Google Cloud Text-to-Speech service. */
    const val apiKey = BuildConfig.GOOGLE_TTS_API_KEY
    /** The OkHttpClient instance used for all TTS API requests. */
    private val client = OkHttpClient()
    /** The endpoint URL for the text synthesis API. */
    private const val API_URL = "https://texttospeech.googleapis.com/v1beta1/text:synthesize?key=$apiKey"

    /**
     * Synthesizes speech from text using a default voice.
     *
     * This is an overload of [synthesize] that uses [TTSVoice.CHIRP_LAOMEDEIA] as the default voice.
     *
     * @param text The text to be synthesized into speech.
     * @return A [ByteArray] containing the raw audio data in LINEAR16 PCM format.
     * @throws Exception if the API key is not configured, there is no network connection,
     *                   or the API call fails.
     */
    suspend fun synthesize(text: String): ByteArray = synthesize(text, TTSVoice.CHIRP_LAOMEDEIA)

    /**
     * Synthesizes speech from text using a specified voice.
     *
     * This function sends a request to the Google Cloud TTS API and returns the resulting
     * audio data. It performs a network check before making the API call.
     *
     * @param text The text to be synthesized.
     * @param voice The [TTSVoice] to use for the synthesis.
     * @return A [ByteArray] containing the raw audio data in LINEAR16 PCM format.
     * @throws Exception if the API key is not configured, there is no network connection,
     *                   or the API call fails.
     */
    suspend fun synthesize(text: String, voice: TTSVoice): ByteArray = withContext(Dispatchers.IO) {
        if (apiKey.isEmpty()) {
            throw Exception("Google TTS API key is not configured.")
        }
        println(voice.displayName)

        // Network check
        try {
            val isOnline = NetworkConnectivityManager(MyApplication.appContext).isNetworkAvailable()
            if (!isOnline) {
                NetworkNotifier.notifyOffline()
                throw Exception("No internet connection for TTS request.")
            }
        } catch (e: Exception) {
            Log.e("GoogleTts", "Network check failed, assuming offline. ${e.message}")
            throw Exception("Network check failed: ${e.message}")
        }

        // 1. Construct the JSON payload
        val jsonPayload = JSONObject().apply {
            put("input", JSONObject().put("text", text))
            put("voice", JSONObject().apply {
                put("languageCode", "en-US")
                put("name", voice.voiceName)
            })
            put("audioConfig", JSONObject().apply {
                put("audioEncoding", "LINEAR16")
                put("sampleRateHertz", 24000)
            })
        }.toString()

        // 2. Build the network request
        val request = Request.Builder()
            .url(API_URL)
            .header("X-Goog-Api-Key", apiKey)
            .header("Content-Type", "application/json; charset=utf-8")
            .header("X-Android-Package", BuildConfig.APPLICATION_ID)
//            .header("X-Android-Cert", BuildConfig.SHA1_FINGERPRINT)
            .post(jsonPayload.toRequestBody("application/json".toMediaType()))
            .build()

        // 3. Execute the request and handle the response
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                Log.e("GoogleTts", "API Error: ${response.code} - $errorBody")
                throw Exception("Google TTS API request failed with code ${response.code}")
            }

            val responseBody = response.body?.string()
            if (responseBody.isNullOrEmpty()) {
                throw Exception("Received an empty response from Google TTS API.")
            }

            // 4. Decode the Base64 audio content into a ByteArray
            val jsonResponse = JSONObject(responseBody)
            val audioContent = jsonResponse.getString("audioContent")
            return@withContext Base64.decode(audioContent, Base64.DEFAULT)
        }
    }

    /**
     * Retrieves a list of all available TTS voices.
     *
     * @return A list of all entries from the [TTSVoice] enum.
     */
    fun getAvailableVoices(): List<TTSVoice> = TTSVoice.values().toList()
}