/**
 * @file FreemiumManager.kt
 * @brief Manages user subscription status and task limits for a freemium model.
 *
 * This file contains the `FreemiumManager` class, which interacts with RevenueCat for subscription
 * status and Firestore for tracking the number of tasks a free-plan user has remaining.
 */
package com.blurr.voice.utilities

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.awaitCustomerInfo
import kotlinx.coroutines.tasks.await

/**
 * Handles the business logic for a freemium user model.
 *
 * This class checks if a user is subscribed to a "pro" plan via RevenueCat. If not, it
 * manages a "tasksRemaining" counter in a Firestore document for that user. It provides
 * functions to check if a user can perform a task and to decrement their task count.
 */
class FreemiumManager {

    /** The Firestore database instance for user data. */
    private val db = Firebase.firestore
    /** The Firebase Authentication instance. */
    private val auth = Firebase.auth

    /**
     * Companion object for constants.
     */
    companion object {
        /** The number of tasks a user on the free plan can perform. */
        const val FREE_PLAN_TASK_LIMIT = 15
    }

    /**
     * Checks the user's subscription status via the RevenueCat SDK.
     *
     * @return `true` if the user has an active "pro" entitlement, `false` otherwise or on error.
     */
    private suspend fun isUserSubscribed(): Boolean {
        return try {
            val customerInfo = Purchases.sharedInstance.awaitCustomerInfo()
            customerInfo.entitlements["pro"]?.isActive == true
        } catch (e: Exception) {
            Log.e("FreemiumManager", "Error fetching customer info: $e")
            false
        }
    }

    /**
     * Ensures a user document exists in Firestore. If not, it creates one with
     * the default free plan values. This should be called upon successful user login.
     */
    suspend fun provisionUserIfNeeded() {
        val currentUser = auth.currentUser ?: return
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

    /**
     * Gets the number of tasks remaining for the current user.
     *
     * Returns a very large number for subscribed users, effectively granting them unlimited tasks.
     *
     * @return The number of tasks remaining as a [Long], or `Long.MAX_VALUE` for subscribed users.
     *         Returns null if the user is not logged in or an error occurs.
     */
    suspend fun getTasksRemaining(): Long? {
        if (isUserSubscribed()) return Long.MAX_VALUE
        val currentUser = auth.currentUser ?: return null
        return try {
            val document = db.collection("users").document(currentUser.uid).get().await()
            document.getLong("tasksRemaining")
        } catch (e: Exception) {
            Log.e("FreemiumManager", "Error fetching tasks remaining", e)
            null
        }
    }
    /**
     * Checks if the current user is permitted to perform a task.
     *
     * A user can perform a task if they are subscribed or if their `tasksRemaining` count is greater than 0.
     *
     * @return `true` if the user can perform a task, `false` otherwise. Fails safely to `false`.
     */
    suspend fun canPerformTask(): Boolean {
        if (isUserSubscribed()) return true

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
            false
        }
    }

    /**
     * Decrements the user's remaining task count by 1 in Firestore.
     *
     * This is a "fire-and-forget" operation that does not block the UI. It does nothing
     * for subscribed users.
     */
    suspend fun decrementTaskCount() {
        if (isUserSubscribed()) return

        val currentUser = auth.currentUser ?: return
        db.collection("users").document(currentUser.uid)
            .update("tasksRemaining", FieldValue.increment(-1))
            .addOnSuccessListener {
                Log.d("FreemiumManager", "Successfully decremented task count for user ${currentUser.uid}.")
            }
            .addOnFailureListener { e ->
                Log.e("FreemiumManager", "Failed to decrement task count.", e)
            }
    }
}