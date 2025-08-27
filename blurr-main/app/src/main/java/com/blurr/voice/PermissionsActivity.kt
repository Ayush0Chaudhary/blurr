package com.blurr.voice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class PermissionsActivity : AppCompatActivity() {

    // --- UI Views ---
    private lateinit var accessibilityStatus: TextView
    private lateinit var microphoneStatus: TextView
    private lateinit var overlayStatus: TextView

    // NEW: Add Button variables
    private lateinit var grantAccessibilityButton: Button
    private lateinit var grantMicrophoneButton: Button
    private lateinit var grantOverlayButton: Button

    // NEW: Add a permission launcher for the microphone
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Toast.makeText(this, "Microphone permission granted!", Toast.LENGTH_SHORT).show()
                // The onResume will handle updating the UI
            } else {
                Toast.makeText(this, "Permission denied.", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permissions)

        // Find all status TextViews
        accessibilityStatus = findViewById(R.id.accessibilityStatus)
        microphoneStatus = findViewById(R.id.microphoneStatus)
        overlayStatus = findViewById(R.id.overlayStatus)

        // NEW: Find all Grant Buttons
        grantAccessibilityButton = findViewById(R.id.grantAccessibilityButton)
        grantMicrophoneButton = findViewById(R.id.grantMicrophoneButton)
        grantOverlayButton = findViewById(R.id.grantOverlayButton)

        val backButton: Button = findViewById(R.id.backButtonPermissions)
        backButton.setOnClickListener {
            finish()
        }

        // NEW: Set up click listeners for the grant buttons
        setupGrantButtonListeners()
    }

    override fun onResume() {
        super.onResume()
        // Update statuses and button visibility every time the user returns to this screen
        updatePermissionStatuses()
    }

    private fun setupGrantButtonListeners() {
        // --- CHANGED: The click listener now shows a consent dialog ---
        grantAccessibilityButton.setOnClickListener {
            showAccessibilityConsentDialog()
        }

        grantMicrophoneButton.setOnClickListener {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }

        grantOverlayButton.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
        }
    }

    private fun showAccessibilityConsentDialog() {
        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.accessibility_consent_title))
            .setMessage(getString(R.string.accessibility_consent_message))
            .setPositiveButton(getString(R.string.accept)) { _, _ ->
                // User clicked Accept, navigate to System Settings
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                startActivity(intent)
            }
            .setNegativeButton(getString(R.string.decline)) { dialog, _ ->
                // User clicked Decline, just dismiss the dialog and do nothing
                dialog.dismiss()
            }
            .create()

        // Now, show the dialog
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.WHITE)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.parseColor("#F44336"))
    }

    private fun updatePermissionStatuses() {
        // 1. Accessibility Service Check
        if (isAccessibilityServiceEnabled()) {
            accessibilityStatus.text = "Granted"
            accessibilityStatus.setTextColor(Color.parseColor("#4CAF50")) // Green
            accessibilityStatus.setBackgroundResource(R.drawable.status_background_granted)
            grantAccessibilityButton.visibility = View.GONE // Hide button
        } else {
            accessibilityStatus.text = "Not Granted"
            accessibilityStatus.setTextColor(Color.parseColor("#F44336")) // Red
            accessibilityStatus.setBackgroundResource(R.drawable.status_background_denied)
            grantAccessibilityButton.visibility = View.VISIBLE // Show button
        }

        // 2. Microphone Permission Check
        if (isMicrophonePermissionGranted()) {
            microphoneStatus.text = "Granted"
            microphoneStatus.setTextColor(Color.parseColor("#4CAF50"))
            microphoneStatus.setBackgroundResource(R.drawable.status_background_granted)
            grantMicrophoneButton.visibility = View.GONE
        } else {
            microphoneStatus.text = "Not Granted"
            microphoneStatus.setTextColor(Color.parseColor("#F44336"))
            microphoneStatus.setBackgroundResource(R.drawable.status_background_denied)
            grantMicrophoneButton.visibility = View.VISIBLE
        }

        // 3. Display Over Other Apps Check
        if (isOverlayPermissionGranted()) {
            overlayStatus.text = "Granted"
            overlayStatus.setTextColor(Color.parseColor("#4CAF50"))
            overlayStatus.setBackgroundResource(R.drawable.status_background_granted)
            grantOverlayButton.visibility = View.GONE
        } else {
            overlayStatus.text = "Not Granted"
            overlayStatus.setTextColor(Color.parseColor("#F44336"))
            overlayStatus.setBackgroundResource(R.drawable.status_background_denied)
            grantOverlayButton.visibility = View.VISIBLE
        }
    }

    // --- Helper functions to check each permission (no changes here) ---

    private fun isAccessibilityServiceEnabled(): Boolean {
        val service = packageName + "/" + ScreenInteractionService::class.java.canonicalName
        val accessibilityEnabled = Settings.Secure.getInt(
            applicationContext.contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            0
        )
        if (accessibilityEnabled == 1) {
            val settingValue = Settings.Secure.getString(
                applicationContext.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            if (settingValue != null) {
                val splitter = TextUtils.SimpleStringSplitter(':')
                splitter.setString(settingValue)
                while (splitter.hasNext()) {
                    if (splitter.next().equals(service, ignoreCase = true)) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun isMicrophonePermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun isOverlayPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true // Granted at install time on older versions
        }
    }
}