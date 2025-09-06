package com.blurr.voice // Use your app's package name

import android.app.Application
import android.content.Context
import com.blurr.voice.intents.IntentRegistry
import com.blurr.voice.intents.impl.DialIntent

class MyApplication : Application() {

    companion object {
        lateinit var appContext: Context
            private set // Make the setter private to ensure it's not changed elsewhere
    }

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext

        // Register built-in app intents (plug-and-play extensions can add their own here)
        IntentRegistry.register(DialIntent())
        // Optional: initialize registry scanning for additional implementations
        IntentRegistry.init(this)
    }
}