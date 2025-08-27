package com.blurr.voice.agent.v1.tips

import com.blurr.voice.agent.v1.AgentConfig
import com.blurr.voice.agent.v1.BaseAgent
import com.blurr.voice.agent.v1.InfoPool
import com.google.ai.client.generativeai.type.TextPart

class ExperienceRetrieverTips : BaseAgent() {
    override fun initChat(): List<Pair<String, List<TextPart>>>  {
        val systemPrompt = "You are a helpful AI assistant specializing in mobile phone operations. Your goal is to select relevant tips from previous experience to the current task."
        return listOf("user" to listOf(TextPart(systemPrompt)))
    }

    override fun getPrompt(infoPool: InfoPool,config: AgentConfig): String {
        var prompt = "### Existing Tips from Past Experience ###\n"
        prompt += "${infoPool.tips}\n\n"

        prompt += "\n"
        prompt += "### Current Task ###\n"
        prompt += "${infoPool.instruction}\n\n"

        prompt += "---\n"
        prompt += "Carefully examine the information provided above to pick the tips that can be helpful to the current task. Remove tips that are irrelevant to the current task.\n"

        prompt += "Provide your output in the following format:\n\n"

        prompt += "### Selected Tips ###\n"
        prompt += "Tips that are generally useful and relevant to the current task. Feel free to reorganize the bullets. If there are no relevant tips, put \"None\" here.\n"

        return prompt
    }

    override fun parseResponse(response: String): Map<String, String> {
        val selectedTips = response.split("### Selected Tips ###").last().replace("\n", " ").replace("  ", " ").trim()
        return mapOf("selected_tips" to selectedTips)
    }
}
