package com.blurr.voice.api

import android.content.Context
import com.blurr.voice.agent.v1.AgentConfig
import com.blurr.voice.agent.v1.ClickableInfo
import org.json.JSONArray
import org.json.JSONObject
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi

class Retina(
    private val eyes: Eyes,
) {

    private fun extractJsonArray(text: String): List<JSONObject> {
        val regex = Regex("""\[\s*\{.*?\}\s*]""", RegexOption.DOT_MATCHES_ALL)
        val match = regex.find(text) ?: return emptyList()
        val array = JSONArray(match.value)
        return List(array.length()) { array.getJSONObject(it) }
    }

    fun sanitizeJson(json: String): String {
        var cleanJson = json

        // Remove trailing commas before object/array close
        cleanJson = cleanJson.replace(Regex(",\\s*([}\\]])"), "$1")

        // Remove dangling commas before closing brackets of arrays
        cleanJson = cleanJson.replace(Regex(",\\s*]"), "]")

        // Remove dangling commas before closing brackets of objects
        cleanJson = cleanJson.replace(Regex(",\\s*\\}"), "}")

        // Remove invalid commas after top-level object or array
        cleanJson = cleanJson.trim().removeSuffix(",")

        return cleanJson
    }

    private val boundingBoxSystemInstructionsv3 = """
        You are an expert at analyzing Android app screenshots. Your task is to extract clickable or recognizable UI elements such as:

        - App icons, logos (e.g., YouTube logo)
        - Buttons, toggles, and input fields
        - Icons (e.g., search, mic, share, play)
        - Text elements (titles, labels, placeholders)

        Return a **valid JSON array** where each element is a JSON object with:
        - "label": a short string (e.g., "search icon", "login button", "YouTube logo")
        - "box_2d": an array of 4 numbers [ymin, xmin, ymax, xmax] in 1000-scale relative coordinates

        Important formatting instructions:
        - The response MUST be a valid JSON array, and ONLY a JSON array.
        - DO NOT include any text outside the JSON (no comments, explanations, or formatting).
        - DO NOT use markdown, backticks, or ellipsis (`...`) anywhere in the response.
        - Ensure proper JSON syntax: use double quotes for all strings, and no trailing commas.

        To help identify the search function, always label magnifying glass icons as `"search icon"`.

        Examples of valid output format:

        [
          {
            "label": "search icon",
            "box_2d": [100, 200, 180, 260]
          },
          {
            "label": "login button",
            "box_2d": [500, 100, 580, 900]
          }
        ]

        Only output such a JSON array. Any extra formatting or explanation will cause failure in parsing.
        """.trimIndent()

    @RequiresApi(Build.VERSION_CODES.R)
    suspend fun getPerceptionInfos(context: Context?, bitmap: Bitmap, config: AgentConfig): PerceptionResult {

        val width = bitmap.width
        val height = bitmap.height
        val clickableInfos = mutableListOf<ClickableInfo>()

        // Only perform visual analysis if NOT in XML mode (i.e., in screenshot mode)
//        if (!config.isXmlMode) {
//            Log.d("Retina", "Performing visual analysis (screenshot mode)")
//            // Step 4: Send request to Gemini
//            val responseText = GeminiApi.generateContent(boundingBoxSystemInstructionsv3, listOf(bitmap), context = context)
//            val sanitizedJson = sanitizeJson(responseText.toString())
//
//            val boxes = extractJsonArray(sanitizedJson)
//            boxes.mapNotNull { obj ->
//                try {
//                    val box = obj.getJSONArray("box_2d")
//                    val label = obj.getString("label")
//                    val ymin = box.getDouble(0) / 1000 * height
//                    val xmin = box.getDouble(1) / 1000 * width
//                    val ymax = box.getDouble(2) / 1000 * height
//                    val xmax = box.getDouble(3) / 1000 * width
//                    val centerX = ((xmin + xmax) / 2).toInt()
//                    val centerY = ((ymin + ymax) / 2).toInt()
//                    clickableInfos.add(ClickableInfo("icon: $label", centerX to centerY))
//                } catch (e: Exception) {
//                    null
//                }
//            }
//        }

        val keyboardOpen = eyes.getKeyBoardStatus()

        // Handle XML mode if needed
        var xmlData = ""
        if (config.isXmlMode) {
            Log.d("Retina", "Skipping visual analysis, using XML mode only")
            try {
                xmlData = eyes.openPureXMLEyes()
                Log.d("Retina", "XML data captured for XML mode")
            } catch (e: Exception) {
                Log.e("Retina", "Failed to capture XML data", e)
                xmlData = "<hierarchy error=\"XML capture failed\"/>"
            }
        } else {
            Log.d("Retina", "No visual analysis performed (XML mode)")
        }

        return PerceptionResult(clickableInfos, width, height, keyboardOpen, xmlData)
    }
}



/**
 * Comprehensive perception result that includes both visual and XML data
 */
data class PerceptionResult(
    val clickableInfos: List<ClickableInfo>,
    val width: Int,
    val height: Int,
    val keyboardOpen: Boolean,
    val xmlData: String = ""
)
