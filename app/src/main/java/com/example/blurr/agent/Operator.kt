package com.example.blurr.agent

import android.content.Context
import com.example.blurr.api.Eyes
import com.example.blurr.api.Finger
import com.google.ai.client.generativeai.type.TextPart
import java.io.File
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import androidx.annotation.RequiresApi

import java.io.FileOutputStream
import kotlin.math.min

data class AtomicActionSignature(
    val arguments: List<String>,
    val description: (InfoPool) -> String
)

// Base atomic action signatures without Open_App
val baseAtomicActionSignatures = mapOf(
    "Tap" to AtomicActionSignature(
        listOf("x", "y")
    ) { "Tap the position (x, y) in current screen." },

    "Swipe" to AtomicActionSignature(
        listOf("x1", "y1", "x2", "y2")
    ) { info -> "Swipe from position (x1, y1) to position (x2, y2). To swipe up or down to review more content, you can adjust the y-coordinate offset based on the desired scroll distance. For example, setting x1 = x2 = ${(0.5 * info.width)}, y1 = ${(0.5 * info.height)}, and y2 = ${(0.1 * info.height)} will swipe upwards to review additional content below. To swipe left or right in the App switcher screen to choose between open apps, set the x-coordinate offset to at least ${(0.5 * info.width)}." },

    "Type" to AtomicActionSignature(
        listOf("text")
    ) { "Type the \"text\" in an input box. You dont need to type everything on keyboard 1 by 1, this is fast" },

//    "Enter" to AtomicActionSignature(emptyList()) { "Press the Enter key after typing." },

    "Switch_App" to AtomicActionSignature(emptyList()) { "Show the App switcher." },

    "Back" to AtomicActionSignature(emptyList()) { "Go back to the previous state." },

    "Home" to AtomicActionSignature(emptyList()) { "Go to the home page." },

    "Wait" to AtomicActionSignature(emptyList()) { "Wait for 10 seconds to give more time for a page loading" },

    "Speak" to AtomicActionSignature(
        listOf("message")
    ) { "Speak the \"message\" to the user. Use this when you need to communicate important information, provide status updates, or give instructions to the user. This message will be spoken on loud speaker, so dont say private information." },

    "Ask" to AtomicActionSignature(
        listOf("question")
    ) { "Ask the \"question\" to the user and wait for their response. Use this when you need clarification, more information, or user input to proceed with the task. The user's response will be added to the instruction to help you complete the task." }
)

// Function to get atomic action signatures based on configuration
fun getAtomicActionSignatures(config: AgentConfig): Map<String, AtomicActionSignature> {
    val signatures = baseAtomicActionSignatures.toMutableMap()
    
    // Add Open_App action only if direct app opening is enabled
    if (config.enableDirectAppOpening) {
        signatures["Open_App"] = AtomicActionSignature(
            listOf("app_name")
        ) { "Open the app named \"app_name\" directly using package manager. This is used to increase the speed of you performing the task." }
    }
    
    return signatures
}

// Legacy atomicActionSignatures for backward compatibility (without Open_App)
val atomicActionSignatures = baseAtomicActionSignatures

class Operator(private val finger: Finger) : BaseAgent() {

    override fun initChat(): List<Pair<String, List<TextPart>>> {
        val systemPrompt = """
            You are a helpful AI assistant for operating mobile phones.
            Your goal is to choose the correct actions to complete the user's instruction.
            Think as if you are a human user operating the phone.
        """.trimIndent()

        return listOf("user" to listOf(TextPart(systemPrompt)))
    }

