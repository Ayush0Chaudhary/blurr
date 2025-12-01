package com.blurr.voice.v2.actions

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import com.blurr.voice.ScreenInteractionService
import com.blurr.voice.api.Finger
import com.blurr.voice.utilities.SpeechCoordinator
import com.blurr.voice.utilities.UserInputManager
import com.blurr.voice.overlay.OverlayManager
import com.blurr.voice.v2.ActionResult
import com.blurr.voice.v2.fs.FileSystem
import com.blurr.voice.v2.perception.ScreenAnalysis
import com.blurr.voice.intents.IntentRegistry
import com.blurr.voice.overlay.OverlayDispatcher
import com.blurr.voice.overlay.OverlayPriority
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.system.measureTimeMillis
import kotlin.text.removePrefix

/**
 * Executes a pre-validated, type-safe Action command.
 * The 'when' block is exhaustive, ensuring every action is handled.
 */
class ActionExecutor(private val finger: Finger) {

    // Add this function inside ActionExecutor.kt, outside the class, or as a private fun.
    private fun getExtraInfo(node: AccessibilityNodeInfo): String {
        val infoParts = mutableListOf<String>()
        if (node.isCheckable) infoParts.add("checkable")
        if (node.isChecked) infoParts.add("checked")
        if (node.isClickable) infoParts.add("clickable")
        if (node.isEnabled) infoParts.add("enabled")
        if (node.isFocusable) infoParts.add("focusable")
        if (node.isFocused) infoParts.add("focused")
        if (node.isScrollable) infoParts.add("scrollable")
        if (node.isLongClickable) infoParts.add("long clickable")
        if (node.isSelected) infoParts.add("selected")

        return if (infoParts.isNotEmpty()) {
            "This element is ${infoParts.joinToString(", ")}."
        } else {
            ""
        }
    }

    private fun findPackageNameFromAppName(appName: String, context: Context): String? {
        val pm = context.packageManager
        val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(0)
        }

        // First, try for an exact match (case-insensitive)
        for (appInfo in packages) {
            val label = pm.getApplicationLabel(appInfo).toString()
            if (label.equals(appName, ignoreCase = true)) {
                return appInfo.packageName
            }
        }

        // If no exact match, try for a partial match (contains)
        for (appInfo in packages) {
            val label = pm.getApplicationLabel(appInfo).toString()
            if (label.contains(appName, ignoreCase = true)) {
                return appInfo.packageName
            }
        }

