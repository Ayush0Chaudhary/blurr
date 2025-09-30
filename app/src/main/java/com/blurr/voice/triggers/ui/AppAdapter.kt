/**
 * @file AppAdapter.kt
 * @brief Defines a RecyclerView adapter for displaying a selectable list of installed applications.
 *
 * This file contains the `AppInfo` data class to hold application details and the `AppAdapter`
 * class, which is a `RecyclerView.Adapter` responsible for rendering the list of apps,
 * handling user selections, and providing filtering capabilities.
 */
package com.blurr.voice.triggers.ui

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.Filter
import android.widget.Filterable
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.blurr.voice.R
import java.util.Locale

/**
 * A data class to hold essential information about an installed application.
 *
 * @property appName The user-facing name of the application.
 * @property packageName The unique package name of the application.
 * @property icon The application's launcher icon as a [Drawable].
 */
data class AppInfo(
    val appName: String,
    val packageName: String,
    val icon: Drawable? = null
)

/**
 * A [RecyclerView.Adapter] for displaying a filterable and selectable list of applications.
 *
 * This adapter manages a list of [AppInfo] objects, allows the user to select multiple apps,
 * and provides a search filter based on the app name.
 *
 * @param apps The initial list of [AppInfo] objects to display.
 * @param onSelectionChanged A callback function that is invoked whenever the set of selected apps changes.
 */
class AppAdapter(
    private var apps: List<AppInfo>,
    private val onSelectionChanged: (List<AppInfo>) -> Unit
) : RecyclerView.Adapter<AppAdapter.AppViewHolder>(), Filterable {

    private var filteredApps: List<AppInfo> = apps
    private val selectedApps = mutableSetOf<AppInfo>()

    /**
     * Updates the list of apps displayed by the adapter.
     * @param newApps The new list of [AppInfo] to display.
     */
    fun updateApps(newApps: List<AppInfo>) {
        this.apps = newApps
        this.filteredApps = newApps
        notifyDataSetChanged()
    }

    /**
     * Sets the currently selected apps.
     * @param apps The list of [AppInfo] that should be marked as selected.
     */
    fun setSelectedApps(apps: List<AppInfo>) {
        selectedApps.clear()
        selectedApps.addAll(apps)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val app = filteredApps[position]
        holder.bind(app, selectedApps.contains(app))
    }

    override fun getItemCount(): Int = filteredApps.size

    /**
     * Returns a [Filter] for searching the list of applications by name.
     */
    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val charString = constraint?.toString()?.lowercase(Locale.getDefault()) ?: ""
                val filteredList = if (charString.isEmpty()) {
                    apps
                } else {
                    apps.filter {
                        it.appName.lowercase(Locale.getDefault()).contains(charString)
                    }
                }
                val filterResults = FilterResults()
                filterResults.values = filteredList
                return filterResults
            }

            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                filteredApps = results?.values as? List<AppInfo> ?: emptyList()
                notifyDataSetChanged()
            }
        }
    }

    /**
     * A [RecyclerView.ViewHolder] for displaying a single application item.
     * It holds the views for the app icon, name, and selection checkbox.
     * @param itemView The view for the item layout.
     */
    inner class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val appIconImageView: ImageView = itemView.findViewById(R.id.appIconImageView)
        private val appNameTextView: TextView = itemView.findViewById(R.id.appNameTextView)
        private val appCheckBox: CheckBox = itemView.findViewById(R.id.appCheckBox)

        /**
         * Binds an [AppInfo] object to the views in the ViewHolder.
         * Sets the app icon and name, and manages the state of the selection checkbox.
         *
         * @param app The [AppInfo] to display.
         * @param isSelected Whether the app is currently selected.
         */
        fun bind(app: AppInfo, isSelected: Boolean) {
            appIconImageView.setImageDrawable(app.icon)
            appNameTextView.text = app.appName
            appCheckBox.isChecked = isSelected

            itemView.setOnClickListener {
                if (selectedApps.contains(app)) {
                    selectedApps.remove(app)
                } else {
                    selectedApps.add(app)
                }
                notifyItemChanged(bindingAdapterPosition)
                onSelectionChanged(selectedApps.toList())
            }
        }
    }
}
