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
 * Available voice options for Google TTS, using all provided Chirp3-HD voices.
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
 * Handles communication with the Google Cloud Text-to-Speech API.
 */
object GoogleTts {
    const val apiKey = BuildConfig.GOOGLE_TTS_API_KEY
    private val client = OkHttpClient()
    private const val API_URL = "https://texttospeech.googleapis.com/v1beta1/text:synthesize?key=$apiKey"

    /**
     * Synthesizes speech from text using the Google Cloud TTS API with default voice.
     * @param text The text to synthesize.
     * @return A ByteArray containing the raw audio data (LINEAR16 PCM).
     * @throws Exception if the API call fails or the response is invalid.
     */
    suspend fun synthesize(text: String): ByteArray = synthesize(text, TTSVoice.CHIRP_PUCK)
    /**
     * Synthesizes speech from text using the Google Cloud TTS API.
     * @param text The text to synthesize.
     * @param voice The voice to use for synthesis.
     * @return A ByteArray containing the raw audio data (LINEAR16 PCM).
     * @throws Exception if the API call fails or the response is invalid.
     */
    suspend fun synthesize(text: String, voice: TTSVoice): ByteArray = withContext(Dispatchers.IO) {
        if (apiKey.isEmpty()) {
            throw Exception("Google TTS API key is not configured.")
        }
        println(voice.displayName)

        // Network check
        val isOnline = try {
            true
        } catch (e: Exception) {
            Log.e("GoogleTts", "Network check failed, assuming offline. ${'$'}{e.message}")
            false
        }
        if (!isOnline) {
            NetworkNotifier.notifyOffline()
            throw Exception("No internet connection for TTS request.")
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
     * Get all available voice options
     * @return List of all available TTS voices
     */
    fun getAvailableVoices(): List<TTSVoice> = TTSVoice.values().toList()
}