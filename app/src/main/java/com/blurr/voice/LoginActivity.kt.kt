/**
 * @file LoginActivity.kt
 * @brief Manages the user sign-in process using Google One Tap and Firebase Authentication.
 *
 * This file contains the `LoginActivity`, which is the entry point for user authentication.
 * It handles the entire sign-in flow, from displaying the Google One Tap UI to authenticating
 * with Firebase and navigating the user to the appropriate next screen.
 */
package com.blurr.voice

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.blurr.voice.utilities.FreemiumManager
import com.blurr.voice.utilities.OnboardingManager
import com.blurr.voice.utilities.UserProfileManager
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.SignInClient
import com.google.android.gms.common.api.ApiException
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

/**
 * An activity that handles the user authentication flow.
 *
 * This activity orchestrates the sign-in process using Google's One Tap API for a streamlined
 * user experience. After a successful Google sign-in, it uses the obtained ID token to
 * authenticate with Firebase. Upon successful Firebase authentication, it saves the user's
 * profile, provisions a freemium account for new users, and then navigates them to either the
 * main application (`MainActivity`) or the permission onboarding flow (`OnboardingPermissionsActivity`)
 * based on their local onboarding status.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var oneTapClient: SignInClient
    private lateinit var signInRequest: BeginSignInRequest
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var signInButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var loadingText: TextView

    private lateinit var googleSignInLauncher: ActivityResultLauncher<IntentSenderRequest>

    /**
     * Called when the activity is first created.
     * Initializes Firebase, the Google One Tap client, and the UI components. It also sets up
     * the `ActivityResultLauncher` to handle the result from the Google Sign-In flow.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        signInButton = findViewById(R.id.googleSignInButton)
        progressBar = findViewById(R.id.progressBar)
        loadingText = findViewById(R.id.loadingText)
        firebaseAuth = Firebase.auth

        oneTapClient = Identity.getSignInClient(this)

        // Configure the sign-in request for Google's One Tap UI.
        signInRequest = BeginSignInRequest.builder()
            .setGoogleIdTokenRequestOptions(
                BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                    .setSupported(true)
                    // The server client ID is essential for Firebase authentication.
                    .setServerClientId(getString(R.string.default_web_client_id))
                    .setFilterByAuthorizedAccounts(false)
                    .build()
            )
            .build()

        // Register a launcher to handle the result from the One Tap sign-in intent.
        googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                try {
                    val credential = oneTapClient.getSignInCredentialFromIntent(result.data)
                    val googleIdToken = credential.googleIdToken
                    if (googleIdToken != null) {
                        Log.d("LoginActivity", "Got Google ID Token.")
                        firebaseAuthWithGoogle(googleIdToken)
                    } else {
                        Log.e("LoginActivity", "Google ID Token was null.")
                        Toast.makeText(this, "Google Sign-In failed.", Toast.LENGTH_SHORT).show()
                        progressBar.visibility = View.GONE
                        loadingText.visibility = View.GONE
                    }
                } catch (e: ApiException) {
                    Log.w("LoginActivity", "Google sign in failed", e)
                    Toast.makeText(this, "Google Sign-In failed.", Toast.LENGTH_SHORT).show()
                    progressBar.visibility = View.GONE
                    loadingText.visibility = View.GONE
                }
            } else {
                // User cancelled or there was an error.
                progressBar.visibility = View.GONE
                loadingText.visibility = View.GONE
            }
        }

        signInButton.setOnClickListener {
            signIn()
        }
    }

    /**
     * Initiates the Google One Tap sign-in flow.
     * It shows a progress indicator and calls the `beginSignIn` method of the `SignInClient`.
     */
    private fun signIn() {
        progressBar.visibility = View.VISIBLE
        loadingText.visibility = View.VISIBLE
        oneTapClient.beginSignIn(signInRequest)
            .addOnSuccessListener(this) { result ->
                try {
                    val intentSenderRequest = IntentSenderRequest.Builder(result.pendingIntent.intentSender).build()
                    googleSignInLauncher.launch(intentSenderRequest)
                } catch (e: Exception) {
                    Log.e("LoginActivity", "Couldn't start One Tap UI: ${e.localizedMessage}")
                    progressBar.visibility = View.GONE
                    loadingText.visibility = View.GONE
                }
            }
            .addOnFailureListener(this) { e ->
                Log.e("LoginActivity", "Sign-in failed: ${e.localizedMessage}")
                Toast.makeText(this, "Sign-in failed. Please try again.", Toast.LENGTH_SHORT).show()
                progressBar.visibility = View.GONE
                loadingText.visibility = View.GONE
            }
    }

    /**
     * Authenticates with Firebase using the Google ID token obtained from the One Tap sign-in.
     *
     * On success, it saves the user's profile, provisions a freemium account if they are a new user,
     * and navigates to the appropriate next screen based on their onboarding completion status.
     *
     * @param idToken The Google ID token.
     */
    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        firebaseAuth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                progressBar.visibility = View.GONE
                loadingText.visibility = View.GONE
                
                if (task.isSuccessful) {
                    val isNewUser = task.result?.additionalUserInfo?.isNewUser ?: false

                    Log.d("LoginActivity", "signInWithCredential:success")
                    val user = firebaseAuth.currentUser
                    val name = user?.displayName
                    val email = user?.email
                    val profileManager = UserProfileManager(this)

                    if (name != null && email != null) {
                        profileManager.saveProfile(name, email)
                        Log.d("LoginActivity", "User profile saved: Name='${name}', Email='${email}'")
                    } else {
                        profileManager.saveProfile("Unknown", "unknown")
                        Log.w("LoginActivity", "User name or email was null, profile not saved.")
                    }

                    lifecycleScope.launch {
                        val onboardingManager = OnboardingManager(this@LoginActivity)

                        if (isNewUser) {
                            Log.d("LoginActivity", "New user detected. Provisioning freemium account.")
                            val freemiumManager = FreemiumManager()
                            freemiumManager.provisionUserIfNeeded()
                        }

                        // Check the local flag to see if onboarding has been completed on this device.
                        if (onboardingManager.isOnboardingCompleted()) {
                            Log.d("LoginActivity", "Onboarding already completed on this device. Launching main activity.")
                            startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                        } else {
                            Log.d("LoginActivity", "Onboarding not completed. Launching permissions stepper.")
                            startActivity(Intent(this@LoginActivity, OnboardingPermissionsActivity::class.java))
                        }
                        finish()
                    }
                } else {
                    Log.w("LoginActivity", "signInWithCredential:failure", task.exception)
                    Toast.makeText(this, "Authentication Failed.", Toast.LENGTH_SHORT).show()
                }
            }
    }
}