/**
 * @file OnboardingPermissionsActivity.kt
 * @brief Defines the activity that guides the user through the permission granting process.
 *
 * This file contains the logic for a step-by-step UI that requests all necessary permissions
 * for the application to function correctly.
 */
package com.blurr.voice

import android.Manifest
import android.app.AlertDialog
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.blurr.voice.utilities.OnboardingManager

/**
 * An activity that provides a step-by-step guide for the user to grant essential permissions.
 *
 * This UI presents permissions one at a time with explanations for why they are needed.
 * It handles various types of permissions:
 * - Standard runtime permissions (e.g., Microphone).
 * - Special permissions requiring navigation to system settings (e.g., Overlay, Accessibility).
 * - System roles (e.g., Default Assistant).
 *
 * It uses the modern `ActivityResultLauncher` API to handle permission results and updates
 * the UI dynamically based on the current permission status.
 */
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

    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var requestOverlayLauncher: ActivityResultLauncher<Intent>
    private lateinit var requestRoleLauncher: ActivityResultLauncher<Intent>
    private var isLaunchingRole = false
    private val accessibilityServiceChecker = AccessibilityServiceChecker(this)

    /**
     * Called when the activity is first created.
     * Initializes UI components, result launchers, and the list of permission steps.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding_stepper)

        permissionIcon = findViewById(R.id.permissionIcon)
        permissionTitle = findViewById(R.id.permissionTitle)
        permissionDescription = findViewById(R.id.permissionDescription)
        grantButton = findViewById(R.id.grantButton)
        nextButton = findViewById(R.id.nextButton)
        skipButton = findViewById(R.id.skipButton)
        stepperIndicator = findViewById(R.id.stepperIndicator)

        setupLaunchers()
        setupPermissionSteps()
        setupClickListeners()
    }

    /**
     * Called when the activity resumes.
     * This is crucial for re-checking the status of permissions after the user returns
     * from a system settings screen.
     */
    override fun onResume() {
        super.onResume()
        if (currentStep < permissionSteps.size) {
            updateUIForStep(currentStep)
        }
    }

    /**
     * Defines the sequence of permissions to be requested in the onboarding flow.
     * Each step is represented by a `PermissionStep` data class instance.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun setupPermissionSteps() {
        // Step 1: Accessibility Service
        permissionSteps.add(
            PermissionStep(
                titleRes = R.string.accessibility_permission_title,
                descRes = R.string.accessibility_permission_full_desc,
                iconRes = R.drawable.ic_accessibility,
                isGranted = { accessibilityServiceChecker.isAccessibilityServiceEnabled() },
                action = { showAccessibilityConsentDialog() }
            )
        )

        // Step 2: Microphone
        permissionSteps.add(
            PermissionStep(
                titleRes = R.string.microphone_permission_title,
                descRes = R.string.microphone_permission_desc,
                iconRes = R.drawable.ic_microphone,
                isGranted = { ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED },
                action = { requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
            )
        )

        // Step 3: Overlay
        permissionSteps.add(
            PermissionStep(
                titleRes = R.string.overlay_permission_title,
                descRes = R.string.overlay_permission_desc,
                iconRes = R.drawable.ic_overlay,
                isGranted = { Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this) },
                action = {
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                    requestOverlayLauncher.launch(intent)
                }
            )
        )

        // Step 4: Notifications (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionSteps.add(
                PermissionStep(
                    titleRes = R.string.notifications_permission_title,
                    descRes = R.string.notifications_permission_desc,
                    iconRes = R.drawable.ic_overlay,
                    isGranted = { ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED },
                    action = { requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                )
            )
        }

        // Step 5: Default Assistant Role
        permissionSteps.add(
            PermissionStep(
                titleRes = R.string.default_assistant_role_title,
                descRes = R.string.default_assistant_role_desc,
                iconRes = R.drawable.ic_launcher_foreground,
                isGranted = {
                    val rm = getSystemService(RoleManager::class.java)
                    rm?.isRoleHeld(RoleManager.ROLE_ASSISTANT) == true
                },
                action = {
                    startActivity(Intent(this, RoleRequestActivity::class.java))
                }
            )
        )

        updateUIForStep(currentStep)
    }

    /**
     * Displays a consent dialog before navigating the user to the system's accessibility settings.
     * This provides context for why the permission is needed.
     */
    private fun showAccessibilityConsentDialog() {
        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.accessibility_consent_title))
            .setMessage(getString(R.string.accessibility_permission_details))
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
     * Initializes the `ActivityResultLauncher`s for handling results from permission requests
     * and settings screens.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun setupLaunchers() {
        requestPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {
                updateUIForStep(currentStep)
            }

        requestOverlayLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                updateUIForStep(currentStep)
            }

        requestRoleLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                isLaunchingRole = false
                updateUIForStep(currentStep)
            }
    }

    /**
     * Sets up the click listeners for the Grant, Next, and Skip buttons.
     */
    private fun setupClickListeners() {
        grantButton.setOnClickListener {
            permissionSteps[currentStep].action.invoke()
        }

        nextButton.setOnClickListener {
            if (currentStep < permissionSteps.size - 1) {
                currentStep++
                updateUIForStep(currentStep)
            } else {
                finishOnboarding()
            }
        }

        skipButton.setOnClickListener {
            if (currentStep < permissionSteps.size - 1) {
                currentStep++
                updateUIForStep(currentStep)
            } else {
                finishOnboarding()
            }
        }
    }

    /**
     * Updates the entire UI to reflect the current step in the onboarding flow.
     * It sets the icon, title, and description, and dynamically shows/hides the
     * Grant, Next, and Skip buttons based on whether the permission has already been granted.
     * @param stepIndex The index of the current permission step.
     */
    private fun updateUIForStep(stepIndex: Int) {
        if (stepIndex >= permissionSteps.size) {
            finishOnboarding()
            return
        }

        val step = permissionSteps[stepIndex]

        permissionIcon.setImageResource(step.iconRes)
        permissionTitle.setText(step.titleRes)
        permissionDescription.setText(step.descRes)
        stepperIndicator.text = "Step ${stepIndex + 1} of ${permissionSteps.size}"

        val isGranted = step.isGranted()

        if (isGranted) {
            grantButton.visibility = View.GONE
            skipButton.visibility = View.GONE
            nextButton.visibility = View.VISIBLE
            nextButton.text = "Next"
        } else {
            grantButton.visibility = View.VISIBLE
            nextButton.visibility = View.GONE
            skipButton.visibility = View.VISIBLE
            grantButton.text = getString(R.string.grant_permission_button)
        }

        // Special UI handling for the final step (Default Assistant).
        if (stepIndex == permissionSteps.size - 1) {
            if (!isGranted) {
                grantButton.visibility = View.VISIBLE
                grantButton.text = "Open Assistant Settings"
                nextButton.visibility = View.GONE
                skipButton.visibility = View.VISIBLE
            } else {
                nextButton.text = getString(R.string.finish_onboarding_button)
                nextButton.visibility = View.VISIBLE
                skipButton.visibility = View.GONE
                grantButton.visibility = View.GONE
            }
        }
    }

    /**
     * Marks the onboarding process as complete and navigates to the `MainActivity`.
     */
    private fun finishOnboarding() {
        val onboardingManager = OnboardingManager(this)
        onboardingManager.setOnboardingCompleted(true)

        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}

/**
 * A data class representing a single step in the permission onboarding flow.
 *
 * @property iconRes The drawable resource ID for the step's icon.
 * @property titleRes The string resource ID for the step's title.
 * @property descRes The string resource ID for the step's detailed description.
 * @property isGranted A lambda function that returns `true` if the permission for this step is granted.
 * @property action A lambda function to be executed when the user clicks the "Grant" button.
 */
data class PermissionStep(
    @DrawableRes val iconRes: Int,
    val titleRes: Int,
    val descRes: Int,
    val isGranted: () -> Boolean,
    val action: () -> Unit
)

/**
 * A helper class to check if the app's accessibility service is enabled.
 */
class AccessibilityServiceChecker(private val context: Context) {
    /**
     * Checks the system's list of enabled accessibility services to see if this app's service is active.
     * @return `true` if the service is enabled, `false` otherwise.
     */
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