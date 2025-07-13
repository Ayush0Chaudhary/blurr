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
import com.example.blurr.api.Eyes
import com.example.blurr.api.Finger
import com.example.blurr.api.MemoryService
import com.example.blurr.api.Retina
import com.example.blurr.utilities.TipsStorageManager
import com.example.blurr.utilities.UserIdManager
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
            val speechCoordinator = com.example.blurr.utilities.SpeechCoordinator.getInstance(this)
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



            val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())
            val logDir = File(context.filesDir, "logs/mobile_agent_E/test/$timestamp").apply { mkdirs() }
            val screenshotsDir = File(logDir, "screenshots").apply { mkdirs() }

            //NOTE LIMITED RESOURCE, YOUR CAREFULLY, for more info read about Hardware Buffer
            val eyes = Eyes(context)

            val retina = Retina(eyes)
            val infoPool = InfoPool()
            val finger = Finger(context)

            val manager = Manager()
            val operator = Operator(finger)
            val actionReflector = ActionReflector()

            var iteration = 0

            val taskLog = File(logDir, "taskLog.txt")

            infoPool.instruction = inputText

            infoPool.tips = TipsStorageManager.getLocalTips(applicationContext)
            infoPool.errToManagerThresh = config.errorThreshold



            appendToFile(taskLog, "{step: 0, operation: init, instruction: $inputText, maxItr: ${config.maxIterations}, vision_mode: ${config.visionMode.displayName} }")

        var screenshotFile = eyes.openEyes()

        var postScreenshotFile: Bitmap?

        // Implementing Memory in the agent
        val userIdManager = UserIdManager(context)
        val userId = userIdManager.getOrCreateUserId()
        val memoryService = MemoryService()
        CoroutineScope(Dispatchers.IO).launch {
            memoryService.addMemory(infoPool.instruction, userId)
        }

        val recalledMemories = memoryService.searchMemory(infoPool.instruction, userId)
