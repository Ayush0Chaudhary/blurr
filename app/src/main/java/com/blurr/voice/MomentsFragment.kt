package com.blurr.voice

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.blurr.voice.data.TaskHistoryItem
import com.blurr.voice.utilities.Logger
import com.blurr.voice.v2.AgentService
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

class MomentsFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: LinearLayout
    private lateinit var adapter: MomentsAdapter
    private val db = Firebase.firestore
    private val auth = Firebase.auth

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_moments, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.task_history_recycler_view)
        emptyState = view.findViewById(R.id.empty_state)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        // Initialize adapter with callbacks for the menu actions
        adapter = MomentsAdapter(
            emptyList(),
            onRepeatClick = { taskInstruction -> repeatTask(taskInstruction) },
            onPinClick = { taskItem -> togglePin(taskItem) }
        )

        recyclerView.adapter = adapter

        loadTaskHistory()
    }

    private fun repeatTask(task: String) {
        Toast.makeText(requireContext(), "Repeating: $task", Toast.LENGTH_SHORT).show()
        AgentService.start(requireContext(), task)
    }

    private fun togglePin(item: TaskHistoryItem) {
        val currentUser = auth.currentUser ?: return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val docRef = db.collection("users").document(currentUser.uid)

                // 1. Get current data
                val snapshot = docRef.get().await()
                if (!snapshot.exists()) return@launch

                val rawHistory = snapshot.get("taskHistory") as? List<Map<String, Any>> ?: return@launch

                // 2. Find and update the specific item in the list
                val updatedHistory = rawHistory.map { map ->
                    val taskName = map["task"] as? String
                    val timestamp = map["startedAt"] as? Timestamp

                    // Match by task name and start time
                    if (taskName == item.task && timestamp == item.startedAt) {
                        val newMap = map.toMutableMap()
                        val currentPinState = map["isPinned"] as? Boolean ?: false
                        newMap["isPinned"] = !currentPinState
                        newMap
                    } else {
                        map
                    }
                }

                // 3. Write back to Firestore
                docRef.update("taskHistory", updatedHistory).await()

                withContext(Dispatchers.Main) {
                    val newPinState = !item.isPinned
                    val msg = if (newPinState) "Task pinned" else "Task unpinned"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    // Reload to update sort order
                    loadTaskHistory()
                }

            } catch (e: Exception) {
                Logger.e("MomentsFragment", "Error toggling pin", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to update pin", Toast.LENGTH_SHORT).show()
                }
            }
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
                                    // Generate temp ID if missing
                                    id = UUID.randomUUID().toString(),
                                    task = taskData["task"] as? String ?: "",
                                    status = taskData["status"] as? String ?: "",
                                    startedAt = taskData["startedAt"] as? Timestamp,
                                    completedAt = taskData["completedAt"] as? Timestamp,
                                    success = taskData["success"] as? Boolean,
                                    errorMessage = taskData["errorMessage"] as? String,
                                    // Default isPinned to false
                                    isPinned = taskData["isPinned"] as? Boolean ?: false
                                )
                            } catch (e: Exception) {
                                Logger.e("MomentsFragment", "Error parsing task history item", e)
                                null
                            }
                        }

                        // Sorting: Pinned items first, then by date (newest first)
                        val sortedTaskHistory = taskHistory.sortedWith(
                            compareByDescending<TaskHistoryItem> { it.isPinned }
                                .thenByDescending { it.startedAt?.toDate() ?: java.util.Date(0) }
                        )

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
                Logger.e("MomentsFragment", "Error loading task history", e)
                showEmptyState()
            }
        }
    }

    private fun showTaskHistory(taskHistory: List<TaskHistoryItem>) {
        // Re-initialize adapter with updated data
        adapter = MomentsAdapter(
            taskHistory,
            onRepeatClick = { taskInstruction -> repeatTask(taskInstruction) },
            onPinClick = { taskItem -> togglePin(taskItem) }
        )
        recyclerView.adapter = adapter
        recyclerView.visibility = View.VISIBLE
        emptyState.visibility = View.GONE
    }

    private fun showEmptyState() {
        recyclerView.visibility = View.GONE
        emptyState.visibility = View.VISIBLE
    }
}