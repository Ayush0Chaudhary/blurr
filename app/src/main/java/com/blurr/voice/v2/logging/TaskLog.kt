package com.blurr.voice.v2.logging

import kotlinx.serialization.Serializable

@Serializable
data class TaskLog(
    val uid: String,
    val timestamp: Long,
    val input: String, // The prompt or messages sent to Gemini
    val output: String // The response from Gemini
)
