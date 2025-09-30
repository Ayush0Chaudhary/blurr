/**
 * @file TriggerAdapter.kt
 * @brief Defines a RecyclerView adapter for displaying and managing a list of triggers.
 *
 * This file contains the `TriggerAdapter` class, which is responsible for binding `Trigger`
 * data to the views in a RecyclerView. It handles user interactions like enabling/disabling,
 * editing, and deleting triggers.
 */
package com.blurr.voice.triggers.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.RecyclerView
import com.blurr.voice.R
import com.blurr.voice.triggers.Trigger
import com.blurr.voice.triggers.TriggerType
import java.util.Locale

/**
 * A [RecyclerView.Adapter] for displaying a list of [Trigger] objects.
 *
 * This adapter provides views for each trigger in the list and handles callbacks for
 * user actions such as toggling the enabled state, deleting, or editing a trigger.
 *
 * @param triggers The initial list of [Trigger] objects to display.
 * @param onCheckedChange A callback function invoked when a trigger's enabled switch is toggled.
 * @param onDeleteClick A callback function invoked when the delete button for a trigger is clicked.
 * @param onEditClick A callback function invoked when the edit button for a trigger is clicked.
 */
class TriggerAdapter(
    private val triggers: MutableList<Trigger>,
    private val onCheckedChange: (Trigger, Boolean) -> Unit,
    private val onDeleteClick: (Trigger) -> Unit,
    private val onEditClick: (Trigger) -> Unit
) : RecyclerView.Adapter<TriggerAdapter.TriggerViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TriggerViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_trigger, parent, false)
        return TriggerViewHolder(view)
    }

    override fun onBindViewHolder(holder: TriggerViewHolder, position: Int) {
        val trigger = triggers[position]
        holder.bind(trigger)
    }

    override fun getItemCount(): Int = triggers.size

    /**
     * Updates the list of triggers displayed by the adapter.
     * @param newTriggers The new list of [Trigger] objects to display.
     */
    fun updateTriggers(newTriggers: List<Trigger>) {
        triggers.clear()
        triggers.addAll(newTriggers)
        notifyDataSetChanged()
    }

    /**
     * A [RecyclerView.ViewHolder] for displaying a single trigger item.
     * It holds the views for the trigger's details and action buttons.
     * @param itemView The view for the item layout.
     */
    inner class TriggerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val instructionTextView: TextView = itemView.findViewById(R.id.triggerInstructionTextView)
        private val timeTextView: TextView = itemView.findViewById(R.id.triggerTimeTextView)
        private val enabledSwitch: SwitchCompat = itemView.findViewById(R.id.triggerEnabledSwitch)
        private val deleteButton: android.widget.ImageButton = itemView.findViewById(R.id.deleteTriggerButton)
        private val editButton: android.widget.ImageButton = itemView.findViewById(R.id.editTriggerButton)

        /**
         * Binds a [Trigger] object to the views in the ViewHolder.
         * Sets the instruction, trigger details, and click/change listeners.
         * @param trigger The [Trigger] to display.
         */
        fun bind(trigger: Trigger) {
            instructionTextView.text = trigger.instruction

            deleteButton.setOnClickListener {
                onDeleteClick(trigger)
            }

            editButton.setOnClickListener {
                onEditClick(trigger)
            }

            when (trigger.type) {
                TriggerType.SCHEDULED_TIME -> {
                    timeTextView.text = String.format(
                        Locale.getDefault(),
                        "At %02d:%02d",
                        trigger.hour ?: 0,
                        trigger.minute ?: 0
                    )
                }
                TriggerType.NOTIFICATION -> {
                    timeTextView.text = "On notification from ${trigger.appName}"
                }

                TriggerType.CHARGING_STATE -> {
                    timeTextView.text = "On charging state: ${trigger.chargingStatus}"
                }
            }

            // Set listener to null before changing checked state to prevent infinite loops.
            enabledSwitch.setOnCheckedChangeListener(null)
            enabledSwitch.isChecked = trigger.isEnabled
            enabledSwitch.setOnCheckedChangeListener { _, isChecked ->
                onCheckedChange(trigger, isChecked)
            }
        }
    }
}