        return null // Not found
    }

    private fun getVisibleText(node: AccessibilityNodeInfo): String {
        val text = node.text?.toString() ?: ""
        val contentDesc = node.contentDescription?.toString() ?: ""
        // Prefer text, fall back to content description
        return (if (text.isNotBlank()) text else contentDesc).replace("\n", " ")
    }
    private fun getCenterFromNode(node: AccessibilityNodeInfo): Pair<Int, Int>? {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (bounds.isEmpty) {
            return null // Node is not on screen or has no bounds
        }
        return Pair(bounds.centerX(), bounds.centerY())
    }
    /**
     * Executes a single action and returns the result.
     * @return An ActionResult detailing the outcome of the action.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    suspend fun execute(
        action: Action,
        screenAnalysis: ScreenAnalysis,
        context: Context,
        fileSystem: FileSystem
    ): ActionResult {
        // This 'when' block now returns an ActionResult for every case.
        return when (action) {
            is Action.TapElement -> {
                val elementNode = screenAnalysis.elementMap[action.elementId]
                if (elementNode != null) {
                    val text = getVisibleText(elementNode)
                    val service = ScreenInteractionService.instance

                    var signatureBefore = ""
                    var signatureAfter = ""
                    var screenChanged = false

                    // --- START: Time Measurement ---
                    val diffTime = measureTimeMillis {
                        // 1. GET SIGNATURE (The entire XML tree)
                        signatureBefore = service?.getWindowHierarchySignature() ?: ""

                        // 2. ATTEMPT 1: Polite Accessibility Action
                        elementNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)

                        // 3. WAIT & VERIFY
                        // We wait for the app to process the click and update the UI
                        delay(100)

                        signatureAfter = service?.getWindowHierarchySignature() ?: ""

                        // If the XML strings are different, the screen changed.
                        screenChanged = signatureBefore != signatureAfter
                    }

                    // --- LOG THE RESULT ---
                    Log.d("ActionExecutor", "Signature diff + 100ms delay took ${diffTime}ms. Screen changed: $screenChanged")

                    if (screenChanged) {
                        ActionResult(longTermMemory = "Clicked element '$text'. Screen updated successfully.")
                    } else {
                        // 4. ESCALATE: BRUTE FORCE TAP
                        // The XML is identical, so the app ignored the click.
                        val center = getCenterFromNode(elementNode)
                        if (center != null) {
                            finger.tap(center.first, center.second)
                            delay(500) // Wait for the physical tap to register
                            ActionResult(longTermMemory = "Accessibility click failed (screen didn't change). Escalated to physical tap at ${center.first},${center.second} on '$text'.")
                        } else {
                            ActionResult(error = "Click sent to '$text' but screen did not change, and cannot find coordinates for physical retry.")
                        }
                    }
                } else {
                    ActionResult(error = "Element with ID ${action.elementId} not found.")
                }
            }
//            is Action.TapElement -> {
//                val elementNode = screenAnalysis.elementMap[action.elementId]
//                if (elementNode != null) {
//                    val text = getVisibleText(elementNode)
//                    val service = ScreenInteractionService.instance
//
//                    // 1. GET SIGNATURE (The entire XML tree)
//                    val signatureBefore = service?.getWindowHierarchySignature() ?: ""
//
//                    // 2. ATTEMPT 1: Polite Accessibility Action
//                    elementNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
//
//                    // 3. WAIT & VERIFY
//                    // We wait for the app to process the click and update the UI
//                    delay(600)
//
//                    val signatureAfter = service?.getWindowHierarchySignature() ?: ""
//
//                    // If the XML strings are different, the screen changed.
//                    val screenChanged = signatureBefore != signatureAfter
//
//                    if (screenChanged) {
//                        ActionResult(longTermMemory = "Clicked element '$text'. Screen updated successfully.")
//                    } else {
//                        // 4. ESCALATE: BRUTE FORCE TAP
//                        // The XML is identical, so the app ignored the click.
//                        val center = getCenterFromNode(elementNode)
//                        if (center != null) {
//                            finger.tap(center.first, center.second)
//                            delay(500) // Wait for the physical tap to register
//                            ActionResult(longTermMemory = "Accessibility click failed (screen didn't change). Escalated to physical tap at ${center.first},${center.second} on '$text'.")
//                        } else {
//                            ActionResult(error = "Click sent to '$text' but screen did not change, and cannot find coordinates for physical retry.")
//                        }
//                    }
//                } else {
//                    ActionResult(error = "Element with ID ${action.elementId} not found.")
//                }
//            }
//            is Action.TapElement -> {
//                // MODIFIED: 'elementNode' is now AccessibilityNodeInfo
//                val elementNode = screenAnalysis.elementMap[action.elementId]
//                if (elementNode != null) {
//                    // MODIFIED: Use new helpers
//                    val text = getVisibleText(elementNode)
//                    val resourceId = elementNode.viewIdResourceName ?: ""
//                    val extraInfo = getExtraInfo(elementNode)
//                    val className = (elementNode.className ?: "").removePrefix("android.")
//
//                    val center = getCenterFromNode(elementNode)
//                    if (center != null) {
//                        elementNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
////                        finger.tap(center.first, center.second)
//                        val si = ScreenInteractionService.instance
//                        si?.showDebugTap(center.first.toFloat(), center.second.toFloat())
//                        ActionResult(longTermMemory = "Tapped element text:$text <$resourceId> <$extraInfo> <$className>")
//                    } else {
//                        ActionResult(error = "Element with ID ${action.elementId} has no visible bounds.")
//                    }
//                } else {
//                    ActionResult(error = "Element with ID ${action.elementId} not found in the current screen state.")
//                }
//            }
            is Action.Speak -> {
                // The message is taken directly from the type-safe action class.
                val message = action.message
                runBlocking {
                    SpeechCoordinator.getInstance(context).speakToUser(message)
                }
                ActionResult(longTermMemory = "Spoke the message: \"${message.take(50)}...\"")
            }
            is Action.Ask -> {
                val question = action.question
                val userResponse = withContext(Dispatchers.IO) { // User input is blocking
                    val userInputManager = UserInputManager(context)
                    userInputManager.askQuestion(question) // This internally speaks and listens
                }

                val memory = "Asked user: '$question'. User responded: '$userResponse'."
                ActionResult(
                    longTermMemory = memory,
                    extractedContent = userResponse, // The user's answer is the result
                    includeExtractedContentOnlyOnce = true
                )
            }
            is Action.LongPressElement -> {
                // MODIFIED: 'elementNode' is now AccessibilityNodeInfo
                val elementNode = screenAnalysis.elementMap[action.elementId]
                if (elementNode != null) {
                    // MODIFIED: Use new helpers
                    val text = getVisibleText(elementNode)
                    val resourceId = elementNode.viewIdResourceName ?: ""
                    val extraInfo = getExtraInfo(elementNode)
                    val className = (elementNode.className ?: "").removePrefix("android.")

                    val center = getCenterFromNode(elementNode)
                    if (center != null) {
//                        finger.longPress(center.first, center.second)
                        elementNode.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
                        ActionResult(longTermMemory = "Long-pressed element text:$text <$resourceId> <$extraInfo> <$className>")
                    } else {
                        ActionResult(error = "Element with ID ${action.elementId} has no visible bounds.")
                    }
                } else {
                    ActionResult(error = "Element with ID ${action.elementId} not found in the current screen state.")
                }
            }
            is Action.OpenApp -> {
                val packageName = findPackageNameFromAppName(action.appName, context)
                if (packageName != null) {
                    val success = finger.openApp(packageName)
                    if (success) {
                        ActionResult(longTermMemory = "Opened app '${action.appName}'.")
                    } else {
                        ActionResult(error = "Failed to open app '${action.appName}' (package: $packageName). Maybe try using different name or use app drawer by scrolling up.")
                    }
                } else {
                    ActionResult(error = "App '${action.appName}' not found. Maybe try using different name or use app drawer by scrolling up.")
                }
            }
            Action.Back -> {
                finger.back()
                ActionResult(longTermMemory = "Pressed the back button.")
            }
            Action.Home -> {
                finger.home()
                ActionResult(longTermMemory = "Pressed the home button.")
            }
            Action.SwitchApp -> {
                finger.switchApp()
                ActionResult(longTermMemory = "Opened the app switcher.")
            }
            Action.Wait -> {
                // Use delay in a coroutine instead of Thread.sleep
                delay(5_000)
                ActionResult(longTermMemory = "Waited for 5 seconds.")
            }
            is Action.ScrollDown -> {
                finger.scrollDown(action.amount)
                ActionResult(longTermMemory = "Scrolled down by ${action.amount} pixels.")
            }
            is Action.ScrollUp -> {
                finger.scrollUp(action.amount)
                ActionResult(longTermMemory = "Scrolled up by ${action.amount} pixels.")
            }
            is Action.SearchGoogle -> {
                // This is a multi-step conceptual action. The executor should handle the concrete steps.
                finger.openApp("com.android.chrome") // More reliable to use package name
                // The next steps (typing, pressing enter) should be decided by the agent in the next turn.
                ActionResult(longTermMemory = "Opened Chrome to search Google.")
            }
            is Action.Done -> {
                // This action doesn't *do* anything. It's a signal to the main loop.
                // We just construct the final ActionResult.
                ActionResult(
                    isDone = true,
                    success = action.success,
                    longTermMemory = "Task finished: ${action.text}",
                    attachments = action.filesToDisplay
                )
            }
//            is Action.ExtractStructuredData -> {
//                // This is a placeholder for a complex action.
//                // A full implementation would require another LLM call with the screen content.
//                // For now, we return an error indicating it's not yet implemented.
//                ActionResult(error = "Action 'ExtractStructuredData' is not yet implemented.")
//            }
            is Action.InputText -> {
                finger.type(action.text)
                ActionResult(longTermMemory = "Input text ${action.text}.")
            }
//            is Action.ScrollToText -> {
//                // As requested, skipping implementation.
//                ActionResult(error = "Action 'ScrollToText' is not implemented.")
//            }
            is Action.AppendFile -> {
                val success = fileSystem.appendFile(action.fileName, action.content)
                if (success) {
                    ActionResult(longTermMemory = "Appended content to '${action.fileName}'.")
                } else {
                    ActionResult(error = "Failed to append to file '${action.fileName}'.")
                }
            }
            is Action.ReadFile -> {
                val content = fileSystem.readFile(action.fileName)
                if (content.startsWith("Error:")) {
                    ActionResult(error = content)
                } else {
                    ActionResult(
                        longTermMemory = "Read content from '${action.fileName}'.",
                        extractedContent = content,
                        includeExtractedContentOnlyOnce = true
                    )
                }
            }
            is Action.WriteFile -> {
                val success = fileSystem.writeFile(action.fileName, action.content)
                if (success) {
                    Log.d("ActionExecutor", "Wrote content to '${action.fileName} ${action.content}'.")
                        OverlayDispatcher.show(
                            action.content,
                            OverlayPriority.CAPTION
                        )
                    ActionResult(longTermMemory = "Wrote content to '${action.fileName}'.")
                } else {
                    ActionResult(error = "Failed to write to file '${action.fileName}'.")
                }
            }

//            is Action.ScrollToText -> TODO()
            is Action.TapElementInputTextPressEnter -> {
                val elementNode = screenAnalysis.elementMap[action.index]
                if (elementNode != null) {

                    val text = getVisibleText(elementNode)
                    val resourceId = elementNode.viewIdResourceName ?: ""
                    val extraInfo = getExtraInfo(elementNode)
                    val className = (elementNode.className ?: "").removePrefix("android.")

                    val center = getCenterFromNode(elementNode)
                    if (center != null) {
                        elementNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        delay(200)
                        finger.type(action.text)
                        delay(100)
                        finger.enter()
                        ActionResult(longTermMemory = "Tapped, typed '${action.text}', and pressed Enter on element: text:$text <$resourceId> <$extraInfo> <$className>.")
                    } else {
                        ActionResult(error = "Element with ID ${action.index} has no visible bounds.")
                    }
                } else {
                    ActionResult(error = "Element with ID ${action.index} for input not found.")
                }
            }
            is Action.LaunchIntent -> {
                val name = action.intentName
                val params = action.parameters
                val appIntent = IntentRegistry.findByName(context, name)
                if (appIntent == null) {
                    return ActionResult(error = "Intent '$name' not found. Check intents catalog for valid names.")
                }
                val intent = appIntent.buildIntent(context, params)
                return if (intent == null) {
                    ActionResult(error = "Intent '$name' missing or invalid parameters: ${params}")
                } else {
                    try {
                        val launchSuccess = finger.launchIntent(intent)
                        if (launchSuccess) {
                            ActionResult(longTermMemory = "Launched intent '$name' with params ${params}")
                        } else {
                            ActionResult(error = "Failed to launch intent '$name' with params ${params}")
                        }
                    } catch (t: Throwable) {
                        ActionResult(error = "Failed to launch intent '$name': ${t.message}")
                    }
                }
            }
        }
    }
}
