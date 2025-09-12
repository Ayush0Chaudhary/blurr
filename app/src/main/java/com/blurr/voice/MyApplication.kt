package com.blurr.voice // Use your app's package name

import android.app.Application
import android.content.Context
import com.blurr.voice.intents.IntentRegistry
import com.blurr.voice.intents.impl.DialIntent
import com.blurr.voice.intents.impl.EmailComposeIntent
import com.blurr.voice.intents.impl.ShareTextIntent
import com.blurr.voice.intents.impl.ViewUrlIntent
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration

class MyApplication : Application() {

    companion object {
        lateinit var appContext: Context
            private set // Make the setter private to ensure it's not changed elsewhere
    }

    override fun onCreate() {
        super.onCreate()
        Purchases.configure(
            PurchasesConfiguration.Builder(this, BuildConfig.REVENUE_CAT_PUBLIC_URL).build()
        )
        appContext = applicationContext

        // Register built-in app intents (plug-and-play extensions can add their own here)
        IntentRegistry.register(DialIntent())
        IntentRegistry.register(ViewUrlIntent())
        IntentRegistry.register(ShareTextIntent())
        IntentRegistry.register(EmailComposeIntent())
        // Optional: initialize registry scanning for additional implementations
        IntentRegistry.init(this)
    }
}