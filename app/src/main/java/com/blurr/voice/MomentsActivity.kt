package com.blurr.voice

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.blurr.voice.data.TaskHistoryItem
import com.blurr.voice.utilities.Logger
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MomentsActivity : BaseNavigationActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: LinearLayout
    private lateinit var adapter: MomentsAdapter
    private lateinit var clearMomentsButton: Button
    private val db = Firebase.firestore
    private val auth = Firebase.auth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_moments_content)

        // Initialize views
        recyclerView = findViewById(R.id.task_history_recycler_view)
        emptyState = findViewById(R.id.empty_state)
        clearMomentsButton = findViewById(R.id.clear_moments_button)

        // Setup RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = MomentsAdapter(emptyList())
        recyclerView.adapter = adapter

        // Load task history
        loadTaskHistory()

        // Setup Clear Button
        clearMomentsButton.setOnClickListener {
            showClearConfirmationDialog()
        }
    }

    private fun loadTaskHistory() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            showEmptyState()
            return
        }

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val document = db.collection("users").document(currentUser.uid).get().await()
                if (document.exists()) {
                    val taskHistoryData = document.get("taskHistory") as? List<Map<String, Any>>
                    if (taskHistoryData != null && taskHistoryData.isNotEmpty()) {
                        val taskHistory = taskHistoryData.mapNotNull { taskData ->
                            try {
                                TaskHistoryItem(
                                    task = taskData["task"] as? String ?: "",
                                    status = taskData["status"] as? String ?: "",
                                    startedAt = taskData["startedAt"] as? Timestamp,
                                    completedAt = taskData["completedAt"] as? Timestamp,
                                    success = taskData["success"] as? Boolean,
                                    errorMessage = taskData["errorMessage"] as? String
                                )
                            } catch (e: Exception) {
                                Logger.e("MomentsActivity", "Error parsing task history item", e)
                                null
                            }
                        }

                        // Sort by startedAt in descending order (most recent first)
                        val sortedTaskHistory = taskHistory.sortedByDescending {
                            it.startedAt?.toDate() ?: java.util.Date(0)
                        }

                        if (sortedTaskHistory.isNotEmpty()) {
                            showTaskHistory(sortedTaskHistory)
                        } else {
                            showEmptyState()
                        }
                    } else {
                        showEmptyState()
                    }
                } else {
                    showEmptyState()
                }
            } catch (e: Exception) {
                Logger.e("MomentsActivity", "Error loading task history", e)
                showEmptyState()
            }
        }
    }

    private fun showTaskHistory(taskHistory: List<TaskHistoryItem>) {
        adapter = MomentsAdapter(taskHistory)
        recyclerView.adapter = adapter
        recyclerView.visibility = View.VISIBLE
        emptyState.visibility = View.GONE
        clearMomentsButton.visibility = View.VISIBLE
    }

    private fun showEmptyState() {
        recyclerView.visibility = View.GONE
        emptyState.visibility = View.VISIBLE
        clearMomentsButton.visibility = View.GONE
    }

    private fun showClearConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Clear All Moments")
            .setMessage("Are you sure you want to permanently delete all task history? This action cannot be undone.")
            .setPositiveButton("Clear All") { dialog, _ ->
                clearMoments()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun clearMoments() {
        val currentUser = auth.currentUser ?: return

        // Update the 'taskHistory' field in Firestore to an empty list
        db.collection("users").document(currentUser.uid)
            .update("taskHistory", emptyList<Any>())
            .addOnSuccessListener {
                android.util.Log.d("MomentsActivity", "Successfully cleared moments.")
                Toast.makeText(this, "Moments cleared", Toast.LENGTH_SHORT).show()
                showEmptyState() // Refresh the UI to show the empty state
            }
            .addOnFailureListener { e ->
                android.util.Log.e("MomentsActivity", "Error clearing moments", e)
                Toast.makeText(this, "Error: Could not clear moments.", Toast.LENGTH_SHORT).show()
            }
    }

    override fun getContentLayoutId(): Int = R.layout.activity_moments_content

    override fun getCurrentNavItem(): BaseNavigationActivity.NavItem = BaseNavigationActivity.NavItem.MOMENTS
}