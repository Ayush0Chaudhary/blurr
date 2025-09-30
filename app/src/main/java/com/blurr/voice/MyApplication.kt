/**
 * @file MyApplication.kt
 * @brief The custom Application class for global app initialization.
 *
 * This file defines `MyApplication`, which is the entry point of the Android application.
 * It is used to perform one-time setup tasks when the app process is created.
 */
package com.blurr.voice

import android.app.Application
import android.content.Context
import android.content.Intent
import com.blurr.voice.intents.IntentRegistry
import com.blurr.voice.intents.impl.DialIntent
import com.blurr.voice.intents.impl.EmailComposeIntent
import com.blurr.voice.intents.impl.ShareTextIntent
import com.blurr.voice.intents.impl.ViewUrlIntent
import com.blurr.voice.triggers.TriggerMonitoringService
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration

/**
 * The custom `Application` class for the app.
 *
 * This class is instantiated when the application process is started. It handles several
 * key initialization tasks that need to be performed only once per app lifecycle, such as:
 * - Providing a global application context.
 * - Configuring the RevenueCat Purchases SDK.
 * - Registering the built-in `AppIntent`s for the agent.
 * - Starting the background `TriggerMonitoringService`.
 */
class MyApplication : Application() {

    companion object {
        /** A globally accessible application context. */
        lateinit var appContext: Context
            private set
    }

    /**
     * Called when the application is starting, before any other application objects have been created.
     * This is where all one-time initializations are performed.
     */
    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext

        // Configure RevenueCat for in-app purchases.
        Purchases.logLevel = LogLevel.DEBUG
        Purchases.configure(
            PurchasesConfiguration.Builder(this, BuildConfig.REVENUECAT_API_KEY).build()
        )

        // Register all built-in AppIntents so the agent knows about them.
        IntentRegistry.register(DialIntent())
        IntentRegistry.register(ViewUrlIntent())
        IntentRegistry.register(ShareTextIntent())
        IntentRegistry.register(EmailComposeIntent())
        IntentRegistry.init(this)

        // Start the service that monitors for custom triggers in the background.
        val serviceIntent = Intent(this, TriggerMonitoringService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }
}
