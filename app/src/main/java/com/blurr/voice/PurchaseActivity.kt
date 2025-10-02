package com.blurr.voice

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.queryProductDetails
import kotlinx.coroutines.launch

class PurchaseActivity : AppCompatActivity() {

    private lateinit var billingClient: BillingClient
    private var proProductDetails: ProductDetails? = null
    private lateinit var purchaseButton: Button

    companion object {
        private const val PRO_SKU = "pro"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_purchase)

        billingClient = MyApplication.billingClient

        val backButton: ImageButton = findViewById(R.id.backButton)
        backButton.setOnClickListener {
            finish()
        }

        purchaseButton = findViewById(R.id.purchaseButton)
        purchaseButton.isEnabled = false // Disable until product details are loaded
        purchaseButton.setOnClickListener {
            launchPurchaseFlow()
        }

        queryProSubscriptionDetails()
    }

    private fun queryProSubscriptionDetails() {
        lifecycleScope.launch {
            if (!MyApplication.isBillingClientReady.value) {
                Log.e("PurchaseActivity", "BillingClient is not ready.")
                Toast.makeText(this@PurchaseActivity, "Billing service not ready. Please try again later.", Toast.LENGTH_LONG).show()
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
                val detailsList = productDetailsResult.productDetailsList
                if (detailsList.isNullOrEmpty()) {
                    Log.e("PurchaseActivity", "Pro subscription product not found.")
                    Toast.makeText(this@PurchaseActivity, "Pro subscription not available.", Toast.LENGTH_SHORT).show()
                } else {
                    proProductDetails = detailsList.find { it.productId == PRO_SKU }
                    purchaseButton.isEnabled = proProductDetails != null
                    if (proProductDetails == null) {
                         Log.e("PurchaseActivity", "Pro subscription product not found in list.")
                         Toast.makeText(this@PurchaseActivity, "Pro subscription not available.", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Log.e("PurchaseActivity", "Failed to query product details: ${productDetailsResult.billingResult.debugMessage}")
                Toast.makeText(this@PurchaseActivity, "Error fetching subscription details.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun launchPurchaseFlow() {
        proProductDetails?.let { productDetails ->
            val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken
            if (offerToken == null) {
                Log.e("PurchaseActivity", "No valid offer token found for pro subscription.")
                Toast.makeText(this, "Subscription offer not available.", Toast.LENGTH_SHORT).show()
                return
            }

            val productDetailsParamsList = listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(productDetails)
                    .setOfferToken(offerToken)
                    .build()
            )

            val billingFlowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(productDetailsParamsList)
                .build()

            val billingResult = billingClient.launchBillingFlow(this, billingFlowParams)
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.e("PurchaseActivity", "Failed to launch billing flow: ${billingResult.debugMessage}")
                Toast.makeText(this, "Could not start purchase flow.", Toast.LENGTH_SHORT).show()
            }
        } ?: run {
            Log.e("PurchaseActivity", "Attempted to launch purchase flow with null product details.")
            Toast.makeText(this, "Subscription details not loaded yet.", Toast.LENGTH_SHORT).show()
        }
    }
}