package com.blurr.voice

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.graphics.toColorInt
import androidx.lifecycle.lifecycleScope
import com.blurr.voice.services.EnhancedWakeWordService
import com.blurr.voice.utilities.FreemiumManager
import com.blurr.voice.utilities.PermissionManager
import com.blurr.voice.utilities.UserIdManager
import com.blurr.voice.utilities.UserProfileManager
import com.blurr.voice.utilities.VideoAssetManager
import com.blurr.voice.utilities.WakeWordManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var handler: Handler
    private lateinit var managePermissionsButton: TextView
    private lateinit var tvPermissionStatus: TextView
    private lateinit var settingsButton: ImageButton
    private lateinit var wakeWordButton: TextView
    private lateinit var userId: String
    private lateinit var permissionManager: PermissionManager
    private lateinit var wakeWordManager: WakeWordManager
    private lateinit var auth: FirebaseAuth
    private lateinit var tasksRemainingTextView: TextView
    private lateinit var freemiumManager: FreemiumManager
    private lateinit var wakeWordHelpLink: TextView
    private lateinit var increaseLimitsLink: TextView


    companion object {
        const val ACTION_WAKE_WORD_FAILED = "com.blurr.voice.WAKE_WORD_FAILED"
    }
    private val wakeWordFailureReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_WAKE_WORD_FAILED) {
                Log.d("MainActivity", "Received wake word failure broadcast.")
                // The service stops itself, but we should refresh the UI state
                updateUI()
                showWakeWordFailureDialog()
            }
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Toast.makeText(this, "Permission granted!", Toast.LENGTH_SHORT).show()
                // The manager will handle the service start after permission is granted.
                wakeWordManager.handleWakeWordButtonClick(wakeWordButton)
            } else {
                Toast.makeText(this, "Permission denied.", Toast.LENGTH_SHORT).show()
            }
        }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = Firebase.auth
        val currentUser = auth.currentUser
        val profileManager = UserProfileManager(this)

        // --- UNIFIED AUTHENTICATION & PROFILE CHECK ---
        // We check both conditions at once. If the user is either not logged in
        // OR their profile is incomplete, we send them to the LoginActivity.
        if (currentUser == null || !profileManager.isProfileComplete()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish() // Destroy MainActivity
            return   // Stop executing any more code in this method
        }


        setContentView(R.layout.activity_main)
        handleIntent(intent)
        managePermissionsButton = findViewById(R.id.btn_manage_permissions) // ADDED

        val userIdManager = UserIdManager(applicationContext)
        userId = userIdManager.getOrCreateUserId()
        increaseLimitsLink = findViewById(R.id.increase_limits_link) // ADDED

        permissionManager = PermissionManager(this)
        permissionManager.initializePermissionLauncher()
        permissionManager.requestAllPermissions()
        // Initialize UI components
        managePermissionsButton = findViewById(R.id.btn_manage_permissions)

        tvPermissionStatus = findViewById(R.id.tv_permission_status)
        settingsButton = findViewById(R.id.settingsButton)
        wakeWordHelpLink = findViewById(R.id.wakeWordHelpLink)

        wakeWordButton = findViewById(R.id.wakeWordButton)
        tasksRemainingTextView = findViewById(R.id.tasks_remaining_textview)
        freemiumManager = FreemiumManager()
        // Initialize managers
        wakeWordManager = WakeWordManager(this, requestPermissionLauncher)
        handler = Handler(Looper.getMainLooper())


        // Setup UI and listeners
        setupClickListeners()
        setupSettingsButton()
        setupGradientText()
        lifecycleScope.launch {
            val videoUrl = "https://storage.googleapis.com/blurr-app-assets/wake_word_demo.mp4"
            VideoAssetManager.getVideoFile(this@MainActivity, videoUrl)
        }

    }
    override fun onStart() {
        super.onStart()
        // It's good practice to re-check authentication in onStart as well.
        if (auth.currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
//    private fun signOut() {
//        auth.signOut()
//        // Optional: Also sign out from the Google account on the device
//        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
//        val googleSignInClient = GoogleSignIn.getClient(this, gso)
//        googleSignInClient.signOut().addOnCompleteListener {
//            // After signing out, redirect to LoginActivity
//            startActivity(Intent(this, LoginActivity::class.java))
//            finish()
//        }
//    }
    private fun handleIntent(intent: Intent?) {
        if (intent?.action == "com.blurr.voice.WAKE_UP_PANDA") {
            Log.d("MainActivity", "Wake up Panda shortcut activated!")
            if (!ConversationalAgentService.isRunning) {
                val serviceIntent = Intent(this, ConversationalAgentService::class.java)
                ContextCompat.startForegroundService(this, serviceIntent)
                Toast.makeText(this, "Panda is waking up...", Toast.LENGTH_SHORT).show()
            } else {
                Log.d("MainActivity", "ConversationalAgentService is already running.")
                Toast.makeText(this, "Panda is already awake!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun setupClickListeners() {
        findViewById<TextView>(R.id.memoriesButton).setOnClickListener {
            startActivity(Intent(this, MemoriesActivity::class.java))
        }
        wakeWordButton.setOnClickListener {
            wakeWordManager.handleWakeWordButtonClick(wakeWordButton)
            // Give the service a moment to update its state before refreshing the UI
            handler.postDelayed({ updateUI() }, 500)
        }

        managePermissionsButton.setOnClickListener {
            startActivity(Intent(this, PermissionsActivity::class.java))
        }
        increaseLimitsLink.setOnClickListener {
            requestLimitIncrease()
        }
        findViewById<TextView>(R.id.github_link_textview).setOnClickListener {
            val url = "https://github.com/Ayush0Chaudhary/blurr"
            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
            startActivity(intent)
        }
        wakeWordHelpLink.setOnClickListener {
            showWakeWordFailureDialog()
        }
    }

    private fun setupSettingsButton() {
        settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }
    private fun requestLimitIncrease() {
        val userEmail = auth.currentUser?.email
        if (userEmail.isNullOrEmpty()) {
            Toast.makeText(this, "Could not get your email. Please try again.", Toast.LENGTH_SHORT).show()
            return
        }

        val recipient = "ayush0000ayush@gmail.com"
        val subject = "Please increase limits"
        val body = "Hello,\n\nPlease increase the task limits for my account: $userEmail\n\nThank you."

        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:") // Only email apps should handle this
            putExtra(Intent.EXTRA_EMAIL, arrayOf(recipient))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }

        // Verify that the intent will resolve to an activity
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            Toast.makeText(this, "No email application found.", Toast.LENGTH_SHORT).show()
        }
    }
    private fun setupGradientText() {
        val karanTextView = findViewById<TextView>(R.id.karan_textview_gradient)
        karanTextView.measure(0, 0)
        val textShader: Shader = LinearGradient(
            0f, 0f, karanTextView.measuredWidth.toFloat(), 0f,
            intArrayOf("#BE63F3".toColorInt(), "#5880F7".toColorInt()),
            null, Shader.TileMode.CLAMP
        )
        karanTextView.paint.shader = textShader
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onResume() {
        super.onResume()
        updateTaskCounter()

        updateUI()
        val filter = IntentFilter(ACTION_WAKE_WORD_FAILED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(wakeWordFailureReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(wakeWordFailureReceiver, filter)
        }
    }
    override fun onPause() {
        super.onPause()
        // Unregister the BroadcastReceiver to avoid leaks
        unregisterReceiver(wakeWordFailureReceiver)
    }


    private fun showWakeWordFailureDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_wake_word_failure, null)
        val videoView = dialogView.findViewById<VideoView>(R.id.video_demo)
        // You may need to add an ID to the CardView in your XML to hide it
        val videoContainer = dialogView.findViewById<View>(R.id.video_container_card)

        val builder = AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Okay") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)

        val alertDialog = builder.create()

        // Use a coroutine to get the file, as it might trigger a download
        lifecycleScope.launch {
            val videoUrl = "https://storage.googleapis.com/YOUR_BUCKET_NAME/wake_up_demo.mp4"
            val videoFile: File? = VideoAssetManager.getVideoFile(this@MainActivity, videoUrl)

            if (videoFile != null && videoFile.exists()) {
                videoContainer.visibility = View.VISIBLE
                videoView.setVideoURI(Uri.fromFile(videoFile))
                videoView.setOnPreparedListener { mp ->
                    mp.isLooping = true
                }
                alertDialog.setOnShowListener {
                    videoView.start()
                }
            } else {
                // If file doesn't exist (e.g., download failed), hide the video player
                Log.e("MainActivity", "Video file not found, hiding video container.")
                videoContainer.visibility = View.GONE
            }
        }

        alertDialog.show()
    }
    private fun updateTaskCounter() {
        lifecycleScope.launch {
            val tasksLeft = freemiumManager.getTasksRemaining()
            if (tasksLeft != null && tasksLeft >= 0) {
                tasksRemainingTextView.text = "You have $tasksLeft free tasks remaining."
                tasksRemainingTextView.visibility = View.VISIBLE

                // ADDED: Logic to show/hide the increase limits link
                // Show the link if the user has 5 or fewer tasks left.
                if (tasksLeft <= 10) {
                    increaseLimitsLink.visibility = View.VISIBLE
                } else {
                    increaseLimitsLink.visibility = View.GONE
                }

            } else {
                // Hide both text views if there's an error or count is invalid
                tasksRemainingTextView.visibility = View.GONE
                increaseLimitsLink.visibility = View.GONE // ADDED
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateUI() {
        val allPermissionsGranted = permissionManager.areAllPermissionsGranted()
        if (allPermissionsGranted) {
            tvPermissionStatus.text = "All required permissions are granted."
            tvPermissionStatus.setTextColor(Color.parseColor("#4CAF50")) // Green
        } else {
            tvPermissionStatus.text = "Some permissions are missing. Tap below to manage."
            tvPermissionStatus.setTextColor(Color.parseColor("#F44336")) // Red
        }
        wakeWordManager.updateButtonState(wakeWordButton)
    }

}