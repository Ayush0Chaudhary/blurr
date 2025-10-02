package com.blurr.voice

import android.app.Application
import android.content.Context
import android.content.Intent
import android.util.Log
import com.android.billingclient.api.*
import com.blurr.voice.intents.IntentRegistry
import com.blurr.voice.intents.impl.DialIntent
import com.blurr.voice.intents.impl.EmailComposeIntent
import com.blurr.voice.intents.impl.ShareTextIntent
import com.blurr.voice.intents.impl.ViewUrlIntent
import com.blurr.voice.triggers.TriggerMonitoringService
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
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

        val serviceIntent = Intent(this, TriggerMonitoringService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun connectToBillingService() {
        if (billingClient.isReady) {
            Log.d("MyApplication", "BillingClient is already connected.")
            return
        }
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d("MyApplication", "BillingClient setup successfully.")
                    _isBillingClientReady.value = true
                    reconnectAttempts = 0
                    queryPurchases()
                } else {
                    Log.e("MyApplication", "BillingClient setup failed: ${billingResult.debugMessage}")
                    _isBillingClientReady.value = false
                    retryConnectionWithBackoff()
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w("MyApplication", "Billing service disconnected. Retrying...")
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
                Log.d("MyApplication", "Retrying connection, attempt #${reconnectAttempts + 1}")
                connectToBillingService()
            }
            reconnectAttempts++
        } else {
            Log.e("MyApplication", "Max reconnect attempts reached. Will not retry further.")
        }
    }

    private fun queryPurchases() {
        if (!_isBillingClientReady.value) {
            Log.e("MyApplication", "queryPurchases: BillingClient is not ready")
            return
        }
        applicationScope.launch {
            val params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
            val purchasesResult = billingClient.queryPurchasesAsync(params)
            val billingResult = purchasesResult.billingResult
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                purchasesResult.purchasesList.forEach { purchase ->
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        handlePurchase(purchase)
                    }
                }
            } else {
                Log.e("MyApplication", "Failed to query purchases: ${billingResult.debugMessage}")
            }
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                handlePurchase(purchase)
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Log.d("MyApplication", "User cancelled the purchase.")
        } else {
            Log.e("MyApplication", "Purchase error: ${billingResult.debugMessage}")
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        applicationScope.launch(Dispatchers.IO) {
            try {
                if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    if (!purchase.isAcknowledged) {
                        val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(purchase.purchaseToken)
                            .build()
                        billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
                            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                                Log.d("MyApplication", "Purchase acknowledged: ${purchase.orderId}")
                                updateUserToPro()
                            } else {
                                Log.e("MyApplication", "Failed to acknowledge purchase: ${billingResult.debugMessage}")
                            }
                        }
                    } else {
                        // Purchase already acknowledged, ensure backend is updated.
                        updateUserToPro()
                    }
                }
            } catch (e: Exception) {
                Log.e("MyApplication", "Error handling purchase", e)
            }
        }
    }

    private fun updateUserToPro() {
        val auth = Firebase.auth
        val db = Firebase.firestore
        auth.currentUser?.uid?.let { uid ->
            val userDocRef = db.collection("users").document(uid)
            userDocRef.update("plan", "pro")
                .addOnSuccessListener {
                    Log.d("MyApplication", "User plan updated to 'pro' in Firestore.")
                }
                .addOnFailureListener { e ->
                    Log.e("MyApplication", "Failed to update user plan in Firestore.", e)
                }
        } ?: Log.e("MyApplication", "Cannot update user to pro: current user is null.")
    }
}