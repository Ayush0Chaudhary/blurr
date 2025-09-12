package com.blurr.voice

import android.Manifest
import android.annotation.SuppressLint
import android.app.role.RoleManager
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
import androidx.activity.result.ActivityResultLauncher
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
import com.blurr.voice.utilities.OnboardingManager
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
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.getOfferingsWith
import com.revenuecat.purchases.purchasePackageWith
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
    private lateinit var onboardingManager: OnboardingManager
    private lateinit var requestRoleLauncher: ActivityResultLauncher<Intent>
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
        if (currentUser == null || !profileManager.isProfileComplete()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        onboardingManager = OnboardingManager(this)
        if (!onboardingManager.isOnboardingCompleted()) {
            Log.d("MainActivity", "User is logged in but onboarding not completed. Relaunching permissions stepper.")
            startActivity(Intent(this, OnboardingPermissionsActivity::class.java))
            finish()
            return
        }

        requestRoleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                Toast.makeText(this, "Set as default assistant successfully!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Couldn’t become default assistant. Opening settings…", Toast.LENGTH_SHORT).show()
                Log.w("MainActivity", "Role request canceled or app not eligible.\n${explainAssistantEligibility()}")
                openAssistantPickerSettings()
            }
            showAssistantStatus(true)
        }


        setContentView(R.layout.activity_main)
        findViewById<TextView>(R.id.btn_set_default_assistant).setOnClickListener {
            startActivity(Intent(this, RoleRequestActivity::class.java))
        }

        updateDefaultAssistantButtonVisibility()

        handleIntent(intent)
        managePermissionsButton = findViewById(R.id.btn_manage_permissions)

        val userIdManager = UserIdManager(applicationContext)
        userId = userIdManager.getOrCreateUserId()
        increaseLimitsLink = findViewById(R.id.increase_limits_link)

        permissionManager = PermissionManager(this)
        permissionManager.initializePermissionLauncher()

        managePermissionsButton = findViewById(R.id.btn_manage_permissions)
        tvPermissionStatus = findViewById(R.id.tv_permission_status)
        settingsButton = findViewById(R.id.settingsButton)
        wakeWordHelpLink = findViewById(R.id.wakeWordHelpLink)
        wakeWordButton = findViewById(R.id.wakeWordButton)
        tasksRemainingTextView = findViewById(R.id.tasks_remaining_textview)
        freemiumManager = FreemiumManager()

        wakeWordManager = WakeWordManager(this, requestPermissionLauncher)
        handler = Handler(Looper.getMainLooper())

        setupClickListeners()
        setupSettingsButton()
        setupGradientText()
        lifecycleScope.launch {
            val videoUrl = "https://storage.googleapis.com/blurr-app-assets/wake_word_demo.mp4"
            VideoAssetManager.getVideoFile(this@MainActivity, videoUrl)
        }

    }

    private fun openAssistantPickerSettings() {
        val specifics = listOf(
            Intent("android.settings.VOICE_INPUT_SETTINGS"),
            Intent(Settings.ACTION_VOICE_INPUT_SETTINGS),
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        )
        for (i in specifics) {
            if (i.resolveActivity(packageManager) != null) {
                startActivity(i); return
            }
        }
        Toast.makeText(this, "Assistant settings not available on this device.", Toast.LENGTH_SHORT).show()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    fun showAssistantStatus(toast: Boolean = false) {
        val rm = getSystemService(RoleManager::class.java)
        val held = rm?.isRoleHeld(RoleManager.ROLE_ASSISTANT) == true
        val msg = if (held) "This app is the default assistant." else "This app is NOT the default assistant."
        if (toast) Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        Log.d("MainActivity", msg)
    }

    private fun explainAssistantEligibility(): String {
        val pm = packageManager
        val pkg = packageName
        val assistIntent = Intent(Intent.ACTION_ASSIST).setPackage(pkg)
        val assistActivities = pm.queryIntentActivities(assistIntent, 0)
        val visIntent = Intent("android.service.voice.VoiceInteractionService").setPackage(pkg)
        val visServices = pm.queryIntentServices(visIntent, 0)
        return buildString {
            append("Assistant eligibility:\n")
            append("• ACTION_ASSIST activity: ${if (assistActivities.isNotEmpty()) "FOUND" else "NOT FOUND"}\n")
            append("• VoiceInteractionService: ${if (visServices.isNotEmpty()) "FOUND" else "NOT FOUND"}\n")
            append("Note: Many OEMs only list apps with a VoiceInteractionService as selectable assistants.\n")
        }
    }

    override fun onStart() {
        super.onStart()
        if (auth.currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

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
            // This is an example of a limited action
            attemptLimitedAction {
                // This code block will run only if the user is allowed to perform the task
                wakeWordManager.handleWakeWordButtonClick(wakeWordButton)
                handler.postDelayed({ updateUI() }, 500)
            }
        }

        managePermissionsButton.setOnClickListener {
            startActivity(Intent(this, PermissionsActivity::class.java))
        }
        increaseLimitsLink.setOnClickListener {
            // This is now the entry point to our paywall
            showPaywall()
        }
        findViewById<TextView>(R.id.github_link_textview).setOnClickListener {
            val url = "https://github.com/Ayush0Chaudhary/blurr"
            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
            startActivity(intent)
        }
        wakeWordHelpLink.setOnClickListener {
            showWakeWordFailureDialog()
        }
        findViewById<TextView>(R.id.disclaimer_link).setOnClickListener {
            showDisclaimerDialog()
        }
    }

    private fun setupSettingsButton() {
        settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    // *** PAYWALL AND PURCHASE LOGIC ***

    private fun attemptLimitedAction(action: () -> Unit) {
        lifecycleScope.launch {
            if (freemiumManager.canPerformTask()) {
                freemiumManager.decrementTaskCount() // Will only decrement for free users
                action() // Execute the passed action
            } else {
                // User is out of tasks, show the paywall
                showPaywall()
            }
        }
    }

    private fun showPaywall() {
        Purchases.sharedInstance.getOfferingsWith(
            onError = { error ->
                Toast.makeText(this, "Error fetching subscription: $error", Toast.LENGTH_SHORT).show()
            },
            onSuccess = { offerings ->
                val monthlyPackage = offerings.current?.getPackage("default") // Use your package identifier
                if (monthlyPackage != null) {
                    purchasePackage(monthlyPackage)
                } else {
                    Toast.makeText(this, "No subscription available at this time.", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun purchasePackage(packageToPurchase: Package) {
        Purchases.sharedInstance.purchasePackageWith(
            this,
            packageToPurchase,
            onError = { error, userCancelled ->
                if (!userCancelled) {
                    Toast.makeText(this, "Purchase failed: $error", Toast.LENGTH_SHORT).show()
                }
            },
            onSuccess = { storeTransaction, customerInfo ->
                if (customerInfo.entitlements.active.containsKey("premium")) {
                    Toast.makeText(this, "Success! You now have unlimited tasks.", Toast.LENGTH_LONG).show()
                    updateTaskCounter() // Refresh the UI to show premium status
                }
            }
        )
    }

    // **********************************

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
        unregisterReceiver(wakeWordFailureReceiver)
    }

    private fun showDisclaimerDialog() {
        AlertDialog.Builder(this)
            .setTitle("Disclaimer")
            .setMessage("Panda is an experimental AI assistant and is still in development. It may not always be accurate or perform as expected. It does small task better. Your understanding is appreciated!")
            .setPositiveButton("Okay") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun showWakeWordFailureDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_wake_word_failure, null)
        val videoView = dialogView.findViewById<VideoView>(R.id.video_demo)
        val videoContainer = dialogView.findViewById<View>(R.id.video_container_card)
        val builder = AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Got it") { dialog, _ ->
                dialog.dismiss()
            }
        val alertDialog = builder.create()
        lifecycleScope.launch {
            val videoUrl = "https://storage.googleapis.com/blurr-app-assets/wake_word_demo.mp4"
            val videoFile: File? = VideoAssetManager.getVideoFile(this@MainActivity, videoUrl)

            if (videoFile != null && videoFile.exists()) {
                videoContainer.visibility = View.VISIBLE
                videoView.setVideoURI(Uri.fromFile(videoFile))
                videoView.setOnPreparedListener { mp -> mp.isLooping = true }
                alertDialog.setOnShowListener { videoView.start() }
            } else {
                Log.e("MainActivity", "Video file not found, hiding video container.")
                videoContainer.visibility = View.GONE
            }
        }
        alertDialog.show()
    }

    private fun updateTaskCounter() {
        lifecycleScope.launch {
            val tasksLeft = freemiumManager.getTasksRemaining()
            if (tasksLeft == Long.MAX_VALUE) {
                // User is on premium plan
                tasksRemainingTextView.text = "You have unlimited tasks with Premium."
                tasksRemainingTextView.visibility = View.VISIBLE
                increaseLimitsLink.visibility = View.GONE // Hide the link for premium users
            } else if (tasksLeft != null && tasksLeft >= 0) {
                // User is on free plan
                tasksRemainingTextView.text = "You have $tasksLeft free tasks remaining."
                tasksRemainingTextView.visibility = View.VISIBLE
                // Show the link if the user has 10 or fewer tasks left.
                if (tasksLeft <= 10) {
                    increaseLimitsLink.text = "Upgrade to Premium" // Change text
                    increaseLimitsLink.visibility = View.VISIBLE
                } else {
                    increaseLimitsLink.visibility = View.GONE
                }
            } else {
                // Error or invalid state
                tasksRemainingTextView.visibility = View.GONE
                increaseLimitsLink.visibility = View.GONE
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateUI() {
        val allPermissionsGranted = permissionManager.areAllPermissionsGranted()
        if (allPermissionsGranted) {
            tvPermissionStatus.text = "All required permissions are granted."
            managePermissionsButton.visibility = View.GONE
            tvPermissionStatus.setTextColor(Color.parseColor("#4CAF50")) // Green
        } else {
            tvPermissionStatus.text = "Some permissions are missing. Tap below to manage."
            tvPermissionStatus.setTextColor(Color.parseColor("#F44336")) // Red
        }
        wakeWordManager.updateButtonState(wakeWordButton)
    }

    private fun isThisAppDefaultAssistant(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = getSystemService(RoleManager::class.java)
            rm?.isRoleHeld(RoleManager.ROLE_ASSISTANT) == true
        } else {
            val flat = Settings.Secure.getString(contentResolver, "voice_interaction_service")
            val currentPkg = flat?.substringBefore('/')
            currentPkg == packageName
        }
    }

    private fun updateDefaultAssistantButtonVisibility() {
        val btn = findViewById<TextView>(R.id.btn_set_default_assistant)
        btn.visibility = if (isThisAppDefaultAssistant()) View.GONE else View.VISIBLE
    }
}