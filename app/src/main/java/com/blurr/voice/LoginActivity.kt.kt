package com.blurr.voice

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.blurr.voice.utilities.FreemiumManager
import com.blurr.voice.utilities.OnboardingManager
import com.blurr.voice.utilities.UserProfileManager
// Replaced Identity imports with legacy Sign-In imports
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.SignInButton
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.functions
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    // Replaced OneTapClient with GoogleSignInClient
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var firebaseAuth: FirebaseAuth

    private lateinit var functions: FirebaseFunctions
    private lateinit var signInButton: SignInButton
    private lateinit var emailField: EditText
    private lateinit var emailSendOtpButton: Button
    private lateinit var otpField: EditText
    private lateinit var otpVerifyButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var loadingText: TextView
    private lateinit var usernameField: EditText

    // Track whether the email corresponds to an existing Firebase Auth user
    private var isExistingEmailUser: Boolean = true

    // Launcher for the legacy sign-in intent
    private lateinit var googleSignInLauncher: ActivityResultLauncher<Intent>

    // Hold onto the name entered during OTP flow for local profile fallback
    private var lastEnteredName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        signInButton = findViewById(R.id.googleSignInButton)
        // Customize Google Sign-In button appearance and text
        signInButton.setSize(SignInButton.SIZE_WIDE)
        signInButton.setColorScheme(SignInButton.COLOR_LIGHT)
        customizeGoogleSignInButton(signInButton)

        emailField = findViewById(R.id.emailInput)
        otpField = findViewById(R.id.otpInput)
        emailSendOtpButton = findViewById(R.id.emailSendOtpButton)
        otpVerifyButton = findViewById(R.id.otpVerifyButton)
        progressBar = findViewById(R.id.progressBar)
        loadingText = findViewById(R.id.loadingText)
        usernameField = findViewById(R.id.usernameInput)
        firebaseAuth = Firebase.auth
        functions = Firebase.functions("us-central1")

        // 1. Configure Google Sign-In Options (Legacy)
        // We request the ID token, email, and basic profile
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        // 2. Initialize the GoogleSignInClient
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // 3. Initialize the launcher for the sign-in intent
        googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                // The Task returned from this call is always completed, no need to attach a listener.
                val task: Task<GoogleSignInAccount> = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                handleSignInResult(task)
            } else {
                // User cancelled or failed
                progressBar.visibility = View.GONE
                loadingText.visibility = View.GONE
                signInButton.isEnabled = true
                Log.d("LoginActivity", "Google Sign-In cancelled or failed with resultCode: ${result.resultCode}")
            }
        }

        signInButton.setOnClickListener {
            signIn()
        }

        emailSendOtpButton.setOnClickListener {
            val email = emailField.text?.toString()?.trim()
            if (!email.isNullOrEmpty()) {
                requestOtp(email)
            } else {
                Toast.makeText(this, "Enter your email", Toast.LENGTH_SHORT).show()
            }
        }

        otpVerifyButton.setOnClickListener {
            val email = emailField.text?.toString()?.trim()
            val otp = otpField.text?.toString()?.trim() ?: ""
            val enteredName = usernameField.text?.toString()?.trim() ?: ""
            if (email.isNullOrEmpty() || otp.isNullOrEmpty()) {
                Toast.makeText(this, "Please enter the OTP", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (usernameField.visibility == View.VISIBLE && enteredName.isBlank()) {
                usernameField.error = "Name required"
                Toast.makeText(this, "Please enter your name", Toast.LENGTH_SHORT).show()
                usernameField.requestFocus()
                return@setOnClickListener
            }
            lastEnteredName = if (enteredName.isNotEmpty()) enteredName else null
            verifyOtpAndSignIn(email, otp, lastEnteredName)
        }
    }

    private fun signIn() {
        progressBar.visibility = View.VISIBLE
        loadingText.visibility = View.VISIBLE
        signInButton.isEnabled = false

        Log.d("LoginActivity", "Starting Legacy Google Sign-In process")
        // Launch the sign-in intent
        val signInIntent = googleSignInClient.signInIntent
        googleSignInLauncher.launch(signInIntent)
    }

    private fun handleSignInResult(completedTask: Task<GoogleSignInAccount>) {
        try {
            val account = completedTask.getResult(ApiException::class.java)
            val idToken = account.idToken
            if (idToken != null) {
                Log.d("LoginActivity", "Got Google ID Token via Legacy API.")
                firebaseAuthWithGoogle(idToken)
            } else {
                Log.e("LoginActivity", "Google ID Token was null.")
                Toast.makeText(this, "Google Sign-In failed.", Toast.LENGTH_SHORT).show()
                progressBar.visibility = View.GONE
                loadingText.visibility = View.GONE
                signInButton.isEnabled = true
            }
        } catch (e: ApiException) {
            // The ApiException status code indicates the detailed failure reason.
            // e.g. GoogleSignInStatusCodes.SIGN_IN_CANCELLED
            Log.w("LoginActivity", "signInResult:failed code=" + e.statusCode)
            FirebaseCrashlytics.getInstance().recordException(e)
            Toast.makeText(this, "Google Sign-In failed. Error: ${e.statusCode}", Toast.LENGTH_SHORT).show()

            progressBar.visibility = View.GONE
            loadingText.visibility = View.GONE
            signInButton.isEnabled = true
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        firebaseAuth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                progressBar.visibility = View.GONE
                loadingText.visibility = View.GONE
                signInButton.isEnabled = true

                if (task.isSuccessful) {
                    val isNewUser = task.result?.additionalUserInfo?.isNewUser ?: false
                    Log.d("LoginActivity", "signInWithCredential:success")

                    val user = firebaseAuth.currentUser
                    val name = user?.displayName
                    val email = user?.email
                    val profileManager = UserProfileManager(this)

                    if (name != null && email != null) {
                        profileManager.saveProfile(name, email)
                        Log.d("LoginActivity", "User profile saved: Name='$name', Email='$email'")
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
                            startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                        } else {
                            startActivity(Intent(this@LoginActivity, OnboardingPermissionsActivity::class.java))
                        }
                        finish()
                    }
                } else {
                    Log.w("LoginActivity", "signInWithCredential:failure", task.exception)
                    task.exception?.let { exception ->
                        FirebaseCrashlytics.getInstance().recordException(exception)
                    }
                    Toast.makeText(this, "Authentication Failed.", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun placeVerifyButtonBelow(target: View) {
        val params = otpVerifyButton.layoutParams as RelativeLayout.LayoutParams
        params.removeRule(RelativeLayout.BELOW)
        params.addRule(RelativeLayout.BELOW, target.id)
        otpVerifyButton.layoutParams = params
    }

    private fun requestOtp(email: String) {
        showProgress(true, "Sending OTP...")
        val data = hashMapOf("email" to email)

        functions
            .getHttpsCallable("sendOtp")
            .call(data)
            .addOnCompleteListener { task ->
                showProgress(false)
                if (task.isSuccessful) {
                    var userExists = true
                    val resultData = task.result?.data
                    if (resultData is Map<*, *>) {
                        val existsFlag = (resultData["userExists"] as? Boolean)
                        if (existsFlag != null) userExists = existsFlag
                    }
                    isExistingEmailUser = userExists

                    Toast.makeText(this, "OTP sent to $email", Toast.LENGTH_LONG).show()
                    emailField.isEnabled = false
                    emailSendOtpButton.visibility = View.GONE
                    otpField.visibility = View.VISIBLE
                    otpVerifyButton.visibility = View.VISIBLE

                    usernameField.visibility = if (userExists) View.GONE else View.VISIBLE

                    if (userExists) {
                        placeVerifyButtonBelow(otpField)
                        otpField.requestFocus()
                    } else {
                        placeVerifyButtonBelow(usernameField)
                        usernameField.requestFocus()
                    }
                    usernameField.requestLayout()
                    otpVerifyButton.requestLayout()
                    (otpVerifyButton.parent as? ViewGroup)?.requestLayout()
                } else {
                    Log.w("LoginActivity", "sendOtp:failure", task.exception)
                    Toast.makeText(this, "Failed to send OTP. Please try again.", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun verifyOtpAndSignIn(
        email: String,
        otp: String,
        name: String? = null,
        allowAutoNameRetry: Boolean = true
    ) {
        showProgress(true, "Verifying OTP...")
        val data = hashMapOf(
            "email" to email,
            "otp" to otp
        )
        if (!name.isNullOrBlank()) {
            data["name"] = name
        }

        functions
            .getHttpsCallable("verifyOtp")
            .call(data)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val result = task.result?.data as? Map<*, *>
                    val customToken = result?.get("token") as? String
                    if (customToken != null) {
                        signInWithCustomToken(customToken)
                    } else {
                        showProgress(false)
                        Toast.makeText(this, "Authentication failed. Please try again.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    showProgress(false)
                    val ex = task.exception
                    Log.w("LoginActivity", "verifyOtp:failure", ex)

                    if (ex is FirebaseFunctionsException) {
                        val message = ex.message?.lowercase() ?: ""
                        val details = ex.details
                        val codeHint = when (details) {
                            is Map<*, *> -> (details["code"] as? String)?.lowercase()
                            else -> null
                        }
                        val nameRequired = codeHint == "name_required" || message.contains("name required") || message.contains("provide name") || message.contains("username")

                        if (nameRequired) {
                            if (isExistingEmailUser && allowAutoNameRetry) {
                                val derived = email.substringBefore('@')
                                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                                lastEnteredName = derived
                                verifyOtpAndSignIn(email, otp, derived, allowAutoNameRetry = false)
                                return@addOnCompleteListener
                            }

                            isExistingEmailUser = false
                            usernameField.visibility = View.VISIBLE
                            placeVerifyButtonBelow(usernameField)
                            usernameField.requestLayout()
                            otpVerifyButton.requestLayout()
                            (otpVerifyButton.parent as? ViewGroup)?.requestLayout()
                            Toast.makeText(this, "Please enter your name to create your account", Toast.LENGTH_LONG).show()
                            return@addOnCompleteListener
                        }
                    }
                    Toast.makeText(this, "Invalid or expired OTP.", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun signInWithCustomToken(token: String) {
        firebaseAuth.signInWithCustomToken(token)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d("LoginActivity", "signInWithCustomToken:success")
                    val isNewUser = task.result?.additionalUserInfo?.isNewUser ?: false
                    startPostAuthFlow(isNewUser)
                } else {
                    showProgress(false)
                    Log.w("LoginActivity", "signInWithCustomToken:failure", task.exception)
                    Toast.makeText(this, "Authentication Failed.", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun startPostAuthFlow(isNewUser: Boolean) {
        val profileManager = UserProfileManager(this)
        val user = firebaseAuth.currentUser
        val name = user?.displayName ?: lastEnteredName ?: "Unknown"
        val email = user?.email ?: "unknown"
        profileManager.saveProfile(name, email)

        lifecycleScope.launch {
            val onboardingManager = OnboardingManager(this@LoginActivity)
            if (isNewUser) {
                val freemiumManager = FreemiumManager()
                freemiumManager.provisionUserIfNeeded()
            }
            if (onboardingManager.isOnboardingCompleted()) {
                startActivity(Intent(this@LoginActivity, MainActivity::class.java))
            } else {
                startActivity(Intent(this@LoginActivity, OnboardingPermissionsActivity::class.java))
            }
            finish()
        }
    }

    private fun showProgress(show: Boolean, text: String = "Loading...") {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        loadingText.visibility = if (show) View.VISIBLE else View.GONE
        loadingText.text = text
    }

    private fun customizeGoogleSignInButton(button: SignInButton) {
        (0 until button.childCount)
            .map { button.getChildAt(it) }
            .firstOrNull { it is TextView }
            ?.let { tv ->
                val textView = tv as TextView
                textView.text = "Continue with Google Login"
                textView.setTextColor(Color.BLACK)
            }
    }
}