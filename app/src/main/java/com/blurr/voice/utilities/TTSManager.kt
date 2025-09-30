/**
 * @file TTSManager.kt
 * @brief Manages Text-to-Speech (TTS) operations, including synthesis, playback, and caching.
 *
 * This file contains the `TTSManager` class, which is a singleton responsible for all TTS-related
 * functionalities. It handles both Google's high-quality TTS API and the native Android TTS engine
 * as a fallback. It features an intelligent caching system for short phrases, smart text chunking
 * for long passages, and optional on-screen caption display.
 */
package com.blurr.voice.utilities

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.blurr.voice.BuildConfig
import com.blurr.voice.api.GoogleTts
import com.blurr.voice.api.TTSVoice
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingDeque

/**
 * Manages all Text-to-Speech operations for the application.
 *
 * This singleton class is the central point for synthesizing text into speech. It primarily uses
 * the Google TTS API for high-quality voice synthesis and falls back to the native Android TTS
 * if the API fails. Key features include:
 * - **Smart Caching**: Caches short audio clips to reduce latency and API calls.
 * - **Smart Text Chunking**: Breaks long text into smaller sentences for smoother playback.
 * - **Direct Audio Playback**: Can play raw audio data directly.
 * - **Caption Display**: Optionally shows on-screen captions of the text being spoken.
 * - **Lifecycle Management**: Handles initialization and shutdown of TTS resources.
 *
 * @param context The application context.
 */
class TTSManager private constructor(private val context: Context) : TextToSpeech.OnInitListener {

    private var nativeTts: TextToSpeech? = null
    private var isNativeTtsInitialized = CompletableDeferred<Unit>()

    // Properties for Caption Management
    private val windowManager by lazy { context.getSystemService(Context.WINDOW_SERVICE) as WindowManager }
    private val mainHandler = Handler(Looper.getMainLooper())
    private var captionView: View? = null
    private var captionsEnabled = false

    private var audioTrack: AudioTrack? = null
    private var googleTtsPlaybackDeferred: CompletableDeferred<Unit>? = null

    /** A listener to be notified when the speaking state changes. */
    var utteranceListener: ((isSpeaking: Boolean) -> Unit)? = null

    private var isDebugMode: Boolean = try {
        BuildConfig.SPEAK_INSTRUCTIONS
    } catch (e: Exception) {
        true
    }

    // Caching System properties
    private val cacheDir by lazy { File(context.cacheDir, "tts_cache") }
    private val cache = ConcurrentHashMap<String, CachedAudio>()
    private val accessOrder = LinkedBlockingDeque<String>()
    private val cacheMutex = Any()
    private val MAX_CACHE_SIZE = 100 // Max number of items in cache
    private val MAX_WORDS_FOR_CACHING = 10 // Max number of words in a phrase to be eligible for caching

    /**
     * Companion object for managing the singleton instance of [TTSManager].
     */
    companion object {
        @Volatile private var INSTANCE: TTSManager? = null
        private const val SAMPLE_RATE = 24000 // Sample rate for Google TTS audio

        /**
         * Gets the singleton instance of the [TTSManager].
         *
         * @param context The application context.
         * @return The singleton [TTSManager] instance.
         */
        fun getInstance(context: Context): TTSManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TTSManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    init {
        nativeTts = TextToSpeech(context, this)
        setupAudioTrack()
        initializeCache()
    }

    /**
     * Data class representing a single cached audio entry.
     * Includes the original text, audio data, voice name, and a timestamp.
     */
    private data class CachedAudio(
        val text: String,
        val audioData: ByteArray,
        val voiceName: String,
        val timestamp: Long = System.currentTimeMillis()
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as CachedAudio
            // Two cached items are considered equal if their text and voice are the same.
            return text == other.text && voiceName == other.voiceName
        }

        override fun hashCode(): Int {
            var result = text.hashCode()
            result = 31 * result + voiceName.hashCode()
            return result
        }
    }

    /**
     * Initializes the cache directory on disk and loads any previously cached audio files.
     */
    private fun initializeCache() {
        try {
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            loadCacheFromDisk()
        } catch (e: Exception) {
            Log.e("TTSManager", "Failed to initialize cache", e)
        }
    }