    override fun getPrompt(infoPool: InfoPool, config: AgentConfig): String {
        val sb = StringBuilder()
        sb.appendLine("### User Instruction ###")
        sb.appendLine(infoPool.instruction)
        sb.appendLine()

        sb.appendLine("### Overall Plan ###")
        sb.appendLine(infoPool.plan)
        sb.appendLine()

        sb.appendLine("### Progress Status ###")
        sb.appendLine(infoPool.progressStatus.ifEmpty { "No progress yet." })
        sb.appendLine()

        sb.appendLine("### Current Subgoal ###")
        sb.appendLine(infoPool.currentSubgoal)
        sb.appendLine()

        sb.appendLine("### Screen Information ###")
        sb.appendLine("The attached image is a screenshot showing the current state of the phone. ")
        sb.appendLine("Its width and height are ${infoPool.width} and ${infoPool.height} pixels, respectively.\n")
        sb.appendLine()


        sb.appendLine("To help you better understand the content in this screenshot, we have extracted positional information for the text elements and icons, including interactive elements such as search bars. ")
        sb.appendLine("The format is: (coordinates; content). The coordinates are [x, y], where x represents the horizontal pixel position (from left to right) ")
        sb.appendLine("and y represents the vertical pixel position (from top to bottom).")

        sb.appendLine("\nThe extracted information is as follows:\n")

        if (infoPool.perceptionInfosPre.isNotEmpty()) {
            infoPool.perceptionInfosPre.forEach { element ->
                sb.appendLine("- Element \"${element.text}\" at position ${element.coordinates}")
            }
            sb.appendLine()
        }

        if (infoPool.perceptionInfosPreXML.isNotEmpty() && config.isXmlMode) {
            sb.appendLine("### Visible Screen Elements in XML format ###")
            sb.appendLine("The following UI elements are currently visible on the screen in XML format:")
            sb.appendLine(infoPool.perceptionInfosPreXML)
            sb.appendLine()
        }

        sb.appendLine("Note that a search bar is often a long, rounded rectangle. If no search bar is presented and you want to perform a search, you may need to tap a search button, which is commonly represented by a magnifying glass.\n")
        sb.appendLine("Also, the information above might not be entirely accurate. ")
        sb.appendLine("You should combine it with the screenshot to gain a better understanding.")

        sb.appendLine("\n")
        sb.appendLine("### Keyboard status ###")
        sb.appendLine(if (infoPool.keyboardPre) "The keyboard has been activated and you can type." else "The keyboard has not been activated and you can\\'t type.")
        sb.appendLine("\n")

        if (infoPool.tips.isNotEmpty()){
            sb.appendLine("### Tips ###")
            sb.appendLine("From previous experience interacting with the device, you have collected the following tips that might be useful for deciding what to do next:\n")
            sb.appendLine(infoPool.tips)
        }

        if (infoPool.importantNotes.isNotBlank()) {
            sb.appendLine("\n### Important Notes ###")
            sb.appendLine("Here are some potentially important content relevant to the user's request you already recorded:\n")
            sb.appendLine(infoPool.importantNotes)
        }

        sb.appendLine("\n---")
        sb.appendLine("Carefully examine all the information provided above and decide on the next action to perform. If you notice an unsolved error in the previous action, think as a human user and attempt to rectify them. You must choose your action from one of the atomic actions listed below.\n\n")

        sb.appendLine("#### Atomic Actions ####")
        sb.appendLine("The atomic action functions are listed in the format of `name(arguments): description` as follows:\n")
        
        // Get the appropriate atomic action signatures based on configuration
        val validActions = getAtomicActionSignatures(config)
        val filteredActions = if (infoPool.keyboardPre) validActions else validActions.filterKeys { it != "Type" }

        filteredActions.forEach { (name, sig) ->
            sb.appendLine("- $name(${sig.arguments.joinToString()}): ${sig.description(infoPool)}")
        }

        if (!infoPool.keyboardPre) {
            sb.appendLine("NOTE: Unable to type. The keyboard has not been activated. To type, please activate the keyboard by tapping on an input box first.\n")
        }


        sb.appendLine("### Latest Action History ###\n")
        if (infoPool.actionHistory.isNotEmpty()){
            sb.appendLine("Recent actions you took previously and whether they were successful:\n")
            val numOfActions = min(5, infoPool.actionHistory.size)
            val latestActions = infoPool.actionHistory.takeLast(numOfActions)
            val latestSummaries = infoPool.summaryHistory.takeLast(numOfActions)
            val latestOutcomes = infoPool.actionOutcomes.takeLast(numOfActions)
            val latestErrorDescriptions = infoPool.errorDescriptions.takeLast(numOfActions)
            val actionLogStrs = mutableListOf<String>()
            for (i in latestActions.indices) {

                val actionLogString: String = if (latestOutcomes[i] == "A") {
                    ("- Action: ${latestActions[i]} | Description: ${latestSummaries[i]} | Outcome: Successful")
                } else {
                    ("- Action: ${latestActions[i]} | Description: ${latestSummaries[i]} | Outcome: Failed | Feedback: ${latestErrorDescriptions[i]}")
                }

                sb.appendLine(actionLogString)
                actionLogStrs.add(actionLogString)
            }
            if (latestOutcomes.last() == "C" &&
                actionLogStrs.size >= 2 &&
                "Tap" in actionLogStrs.last() && // .last() is a safer way to get the last element
                "Tap" in actionLogStrs[actionLogStrs.size - 2]) {
                sb.appendLine(" \nHINT: If multiple Tap actions failed to make changes to the screen, consider using a \\\"Swipe\\\" action to view more content or use another way to achieve the current subgoal.\n")
            }
        }
        else {
            sb.appendLine("\nNo actions have been taken yet.\n")
        }

        sb.appendLine("---\n")
        sb.appendLine("Provide your output in the following format, which contains three parts:\n")

        sb.appendLine("### Thought ###\n")
        sb.appendLine("Provide a detailed explanation of your rationale for the chosen action.\n\n")

        sb.appendLine("### Action ###\n")
        sb.appendLine("Choose only one action from the atomic actions provided. IMPORTANT: Do NOT return invalid actions like null or stop. Do NOT repeat previously failed actions.\n")
        sb.appendLine("You must provide your decision using a valid JSON format specifying the name and arguments of the action. For example, if you choose to tap at position (100, 200), you should write {\"name\":\"Tap\", \"arguments\":{\"x\":100, \"y\":100}}. If an action does not require arguments, such as Home, fill in null to the \"arguments\" field. Ensure that the argument keys match the action function's signature exactly.\n\n")

        sb.appendLine("### Description ###\n")
        sb.appendLine("A brief description of the chosen action and the expected outcome.")

        return sb.toString()
    }

