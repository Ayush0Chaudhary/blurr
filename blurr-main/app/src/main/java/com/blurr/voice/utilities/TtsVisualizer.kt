package com.blurr.voice.utilities

import android.media.audiofx.Visualizer
import android.util.Log

class TtsVisualizer(
    private val audioSessionId: Int,
    private val onAmplitudeChanged: (Float) -> Unit
) {
    private var visualizer: Visualizer? = null

    fun start() {
        if (visualizer != null) return // Already running
        Log.d("TtsVisualizer", "Starting to visualize audio session: $audioSessionId")
        try {
            visualizer = Visualizer(audioSessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[0] // Use a small capture size for waveform
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        visualizer: Visualizer?,
                        waveform: ByteArray?,
                        samplingRate: Int
                    ) {
                        if (waveform == null) return

                        // Calculate amplitude from the waveform data
                        var maxAmplitude = 0.0
                        for (i in waveform.indices step 2) {
                            // Combine two bytes to form a 16-bit sample
                            val sample = (waveform[i+1].toInt() shl 8) or (waveform[i].toInt() and 0xFF)
                            if(sample > maxAmplitude) {
                                maxAmplitude = sample.toDouble()
                            }
                        }
                        // Normalize the amplitude to a 0.0f to 1.0f range
                        val normalized = (maxAmplitude / 32767.0).toFloat()
                        onAmplitudeChanged(normalized)
                    }

                    override fun onFftDataCapture(p0: Visualizer?, p1: ByteArray?, p2: Int) {
                        // We don't need FFT data for this effect
                    }
                }, Visualizer.getMaxCaptureRate() / 2, true, false)
                enabled = true
            }
        } catch (e: Exception) {
            Log.e("TtsVisualizer", "Error initializing Visualizer", e)
        }
    }

    fun stop() {
        Log.d("TtsVisualizer", "Stopping visualization.")
        visualizer?.enabled = false
        visualizer?.release()
        visualizer = null
        // Reset amplitude to 0 when stopped
        onAmplitudeChanged(0f)
    }
}