    /**
     * Generates a unique SHA-256 hash key for a given text and voice combination.
     * This key is used for storing and retrieving audio from the cache.
     *
     * @param text The text to be synthesized.
     * @param voice The [TTSVoice] used for synthesis.
     * @return A unique string key for the cache.
     */
    private fun generateCacheKey(text: String, voice: TTSVoice): String {
        val combined = "${text.trim().lowercase()}_${voice.name}"
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(combined.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }

    /**
     * Determines if a given text string is eligible for caching based on its word count.
     *
     * @param text The input string.
     * @return `true` if the text should be cached, `false` otherwise.
     */
    private fun shouldCache(text: String): Boolean {
        val wordCount = text.trim().split(Regex("\\s+")).size
        return wordCount <= MAX_WORDS_FOR_CACHING
    }

    /**
     * Retrieves cached audio data for a given text and voice, if it exists.
     * Returns `null` on a cache miss.
     *
     * @param text The text to retrieve from the cache.
     * @param voice The [TTSVoice] associated with the cached audio.
     * @return The cached audio as a [ByteArray], or `null` if not found.
     */
    private fun getCachedAudio(text: String, voice: TTSVoice): ByteArray? {
        if (!shouldCache(text)) return null
        
        val cacheKey = generateCacheKey(text, voice)
        synchronized(cacheMutex) {
            val cachedAudio = cache[cacheKey]
            if (cachedAudio != null) {
                // Update access order (move to end)
                accessOrder.remove(cacheKey)
                accessOrder.addLast(cacheKey)
                Log.d("TTSManager", "Cache hit for: ${text.take(50)}...")
                return cachedAudio.audioData
            }
        }
        return null
    }

    /**
     * Stores synthesized audio data in the cache if it meets the eligibility criteria.
     * This involves saving to both an in-memory map and a file on disk.
     *
     * @param text The original text.
     * @param audioData The synthesized audio data.
     * @param voice The [TTSVoice] used for synthesis.
     */
    private fun cacheAudio(text: String, audioData: ByteArray, voice: TTSVoice) {
        if (!shouldCache(text)) return
        
        val cacheKey = generateCacheKey(text, voice)
        synchronized(cacheMutex) {
            // Remove if already exists
            cache.remove(cacheKey)
            accessOrder.remove(cacheKey)
            
            // Add new entry
            val cachedAudio = CachedAudio(text.trim(), audioData, voice.name)
            cache[cacheKey] = cachedAudio
            accessOrder.addLast(cacheKey)
            
            // Enforce cache size limit
            if (cache.size > MAX_CACHE_SIZE) {
                val oldestKey = accessOrder.removeFirst()
                cache.remove(oldestKey)
                deleteCacheFile(oldestKey)
            }
            
            // Save to disk
            saveCacheToDisk(cacheKey, cachedAudio)
            Log.d("TTSManager", "Cached audio for: ${text.take(50)}... (Cache size: ${cache.size})")
        }
    }

    /**
     * Saves a [CachedAudio] entry to a file on disk using its unique key.
     *
     * @param cacheKey The unique key for the cache entry.
     * @param cachedAudio The [CachedAudio] object to save.
     */
    private fun saveCacheToDisk(cacheKey: String, cachedAudio: CachedAudio) {
        try {
            val file = File(cacheDir, cacheKey)
            file.writeBytes(cachedAudio.audioData)
        } catch (e: Exception) {
            Log.e("TTSManager", "Failed to save cache to disk", e)
        }
    }

    /**
     * Loads existing audio files from the cache directory into memory upon initialization.
     * This is a simplified implementation; it does not restore full metadata.
     */
    private fun loadCacheFromDisk() {
        try {
            val files = cacheDir.listFiles() ?: return
            for (file in files) {
                if (file.isFile && file.length() > 0) {
                    val cacheKey = file.name
                    val audioData = file.readBytes()
                    // Note: We can't fully reconstruct CachedAudio without metadata
                    // This is a simplified version - in production you might want to store metadata
                    Log.d("TTSManager", "Loaded cached audio: $cacheKey")
                }
            }
        } catch (e: Exception) {
            Log.e("TTSManager", "Failed to load cache from disk", e)
        }
    }

    /**
     * Deletes a specific cache file from disk.
     *
     * @param cacheKey The key of the cache file to delete.
     */
    private fun deleteCacheFile(cacheKey: String) {
        try {
            val file = File(cacheDir, cacheKey)
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            Log.e("TTSManager", "Failed to delete cache file", e)
        }
    }

    /**
     * Clears all cached TTS data from both memory and disk storage.
     */
    fun clearCache() {
        synchronized(cacheMutex) {
            cache.clear()
            accessOrder.clear()
            try {
                cacheDir.listFiles()?.forEach { it.delete() }
            } catch (e: Exception) {
                Log.e("TTSManager", "Failed to clear cache directory", e)
            }
        }
    }

    /**
     * Initializes and configures the [AudioTrack] instance used for playing synthesized audio.
     * Sets up the audio attributes, format, buffer size, and a listener to detect playback completion.
     */
    private fun setupAudioTrack() {
        val bufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .build()

        audioTrack?.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
            override fun onMarkerReached(track: AudioTrack?) {
                // This callback is triggered when playback reaches the marker set by setNotificationMarkerPosition,
                // indicating the end of the audio data. We use it to signal completion.
                googleTtsPlaybackDeferred?.complete(Unit)
            }
            override fun onPeriodicNotification(track: AudioTrack?) {}
        }, Handler(Looper.getMainLooper()))
    }

    /**
     * Enables or disables the on-screen display of captions for spoken text.
     *
     * @param enabled `true` to show captions, `false` to hide them.
     */
    fun setCaptionsEnabled(enabled: Boolean) {
        this.captionsEnabled = enabled
        if (!enabled) {
            mainHandler.post { removeCaption() }
        }
    }

    /**
     * Gets the current status of the caption display.
     *
     * @return `true` if captions are enabled, `false` otherwise.
     */
    fun getCaptionStatus(): Boolean{
        return this.captionsEnabled
    }

    /**
     * Callback method invoked when the native [TextToSpeech] engine has been initialized.
     *
     * @param status The initialization status.
     */
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            nativeTts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) { utteranceListener?.invoke(true) }
                override fun onDone(utteranceId: String?) {
                    mainHandler.post { removeCaption() }
                    utteranceListener?.invoke(false)
                }
                override fun onError(utteranceId: String?) {
                    mainHandler.post { removeCaption() }
                    utteranceListener?.invoke(false)
                }
            })
            isNativeTtsInitialized.complete(Unit)
        } else {
            isNativeTtsInitialized.completeExceptionally(Exception("Native TTS Initialization failed"))
        }
    }

    /**
     * Immediately stops any ongoing TTS playback from both Google TTS and the native engine.
     * It also cancels any related background tasks.
     */
    fun stop() {
        if (audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING) {
            audioTrack?.stop()
            audioTrack?.flush()
        }
        if (googleTtsPlaybackDeferred?.isActive == true) {
            googleTtsPlaybackDeferred?.completeExceptionally(CancellationException("Playback stopped by new request."))
        }
    }

    /**
     * Speaks the given text, typically for instructional or debug purposes.
     * The speech is only produced if debug mode is enabled.
     *
     * @param text The text to speak.
     */
    suspend fun speakText(text: String) {
        if (!isDebugMode) return
        speak(text)
    }

    /**
     * Synthesizes and speaks the given text to the user using the preferred voice.
     * This is the primary method for user-facing speech.
     *
     * @param text The text to speak.
     */
    suspend fun speakToUser(text: String) {
        speak(text)
    }

    /**
     * Retrieves the audio session ID from the underlying [AudioTrack].
     * This is useful for integrating with audio visualizers.
     *
     * @return The audio session ID, or 0 if not available.
     */
    fun getAudioSessionId(): Int {
        return audioTrack?.audioSessionId ?: 0
    }

    /**
     * The core speech synthesis and playback logic.
     * It chunks the text, handles caching, and orchestrates playback. If Google TTS fails,
     * it falls back to the native Android TTS engine.
     *
     * @param text The text to be spoken.
     */
    private suspend fun speak(text: String) {
        try {
            val selectedVoice = VoicePreferenceManager.getSelectedVoice(context)
            
            val textChunks = chunkTextIntoSentences(text, maxWordsPerChunk = 50)
            
            if (textChunks.size == 1) {
                speakChunk(textChunks[0].trim(), selectedVoice)
            } else {
                playWithSmartQueue(textChunks, selectedVoice)
            }

        } catch (e: Exception) {
            if (e is CancellationException) throw e // Re-throw cancellation to stop execution
            Log.e("TTSManager", "Google TTS failed: ${e.message}. Falling back to native engine.")
            isNativeTtsInitialized.await()
            nativeTts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, this.hashCode().toString())
        }
    }
    
    /**
     * Handles playback of long text by creating a smart queue.
     * It starts playing the first chunk of synthesized audio immediately while concurrently
     * synthesizing and preloading the subsequent chunks in the background.
     *
     * @param textChunks A list of text strings to be played in sequence.
     * @param selectedVoice The [TTSVoice] to use for synthesis.
     */
    private suspend fun playWithSmartQueue(textChunks: List<String>, selectedVoice: TTSVoice) {
        val audioQueue = mutableListOf<Pair<String, ByteArray>>()
        val queueMutex = Any()
        
        // Start preloading the first chunk immediately
        val firstChunk = textChunks[0].trim()
        val firstAudioData = getCachedAudio(firstChunk, selectedVoice) ?: try {
            GoogleTts.synthesize(firstChunk, selectedVoice).also { audioData ->
                cacheAudio(firstChunk, audioData, selectedVoice)
            }
        } catch (e: Exception) {
            Log.e("TTSManager", "Failed to synthesize first chunk: ${e.message}")
            return
        }
        
        // Add first chunk to queue and start playing
        synchronized(queueMutex) {
            audioQueue.add(Pair(firstChunk, firstAudioData))
        }
        
        // Start background preloading for remaining chunks
        val preloadJob = CoroutineScope(Dispatchers.IO).launch {
            for (i in 1 until textChunks.size) {
                val chunk = textChunks[i].trim()
                if (chunk.isNotEmpty()) {
                    try {
                        val audioData = getCachedAudio(chunk, selectedVoice) ?: GoogleTts.synthesize(chunk, selectedVoice).also { audioData ->
                            cacheAudio(chunk, audioData, selectedVoice)
                        }
                        synchronized(queueMutex) {
                            audioQueue.add(Pair(chunk, audioData))
                        }
                        Log.d("TTSManager", "Preloaded chunk ${i + 1}/${textChunks.size}: ${chunk.take(50)}...")
                    } catch (e: Exception) {
                        Log.e("TTSManager", "Failed to preload chunk ${i + 1}: ${e.message}")
                    }
                }
            }
        }
        
        // Start playing from queue
        while (true) {
            val currentChunk: Pair<String, ByteArray>?
            
            synchronized(queueMutex) {
                if (audioQueue.isNotEmpty()) {
                    currentChunk = audioQueue.removeAt(0)
                } else {
                    currentChunk = null
                }
            }
            
            if (currentChunk == null) {
                // No more chunks in queue, check if preloading is complete
                if (preloadJob.isCompleted) {
                    break
                } else {
                    // Wait a bit for more chunks to be preloaded
                    delay(100)
                    continue
                }
            }
            
            // Play current chunk
            try {
                val (chunkText, audioData) = currentChunk
                
                // This deferred will complete when onMarkerReached is called.
                googleTtsPlaybackDeferred = CompletableDeferred()
                
                // Show caption for current chunk
                withContext(Dispatchers.Main) {
                    showCaption(chunkText)
                    utteranceListener?.invoke(true)
                }
                
                // Play audio on background thread
                withContext(Dispatchers.IO) {
                    audioTrack?.play()
                    val numFrames = audioData.size / 2
                    audioTrack?.setNotificationMarkerPosition(numFrames)
                    audioTrack?.write(audioData, 0, audioData.size)
                }
                
                // Wait for playback completion
                withTimeoutOrNull(10000) { // 10-second timeout per chunk
                    googleTtsPlaybackDeferred?.await()
                }
                
                audioTrack?.stop()
                audioTrack?.flush()
                
                withContext(Dispatchers.Main) {
                    removeCaption()
                    utteranceListener?.invoke(false)
                }
                
                Log.d("TTSManager", "Successfully played queued audio chunk: ${chunkText.take(50)}...")
                
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("TTSManager", "Failed to play queued chunk: ${currentChunk.first.take(50)}... Error: ${e.message}")
            }
        }
        
        // Cancel preloading job if it's still running
        if (preloadJob.isActive) {
            preloadJob.cancel()
        }
    }
    
    /**
     * Breaks a long string of text into smaller, more manageable chunks based on sentences.
     * Aims to keep each chunk below a maximum word count.
     *
     * @param text The full text to be chunked.
     * @param maxWordsPerChunk The desired maximum number of words per chunk.
     * @return A list of text chunks.
     */
    private fun chunkTextIntoSentences(text: String, maxWordsPerChunk: Int): List<String> {
        if (text.length <= 500) {
            // For short text, return as is
            return listOf(text)
        }
        
        val sentences = text.split(Regex("(?<=[.!?])\\s+")).filter { it.trim().isNotEmpty() }
        val chunks = mutableListOf<String>()
        var currentChunk = StringBuilder()
        var currentWordCount = 0
        
        for (sentence in sentences) {
            val sentenceWordCount = sentence.split(Regex("\\s+")).size
            
            // If adding this sentence would exceed the limit and we already have content
            if (currentWordCount + sentenceWordCount > maxWordsPerChunk && currentChunk.isNotEmpty()) {
                // Add current chunk to results
                chunks.add(currentChunk.toString().trim())
                currentChunk.clear()
                currentWordCount = 0
            }
            
            // Add sentence to current chunk
            if (currentChunk.isNotEmpty()) {
                currentChunk.append(" ")
            }
            currentChunk.append(sentence)
            currentWordCount += sentenceWordCount
        }
        
        // Add the last chunk if it has content
        if (currentChunk.isNotEmpty()) {
            chunks.add(currentChunk.toString().trim())
        }
        
        // If no chunks were created (e.g., very long single sentence), 
        // break by words instead
        if (chunks.isEmpty() || chunks.size == 1 && chunks[0].split(Regex("\\s+")).size > maxWordsPerChunk * 2) {
            return chunkTextByWords(text, maxWordsPerChunk)
        }
        
        return chunks
    }
    
    /**
     * A fallback chunking method that splits text strictly by word count.
     * This is used if sentence-based chunking is ineffective (e.g., a very long sentence).
     *
     * @param text The text to be chunked.
     * @param maxWordsPerChunk The maximum number of words per chunk.
     * @return A list of text chunks.
     */
    private fun chunkTextByWords(text: String, maxWordsPerChunk: Int): List<String> {
        val words = text.split(Regex("\\s+"))
        val chunks = mutableListOf<String>()
        
        for (i in words.indices step maxWordsPerChunk) {
            val chunk = words.drop(i).take(maxWordsPerChunk).joinToString(" ")
            if (chunk.isNotEmpty()) {
                chunks.add(chunk)
            }
        }
        
        return chunks
    }
    
    /**
     * Synthesizes and plays a single, self-contained chunk of text.
     * It checks the cache before synthesizing and handles the entire playback lifecycle for the chunk.
     *
     * @param chunk The text chunk to speak.
     * @param selectedVoice The [TTSVoice] to use for synthesis.
     */
    private suspend fun speakChunk(chunk: String, selectedVoice: TTSVoice) {
        try {
            // Check cache first
            val audioData = getCachedAudio(chunk, selectedVoice) ?: GoogleTts.synthesize(chunk, selectedVoice).also { audioData ->
                cacheAudio(chunk, audioData, selectedVoice)
            }
            
            // This deferred will complete when onMarkerReached is called.
            googleTtsPlaybackDeferred = CompletableDeferred()
            
            // Correctly signal start and wait for completion.
            withContext(Dispatchers.Main) {
                showCaption(chunk)
                utteranceListener?.invoke(true)
            }
            
            // Write and play audio on a background thread
            withContext(Dispatchers.IO) {
                audioTrack?.play()
                // The number of frames is the size of the data divided by the size of each frame (2 bytes for 16-bit audio).
                val numFrames = audioData.size / 2
                audioTrack?.setNotificationMarkerPosition(numFrames)
                audioTrack?.write(audioData, 0, audioData.size)
            }
            
            // Wait for the playback to complete, with a timeout for safety.
            withTimeoutOrNull(15000) { // 15-second timeout per chunk
                googleTtsPlaybackDeferred?.await()
            }
            
            audioTrack?.stop()
            audioTrack?.flush()
            
            withContext(Dispatchers.Main) {
                removeCaption()
                utteranceListener?.invoke(false)
            }
            
            Log.d("TTSManager", "Successfully played audio chunk: ${chunk.take(50)}...")
            
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("TTSManager", "Failed to speak chunk: ${chunk.take(50)}... Error: ${e.message}")
            // Continue with next chunk instead of falling back to native TTS for the entire text
        }
    }

    /**
     * Plays raw audio data directly through the [AudioTrack], bypassing TTS synthesis.
     * This is useful for playing pre-synthesized or cached audio.
     *
     * @param audioData The byte array of PCM 16-bit audio data to play.
     */
    suspend fun playAudioData(audioData: ByteArray) {
        try {
            googleTtsPlaybackDeferred = CompletableDeferred()
            withContext(Dispatchers.Main) {
                utteranceListener?.invoke(true)
            }

            withContext(Dispatchers.IO) {
                audioTrack?.play()
                val numFrames = audioData.size / 2
                audioTrack?.setNotificationMarkerPosition(numFrames)
                audioTrack?.write(audioData, 0, audioData.size)
            }

            withTimeoutOrNull(30000) { googleTtsPlaybackDeferred?.await() }

            withContext(Dispatchers.Main) { utteranceListener?.invoke(false) }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("TTSManager", "Error playing audio data", e)
        } finally {
            if (audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING) {
                audioTrack?.stop()
                audioTrack?.flush()
            }
            if (utteranceListener != null && Looper.myLooper() != Looper.getMainLooper()) {
                withContext(Dispatchers.Main) { utteranceListener?.invoke(false) }
            } else {
                utteranceListener?.invoke(false)
            }
        }
    }

    /**
     * Displays an on-screen caption as a system overlay.
     * The caption appears at the bottom of the screen. This method handles the creation,
     * styling, and addition of the view to the window manager.
     *
     * @param text The text to be displayed in the caption.
     */
    private fun showCaption(text: String) {
        if (!captionsEnabled) return

        removeCaption()

        val textView = TextView(context).apply {
            this.text = text
            background = GradientDrawable().apply {
                setColor(0xCC000000.toInt()) // 80% opaque black background
                cornerRadius = 24f
            }
            setTextColor(0xFFFFFFFF.toInt()) // White text
            textSize = 16f
            setPadding(24, 16, 24, 16)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 250 // Pixels up from the bottom
        }

        try {
            windowManager.addView(textView, params)
            captionView = textView
        } catch (e: Exception) {
            Log.e("TTSManager", "Failed to display caption on screen.", e)
        }
    }

    /**
     * Removes the currently displayed caption view from the screen.
     */
    private fun removeCaption() {
        captionView?.let {
            if (it.isAttachedToWindow) {
                try {
                    windowManager.removeView(it)
                } catch (e: Exception) {
                    Log.e("TTSManager", "Error removing caption view.", e)
                }
            }
        }
        captionView = null
    }

    /**
     * Shuts down the TTS manager, releasing all associated resources.
     * This includes the native TTS engine and the [AudioTrack].
     */
    fun shutdown() {
        stop()
        nativeTts?.shutdown()
        audioTrack?.release()
        audioTrack = null
        INSTANCE = null
    }
}