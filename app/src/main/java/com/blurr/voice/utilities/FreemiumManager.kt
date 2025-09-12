package com.blurr.voice.utilities

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.getCustomerInfoWith
import com.revenuecat.purchases.interfaces.ReceiveCustomerInfoCallback

import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class FreemiumManager {

    private val db = Firebase.firestore
    private val auth = Firebase.auth

    companion object {
        const val FREE_PLAN_TASK_LIMIT = 15 // Set your free task limit here
    }

    /**
     * Checks if a user document exists in Firestore. If not, creates one with
     * the default free plan values. This should be called upon successful login.
     */
    suspend fun provisionUserIfNeeded() {
        val currentUser = auth.currentUser ?: return // Should not be null if called after login
        val userDocRef = db.collection("users").document(currentUser.uid)

        try {
            val document = userDocRef.get().await()
            if (!document.exists()) {
                Log.d("FreemiumManager", "User document does not exist for UID ${currentUser.uid}. Provisioning new user.")
                val newUser = hashMapOf(
                    "email" to currentUser.email,
                    "plan" to "free",
                    "tasksRemaining" to FREE_PLAN_TASK_LIMIT,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                userDocRef.set(newUser).await()
                Log.d("FreemiumManager", "Successfully provisioned user.")
            } else {
                Log.d("FreemiumManager", "User document already exists for UID ${currentUser.uid}.")
            }
        } catch (e: Exception) {
            Log.e("FreemiumManager", "Error provisioning user", e)
        }
    }

    suspend fun getTasksRemaining(): Long? {
        val customerInfo = getCustomerInfo()
        if (customerInfo?.entitlements?.active?.containsKey("premium") == true) {
            return Long.MAX_VALUE // Represents unlimited
        }

        val currentUser = auth.currentUser ?: return null
        return try {
            val document = db.collection("users").document(currentUser.uid).get().await()
            document.getLong("tasksRemaining")
        } catch (e: Exception) {
            Log.e("FreemiumManager", "Error fetching tasks remaining", e)
            null // Return null on error so the UI can handle it
        }
    }
    /**
     * Checks if the user can perform a task.
     * This is a simple read operation.
     * @return true if tasksRemaining > 0, false otherwise.
     */
    suspend fun canPerformTask(): Boolean {
        val customerInfo = getCustomerInfo()
        if (customerInfo?.entitlements?.active?.containsKey("premium") == true) {
            Log.d("FreemiumManager", "User has 'premium' entitlement. Unlimited tasks allowed.")
            return true // User is subscribed, allow the task
        }

        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.w("FreemiumManager", "Cannot check task count, user is not logged in.")
            return false
        }

        return try {
            val document = db.collection("users").document(currentUser.uid).get().await()
            val tasksRemaining = document.getLong("tasksRemaining") ?: 0
            Log.d("FreemiumManager", "User has $tasksRemaining tasks remaining.")
            tasksRemaining > 0
        } catch (e: Exception) {
            Log.e("FreemiumManager", "Error fetching user task count", e)
            false // Fail safely, preventing task execution on error
        }
    }

    /**
     * Decrements the user's task count by 1.
     * Uses an atomic increment operation for safety.
     * This is a "fire and forget" call, we don't block the UI for it.
     */
    fun decrementTaskCount() {
        Purchases.sharedInstance.getCustomerInfo(object : ReceiveCustomerInfoCallback {
            override fun onReceived(customerInfo: CustomerInfo) {
                if (customerInfo.entitlements.active.containsKey("premium")) {
                    Log.d("FreemiumManager", "User is premium. Not decrementing task count.")
                    return
                }
                // If not premium, proceed with decrementing
                val currentUser = auth.currentUser ?: return
                db.collection("users").document(currentUser.uid)
                    .update("tasksRemaining", FieldValue.increment(-1))
            }
            override fun onError(error: PurchasesError) {
                Log.e("FreemiumManager", "Error getting customer info for decrement: $error")
                // Fallback: decrement anyway to be safe
                val currentUser = auth.currentUser ?: return
                db.collection("users").document(currentUser.uid)
                    .update("tasksRemaining", FieldValue.increment(-1))
            }
        })
    }
    /**
     * Private helper function to get customer info from RevenueCat.
     */
    private suspend fun getCustomerInfo(): CustomerInfo? = suspendCoroutine { continuation ->
        Purchases.sharedInstance.getCustomerInfo(object : ReceiveCustomerInfoCallback {
            override fun onReceived(customerInfo: CustomerInfo) {
                continuation.resume(customerInfo)
            }

            override fun onError(error: PurchasesError) {
                Log.e("FreemiumManager", "Could not get customer info from RevenueCat: $error")
                continuation.resume(null)
            }
        })
    }
}