//        infoPool.recalledMemories = "No recalledMemories"
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


                // Step 2: Manager Planning
                val managerPlanningStart = System.currentTimeMillis()
                val promptPlan = manager.getPrompt(infoPool, config)
                val chatPlan = manager.initChat()
                val combinedChatPlan = VisionHelper.createChatResponse(
                    "user",
                    promptPlan,
                    chatPlan,
                    config,
                    screenshotFile
                )

                val outputPlan =
                    getReasoningModelApiResponse(combinedChatPlan, apiKey = config.apiKey)
                val parsedManagerPlan = manager.parseResponse(outputPlan.toString())

                // Updating the InfoPool
                infoPool.plan = parsedManagerPlan["plan"].toString()
                infoPool.currentSubgoal =  parsedManagerPlan["current_subgoal"].toString()

                TipsStorageManager.appendTips(applicationContext, parsedManagerPlan["tips"].toString())
                infoPool.tips = TipsStorageManager.getLocalTips(applicationContext)

                Log.d("AgentTaskService", infoPool.tips)

                speechCoordinator.speakText(infoPool.currentSubgoal)
                appendToFile(taskLog, "{ \n" +
                        "                    \"step\": $iteration, \n" +
                        "                    \"operation\": \"planning\", \n" +
                        "                    \"prompt_planning\": $promptPlan, \n" +
                        "                    \"error_flag_plan\": ${infoPool.errorFlagPlan}, \n" +
                        "                    \"raw_response\": $outputPlan, \n" +
                        "                    \"thought\": ${parsedManagerPlan["thought"]}, \n" +
                        "                    \"plan\": ${parsedManagerPlan["plan"]}, \n" +
                        "                    \"current_subgoal\": ${parsedManagerPlan["current_subgoal"]}, \n" +
                        "                    \"duration\": ${(System.currentTimeMillis() - managerPlanningStart)/1000}, \n" +
                        "                }")
                Log.d("MainActivity", "Thought: ${parsedManagerPlan["thought"]}")
                Log.d("MainActivity", "Overall Plan: ${infoPool.plan}")
                Log.d("MainActivity", "Current Subgoal: ${infoPool.currentSubgoal}")


                // Stopping by planner
                if (infoPool.currentSubgoal.trim().contains("Finished")) {
                    infoPool.finishThought = parsedManagerPlan["thought"].toString()
                    val taskEndTime = System.currentTimeMillis()
                    appendToFile(taskLog, "{step: $iteration, operation: finish, finish_flag: success, final_info_pool: $infoPool, task_duration: ${(taskEndTime - taskStartTime)/1000} seconds}")
                    Log.i("MainActivity", "Task finished successfully by planner.")
                    speechCoordinator.speakText("Task finished")
                    return
                }

                var actionThinkingTimeStart = System.currentTimeMillis()
                // Step 3 Operator's turn, he will execute on the plan of manager
                val actionPrompt = operator.getPrompt(infoPool, config)
                val actionChat = operator.initChat()
                val actionCombinedChat = VisionHelper.createChatResponse(
                    "user",
                    actionPrompt,
                    actionChat,
                    config,
                    screenshotFile
                )
                var actionOutput =
                    getReasoningModelApiResponse(actionCombinedChat, apiKey = config.apiKey)
                var parsedAction = operator.parseResponse(actionOutput.toString())
                var actionThought = parsedAction["thought"]
                var actionObjStr = parsedAction["action"]
                var actionDesc = parsedAction["description"]

                TipsStorageManager.appendTips(applicationContext, parsedAction["tips"].toString())
                infoPool.tips = TipsStorageManager.getLocalTips(applicationContext)
                Log.d("AgentTaskService", infoPool.tips)

                println(parsedAction)
                val actionThinkingTimeEnd = System.currentTimeMillis()

                infoPool.lastActionThought = actionThought.toString()

                val actionExecTimeStart = System.currentTimeMillis()

                val (actionObject, _, errorMessage) = operator.execute(
                    actionObjStr.toString(), infoPool, context, config
                )

                var actionExecTimeEnd = System.currentTimeMillis()

                if (actionObject == null) {
                    val taskEndTime = System.currentTimeMillis()
                    appendToFile(
                        taskLog,
                        """
                        {
                            "step": $iteration,
                            "operation": "finish",
                            "finish_flag": "abnormal",
                            "final_info_pool": $infoPool,
                            "task_duration": ${(taskEndTime - taskStartTime) / 1000} seconds
                        }
                        """.trimIndent()
                    )
                    Log.w("MainActivity", "WARNING!!: Abnormal finishing: $actionObjStr")
                    return
                }


                infoPool.lastAction = actionObjStr.toString()
                infoPool.lastSummary = actionDesc.toString()

                appendToFile(taskLog, "{\n" +
                        "    \"step\": $iteration,\n" +
                        "    \"operation\": \"action\",\n" +
                        "    \"prompt_action\": \"$actionPrompt\",\n" +
                        "    \"raw_response\": \"$actionOutput\",\n" +
                        "    \"action_object\": \"${actionObject.toString()}\",\n" +
                        "    \"action_object_str\": \"${actionObjStr.toString()}\",\n" +
                        "    \"action_thought\": \"${actionThought.toString()}\",\n" +
                        "    \"action_description\": \"${actionDesc.toString()}\",\n" +
                        "    \"duration\": ${(actionThinkingTimeEnd - actionThinkingTimeStart) / 1000},\n" +
                        "    \"execution_duration\": ${(actionExecTimeEnd - actionExecTimeStart) / 1000}\n" +
                        "}")
                Log.d("MainActivity","Action Thought: ${actionThought.toString()}")
                Log.d("MainActivity","Action Description: ${actionDesc.toString()}")
                Log.d("MainActivity","Action: ${actionObjStr.toString()}")

                val perceptionPostStartTime = System.currentTimeMillis()
                postScreenshotFile = eyes.openEyes()
//               Step 4 : Take the Perception after the action by operator has been performed
                if (postScreenshotFile!= null && screenshotFile != null)
                {
                    val postPerceptionResult = retina.getPerceptionInfos(
                        context,
                        postScreenshotFile,
                        config
                    )
                    val perceptionPostEndTime = System.currentTimeMillis()
                    appendToFile(
                        taskLog,
                        "{step: $iteration, operation: perception_post, screenshot: post_screenshot, perception_infos: ${
                            postPerceptionResult.clickableInfos.forEach { (text, coordinates) ->
                                println("Text: $text, Coordinates: $coordinates \n")
                            }
                        }, xml_mode: ${config.isXmlMode}, duration: ${(perceptionPostEndTime - perceptionPostStartTime) / 1000} seconds}"
                    )


                    infoPool.perceptionInfosPost = postPerceptionResult.clickableInfos.toMutableList()
                    infoPool.keyboardPost = postPerceptionResult.keyboardOpen

                    // Set XML data if available
                    if (config.isXmlMode && postPerceptionResult.xmlData.isNotEmpty()) {
                        infoPool.perceptionInfosPostXML = postPerceptionResult.xmlData
                        Log.d("AgentTaskService", "Post-action XML data captured")
                    }
                }



