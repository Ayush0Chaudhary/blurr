package com.blurr.voice.agent.v1

import com.blurr.voice.crawler.UIElementWithId

data class ClickableInfo(
    val text: String,
    val coordinates: Pair<Int, Int>
)

data class InfoPool(
    var instruction: String = "",

    var width: Int = 1080,
    var height: Int = 2340,
    var perceptionInfosPre: MutableList<ClickableInfo> = mutableListOf(),
    var perceptionInfosPost: MutableList<ClickableInfo> = mutableListOf(),
    var perceptionInfosPreXML: String = "",
    var perceptionInfosPostXML: String = "",
    var screenShotPre: String = "",
    var screenShotPost: String = "",

    var reflectionPreActionXML: String = "",
    var reflectionPostActionXML: String = "",

    var summaryHistory: MutableList<String> = mutableListOf(),
    var actionHistory: MutableList<String> = mutableListOf(),
    var actionOutcomes: MutableList<String> = mutableListOf(),
    var errorDescriptions: MutableList<String> = mutableListOf(),

    var lastSummary: String = "",
    var lastAction: String = "",
    var lastActionThought: String = "",
    var importantNotes: String = "",
    var tips: String = "",
    var errorFlagPlan: Boolean = false,
    var errorDescriptionPlan: Boolean = false,

    var plan: String = "",
    var progressStatus: String = "",
    var progressStatusHistory: MutableList<String> = mutableListOf(),
    var finishThought: String = "",
    var currentSubgoal: String = "",
    var prevSubgoal: String = "",
    var errToManagerThresh: Int = 2,
    var keyboardPre: Boolean = false,
    var keyboardPost: Boolean = false,

    var futureTasks: MutableList<String> = mutableListOf(),

    var recalledMemories: String = "",
    
    // Store current UI elements with their IDs and coordinates
    var currentElementsWithIds: MutableList<UIElementWithId> = mutableListOf(),
    
    // Store markdown formatted elements for pre and post perception
    var perceptionInfosPreMarkdown: String = "",
    var perceptionInfosPostMarkdown: String = ""
)



data class AtomicAction(
    val name: String,
    val argumentsMap: Map<String, String> = emptyMap()
)
