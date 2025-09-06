package com.blurr.voice.v2.actions

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import com.blurr.voice.api.Finger
import com.blurr.voice.utilities.SpeechCoordinator
import com.blurr.voice.utilities.UserInputManager
import com.blurr.voice.v2.ActionResult
import com.blurr.voice.v2.fs.FileSystem
import com.blurr.voice.v2.perception.ScreenAnalysis
import com.blurr.voice.intents.IntentRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.text.removePrefix

/**
 * Executes a pre-validated, type-safe Action command.
 * The 'when' block is exhaustive, ensuring every action is handled.
 */
class ActionExecutor(private val finger: Finger) {

    // Add this function inside ActionExecutor.kt, outside the class, or as a private fun.

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
    private fun getCenterFromBounds(bounds: String): Pair<Int, Int> {
        val regex = """\[(\d+),(\d+)\]\[(\d+),(\d+)\]""".toRegex()
        val match = regex.find(bounds)
        if (match != null) {
            val (l, t, r, b) = match.destructured.toList().map { it.toInt() }
            return Pair((l + r) / 2, (t + b) / 2)
        }
        return Pair(0, 0) // Should not happen if bounds are valid
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
                    val bounds = elementNode.attributes["bounds"]
                    val text = elementNode.getVisibleText().replace("\n", " ")
                    val resourceId = elementNode.attributes["resource-id"] ?: ""
                    val extraInfo = elementNode.extraInfo
                    val className = (elementNode.attributes["class"] ?: "").removePrefix("android.")

                    if (bounds != null) {
                        val (centerX, centerY) = getCenterFromBounds(bounds)
                        finger.tap(centerX, centerY)
                        ActionResult(longTermMemory = "Tapped element text:$text <$resourceId> <$extraInfo> <$className>")
                    } else {
                        ActionResult(error = "Element with ID ${action.elementId} has no bounds information.")
                    }
                } else {
                    ActionResult(error = "Element with ID ${action.elementId} not found in the current screen state.")
                }
            }
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
                    ActionResult(longTermMemory = "Wrote content to '${action.fileName}'.")
                } else {
                    ActionResult(error = "Failed to write to file '${action.fileName}'.")
                }
            }

//            is Action.ScrollToText -> TODO()
            is Action.TapElementInputTextPressEnter -> {
                val elementNode = screenAnalysis.elementMap[action.index]
                if (elementNode != null) {
                    val bounds = elementNode.attributes["bounds"]
                    val text = elementNode.getVisibleText().replace("\n", " ")
                    val resourceId = elementNode.attributes["resource-id"] ?: ""
                    val extraInfo = elementNode.extraInfo
                    val className = (elementNode.attributes["class"] ?: "").removePrefix("android.")

                    if (bounds != null) {
                        val (centerX, centerY) = getCenterFromBounds(bounds)
                        finger.tap(centerX, centerY)
                        delay(200) // Small delay to ensure focus
                        finger.type(action.text)
                        ActionResult(longTermMemory = "Typed ${action.text} into element  text:$text <$resourceId> <$extraInfo> <$className>.")
                    } else {
                        ActionResult(error = "Element with ID ${action.index} has no bounds information.")
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
                    return try {
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
