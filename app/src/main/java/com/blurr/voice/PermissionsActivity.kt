/**
 * @file PermissionsActivity.kt
 * @brief Defines an activity that provides a dashboard for viewing and managing app permissions.
 *
 * This file contains the `PermissionsActivity`, which serves as a centralized location for the user
 * to check the status of crucial permissions and grant any that are missing.
 */
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

/**
 * An activity that provides a user-friendly dashboard for managing required permissions.
 *
 * This screen displays the status of the three most critical permissions for the app's operation:
 * 1.  **Accessibility Service**: For observing and interacting with the screen.
 * 2.  **Microphone**: For voice input.
 * 3.  **Display Over Other Apps**: For showing UI overlays.
 *
 * It dynamically updates the status of each permission and provides a "Grant" button for any
 * that are currently denied, guiding the user to the appropriate system settings screen.
 */
class PermissionsActivity : AppCompatActivity() {

    private lateinit var accessibilityStatus: TextView
    private lateinit var microphoneStatus: TextView
    private lateinit var overlayStatus: TextView
    private lateinit var grantAccessibilityButton: Button
    private lateinit var grantMicrophoneButton: Button
    private lateinit var grantOverlayButton: Button

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Toast.makeText(this, "Microphone permission granted!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Permission denied.", Toast.LENGTH_SHORT).show()
            }
        }

    /**
     * Initializes the activity, finds UI views, and sets up button listeners.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permissions)

        accessibilityStatus = findViewById(R.id.accessibilityStatus)
        microphoneStatus = findViewById(R.id.microphoneStatus)
        overlayStatus = findViewById(R.id.overlayStatus)
        grantAccessibilityButton = findViewById(R.id.grantAccessibilityButton)
        grantMicrophoneButton = findViewById(R.id.grantMicrophoneButton)
        grantOverlayButton = findViewById(R.id.grantOverlayButton)

        val backButton: Button = findViewById(R.id.backButtonPermissions)
        backButton.setOnClickListener {
            finish()
        }

        setupGrantButtonListeners()
    }

    /**
     * Called when the activity resumes. Refreshes the permission status UI every time
     * the user returns to this screen, ensuring it's always up-to-date.
     */
    override fun onResume() {
        super.onResume()
        updatePermissionStatuses()
    }

    /**
     * Sets up the click listeners for each of the "Grant" buttons.
     */
    private fun setupGrantButtonListeners() {
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

    /**
     * Displays an educational dialog explaining why the Accessibility Service is needed
     * before sending the user to the system settings page.
     */
    private fun showAccessibilityConsentDialog() {
        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.accessibility_consent_title))
            .setMessage(getString(R.string.accessibility_consent_message))
            .setPositiveButton(getString(R.string.accept)) { _, _ ->
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                startActivity(intent)
            }
            .setNegativeButton(getString(R.string.decline)) { dialog, _ ->
                dialog.dismiss()
            }
            .create()

        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.WHITE)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.parseColor("#F44336"))
    }

    /**
     * Checks the status of each required permission and updates the UI accordingly.
     * It sets the text, color, and background for the status indicators and controls
     * the visibility of the "Grant" buttons.
     */
    private fun updatePermissionStatuses() {
        // Accessibility Service Check
        if (isAccessibilityServiceEnabled()) {
            accessibilityStatus.text = "Granted"
            accessibilityStatus.setTextColor(Color.parseColor("#4CAF50")) // Green
            accessibilityStatus.setBackgroundResource(R.drawable.status_background_granted)
            grantAccessibilityButton.visibility = View.GONE
        } else {
            accessibilityStatus.text = "Not Granted"
            accessibilityStatus.setTextColor(Color.parseColor("#F44336")) // Red
            accessibilityStatus.setBackgroundResource(R.drawable.status_background_denied)
            grantAccessibilityButton.visibility = View.VISIBLE
        }

        // Microphone Permission Check
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

        // Display Over Other Apps Check
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

    /**
     * Checks if the app's accessibility service is currently enabled in system settings.
     */
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

    /**
     * Checks if the `RECORD_AUDIO` permission has been granted.
     */
    private fun isMicrophonePermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Checks if the "Display over other apps" permission has been granted.
     */
    private fun isOverlayPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true // Granted at install time on older versions.
        }
    }
}