    override fun parseResponse(response: String):  Map<String, String>  {
        val thought = extractSection(response, "### Thought ###", "### Action ###")
        val action = extractSection(response, "### Action ###", "### Description ###")
        val description = extractSection(response, "### Description ###", null)
        return mapOf(
            "thought" to thought,
            "action" to action,
            "description" to description
        )
    }

    suspend fun executeAtomicAction(name: String, args: Map<*, *>, context: Context, infoPool: InfoPool, config: AgentConfig? = null) {

        when (name.lowercase()) {
            "tap" -> finger.tap((args["x"] as Number).toInt(), (args["y"] as Number).toInt())
            "swipe" -> finger.swipe(
                (args["x1"] as Number).toInt(),
                (args["y1"] as Number).toInt(),
                (args["x2"] as Number).toInt(),
                (args["y2"] as Number).toInt()
            )
            "type" -> finger.type(args["text"].toString())
            "enter" -> finger.enter()
            "back" -> finger.back()
            "home" -> finger.home()
            "switch_app" -> finger.switchApp()
            "wait" -> Thread.sleep(10_000)
            "speak" -> {
                val message = args["message"]?.toString()?.trim()
                if (message != null) {
                    // Use SpeechCoordinator to ensure no conflicts with STT
                    kotlinx.coroutines.runBlocking {
                        val speechCoordinator = com.example.blurr.utilities.SpeechCoordinator.getInstance(context)
                        speechCoordinator.speakToUser(message)
                    }
                } else {
                    println("Missing message for Speak action")
                }
            }
            "ask" -> {
                val question = args["question"]?.toString()?.trim()
                if (question != null) {
                    // Use SpeechCoordinator to ask the question (ensures no STT conflicts)
                    kotlinx.coroutines.runBlocking {
                        val speechCoordinator = com.example.blurr.utilities.SpeechCoordinator.getInstance(context)
                        speechCoordinator.speakToUser(question)
                    }
                    
                    // Get user response using UserInputManager (which now uses SpeechCoordinator internally)
                    val userInputManager = com.example.blurr.utilities.UserInputManager(context)
                    val userResponse = userInputManager.askQuestion(question)
                    
                    // Update the instruction with the user's response
                    val updatedInstruction = "${infoPool.instruction}\n\n[Agent asked: $question]\n[User responded: $userResponse]"
                    infoPool.instruction = updatedInstruction
                    
                    println("Agent asked: $question")
                    println("User responded: $userResponse")
                } else {
                    println("Missing question for Ask action")
                }
            }
            "open_app" -> {
                if (config?.enableDirectAppOpening == true) {
                    val appName = args["app_name"]?.toString()?.trim()
                    if (appName != null) {
                        val packageName = findPackageNameFromAppName(appName, context)
                        if (packageName != null) {
                            val success = finger.openApp(packageName)
                            if (!success) {
                                println("Failed to open app: $appName (package: $packageName)")
                            }
                        } else {
                            println("Could not find package name for app: $appName")
                        }
                    } else {
                        println("Missing app_name for Open_App action")
                    }
                } else {
                    println("Open_App action is disabled")
                }
            }
            else -> println("Unsupported action: $name")
        }

        if (name.lowercase() != "wait") Thread.sleep(3000)
    }