//                Step5 The Reflector of our actions
                val reflectionStartTime = System.currentTimeMillis()
                val reflectionPrompt = actionReflector.getPrompt(infoPool, config)
                val reflectionChat = actionReflector.initChat()
                val reflectionCombinedChat = VisionHelper.createPrePostChatResponse(
                    "user",
                    reflectionPrompt,
                    reflectionChat,
                    config,
                    screenshotFile,
                    postScreenshotFile
                )

                // Sending to the GEMINI
                val reflectionLLMOutput =
                    getReasoningModelApiResponse(reflectionCombinedChat, apiKey = config.apiKey)
                val parsedReflection = actionReflector.parseResponse(reflectionLLMOutput.toString())

                val outcome = parsedReflection["outcome"].toString()
                val errorDescription = parsedReflection["error_description"].toString()
                val progressStatus = parsedReflection["progress_status"].toString()

                TipsStorageManager.appendTips(applicationContext, parsedReflection["tips"].toString())
                infoPool.tips = TipsStorageManager.getLocalTips(applicationContext)
                Log.d("AgentTaskService", infoPool.tips)

                infoPool.progressStatusHistory.add(progressStatus)
                val actionReflectionEndTime = System.currentTimeMillis()

                var actionOutcome: String
                var currentErrorDescription = errorDescription
                speechCoordinator.speakText("Outcome was $outcome")
                when {
                    "A" in outcome -> { // Successful. The result of the last action meets the expectation.
                        actionOutcome = "A"
                    }
                    "B" in outcome -> { // Failed. The last action results in a wrong page. I need to return to the previous state.
                        actionOutcome = "B"
                        // NOTE: removing the automatic backing; always stopping at the failed state and then there will be a new perception step
                        // no automatic backing
                        // check how many backs to take
                        val name = (actionObject["name"] as String).trim()
                        val arguments = actionObject["arguments"] as Map<*, *>

                        val actionName = actionObject["name"] // Assuming actionObject is a JSONObject
                        if (atomicActionSignatures.containsKey(actionName)) {


                                            if (errorMessage != null) {
                    currentErrorDescription += "; Error occurred while executing the action: $errorMessage"
                }
                        } else {
                            throw IllegalArgumentException("Invalid action name: $actionName")
                        }
                    }
                    "C" in outcome -> { // Failed. The last action produces no changes.
                        actionOutcome = "C"
                        //TODO: Add currentErrorDescription
                    }
                    else -> {
                        throw IllegalArgumentException("Invalid outcome: $outcome")
                    }
                }
                infoPool.actionHistory.add(actionObjStr.toString())
                infoPool.summaryHistory.add(actionDesc.toString())
                infoPool.actionOutcomes.add(actionOutcome)
                infoPool.errorDescriptions.add(currentErrorDescription)
                infoPool.progressStatus = progressStatus

                appendToFile(taskLog, "{\n" +
                        "    \"step\": $iteration,\n" +
                        "    \"operation\": \"action_reflection\",\n" +
                        "    \"prompt_action_reflect\": \"$reflectionPrompt\",\n" +
                        "    \"raw_response\": \"$reflectionLLMOutput\",\n" +
                        "    \"outcome\": \"$outcome\",\n" +
                        "    \"error_description\": \"$errorDescription\",\n" +
                        "    \"progress_status\": \"$progressStatus\",\n" +
                        "    \"duration\": ${(actionReflectionEndTime - reflectionStartTime) / 1000}\n" +
                        "}")

                Log.d("MainActivity","Outcome: $actionOutcome")
                Log.d("MainActivity","Progress Status: $progressStatus")
                Log.d("MainActivity","Error Description: $errorDescription")

                screenshotFile = postScreenshotFile
                infoPool.keyboardPre = infoPool.keyboardPost
                infoPool.perceptionInfosPre = infoPool.perceptionInfosPost
                infoPool.perceptionInfosPreXML = infoPool.perceptionInfosPostXML
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