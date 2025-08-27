package com.blurr.voice.agent.v1.tips

import com.blurr.voice.agent.v1.AgentConfig
import com.blurr.voice.agent.v1.BaseAgent
import com.blurr.voice.agent.v1.InfoPool
import com.google.ai.client.generativeai.type.TextPart

class ReflectorTips : BaseAgent() {
    override fun initChat(): List<Pair<String, List<TextPart>>>  {
        val systemPrompt = "You are a helpful AI assistant specializing in mobile phone operations. Your goal is to reflect on past experiences and provide insights to improve future interactions."
        return listOf("user" to listOf(TextPart(systemPrompt)))
    }

    override fun getPrompt(infoPool: InfoPool, config: AgentConfig): String {
        var prompt = "### Current Task ###\n"
        prompt += "${infoPool.instruction}\n\n"

        prompt += "### Overall Plan ###\n"
        prompt += "${infoPool.plan}\n\n"

        prompt += "### Progress Status ###\n"
        prompt += "${infoPool.progressStatus}\n\n"

        prompt += "### Existing Tips from Past Experience ###\n"
        if (infoPool.tips.isNotEmpty()) {
            prompt += "${infoPool.tips}\n\n"
        } else {
            prompt += "No tips recorded.\n\n"
        }

        prompt += "### Full Action History ###\n"
        if (infoPool.actionHistory.isNotEmpty()) {
            val latestActions = infoPool.actionHistory
            val latestSummary = infoPool.summaryHistory
            val actionOutcomes = infoPool.actionOutcomes
            val errorDescriptions = infoPool.errorDescriptions
            val progressStatusHistory = infoPool.progressStatusHistory
            for (i in latestActions.indices) {
                val act = latestActions[i]
                val summ = latestSummary[i]
                val outcome = actionOutcomes[i]
                val errDes = errorDescriptions[i]
                val progress = progressStatusHistory[i]
                prompt += if (outcome == "A") {
                    "- Action: $act | Description: $summ | Outcome: Successful | Progress: $progress\n"
                } else {
                    "- Action: $act | Description: $summ | Outcome: Failed | Feedback: $errDes\n"
                }
            }
            prompt += "\n"
        } else {
            prompt += "No actions have been taken yet.\n\n"
        }

        if (infoPool.futureTasks.isNotEmpty()) {
            prompt += "---\n"
            // if the setting provides future tasks explicitly
            prompt += "### Future Tasks ###\n"
            prompt += "Here are some tasks that you might be asked to do in the future:\n"
            for (task in infoPool.futureTasks) {
                prompt += "- $task\n"
            }
            prompt += "\n"
        }

        prompt += "---\n"
        prompt += "Carefully reflect on the interaction history of the current task. Check if there are any general tips that might be useful for handling future tasks, such as advice on preventing certain common errors?\n\n"

        prompt += "Provide your output in the following format:\n\n"

        prompt += "### Updated Tips ###\n"
        prompt += "If you have any important new tips to add (not already included in the existing tips), combine them with the current list. If there are no new tips, simply copy the existing tips here. Keep your tips concise and general.\n"
        return prompt
    }

    override fun parseResponse(response: String): Map<String, String> {
        val updatedTips = response.split("### Updated Tips ###").last().replace("\n", " ").replace("  ", " ").trim()
        return mapOf("updated_tips" to updatedTips)
    }
}