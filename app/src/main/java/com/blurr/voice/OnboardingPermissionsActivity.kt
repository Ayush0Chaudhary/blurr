package com.blurr.voice

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.blurr.voice.utilities.OnboardingManager

class OnboardingPermissionsActivity : AppCompatActivity() {

    private lateinit var permissionIcon: ImageView
    private lateinit var permissionTitle: TextView
    private lateinit var permissionDescription: TextView
    private lateinit var grantButton: Button
    private lateinit var nextButton: Button
    private lateinit var skipButton: Button
    private lateinit var stepperIndicator: TextView


    private var currentStep = 0
    private val permissionSteps = mutableListOf<PermissionStep>()
    private val ASSISTANT_ROLE_REQUEST_CODE = 1001

    // Activity result launchers for different permission types
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var requestOverlayLauncher: ActivityResultLauncher<Intent>
    private lateinit var requestRoleLauncher: ActivityResultLauncher<Intent>
    private var pendingRoleRequest = false
    private var isLaunchingRole = false
    private val accessibilityServiceChecker = AccessibilityServiceChecker(this)
    private var hasScheduledAdvance = false

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding_stepper)

        // Initialize UI components
        permissionIcon = findViewById(R.id.permissionIcon)
        permissionTitle = findViewById(R.id.permissionTitle)
        permissionDescription = findViewById(R.id.permissionDescription)
        grantButton = findViewById(R.id.grantButton)
        nextButton = findViewById(R.id.nextButton)
        skipButton = findViewById(R.id.skipButton)
        stepperIndicator = findViewById(R.id.stepperIndicator)

        setupLaunchers()
        // Initialize permission steps
        setupPermissionSteps()
        // Set up the result launchers
        // Handle the button clicks
        setupClickListeners()
    }


    override fun onResume() {
        super.onResume()
        // Check the status of the current step when the activity resumes
        // This is important after the user returns from a settings screen

        // If we returned from a settings screen or role sheet, reflect current state in UI
        if (currentStep < permissionSteps.size) {
            val isGranted = permissionSteps[currentStep].isGranted()
            if (isGranted) {
                // Permission was granted while away, automatically move to next step after a short delay
                Toast.makeText(this, "Permission granted!", Toast.LENGTH_SHORT).show()
                scheduleAdvanceOnce()
            } else {
                updateUIForStep(currentStep)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun setupPermissionSteps() {
        // Step 1: Accessibility Service (Special Intent)
        permissionSteps.add(
            PermissionStep(
                titleRes = R.string.accessibility_permission_title,
                descRes = R.string.accessibility_permission_full_desc,
                iconRes = R.drawable.a11y_v2,
                isGranted = { accessibilityServiceChecker.isAccessibilityServiceEnabled() },
                // The action now shows the consent dialog instead of going directly to settings
                action = { showAccessibilityConsentDialog() }
            )
        )

        // Step 2: Microphone (Standard Permission)
        permissionSteps.add(
            PermissionStep(
                titleRes = R.string.microphone_permission_title,
                descRes = R.string.microphone_permission_desc,
                iconRes = R.drawable.microphone,
                isGranted = { ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED }
            ) {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        )

        // Step 3: Overlay (Special Intent)
        permissionSteps.add(
            PermissionStep(
                titleRes = R.string.overlay_permission_title,
                descRes = R.string.overlay_permission_desc,
                iconRes = R.drawable.display,
                isGranted = { Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this) }
            ) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                requestOverlayLauncher.launch(intent)
            }
        )

        // Step 4: Notifications (Standard Permission - Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionSteps.add(
                PermissionStep(
                    titleRes = R.string.notifications_permission_title,
                    descRes = R.string.notifications_permission_desc,
                    iconRes = R.drawable.bell,
                    isGranted = { ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED }
                ) {
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            )
        }


        // In your setupPermissionSteps() function
// ...
// Step 5: Default Assistant Role (Special Intent)
// Step 5: Default Assistant Role
        permissionSteps.add(
            PermissionStep(
                titleRes = R.string.default_assistant_role_title,
                descRes = R.string.default_assistant_role_desc,
                iconRes = R.drawable.butler,
                isGranted = {
                    val rm = getSystemService(RoleManager::class.java)
                    rm?.isRoleHeld(RoleManager.ROLE_ASSISTANT) == true
                },
                action = {
                    startActivity(Intent(this, RoleRequestActivity::class.java))
                }
            )
        )

// ...
        // Start the flow with the first step
        updateUIForStep(currentStep)
    }
    private fun requestOverlay() {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        startActivity(intent) // (no launcher)
    }
    private fun showAccessibilityConsentDialog() {
        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.accessibility_consent_title))
            .setMessage(getString(R.string.accessibility_permission_details))
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
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.GREEN)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.parseColor("#F44336"))
    }
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun setupLaunchers() {
        requestPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                if (isGranted) {
                    // Permission granted, automatically move to next step after a short delay
                    Toast.makeText(this, "Permission granted!", Toast.LENGTH_SHORT).show()
                    scheduleAdvanceOnce()
                } else {
                    updateUIForStep(currentStep)
                }
            }

        requestOverlayLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                val isGranted = permissionSteps[currentStep].isGranted()
                if (isGranted) {
                    // Permission granted, automatically move to next step after a short delay
                    Toast.makeText(this, "Permission granted!", Toast.LENGTH_SHORT).show()
                    scheduleAdvanceOnce()
                } else {
                    updateUIForStep(currentStep)
                }
            }

        requestRoleLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                isLaunchingRole = false           // ✅ clear here only
                val rm = getSystemService(RoleManager::class.java)
                val isGranted = rm?.isRoleHeld(RoleManager.ROLE_ASSISTANT) == true
                if (isGranted) {
                    // Role granted, automatically move to next step after a short delay
                    Toast.makeText(this, "Default assistant role granted!", Toast.LENGTH_SHORT).show()
                    scheduleAdvanceOnce()
                } else {
                    updateUIForStep(currentStep)
                }
            }
    }
    private fun scheduleAdvanceOnce(delayMs: Long = 1000) {
        if (hasScheduledAdvance) return
        hasScheduledAdvance = true
        nextButton.postDelayed({
            moveToNextStep()
        }, delayMs)
    }
    private fun resetAssistantAskedFlag() {
        getSharedPreferences("assistant_prefs", Context.MODE_PRIVATE)
            .edit().remove("asked_for_assistant_role").apply()
    }


    @RequiresApi(Build.VERSION_CODES.Q)
    private fun openAssistantPicker() {
        val roleManager = getSystemService(RoleManager::class.java)

        if (roleManager?.isRoleHeld(RoleManager.ROLE_ASSISTANT) == true) {
            Toast.makeText(this, "Already the default assistant", Toast.LENGTH_SHORT).show()
            return
        }

        if (isLaunchingRole) return  // ✅ prevent re-entrancy

        window.decorView.post {
            try {
                if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) {
                    isLaunchingRole = true
                    requestRoleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT))
                } else {
                    openAssistantSettingsFallback()
                }
            } catch (_: SecurityException) {
                openAssistantSettingsFallback()
            }
        }
    }

    private fun openAssistantSettingsFallback() {
        // Use startActivity for settings screens (they don’t meaningfully “return”)
        val intents = listOf(
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
            Intent(Settings.ACTION_VOICE_INPUT_SETTINGS),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", packageName, null))
        )
        for (i in intents) {
            if (i.resolveActivity(packageManager) != null) {
                startActivity(i)
                return
            }
        }
        Toast.makeText(this, "Could not open assistant settings on this device.", Toast.LENGTH_LONG).show()
    }
    private fun setupClickListeners() {
        grantButton.setOnClickListener {
            permissionSteps[currentStep].action.invoke()
        }

        nextButton.setOnClickListener {
            moveToNextStep()
        }

        skipButton.setOnClickListener {
            // Simply move to the next step, without granting
            moveToNextStep()
        }
    }

    private fun moveToNextStep() {
        if (currentStep < permissionSteps.size - 1) {
            hasScheduledAdvance = false
            currentStep++
            updateUIForStep(currentStep)
        } else {
            // Last step, finish onboarding
            finishOnboarding()
        }
    }

