/**
 * @file MemoriesActivity.kt
 * @brief Defines the activity for viewing and managing the agent's stored memories.
 *
 * This file contains the `MemoriesActivity`, which provides a user interface for users to
 * view, add, and delete their personal memories that the agent can use to personalize conversations.
 */
package com.blurr.voice

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.blurr.voice.data.Memory
import com.blurr.voice.data.MemoryManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

/**
 * An activity that provides a UI for managing the agent's memories.
 *
 * This screen displays a list of all memories the user has saved. It allows users to
 * add new memories via a dialog and delete existing ones by swiping them away. The entire
 * feature can be toggled via the `MEMORY_ENABLED` feature flag.
 */
class MemoriesActivity : AppCompatActivity() {
    
    private companion object {
        /** A feature flag to enable or disable memory functionality throughout the activity. */
        const val MEMORY_ENABLED = false
    }
    
    private lateinit var memoriesRecyclerView: RecyclerView
    private lateinit var emptyStateText: TextView
    private lateinit var addMemoryFab: FloatingActionButton
    private lateinit var memoriesAdapter: MemoriesAdapter
    private lateinit var memoryManager: MemoryManager
    
    /**
     * Called when the activity is first created. Initializes the UI and loads memories.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_memories)
        
        memoryManager = MemoryManager.getInstance(this)
        
        setupViews()
        setupRecyclerView()
        loadMemories()
    }

    /**
     * Initializes all UI views, sets up the toolbar, and configures click listeners.
     * It also adapts the UI based on the `MEMORY_ENABLED` flag.
     */
    private fun setupViews() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "My Memories"
        
        memoriesRecyclerView = findViewById(R.id.memoriesRecyclerView)
        emptyStateText = findViewById(R.id.emptyStateText)
        addMemoryFab = findViewById(R.id.addMemoryFab)
        
        val privacyCard = findViewById<com.google.android.material.card.MaterialCardView>(R.id.privacyCard)
        privacyCard.setOnClickListener {
            val intent = Intent(this, PrivacyActivity::class.java)
            startActivity(intent)
        }
        
        addMemoryFab.setOnClickListener {
            if (MEMORY_ENABLED) {
                showAddMemoryDialog()
            } else {
                Toast.makeText(this, "Memory functionality is temporarily disabled", Toast.LENGTH_SHORT).show()
            }
        }
        