    private fun extractSection(text: String, start: String, end: String?): String {
        val startIndex = text.indexOf(start)
        if (startIndex == -1) return ""
        val from = startIndex + start.length
        val to = end?.let { text.indexOf(it, from) } ?: text.length
        return text.substring(from, to).trim()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    suspend fun execute(
        actionStr: String,
        infoPool: InfoPool,
        context: Context,
        config: AgentConfig? = null
    ): Triple<Map<String, Any>?, Int, String?> {

        val actionObj = try {

            var cleanedActionStr = actionStr.lines().filterNot { it.trim().startsWith("#") }.joinToString("\n")
            cleanedActionStr = cleanedActionStr.lines().filterNot { it.trim().startsWith("//") }.joinToString("\n")

            // Extract JSON from potential markdown code blocks
            if (cleanedActionStr.startsWith("```json")){
                cleanedActionStr = cleanedActionStr
                    .replace(Regex("^```(?:json)?\\s*"), "") // remove opening code block
                    .replace(Regex("\\s*```\\s*$"), "")     // remove closing code block
                    .trim()
            }

            val json = org.json.JSONObject(cleanedActionStr)
            val name = json.getString("name")
            val arguments = json.optJSONObject("arguments")?.let {
                it.keys().asSequence().associateWith { key -> it.get(key) }
            } ?: emptyMap()
            mapOf("name" to name, "arguments" to arguments)
        } catch (e: Exception) {
            println("Invalid JSON for executing action: $actionStr")
            return Triple(null, 0, "Invalid JSON format: ${e.message}")
        }

        val name = (actionObj["name"] as String).trim()
        val arguments = actionObj["arguments"] as Map<*, *>

        // Execute atomic action - use dynamic signatures that include Open_App when enabled
        val currentConfig = config ?: AgentConfigFactory.create(context = context, visionMode = "XML", apiKey = "")
        val validSignatures = getAtomicActionSignatures(currentConfig)
        
        if (validSignatures.containsKey(name)) {
            executeAtomicAction(name, arguments, context, infoPool, config)
            return Triple(actionObj, 1, null)
        }
        else {
            if (name.lowercase() in listOf("null", "none", "finish", "exit", "stop")) {
                println("Agent chose to finish the task. Action: $name")
            } else {
                println("Error! Invalid action name: $name")
            }
        }

        infoPool.finishThought = infoPool.lastActionThought

        return Triple(null, 0, null)
    }

    /**
     * Helper function to find package name from app name
     * This is a simple implementation - in a real scenario, you might want to use
     * the AppContextUtility to get a more comprehensive list
     */
    private fun findPackageNameFromAppName(appName: String, context: Context): String? {
        return try {
            val packageManager = context.packageManager
            val apps = packageManager.getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA)
            
            // Try exact match first
            apps.find { app ->
                app.loadLabel(packageManager).toString().equals(appName, ignoreCase = true)
            }?.packageName
            
            // If no exact match, try partial match
            ?: apps.find { app ->
                app.loadLabel(packageManager).toString().contains(appName, ignoreCase = true)
            }?.packageName
            
        } catch (e: Exception) {
            println("Error finding package name for app: $appName - ${e.message}")
            null
        }
    }
}
