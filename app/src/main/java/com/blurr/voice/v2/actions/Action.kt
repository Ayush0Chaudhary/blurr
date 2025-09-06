package com.blurr.voice.v2.actions

import com.blurr.voice.agent.v1.AgentConfig
import com.blurr.voice.agent.v1.InfoPool
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import kotlin.reflect.KClass

// Data class to hold parameter metadata without reflection
data class ParamSpec(val name: String, val type: KClass<*>, val description: String)

/** 
 * A sealed class representing all possible type-safe commands the agent can execute.
 * It is annotated to use the custom, data-driven ActionSerializer.
 */
@Serializable(with = Action.ActionSerializer::class)
sealed class Action {
    // Each action is a data class (if it has args) or an object (if it doesn't).
    // Note: Property names here follow Kotlin's camelCase convention.
    data class TapElement(val elementId: Int) : Action()
    data object SwitchApp : Action()
    data object Back : Action()
    data object Home : Action()
    data object Wait : Action()
    data class Speak(val message: String) : Action()
    data class Ask(val question: String) : Action()
    data class OpenApp(val appName: String) : Action()
    data class ScrollDown(val amount: Int) : Action()
    data class ScrollUp(val amount: Int) : Action()
    data class SearchGoogle(val query: String) : Action()
    data class TapElementInputTextPressEnter(val index: Int, val text: String) : Action()
    data class InputText(val text: String) : Action()
    data class WriteFile(val fileName: String, val content: String) : Action()
    data class AppendFile(val fileName: String, val content: String) : Action()
    data class ReadFile(val fileName: String) : Action()
    data class Done(val success: Boolean, val text: String, val filesToDisplay: List<String>? = null) : Action()
    // New: Launch an Android AppIntent by name with parameters
    data class LaunchIntent(val intentName: String, val parameters: Map<String, String>) : Action()

    // --- The Custom Serializer ---
    // This serializer is now data-driven, using the `allSpecs` map as its source of truth.
    object ActionSerializer : KSerializer<Action> {
        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Action")

        override fun serialize(encoder: Encoder, value: Action) {
            throw NotImplementedError("Serialization is not supported for this agent.")
        }

        override fun deserialize(decoder: Decoder): Action {
            val jsonInput = (decoder as JsonDecoder).decodeJsonElement().jsonObject
            val actionName = jsonInput.keys.first()
            val paramsJson = jsonInput[actionName]?.jsonObject

            // Look up the action's specification from our single source of truth.
            val spec = allSpecs[actionName]
                ?: throw IllegalArgumentException("Unknown action received from LLM: $actionName")

            val args = mutableMapOf<String, Any?>()

            // If the action has parameters, parse them according to the spec.
            paramsJson?.let {
                for (paramSpec in spec.params) {
                    val paramName = paramSpec.name
                    val jsonValue = it[paramName]
                        ?: continue // Allow optional parameters

                    // Convert JSON element to the correct Kotlin type.
                    val value = when (paramSpec.type) {
                        Int::class -> jsonValue.jsonPrimitive.int
                        String::class -> jsonValue.jsonPrimitive.content
                        Boolean::class -> jsonValue.jsonPrimitive.boolean
                        List::class -> jsonValue.jsonArray.map { el -> el.jsonPrimitive.content }
                        Map::class -> jsonValue.jsonObject.mapValues { entry ->
                            // We coerce all values to string for intent parameter passing
                            entry.value.jsonPrimitive.content
                        }
                        else -> throw IllegalStateException("Unsupported parameter type in Spec: ${paramSpec.type}")
                    }
                    args[paramName] = value
                }
            }
            // Use the 'build' lambda from the spec to construct the final, type-safe Action object.
            return spec.build(args)
        }
    }

