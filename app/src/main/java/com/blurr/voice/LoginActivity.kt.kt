package com.blurr.voice

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button // Changed from SignInButton
import android.widget.ProgressBar
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
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.logInWith
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var oneTapClient: SignInClient
    private lateinit var signInRequest: BeginSignInRequest
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var signInButton: Button
    private lateinit var progressBar: ProgressBar

    // New ActivityResultLauncher for the modern Identity API
    private lateinit var googleSignInLauncher: ActivityResultLauncher<IntentSenderRequest>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        signInButton = findViewById(R.id.googleSignInButton)
        progressBar = findViewById(R.id.progressBar)
        firebaseAuth = Firebase.auth

        oneTapClient = Identity.getSignInClient(this)

        signInRequest = BeginSignInRequest.builder()
            .setGoogleIdTokenRequestOptions(
                BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                    .setSupported(true)
                    .setServerClientId(getString(R.string.default_web_client_id))
                    .setFilterByAuthorizedAccounts(false)
                    .build()
            )
            .build()

        // 3. Initialize the new launcher
        googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                try {
                    // The one-tap UI returns a Sign-In Credential
                    val credential = oneTapClient.getSignInCredentialFromIntent(result.data)
                    val googleIdToken = credential.googleIdToken
                    if (googleIdToken != null) {
                        Log.d("LoginActivity", "Got Google ID Token.")
                        // Pass the token to Firebase
                        firebaseAuthWithGoogle(googleIdToken)
                    } else {
                        Log.e("LoginActivity", "Google ID Token was null.")
                        Toast.makeText(this, "Google Sign-In failed.", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: ApiException) {
                    Log.w("LoginActivity", "Google sign in failed", e)
                    Toast.makeText(this, "Google Sign-In failed.", Toast.LENGTH_SHORT).show()
                }
            }
            progressBar.visibility = View.GONE
        }


        signInButton.setOnClickListener {
            signIn()
        }
    }

    private fun signIn() {
        progressBar.visibility = View.VISIBLE
        // 4. Launch the sign-in flow
        oneTapClient.beginSignIn(signInRequest)
            .addOnSuccessListener(this) { result ->
                try {
                    val intentSenderRequest = IntentSenderRequest.Builder(result.pendingIntent.intentSender).build()
                    googleSignInLauncher.launch(intentSenderRequest)
                } catch (e: Exception) {
                    Log.e("LoginActivity", "Couldn't start One Tap UI: ${e.localizedMessage}")
                    progressBar.visibility = View.GONE
                }
            }
            .addOnFailureListener(this) { e ->
                Log.e("LoginActivity", "Sign-in failed: ${e.localizedMessage}")
                Toast.makeText(this, "Sign-in failed. Please try again.", Toast.LENGTH_SHORT).show()
                progressBar.visibility = View.GONE
            }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        firebaseAuth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val isNewUser = task.result?.additionalUserInfo?.isNewUser ?: false

                    Log.d("LoginActivity", "signInWithCredential:success")
                    val user = firebaseAuth.currentUser
                    val name = user?.displayName
                    val email = user?.email
                    val profileManager = UserProfileManager(this)
                    user?.uid?.let {
                        Purchases.sharedInstance.logInWith(
                            it,
                            onError = { error ->
                                Log.e("LoginActivity", "RevenueCat login failed: $error")
                            },
                            onSuccess = { customerInfo, created ->
                                Log.d("LoginActivity", "RevenueCat login success. New user: $created")
                            }
                        )
                    }

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