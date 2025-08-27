package com.blurr.voice

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.blurr.voice.utilities.FreemiumManager
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

class LoginActivity : AppCompatActivity() {

    private lateinit var oneTapClient: SignInClient
    private lateinit var signInRequest: BeginSignInRequest
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var signInButton: Button
    private lateinit var progressBar: ProgressBar

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
                    // Use the default web client ID from Firebase configuration
                    .setServerClientId(getString(R.string.default_web_client_id))
                    .setFilterByAuthorizedAccounts(false)
                    .build()
            )
            .build()

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
                    }
                } catch (e: ApiException) {
                    Log.w("LoginActivity", "Google sign in failed", e)
                    Toast.makeText(this, "Google Sign-In failed.", Toast.LENGTH_SHORT).show()
                    progressBar.visibility = View.GONE
                }
            } else {
                progressBar.visibility = View.GONE
            }
        }

        signInButton.setOnClickListener {
            signIn()
        }
    }

    private fun signIn() {
        progressBar.visibility = View.VISIBLE
        oneTapClient.beginSignIn(signInRequest)
            .addOnSuccessListener(this) { result ->
                try {
                    val intentSenderRequest = IntentSenderRequest.Builder(result.pendingIntent.intentSender).build()
                    googleSignInLauncher.launch(intentSenderRequest)
                } catch (e: Exception) {
                    Log.e("LoginActivity", "Couldn't start One Tap UI: ${e.localizedMessage}")
                    progressBar.visibility = View.GONE
                    Toast.makeText(this, "Sign-in error. Please try again.", Toast.LENGTH_SHORT).show()
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
                    Log.d("LoginActivity", "signInWithCredential:success")
                    val user = firebaseAuth.currentUser
                    val name = user?.displayName ?: "Unknown"
                    val email = user?.email ?: "unknown"

                    val profileManager = UserProfileManager(this)
                    profileManager.saveProfile(name, email)
                    Log.d("LoginActivity", "User profile saved: Name='${name}', Email='${email}'")

                    lifecycleScope.launch {
                        val freemiumManager = FreemiumManager()
                        freemiumManager.provisionUserIfNeeded()

                        Toast.makeText(this@LoginActivity, "Welcome, $name", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                        finish()
                    }
                } else {
                    Log.w("LoginActivity", "signInWithCredential:failure", task.exception)
                    Toast.makeText(this, "Authentication Failed.", Toast.LENGTH_SHORT).show()
                    progressBar.visibility = View.GONE
                }
            }
    }
}