        if (!MEMORY_ENABLED) {
            addMemoryFab.alpha = 0.5f
            addMemoryFab.isEnabled = false
        }
    }
    
    /**
     * Inflates the toolbar menu.
     */
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_memories, menu)
        return true
    }
    
    /**
     * Handles menu item selections.
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_privacy -> {
                val intent = Intent(this, PrivacyActivity::class.java)
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    /**
     * Sets up the RecyclerView, its adapter, and the `ItemTouchHelper` for swipe-to-delete functionality.
     */
    private fun setupRecyclerView() {
        memoriesAdapter = MemoriesAdapter(emptyList()) { memory ->
            showDeleteConfirmationDialog(memory)
        }
        
        memoriesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MemoriesActivity)
            adapter = memoriesAdapter
        }
        
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false
            
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val memory = memoriesAdapter.getMemoryAt(position)
                if (memory != null) {
                    showDeleteConfirmationDialog(memory)
                }
            }
        }
        
        ItemTouchHelper(swipeHandler).attachToRecyclerView(memoriesRecyclerView)
    }
    
    /**
     * Asynchronously loads all memories from the `MemoryManager` and updates the UI.
     * If the memory feature is disabled, it shows a disabled message.
     */
    private fun loadMemories() {
        if (!MEMORY_ENABLED) {
            Log.d("MemoriesActivity", "Memory disabled, showing empty state with disabled message")
            updateUI(emptyList())
            return
        }
        
        lifecycleScope.launch {
            try {
                val memories = memoryManager.getAllMemoriesList()
                updateUI(memories)
            } catch (e: Exception) {
                Toast.makeText(this@MemoriesActivity, "Error loading memories: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    /**
     * Updates the UI to show either the list of memories or the empty state text.
     * The empty state text changes depending on whether the memory feature is disabled.
     * @param memories The list of memories to display.
     */
    private fun updateUI(memories: List<Memory>) {
        if (memories.isEmpty() || !MEMORY_ENABLED) {
            memoriesRecyclerView.visibility = View.GONE
            emptyStateText.visibility = View.VISIBLE
            
            emptyStateText.text = if (!MEMORY_ENABLED) {
                "Memory functionality is temporarily disabled.\nPanda memory is turned off as of yet."
            } else {
                "No memories yet.\nTap the + button to add your first memory!"
            }
        } else {
            memoriesRecyclerView.visibility = View.VISIBLE
            emptyStateText.visibility = View.GONE
            memoriesAdapter.updateMemories(memories)
        }
    }
    
    /**
     * Displays a dialog for the user to enter and save a new memory.
     */
    private fun showAddMemoryDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_memory, null)
        val memoryEditText = dialogView.findViewById<EditText>(R.id.memoryEditText)
        val saveButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.saveButton)
        val cancelButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.cancelButton)
        
        memoryEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                saveButton.isEnabled = !s.isNullOrBlank()
            }
        })
        
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()
        
        saveButton.setOnClickListener {
            val memoryText = memoryEditText.text.toString().trim()
            if (memoryText.isNotEmpty()) {
                addMemory(memoryText)
                dialog.dismiss()
            }
        }
        
        cancelButton.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    /**
     * Adds a new memory to the database via the `MemoryManager`.
     * @param memoryText The content of the memory to add.
     */
    private fun addMemory(memoryText: String) {
        lifecycleScope.launch {
            try {
                val success = memoryManager.addMemory(memoryText)
                if (success) {
                    Toast.makeText(this@MemoriesActivity, "Memory added successfully", Toast.LENGTH_SHORT).show()
                    loadMemories()
                } else {
                    Toast.makeText(this@MemoriesActivity, "Failed to add memory", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MemoriesActivity, "Error adding memory: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    /**
     * Shows a confirmation dialog before deleting a memory.
     * @param memory The [Memory] object to be deleted.
     */
    private fun showDeleteConfirmationDialog(memory: Memory) {
        AlertDialog.Builder(this)
            .setTitle("Delete Memory")
            .setMessage("Are you sure you want to delete this memory?\n\n\"${memory.originalText}\"")
            .setPositiveButton("Delete") { _, _ ->
                deleteMemory(memory)
            }
            .setNegativeButton("Cancel") { _, _ ->
                memoriesAdapter.notifyDataSetChanged()
            }
            .setCancelable(false)
            .show()
    }
    
    /**
     * Deletes a memory from the database and updates the UI.
     * @param memory The [Memory] object to delete.
     */
    private fun deleteMemory(memory: Memory) {
        lifecycleScope.launch {
            try {
                val success = memoryManager.deleteMemoryById(memory.id)
                if (success) {
                    memoriesAdapter.removeMemory(memory)
                    showSnackbar("Memory deleted", "Undo") {
                        // TODO: Implement undo functionality.
                    }
                    
                    if (memoriesAdapter.itemCount == 0) {
                        updateUI(emptyList())
                    }
                } else {
                    Toast.makeText(this@MemoriesActivity, "Failed to delete memory", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MemoriesActivity, "Error deleting memory: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    /**
     * A utility function to show a [Snackbar] message.
     * @param message The main text of the snackbar.
     * @param actionText The text for the action button.
     * @param action The lambda to execute when the action button is pressed.
     */
    private fun showSnackbar(message: String, actionText: String, action: () -> Unit) {
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG)
            .setAction(actionText) { action() }
            .show()
    }
    
    /**
     * Handles the "Up" button navigation in the toolbar.
     */
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
} 