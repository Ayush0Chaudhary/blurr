package com.blurr.voice.data

import com.google.firebase.Timestamp
import java.util.Date

data class UserMemory(
    val id: String = "",
    val text: String = "",
    val source: String = "User",
    val createdAt: Date = Date()
) {
    // No-argument constructor for Firestore deserialization
    constructor() : this("", "", "User", Date())
}
