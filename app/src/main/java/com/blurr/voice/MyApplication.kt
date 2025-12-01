package com.blurr.voice

import android.app.Application
import android.content.Context
import android.content.Intent
import com.blurr.voice.utilities.Logger
import com.android.billingclient.api.*
import com.blurr.voice.intents.IntentRegistry
import com.blurr.voice.intents.impl.DialIntent
import com.blurr.voice.intents.impl.EmailComposeIntent
import com.blurr.voice.intents.impl.ShareTextIntent
import com.blurr.voice.intents.impl.ViewUrlIntent
import com.cactus.CactusContextInitializer
import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.pow

class MyApplication : Application(), PurchasesUpdatedListener {

    private val applicationScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 5
    private val initialReconnectDelayMs = 1000L

    companion object {
        lateinit var appContext: Context
            private set

        lateinit var billingClient: BillingClient
            private set

        private val _isBillingClientReady = MutableStateFlow(false)
        val isBillingClientReady: StateFlow<Boolean> = _isBillingClientReady.asStateFlow()
    }

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        try {
            CactusContextInitializer.initialize(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        // Initialize Firebase Remote Config
        val remoteConfig = Firebase.remoteConfig
        val configSettings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = if (BuildConfig.DEBUG) 1L else 3L
        }
        remoteConfig.setConfigSettingsAsync(configSettings)
        remoteConfig.setDefaultsAsync(R.xml.remote_config_defaults)


        billingClient = BillingClient.newBuilder(this)
            .setListener(this)
            .enablePendingPurchases()
            .build()

        connectToBillingService()

        IntentRegistry.register(DialIntent())
        IntentRegistry.register(ViewUrlIntent())
        IntentRegistry.register(ShareTextIntent())
        IntentRegistry.register(EmailComposeIntent())
        IntentRegistry.init(this)
    }

    private fun connectToBillingService() {
        if (billingClient.isReady) {
            Logger.d("MyApplication", "BillingClient is already connected.")
            return
        }
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Logger.d("MyApplication", "BillingClient setup successfully.")
                    _isBillingClientReady.value = true
                    reconnectAttempts = 0
                } else {
                    Logger.e("MyApplication", "BillingClient setup failed: ${billingResult.debugMessage}")
                    _isBillingClientReady.value = false
                    retryConnectionWithBackoff()
                }
            }

            override fun onBillingServiceDisconnected() {
                Logger.w("MyApplication", "Billing service disconnected. Retrying...")
                _isBillingClientReady.value = false
                retryConnectionWithBackoff()
            }
        })
    }

    private fun retryConnectionWithBackoff() {
        if (reconnectAttempts < maxReconnectAttempts) {
            val delay = initialReconnectDelayMs * (2.0.pow(reconnectAttempts)).toLong()
            applicationScope.launch {
                delay(delay)
                reconnectAttempts++
                Logger.d("MyApplication", "Retrying connection, attempt #$reconnectAttempts")
                connectToBillingService()
            }
        } else {
            Logger.e("MyApplication", "Max reconnect attempts reached. Will not retry further.")
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        Logger.d("MyApplication", "Purchase update received")
        val intent = Intent("com.blurr.voice.PURCHASE_UPDATED")
        intent.putExtra("response_code", billingResult.responseCode)
        intent.putExtra("debug_message", billingResult.debugMessage)
        appContext.sendBroadcast(intent)
    }
}