package com.blurr.voice // Use your app's package name

import android.app.Application
import android.content.Context
import com.blurr.voice.intents.IntentRegistry
import android.content.Intent
import com.blurr.voice.intents.impl.DialIntent
import com.blurr.voice.intents.impl.EmailComposeIntent
import com.blurr.voice.intents.impl.ShareTextIntent
import com.blurr.voice.intents.impl.ViewUrlIntent
import com.blurr.voice.triggers.TriggerMonitoringService
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration

class MyApplication : Application() {

    companion object {
        lateinit var appContext: Context
            private set // Make the setter private to ensure it's not changed elsewhere
    }

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext

        // Initialize singletons that need context
        GoogleTts.init(this)

        Purchases.logLevel = LogLevel.DEBUG
        Purchases.configure(
            PurchasesConfiguration.Builder(this, BuildConfig.REVENUECAT_API_KEY).build()
        )

        // Register built-in app intents (plug-and-play extensions can add their own here)
        IntentRegistry.register(DialIntent())
        IntentRegistry.register(ViewUrlIntent())
        IntentRegistry.register(ShareTextIntent())
        IntentRegistry.register(EmailComposeIntent())
        // Optional: initialize registry scanning for additional implementations
        IntentRegistry.init(this)

        // Start the trigger monitoring service
        val serviceIntent = Intent(this, TriggerMonitoringService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }    }
}
