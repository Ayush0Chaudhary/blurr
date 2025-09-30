package com.blurr.voice.triggers.ui

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.blurr.voice.R

data class AppInfo(
    val appName: String,
    val packageName: String,
    val icon: Drawable
)

class AppAdapter(
    private val apps: List<AppInfo>,
    private val onClick: (AppInfo) -> Unit
) : RecyclerView.Adapter<AppAdapter.AppViewHolder>() {

    private var selectedPosition = RecyclerView.NO_POSITION

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val app = apps[position]
        holder.bind(app, position == selectedPosition)
    }

    override fun getItemCount(): Int = apps.size

    inner class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val appIconImageView: ImageView = itemView.findViewById(R.id.appIconImageView)
        private val appNameTextView: TextView = itemView.findViewById(R.id.appNameTextView)

        fun bind(app: AppInfo, isSelected: Boolean) {
            appIconImageView.setImageDrawable(app.icon)
            appNameTextView.text = app.appName
            itemView.setBackgroundResource(if (isSelected) R.color.purple_200 else android.R.color.transparent)

            itemView.setOnClickListener {
                notifyItemChanged(selectedPosition)
                selectedPosition = bindingAdapterPosition
                notifyItemChanged(selectedPosition)
                onClick(app)
            }
        }
    }
}