    // --- Companion Object: The Registry and Single Source of Truth ---
    companion object {
        data class Spec(
            val name: String,
            val description: String,
            val params: List<ParamSpec>,
            val build: (args: Map<String, Any?>) -> Action
        )

        // The single source of truth for all actions.
        // Keys and names are now consistently in snake_case for the LLM.
        private val allSpecs: Map<String, Spec> = mapOf(
            "tap_element" to Spec(
                name = "tap_element",
                description = "Tap the element with the specified numeric ID.",
                params = listOf(ParamSpec("element_id", Int::class, "The numeric ID of the element.")),
                build = { args -> TapElement(args["element_id"] as Int) }
            ),
            "switch_app" to Spec("switch_app", "Show the App switcher.", emptyList()) { SwitchApp },
            "back" to Spec("back", "Go back to the previous screen.", emptyList()) { Back },
            "home" to Spec("home", "Go to the device's home screen.", emptyList()) { Home },
            "wait" to Spec("wait", "Wait for a few seconds for loading.", emptyList()) { Wait },
            "speak" to Spec(
                name = "speak",
                description = "Speak the 'message' to the user.",
                params = listOf(ParamSpec("message", String::class, "The message to speak.")),
                build = { args -> Speak(args["message"] as String) }
            ),
            "ask" to Spec(
                name = "ask",
                description = "Ask the 'question' to the user and await a response.",
                params = listOf(ParamSpec("question", String::class, "The question to ask.")),
                build = { args -> Ask(args["question"] as String) }
            ),
            "open_app" to Spec(
                name = "open_app",
                description = "Open the app named 'app_name'.",
                params = listOf(ParamSpec("app_name", String::class, "The name of the app.")),
                build = { args -> OpenApp(args["app_name"] as String) }
            ),
            "scroll_down" to Spec(
                name = "scroll_down",
                description = "Scroll down by the specified amount of pixels.",
                params = listOf(ParamSpec("amount", Int::class, "Amount of pixels to scroll down.")),
                build = { args -> ScrollDown(args["amount"] as Int) }
            ),
            "scroll_up" to Spec(
                name = "scroll_up",
                description = "Scroll up by the specified amount of pixels.",
                params = listOf(ParamSpec("amount", Int::class, "Amount of pixels to scroll up.")),
                build = { args -> ScrollUp(args["amount"] as Int) }
            ),
            "search_google" to Spec(
                name = "search_google",
                description = "Search Google with the specified query.",
                params = listOf(ParamSpec("query", String::class, "The search query to perform on Google")),
                build = { args -> SearchGoogle(args["query"] as String) }
            ),
            "tap_element_input_text_and_enter" to Spec(
                name = "tap_element_input_text_and_enter",
                description = "Taps an element, inputs text, and presses enter. Useful for search bars.",
                params = listOf(
                    ParamSpec("index", Int::class, "The numerical index of the input element."),
                    ParamSpec("text", String::class, "The text to be typed into the element.")
                ),
                build = { args -> TapElementInputTextPressEnter(args["index"] as Int, args["text"] as String) }
            ),
            "done" to Spec(
                name = "done",
                description = "Completes the current task.",
                params = listOf(
                    ParamSpec("success", Boolean::class, "True if the task was completed successfully, False otherwise."),
                    ParamSpec("text", String::class, "A summary of the results or a final message for the user."),
                    ParamSpec("files_to_display", List::class, "A list of filenames (e.g., ['report.pdf']) to show the user.")
                ),
                build = { args ->
                    @Suppress("UNCHECKED_CAST")
                    Done(
                        args["success"] as Boolean,
                        args["text"] as String,
                        args["files_to_display"] as? List<String>
                    )
                }
            ),
            "write_file" to Spec(
                name = "write_file",
                description = "Write content to a file, overwriting existing content.",
                params = listOf(
                    ParamSpec("file_name", String::class, "The name of the file (e.g., 'notes.txt')."),
                    ParamSpec("content", String::class, "The content to write to the file.")
                ),
                build = { args -> WriteFile(args["file_name"] as String, args["content"] as String) }
            ),
            "append_file" to Spec(
                name = "append_file",
                description = "Append content to the end of a file.",
                params = listOf(
                    ParamSpec("file_name", String::class, "The name of the file to append to."),
                    ParamSpec("content", String::class, "The content to append.")
                ),
                build = { args -> AppendFile(args["file_name"] as String, args["content"] as String) }
            ),
            "read_file" to Spec(
                name = "read_file",
                description = "Read the entire content of a file.",
                params = listOf(ParamSpec("file_name", String::class, "The name of the file to read.")),
                build = { args -> ReadFile(args["file_name"] as String) }
            ),
            "type" to Spec(
                name = "type",
                description = "Type text into a focused input field.",
                params = listOf(ParamSpec("text", String::class, "The text to type.")),
                build = { args -> InputText(args["text"] as String) }
            ),
            // New action spec: launch_intent
            "launch_intent" to Spec(
                name = "launch_intent",
                description = "Launch an Android AppIntent by name with parameters. Use this for OS-level actions like Dial, Share, etc.",
                params = listOf(
                    ParamSpec("intent_name", String::class, "The name of the intent to launch (see intents catalog)."),
                    ParamSpec("parameters", Map::class, "A map of parameter names to their string values as required by the intent.")
                ),
                build = { args ->
                    @Suppress("UNCHECKED_CAST")
                    LaunchIntent(
                        intentName = args["intent_name"] as String,
                        parameters = args["parameters"] as? Map<String, String> ?: emptyMap()
                    )
                }
            ),
        )

//        /**
//         * Returns the specifications for all actions, respecting the agent's configuration.
//         */
//        fun getAvailableSpecs(config: AgentConfig, infoPool: InfoPool): List<Spec> {
//            return allSpecs.values.filter { spec ->
//                when (spec.name) {
//                    "open_app" -> config.enableDirectAppOpening
//                    "type" -> infoPool.keyboardPre
//                    else -> true
//                }
//            }
//        }

        // Add this function inside the companion object in v2/actions/Action.kt

        fun getAllSpecs(): Collection<Spec> {
            return allSpecs.values
        }
    }
}