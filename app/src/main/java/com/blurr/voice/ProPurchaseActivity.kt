package com.blurr.voice

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.android.billingclient.api.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class ProPurchaseActivity : BaseNavigationActivity(), PurchasesUpdatedListener {

    private lateinit var priceTextView: TextView
    private lateinit var purchaseButton: Button
    private lateinit var loadingProgressBar: ProgressBar
    private lateinit var featuresTextView: TextView
    private lateinit var backButton: View
    
    private val billingClient: BillingClient = MyApplication.billingClient
    private var productDetails: ProductDetails? = null
    
    companion object {
        private const val PRO_SKU = "panda_premium_monthly"
        private const val TAG = "ProPurchaseActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pro_purchase)
        
        initializeViews()
        setupClickListeners()
        loadProductDetails()
    }
    
    private fun initializeViews() {
        priceTextView = findViewById(R.id.price_text)
        purchaseButton = findViewById(R.id.purchase_button)
        loadingProgressBar = findViewById(R.id.loading_progress)
//        featuresTextView = findViewById(R.id.features_text)
        backButton = findViewById(R.id.back_button)
        
        // Initially hide purchase button and show loading
        purchaseButton.visibility = View.GONE
        loadingProgressBar.visibility = View.VISIBLE
    }
    
    private fun setupClickListeners() {
        backButton.setOnClickListener {
            finish()
        }
        
        purchaseButton.setOnClickListener {
            launchPurchaseFlow()
        }
    }
    
    private fun loadProductDetails() {
        lifecycleScope.launch {
            try {
                // Wait for billing client to be ready
                val isReady = withTimeoutOrNull(10000L) {
                    MyApplication.isBillingClientReady.first { it }
                }
                
                if (isReady != true) {
                    showError("Unable to connect to Play Store. Please try again later.")
                    return@launch
                }
                
                val productList = listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRO_SKU)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                )
                
                val params = QueryProductDetailsParams.newBuilder()
                    .setProductList(productList)
                    .build()
                
                val productDetailsResult = billingClient.queryProductDetails(params)
                
                if (productDetailsResult.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    val productDetailsList = productDetailsResult.productDetailsList
                    Log.d(TAG, "Product details: $productDetailsList")
                    if (productDetailsList?.isNotEmpty() == true) {
                        productDetails = productDetailsList[0]
                        updateUIWithProductDetails()
                    } else {
                        showError("Pro subscription not available")
                    }
                } else {
                    showError("Failed to load pricing information")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error loading product details", e)
                showError("Error loading pricing information")
            }
        }
    }
    
    private fun updateUIWithProductDetails() {
        productDetails?.let { details ->
            val subscriptionOfferDetails = details.subscriptionOfferDetails?.firstOrNull()
            val pricingPhase = subscriptionOfferDetails?.pricingPhases?.pricingPhaseList?.firstOrNull()
            
            if (pricingPhase != null) {
                val formattedPrice = pricingPhase.formattedPrice
                val billingPeriod = pricingPhase.billingPeriod
                
                // Convert billing period to readable format
                val periodText = when {
                    billingPeriod.contains("P1M") -> "month"
                    billingPeriod.contains("P1Y") -> "year"
                    billingPeriod.contains("P1W") -> "week"
                    else -> "billing period"
                }
                
                priceTextView.text = "$formattedPrice/$periodText"
                
                // Show purchase button and hide loading
                loadingProgressBar.visibility = View.GONE
                purchaseButton.visibility = View.VISIBLE
                
            } else {
                showError("Pricing information not available")
            }
        }
    }
    
    private fun launchPurchaseFlow() {
        productDetails?.let { details ->
            val subscriptionOfferDetails = details.subscriptionOfferDetails?.firstOrNull()
            if (subscriptionOfferDetails != null) {
                val productDetailsParamsList = listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(subscriptionOfferDetails.offerToken)
                        .build()
                )
                
                val billingFlowParams = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(productDetailsParamsList)
                    .build()
                
                val billingResult = billingClient.launchBillingFlow(this, billingFlowParams)
                
                if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                    Toast.makeText(this, "Failed to launch purchase flow", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun showError(message: String) {
        loadingProgressBar.visibility = View.GONE
        priceTextView.text = "Pricing unavailable"
        purchaseButton.visibility = View.GONE
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    
    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    Toast.makeText(this, "Purchase successful! Welcome to Pro!", Toast.LENGTH_LONG).show()
                    finish() // Close the purchase activity
                }
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Toast.makeText(this, "Purchase cancelled", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Purchase failed: ${billingResult.debugMessage}", Toast.LENGTH_LONG).show()
        }
    }
    override fun getContentLayoutId(): Int {
        return R.layout.activity_pro_purchase
    }

    override fun getCurrentNavItem(): NavItem {
        return BaseNavigationActivity.NavItem.UPGRADE
    }
}