/**
 * @file Action.kt
 * @brief Defines all possible actions the agent can execute, their specifications, and the logic for serialization.
 *
 * This file is the single source of truth for the agent's capabilities. It uses a sealed class `Action`
 * to represent all possible commands in a type-safe manner. A custom `ActionSerializer` handles
 * the deserialization from the LLM's JSON output. The `companion object` contains a registry
 * (`allSpecs`) that defines the name, description, parameters, and construction logic for each action,
 * ensuring consistency between the prompt and the code.
 */
package com.blurr.voice.v2.actions

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlin.reflect.KClass

/**
 * Represents the specification for a single parameter of an action.
 * @property name The name of the parameter (used in JSON).
 * @property type The Kotlin type of the parameter.
 * @property description A natural language description of the parameter for the LLM.
 */
data class ParamSpec(val name: String, val type: KClass<*>, val description: String)

/**
 * A sealed class representing all possible type-safe commands the agent can execute.
 *
 * Each subclass represents a distinct action the agent can perform, such as tapping an element,
 * typing text, or indicating that a task is complete. It uses a custom [ActionSerializer]
 * to deserialize the JSON output from the LLM into the correct, type-safe action object.
 */
@Serializable(with = Action.ActionSerializer::class)
sealed class Action {
    /** Press and hold a UI element, identified by its numeric ID. */
    data class LongPressElement(val elementId: Int) : Action()
    /** Tap a UI element, identified by its numeric ID. */
    data class TapElement(val elementId: Int) : Action()
    /** Open the Android app switcher. */
    data object SwitchApp : Action()
    /** Navigate to the previous screen. */
    data object Back : Action()
    /** Navigate to the device's home screen. */
    data object Home : Action()
    /** Pause execution for a short period, allowing UI to load. */
    data object Wait : Action()
    /** Speak a message to the user via Text-to-Speech. */
    data class Speak(val message: String) : Action()
    /** Ask the user a question and wait for a spoken response. */
    data class Ask(val question: String) : Action()
    /** Open an application by its name. */
    data class OpenApp(val appName: String) : Action()
    /** Scroll down the screen by a specified amount in pixels. */
    data class ScrollDown(val amount: Int) : Action()
    /** Scroll up the screen by a specified amount in pixels. */
    data class ScrollUp(val amount: Int) : Action()
    /** Perform a search on Google. */
    data class SearchGoogle(val query: String) : Action()
    /** A composite action to tap an element, input text, and press enter. */
    data class TapElementInputTextPressEnter(val index: Int, val text: String) : Action()
    /** Type text into the currently focused input field. */
    data class InputText(val text: String) : Action()
    /** Write content to a file in the agent's virtual file system, overwriting existing content. */
    data class WriteFile(val fileName: String, val content: String) : Action()
    /** Append content to the end of a file in the agent's virtual file system. */
    data class AppendFile(val fileName: String, val content: String) : Action()
    /** Read the entire content of a file from the agent's virtual file system. */
    data class ReadFile(val fileName: String) : Action()
    /** Signal that the task is complete. */
    data class Done(val success: Boolean, val text: String, val filesToDisplay: List<String>? = null) : Action()
    /** Launch a system-level Android AppIntent by name with parameters. */
    data class LaunchIntent(val intentName: String, val parameters: Map<String, String>) : Action()

    /**
     * A custom serializer for the [Action] sealed class.
     *
     * This serializer is data-driven, using the `allSpecs` map as its single source of truth.
     * It inspects the incoming JSON to find the action name (e.g., "tap_element"), looks up its
     * specification in `allSpecs`, parses the parameters, and then uses the `build` function
     * from the spec to construct the correct, type-safe [Action] subclass instance.
     */
    object ActionSerializer : KSerializer<Action> {
        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Action")

        override fun serialize(encoder: Encoder, value: Action) {
            throw NotImplementedError("Serialization is not supported for this agent.")
        }

        override fun deserialize(decoder: Decoder): Action {
            val jsonInput = (decoder as JsonDecoder).decodeJsonElement().jsonObject
            val actionName = jsonInput.keys.first()
            val paramsJson = jsonInput[actionName]?.jsonObject

            // Look up the action's specification from the single source of truth.
            val spec = allSpecs[actionName]
                ?: throw IllegalArgumentException("Unknown action received from LLM: $actionName")

            val args = mutableMapOf<String, Any?>()

            // If the action has parameters, parse them according to its spec.
            paramsJson?.let {
                for (paramSpec in spec.params) {
                    val paramName = paramSpec.name
                    val jsonValue = it[paramName]
                        ?: continue // Allow optional parameters.

                    // Convert JSON element to the correct Kotlin type.
                    val value = when (paramSpec.type) {
                        Int::class -> jsonValue.jsonPrimitive.int
                        String::class -> jsonValue.jsonPrimitive.content
                        Boolean::class -> jsonValue.jsonPrimitive.boolean
                        List::class -> jsonValue.jsonArray.map { el -> el.jsonPrimitive.content }
                        Map::class -> jsonValue.jsonObject.mapValues { entry ->
                            // Coerce all map values to string for intent parameter passing.
                            entry.value.jsonPrimitive.content
                        }
                        else -> throw IllegalStateException("Unsupported parameter type in Spec: ${paramSpec.type}")
                    }
                    args[paramName] = value
                }
            }
            // Use the 'build' lambda from the spec to construct the final Action object.
            return spec.build(args)
        }
    }

    /**
     * The companion object serves as a central registry for all action definitions.
     * This "single source of truth" approach ensures that the action's name, description,
     * parameters, and construction logic are all defined in one place.
     */
    companion object {
        /**
         * Defines the complete specification for a single agent action.
         *
         * @property name The snake_case name of the action, as expected from the LLM.
         * @property description A natural language description of what the action does.
         * @property params A list of [ParamSpec] defining the action's parameters.
         * @property build A lambda function that constructs an instance of the action from a map of parsed arguments.
         */
        data class Spec(
            val name: String,
            val description: String,
            val params: List<ParamSpec>,
            val build: (args: Map<String, Any?>) -> Action
        )

        /**
         * The definitive map of all actions available to the agent.
         * The key is the snake_case action name that the LLM will use.
         * The value is the complete [Spec] for that action.
         */
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
            "swipe_down" to Spec(
                name = "swipe_down",
                description = "swipe down by the specified amount of pixels.",
                params = listOf(ParamSpec("amount", Int::class, "Amount of pixels to swipe down.")),
                build = { args -> ScrollDown(args["amount"] as Int) }
            ),
            "long_press_element" to Spec(
                name = "long_press_element",
                description = "Press and hold the element with the specified numeric ID. Useful for context menus, selecting text, etc.",
                params = listOf(ParamSpec("element_id", Int::class, "The numeric ID of the element to long press.")),
                build = { args -> LongPressElement(args["element_id"] as Int) }
            ),
            "swipe_up" to Spec(
                name = "swipe_up",
                description = "swipe up by the specified amount of pixels.",
                params = listOf(ParamSpec("amount", Int::class, "Amount of pixels to swipe up.")),
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

        /**
         * Returns a collection of all action specifications.
         * This is used to generate the list of available actions for the system prompt.
         */
        fun getAllSpecs(): Collection<Spec> {
            return allSpecs.values
        }
    }
}