// In OnboardingPermissionsActivity.kt

    // In OnboardingPermissionsActivity.kt

    private fun updateUIForStep(stepIndex: Int) {
        if (stepIndex >= permissionSteps.size) {
            finishOnboarding()
            return
        }

        val step = permissionSteps[stepIndex]

        // Update UI elements
        permissionIcon.setImageResource(step.iconRes)
        permissionTitle.setText(step.titleRes)
        permissionDescription.setText(step.descRes)
        stepperIndicator.text = "Step ${stepIndex + 1} of ${permissionSteps.size}"

        val isGranted = step.isGranted()

        if (isGranted) {
            // Permission is already granted. Hide the grant and skip buttons.
            grantButton.visibility = View.GONE
            skipButton.visibility = View.GONE

            // Make the next button visible to proceed.
            nextButton.visibility = View.VISIBLE
            
            // Handle the final step
            if (stepIndex == permissionSteps.size - 1) {
                nextButton.text = "Finish"
            } else {
                nextButton.text = "Next"
            }

        } else {
            // Permission is not granted. Show the grant and skip buttons.
            grantButton.visibility = View.VISIBLE
            nextButton.visibility = View.GONE
            skipButton.visibility = View.VISIBLE
            
            // Set the appropriate text for the grant button based on the step
            if (stepIndex == permissionSteps.size - 1) {
                // Default Assistant Role step
                grantButton.text = "Open Assistant Settings"
            } else {
                grantButton.text = getString(R.string.grant_permission_button)
            }
        }
    }
    private fun finishOnboarding() {
        // Set the flag to indicate onboarding is completed for this device.
        val onboardingManager = OnboardingManager(this)
        onboardingManager.setOnboardingCompleted(true)

        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish() // End the onboarding flow
    }
}

// Data class to represent each step
data class PermissionStep(
    @DrawableRes val iconRes: Int,
    val titleRes: Int,
    val descRes: Int,
    val isGranted: () -> Boolean,
    val action: () -> Unit
)

// Helper class to check accessibility service status
class AccessibilityServiceChecker(private val context: Context) {
    fun isAccessibilityServiceEnabled(): Boolean {
        val accessibilityManager = ContextCompat.getSystemService(context, android.view.accessibility.AccessibilityManager::class.java)
        if (accessibilityManager == null || !accessibilityManager.isEnabled) {
            return false
        }
        val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(
            android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        for (service in enabledServices) {
            val serviceInfo = service.resolveInfo.serviceInfo
            if (serviceInfo.packageName == context.packageName &&
                serviceInfo.name == ScreenInteractionService::class.java.name) {
                return true
            }
        }
        return false
    }

}