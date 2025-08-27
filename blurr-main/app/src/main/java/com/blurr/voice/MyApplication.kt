package com.blurr.voice // Use your app's package name

import android.app.Application
import android.content.Context

class MyApplication : Application() {

    companion object {
        lateinit var appContext: Context
            private set // Make the setter private to ensure it's not changed elsewhere
    }

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
    }
}