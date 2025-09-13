package com.blurr.voice.triggers.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.RecyclerView
import com.blurr.voice.R
import com.blurr.voice.triggers.Trigger
import com.blurr.voice.triggers.TriggerType
import java.util.Locale

class TriggerAdapter(
    private val triggers: MutableList<Trigger>,
    private val onCheckedChange: (Trigger, Boolean) -> Unit,
    private val onDeleteClick: (Trigger) -> Unit
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

    fun updateTriggers(newTriggers: List<Trigger>) {
        triggers.clear()
        triggers.addAll(newTriggers)
        notifyDataSetChanged()
    }

    inner class TriggerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val instructionTextView: TextView = itemView.findViewById(R.id.triggerInstructionTextView)
        private val timeTextView: TextView = itemView.findViewById(R.id.triggerTimeTextView)
        private val enabledSwitch: SwitchCompat = itemView.findViewById(R.id.triggerEnabledSwitch)
        private val deleteButton: ImageButton = itemView.findViewById(R.id.deleteTriggerButton)

        fun bind(trigger: Trigger) {
            instructionTextView.text = trigger.instruction

            deleteButton.setOnClickListener {
                onDeleteClick(trigger)
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
            }

            enabledSwitch.setOnCheckedChangeListener(null)
            enabledSwitch.isChecked = trigger.isEnabled
            enabledSwitch.setOnCheckedChangeListener { _, isChecked ->
                onCheckedChange(trigger, isChecked)
            }
        }
    }
}
