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
//import com.blurr.voice.v2.llm.GeminiApi
import com.blurr.voice.v2.llm.GeminiMessage
import com.blurr.voice.v2.llm.MessageRole
import com.blurr.voice.v2.llm.TextPart
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class MemoriesActivity : AppCompatActivity() {
    
    // Memory feature flag - temporarily disabled
    companion object {
        const val MEMORY_ENABLED = true
    }
    
    private lateinit var memoriesRecyclerView: RecyclerView
    private lateinit var emptyStateText: TextView
    private lateinit var addMemoryFab: FloatingActionButton
    private lateinit var memoriesAdapter: MemoriesAdapter
    private lateinit var memoryManager: MemoryManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_memories)
        
        memoryManager = MemoryManager.getInstance(this)
        
        setupViews()
        setupRecyclerView()
        loadMemories()
    }

    private fun setupViews() {
        // Setup toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "My Memories"
        
        // Setup views
        memoriesRecyclerView = findViewById(R.id.memoriesRecyclerView)
        emptyStateText = findViewById(R.id.emptyStateText)
        addMemoryFab = findViewById(R.id.addMemoryFab)
        
        // Setup privacy card click listener
        val privacyCard = findViewById<com.google.android.material.card.MaterialCardView>(R.id.privacyCard)
        privacyCard.setOnClickListener {
            val intent = Intent(this, PrivacyActivity::class.java)
            startActivity(intent)
        }
        
        // Setup FAB click listener - disable if memory is off
        addMemoryFab.setOnClickListener {
            if (MEMORY_ENABLED) {
                showAddMemoryDialog()
            } else {
                Toast.makeText(this, "Memory functionality is temporarily disabled", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Disable FAB if memory is disabled
        if (!MEMORY_ENABLED) {
            addMemoryFab.alpha = 0.5f
            addMemoryFab.isEnabled = false
        }
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_memories, menu)
        return true
    }
    
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
    
    private fun setupRecyclerView() {
        memoriesAdapter = MemoriesAdapter(emptyList()) { memory ->
            showDeleteConfirmationDialog(memory)
        }
        
        memoriesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MemoriesActivity)
            adapter = memoriesAdapter
        }
        
        // Setup swipe to delete
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
    
    private fun updateUI(memories: List<Memory>) {
        if (memories.isEmpty() || !MEMORY_ENABLED) {
            memoriesRecyclerView.visibility = View.GONE
            emptyStateText.visibility = View.VISIBLE
            
            // Update empty state text based on memory status
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
    
    private fun showAddMemoryDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_memory, null)
        val memoryEditText = dialogView.findViewById<EditText>(R.id.memoryEditText)
        val saveButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.saveButton)
        val cancelButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.cancelButton)
        
        // Enable/disable save button based on text input
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
    
    private fun addMemory(memoryText: String) {
        lifecycleScope.launch {
            try {
                val success = memoryManager.addMemory(memoryText)
                if (success) {
                    Toast.makeText(this@MemoriesActivity, "Memory added successfully", Toast.LENGTH_SHORT).show()
                    loadMemories() // Reload the list
                } else {
                    Toast.makeText(this@MemoriesActivity, "Failed to add memory", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MemoriesActivity, "Error adding memory: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun showDeleteConfirmationDialog(memory: Memory) {
        AlertDialog.Builder(this)
            .setTitle("Delete Memory")
            .setMessage("Are you sure you want to delete this memory?\n\n\"${memory.originalText}\"")
            .setPositiveButton("Delete") { _, _ ->
                deleteMemory(memory)
            }
            .setNegativeButton("Cancel") { _, _ ->
                // Restore the swiped item
                memoriesAdapter.notifyDataSetChanged()
            }
            .setCancelable(false)
            .show()
    }
    
    private fun deleteMemory(memory: Memory) {
        lifecycleScope.launch {
            try {
                val success = memoryManager.deleteMemoryById(memory.id)
                if (success) {
                    memoriesAdapter.removeMemory(memory)
                    showSnackbar("Memory deleted", "Undo") {
                        // Undo functionality could be added here
                    }
                    
                    // Update UI if no memories left
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
    
    private fun showSnackbar(message: String, actionText: String, action: () -> Unit) {
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG)
            .setAction(actionText) { action() }
            .show()
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
} 