package com.example.blurr.services

import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import com.example.blurr.BuildConfig
import com.example.blurr.agent.ActionReflector
import com.example.blurr.agent.AgentConfigFactory
import com.example.blurr.agent.InfoPool
import com.example.blurr.agent.Manager
import com.example.blurr.agent.Operator
import com.example.blurr.agent.VisionHelper
import com.example.blurr.agent.atomicActionSignatures
import com.example.blurr.agent.tips.ReflectorTips
import com.example.blurr.api.Eyes
import com.example.blurr.api.Finger
import com.example.blurr.api.MemoryService
import com.example.blurr.api.Retina
import com.example.blurr.utilities.AppContextUtility
import com.example.blurr.utilities.Persistent
import com.example.blurr.utilities.SpeechCoordinator
import com.example.blurr.utilities.TTSManager
import com.example.blurr.utilities.UserIdManager
import com.example.blurr.utilities.addResponse
import com.example.blurr.utilities.getReasoningModelApiResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AgentTaskService : Service() {

    private var agentJob: Job? = null // To keep track of our coroutine
    private var xmlLoggingJob: Job? = null // To keep track of XML logging coroutine

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "AgentTaskChannel"
        const val NOTIFICATION_ID = 2 // Use a different ID from your other service
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val inputText = intent?.getStringExtra("TASK_INSTRUCTION")
        val visionMode = intent?.getStringExtra("VISION_MODE") ?: "XML" // Default to XML mode

        if (inputText == null) {
            Log.e("AgentTaskService", "Service started without user input. Stopping.")
            stopSelf()
            return START_NOT_STICKY
        }

        Log.d("AgentTaskService", "Starting agent task with input: $inputText, vision mode: $visionMode")

       agentJob = CoroutineScope(Dispatchers.IO).launch {
           runAgentLogic(inputText, visionMode)

           Log.d("AgentTaskService", "Agent task finished. Stopping service.")
           stopSelf()
       }

        return START_NOT_STICKY
    }

    fun appendToFile(file: File, content: String) {
        file.appendText(content + "\n")
    }

    /**
     * Starts a background task that logs XML data every 5 seconds
     * This runs independently of the main agent logic
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun startXmlLogging() {
        xmlLoggingJob = CoroutineScope(Dispatchers.IO).launch {
            val eyes = Eyes(this@AgentTaskService)
            val xmlLogDir = File(filesDir, "xml_logs")
            xmlLogDir.mkdirs()

            val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())
            val xmlLogFile = File(xmlLogDir, "xml_log_$timestamp.txt")

            Log.d("AgentTaskService", "Starting XML logging to: ${xmlLogFile.absolutePath}")

            try {
                while (true) {
                    val currentTime = SimpleDateFormat(
                        "yyyy-MM-dd HH:mm:ss.SSS",
                        Locale.getDefault()
                    ).format(Date())

                    try {
                        val xmlData = eyes.openXMLEyes()
                        val logEntry = """
                            === XML LOG ENTRY ===
                            Timestamp: $currentTime
                            XML Data:
                            $xmlData
                            === END XML LOG ===
                            
                        """.trimIndent()

                        appendToFile(xmlLogFile, logEntry)
                        Log.d("AgentTaskService", "XML logged at $logEntry")

                    } catch (e: Exception) {
                        val errorEntry = """
                            === XML LOG ERROR ===
                            Timestamp: $currentTime
                            Error: ${e.message}
                            === END XML LOG ERROR ===
                            
                        """.trimIndent()

                        appendToFile(xmlLogFile, errorEntry)
                        Log.e("AgentTaskService", "Failed to capture XML at $currentTime ", e)
                    }

                    delay(5000) // Wait 5 seconds
                }
            } catch (e: Exception) {
                Log.e("AgentTaskService", "XML logging task failed", e)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun runAgentLogic(inputText: String, visionMode: String) {
        delay(2000)
            val speechCoordinator = SpeechCoordinator.getInstance(this)
            val taskStartTime = System.currentTimeMillis()
            val context = this
            val API_KEY = ""

            val config = AgentConfigFactory.create(
                context = context,
                visionMode = visionMode,
                apiKey = API_KEY,
                enableDirectAppOpening = BuildConfig.ENABLE_DIRECT_APP_OPENING // Set to true for debugging - will be controlled by build config once generated
            )

            // Log vision mode info for debugging
            VisionHelper.logVisionModeInfo(config)


            val tipsFile = File(context.filesDir, "tips.txt")

// AFTER
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())

// Get the public Downloads directory
        val publicDownloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
// Create a dedicated folder for your app's logs inside Downloads
        val appLogBaseDir = File(publicDownloadsDir, "BlurrAgentLogs")
// Create the unique, timestamped directory for the current task
        val logDir = File(appLogBaseDir, timestamp).apply { mkdirs() }

            val screenshotsDir = File(logDir, "screenshots").apply { mkdirs() }

            //NOTE LIMITED RESOURCE, YOUR CAREFULLY, for more info read about Hardware Buffer
            val eyes = Eyes(context)

            val retina = Retina(eyes)
            val infoPool = InfoPool()
            val persistent = Persistent()
            val finger = Finger(context)

            val manager = Manager()
            val operator = Operator(finger)
//            val actionReflector = ActionReflector()

//            val reflectorTips = ReflectorTips()

            var iteration = 0

            val taskLog = File(logDir, "taskLog.txt")
            Log.d("TAG", taskLog.absolutePath)
            infoPool.instruction = inputText

            infoPool.tips = persistent.loadTipsFromFile(tipsFile)
            infoPool.errToManagerThresh = config.errorThreshold



            appendToFile(taskLog, "{step: 0, operation: init, instruction: $inputText, maxItr: ${config.maxIterations}, vision_mode: ${config.visionMode.displayName} }")

        var screenshotFile = eyes.openEyes()

        var postScreenshotFile: Bitmap?

        // Implementing Memory in the agent
//        val userIdManager = UserIdManager(context)
//        val userId = userIdManager.getOrCreateUserId()
//        val memoryService = MemoryService()
//        CoroutineScope(Dispatchers.IO).launch {
//            memoryService.addMemory(infoPool.instruction, userId)
//        }
//
//        val recalledMemories = memoryService.searchMemory(infoPool.instruction, userId)
////        infoPool.recalledMemories = "No recalledMemories"
        while (true) {
                iteration++

//              Iteration LIMIT
                if (config.maxIterations < iteration) {
                    Log.e("MainActivity", "Max iteration reached: ${config.maxIterations}")
                    appendToFile(taskLog, "{step: $iteration, operation: finish, finish_flag: max_iteration, maxItr: ${config.maxIterations}, final_info_pool: $infoPool, task_duration: ${(System.currentTimeMillis() - taskStartTime)/1000} seconds}" )
                    return
                }

//              Consecutive Failure limit
                if (infoPool.actionOutcomes.size >= config.maxConsecutiveFailures) {
                    val lastKActionOutcomes = infoPool.actionOutcomes.takeLast(config.maxConsecutiveFailures)
                    val errFlags = lastKActionOutcomes.map { if (it == "B" || it == "C") 1 else 0 }
                    if (errFlags.sum() == config.maxConsecutiveFailures) {
                        Log.e("MainActivity", "Consecutive failures reach the limit. Stopping...")
                        appendToFile(taskLog, "{step: $iteration, operation: finish, finish_flag: max_consecutive_failures, max_consecutive_failures: ${config.maxConsecutiveFailures}, final_info_pool: $infoPool, task_duration: ${(System.currentTimeMillis() - taskStartTime)/1000} seconds}")
                        return
                    }
                }

//               max repetition allowed
                if (infoPool.actionHistory.size >= config.maxRepetitiveActions){
                    val lastKActions = infoPool.actionHistory.takeLast(config.maxRepetitiveActions)
                    val lastKActionsSet = mutableSetOf<String>()
                    try {
                        for (actStr in lastKActions) {
                            val sanitizedJson = actStr
                                .replace("```json", "")
                                .replace("```", "")
                                .trim()

                            val actObj =
                                JSONObject(sanitizedJson) // Assuming actStr is a JSON string
                            var hashKey = if (actObj.has("name")) actObj.getString("name") else actStr
                            if (actObj.has("arguments")) {
                                val arguments = actObj.optJSONObject("arguments")
                                if (arguments != null) {
                                    arguments.keys().forEach { arg ->
                                        hashKey += "-$arg-${arguments.get(arg)}"
                                    }
                                } else {
                                    hashKey += "-None"
                                }
                            }
                            Log.d("MainActivity", "hashable action key: $hashKey")
                            lastKActionsSet.add(hashKey)
                        }
                    } catch (e: Exception) {
                        // not stopping if there is any error
                        Log.e("MainActivity", "Error hashing actions: ${e.message}")
                    }
                    if (lastKActionsSet.size == 1) {
                        val repeatedActionKey = lastKActionsSet.first()
                        if (!repeatedActionKey.contains("Swipe") && !repeatedActionKey.contains("Back")) {
                            Log.e("MainActivity", "Repetitive actions reaches the limit. Stopping...")
                            appendToFile(taskLog, "{step: $iteration, operation: finish, finish_flag: max_repetitive_actions, max_repetitive_actions: ${config.maxRepetitiveActions}, final_info_pool: $infoPool, task_duration: ${(System.currentTimeMillis() - taskStartTime)/1000} seconds}")
                            return
                        }
                    }
                }

                // Step 1: Take Perception
                if (iteration == 1 && screenshotFile != null){
                    val perceptionTimeStart = System.currentTimeMillis()
                    val screenshotPath = File(screenshotsDir, "screenshot.jpg")

                    // Centralized perception handling
                    val perceptionResult = retina.getPerceptionInfos(context, screenshotFile, config)

                    infoPool.width = perceptionResult.width
                    infoPool.height = perceptionResult.height
                    infoPool.keyboardPre = perceptionResult.keyboardOpen
                    infoPool.perceptionInfosPre = perceptionResult.clickableInfos.toMutableList()

                    // Set XML data if available
                    if (config.isXmlMode && perceptionResult.xmlData.isNotEmpty()) {
                        infoPool.perceptionInfosPreXML = perceptionResult.xmlData
                        Log.d("AgentTaskService", "XML data captured: ${perceptionResult.xmlData.take(100)}...")
                    }

                    appendToFile(
                        taskLog,
                        "{step: $iteration, operation: perception, screenshot: $screenshotPath, perception_infos: ${
                            perceptionResult.clickableInfos.forEach { (text, coordinates) ->
                                println("Text: $text, Coordinates: $coordinates \n")
                            }
                        }, xml_mode: ${config.isXmlMode}, duration: ${(System.currentTimeMillis() - perceptionTimeStart) / 1000} seconds}"
                    )

                    if(perceptionResult.clickableInfos.isEmpty()) {
                        Log.d("AgentTaskService", "No perception infos found, stopping execution")
                    }
                }




                infoPool.errorFlagPlan = false
                if (infoPool.actionOutcomes.size >= infoPool.errToManagerThresh) {
                    val latestOutcomes = infoPool.actionOutcomes.takeLast(infoPool.errToManagerThresh)
                    var count = 0
                    for (outcome in latestOutcomes) {
                        if (outcome in listOf("B", "C")) {
                            count++
                        }
                    }
                    if (count == infoPool.errToManagerThresh) {
                        infoPool.errorFlagPlan = true
                    }
                }
                infoPool.prevSubgoal = infoPool.currentSubgoal


            // ADD this entire block inside your while(true) loop

// Step 2 & 5 Combined: Manager Reflects on previous action and Plans the next one
            val managerThinkingStart = System.currentTimeMillis()
            val promptPlan = manager.getPrompt(infoPool, config)
            val chatPlan = manager.initChat()
            val combinedChatPlan = VisionHelper.createChatResponse(
                "user", promptPlan, chatPlan, config, screenshotFile
            )
            val outputPlan = getReasoningModelApiResponse(combinedChatPlan, apiKey = config.apiKey, agentState = infoPool)
            val parsedManagerResponse = manager.parseResponse(outputPlan.toString())
            val managerThinkingEnd = System.currentTimeMillis()

// --- Process Reflection Output from Manager ---
            if (iteration > 1) { // No reflection on the first iteration
                val outcome = parsedManagerResponse["outcome"].toString()
                val errorDescription = parsedManagerResponse["error_description"].toString()
                val progressStatus = parsedManagerResponse["progress_status"].toString()
                var currentErrorDescription = errorDescription

//                speechCoordinator.speakText("Outcome was $outcome")
                val actionOutcome = when {
                    "A" in outcome -> "A"
                    "B" in outcome -> "B"
                    "C" in outcome -> "C"
                    else -> "C" // Default to no change failure
                }

                // Now that we have the outcome, update all history lists for the PREVIOUS action
                infoPool.actionHistory.add(infoPool.lastAction)
                infoPool.summaryHistory.add(infoPool.lastSummary)
                infoPool.actionOutcomes.add(actionOutcome)
                infoPool.errorDescriptions.add(currentErrorDescription)
                infoPool.progressStatus = progressStatus
                infoPool.progressStatusHistory.add(progressStatus)

                appendToFile(taskLog, "{\n" +
                        "    \"step\": ${iteration - 1},\n" +
                        "    \"operation\": \"reflection_by_manager\",\n" +
                        "    \"outcome\": \"$outcome\",\n" +
                        "    \"error_description\": \"$errorDescription\",\n" +
                        "    \"progress_status\": \"$progressStatus\"\n" +
                        "}")
                Log.d("AgentTaskService", "Reflection by Manager -> Outcome: $actionOutcome, Progress: $progressStatus")
            }

// --- Process Planning Output from Manager ---
            infoPool.plan = parsedManagerResponse["plan"].toString()
            infoPool.currentSubgoal = parsedManagerResponse["current_subgoal"].toString()
//            speechCoordinator.speakText(infoPool.currentSubgoal)
            appendToFile(taskLog, "{ \n" +
                    "\"step\": $iteration, \n" +
                    "\"operation\": \"planning\", \n" +
                    "\"prompt_planning\": ${JSONObject.quote(promptPlan)}, \n" +
                    "\"error_flag_plan\": ${infoPool.errorFlagPlan}, \n" +
                    "\"raw_response\": ${JSONObject.quote(outputPlan.toString())}, \n" +
                    "\"thought\": ${JSONObject.quote(parsedManagerResponse["thought"].toString())}, \n" +
                    "\"plan\": ${JSONObject.quote(infoPool.plan)}, \n" +
                    "\"current_subgoal\": ${JSONObject.quote(infoPool.currentSubgoal)}, \n" +
                    "\"duration\": ${(managerThinkingEnd - managerThinkingStart) / 1000} \n" +
                    "}")
            Log.d("AgentTaskService", "Thought: ${parsedManagerResponse["thought"]}")
            Log.d("AgentTaskService", "Current Subgoal: ${infoPool.currentSubgoal}")

// Stopping by planner
            if (infoPool.currentSubgoal.trim().contains("Finished", ignoreCase = true)) {
                infoPool.finishThought = parsedManagerResponse["thought"].toString()
                appendToFile(taskLog, "{step: $iteration, operation: finish, finish_flag: success, final_info_pool: $infoPool, task_duration: ${(System.currentTimeMillis() - taskStartTime) / 1000} seconds}")
                Log.i("AgentTaskService", "Task finished successfully by planner.")
                speechCoordinator.speakText("Task finished")
                return
            }

// Step 3: Operator's turn
            val actionThinkingTimeStart = System.currentTimeMillis()
            val actionPrompt = operator.getPrompt(infoPool, config)
            val actionChat = operator.initChat()
            val actionCombinedChat = VisionHelper.createChatResponse(
                "user", actionPrompt, actionChat, config, screenshotFile
            )
            val actionOutput = getReasoningModelApiResponse(actionCombinedChat, apiKey = config.apiKey, agentState = infoPool)
            val parsedAction = operator.parseResponse(actionOutput.toString())
            val actionThought = parsedAction["thought"]
            val actionObjStr = parsedAction["action"]
            val actionDesc = parsedAction["description"]
            val actionThinkingTimeEnd = System.currentTimeMillis()

            val actionExecTimeStart = System.currentTimeMillis()
            val (actionObject, _, errorMessage) = operator.execute(actionObjStr.toString(), infoPool, context, config)
            val actionExecTimeEnd = System.currentTimeMillis()

            if (actionObject == null) {
                appendToFile(taskLog, "{step: $iteration, operation: finish, finish_flag: abnormal, error: \"${errorMessage ?: "Action execution failed"}\"}")
                Log.w("AgentTaskService", "WARNING!!: Abnormal finishing: $actionObjStr, Error: $errorMessage")
                return
            }

// Store the executed action details. It will be reflected upon in the NEXT iteration.
            infoPool.lastAction = actionObjStr.toString()
            infoPool.lastSummary = actionDesc.toString()
            infoPool.lastActionThought = actionThought.toString()

            appendToFile(taskLog, "{\n" +
                    "\"step\": $iteration,\n" +
                    "\"operation\": \"action\",\n" +
                    "\"prompt_action\": ${JSONObject.quote(actionPrompt)},\n" +
                    "\"action_object_str\": ${JSONObject.quote(actionObjStr.toString())},\n" +
                    "\"action_thought\": ${JSONObject.quote(actionThought.toString())},\n" +
                    "\"action_description\": ${JSONObject.quote(actionDesc.toString())},\n" +
                    "\"duration\": ${(actionThinkingTimeEnd - actionThinkingTimeStart) / 1000},\n" +
                    "\"execution_duration\": ${(actionExecTimeEnd - actionExecTimeStart) / 1000}\n" +
                    "}")
            Log.d("AgentTaskService", "Action: ${actionObjStr.toString()}")
// REPLACE the old "Step 4" and "Prepare for next iteration" blocks with this:

// Step 4: Take Perception after the action & Prepare for Next Iteration
            val perceptionPostStartTime = System.currentTimeMillis()
            postScreenshotFile = eyes.openEyes()

            if (postScreenshotFile != null) {
                // Get the comprehensive perception result for the new screen state
                val postActionPerceptionResult = retina.getPerceptionInfos(context, postScreenshotFile, config)
                val perceptionPostEndTime = System.currentTimeMillis()

                // Log the post-action perception
                appendToFile(taskLog, "{step: $iteration, operation: perception_post, xml_mode: ${config.isXmlMode}, duration: ${(perceptionPostEndTime - perceptionPostStartTime) / 1000} seconds}")

                // --- Prepare for the next iteration's reflection context ---
                // Save the XML from *before* and *after* the action we just took.
                infoPool.reflectionPreActionXML = infoPool.perceptionInfosPreXML
                infoPool.reflectionPostActionXML = postActionPerceptionResult.xmlData

                // --- Update the main state for the next iteration's planning context ---
                // The state "after" this action becomes the state "before" the next action.
                screenshotFile = postScreenshotFile
                infoPool.perceptionInfosPreXML = postActionPerceptionResult.xmlData
                infoPool.keyboardPre = postActionPerceptionResult.keyboardOpen
                // In XML mode, clickableInfos will be empty, so no need to update it.

                // Inspired by your example, we can add a warning if the new XML is empty.
                if (config.isXmlMode && postActionPerceptionResult.xmlData.isEmpty()) {
                    Log.w("AgentTaskService", "Post-action XML data is empty.")
                }

            } else {
                // Handle the critical error where a post-action screen could not be captured.
                Log.e("AgentTaskService", "Failed to capture post-action screen. Cannot reflect or proceed.")
                infoPool.reflectionPostActionXML = "<hierarchy error=\"Screenshot capture failed\"/>"
                // Consider stopping the agent here by returning from the function.
                return
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cancel all coroutines when service is destroyed
        agentJob?.cancel()
        xmlLoggingJob?.cancel()
        Log.d("AgentTaskService", "Service destroyed.")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}