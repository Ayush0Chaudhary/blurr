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
import com.blurr.voice.data.UserMemory
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.UUID

class MemoriesActivity : AppCompatActivity() {

    private lateinit var memoriesRecyclerView: RecyclerView
    private lateinit var emptyStateText: TextView
    private lateinit var addMemoryFab: FloatingActionButton
    private lateinit var memoriesAdapter: MemoriesAdapter
    
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_memories)
        
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
        
        // Setup FAB click listener
        addMemoryFab.setOnClickListener {
            showAddEditMemoryDialog(null)
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
        memoriesAdapter = MemoriesAdapter(
            memories = emptyList(),
            onEditClick = { memory ->
                showAddEditMemoryDialog(memory)
            },
            onDeleteClick = { memory ->
                showDeleteConfirmationDialog(memory)
            }
        )
        
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
        val user = auth.currentUser
        if (user == null) {
            updateUI(emptyList())
            return
        }

        val docRef = db.collection("users").document(user.uid)
        docRef.addSnapshotListener { snapshot, e ->
            if (e != null) {
                Log.w("MemoriesActivity", "Listen failed.", e)
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                val memoriesList = mutableListOf<UserMemory>()
                val memoriesData = snapshot.get("memories") as? List<Map<String, Any>>
                
                memoriesData?.forEach { map ->
                    try {
                        val timestamp = map["createdAt"] as? com.google.firebase.Timestamp
                        val date = timestamp?.toDate() ?: Date()
                        
                        val memory = UserMemory(
                            id = map["id"] as? String ?: "",
                            text = map["text"] as? String ?: "",
                            source = map["source"] as? String ?: "User",
                            createdAt = date
                        )
                        memoriesList.add(memory)
                    } catch (e: Exception) {
                        Log.e("MemoriesActivity", "Error parsing memory", e)
                    }
                }
                
                // Sort by date descending
                memoriesList.sortByDescending { it.createdAt }
                updateUI(memoriesList)
            } else {
                updateUI(emptyList())
            }
        }
    }
    
    private fun updateUI(memories: List<UserMemory>) {
        if (memories.isEmpty()) {
            memoriesRecyclerView.visibility = View.GONE
            emptyStateText.visibility = View.VISIBLE
            emptyStateText.text = "No memories yet.\nTap the + button to add your first memory!"
        } else {
            memoriesRecyclerView.visibility = View.VISIBLE
            emptyStateText.visibility = View.GONE
            memoriesAdapter.updateMemories(memories)
        }
    }
    
    private fun showAddEditMemoryDialog(existingMemory: UserMemory?) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_memory, null)
        val memoryEditText = dialogView.findViewById<EditText>(R.id.memoryEditText)
        val saveButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.saveButton)
        val cancelButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.cancelButton)
//        val titleTextView = dialogView.findViewById<TextView>(R.id.dialogTitle) // Assuming there is a title view, or I'll just skip setting title if ID not found

        if (existingMemory != null) {
            memoryEditText.setText(existingMemory.text)
            saveButton.text = "Update"
        }
        
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
                if (existingMemory != null) {
                    updateMemory(existingMemory, memoryText)
                } else {
                    addMemory(memoryText)
                }
                dialog.dismiss()
            }
        }
        
        cancelButton.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    private fun addMemory(memoryText: String) {
        val user = auth.currentUser ?: return
        val newMemory = UserMemory(
            id = UUID.randomUUID().toString(),
            text = memoryText,
            source = "User",
            createdAt = Date()
        )
        
        val docRef = db.collection("users").document(user.uid)
        
        // We need to convert UserMemory to a Map because Firestore arrayUnion works best with Maps or exact object matches
        // But since we are using custom objects, we should be careful.
        // Let's use a Map to be safe and match the structure.
        
        val memoryMap = hashMapOf(
            "id" to newMemory.id,
            "text" to newMemory.text,
            "source" to newMemory.source,
            "createdAt" to newMemory.createdAt
        )

        docRef.update("memories", FieldValue.arrayUnion(memoryMap))
            .addOnSuccessListener {
                Toast.makeText(this, "Memory added", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                // If the document doesn't exist or memories field doesn't exist, we might need to set it.
                // However, users usually exist. If "memories" field is missing, update might fail if we don't use set with merge, 
                // but arrayUnion usually creates the field if it doesn't exist? 
                // Actually arrayUnion on a non-existent document fails. But the user document should exist.
                // If "memories" field is missing, arrayUnion creates it.
                Log.e("MemoriesActivity", "Error adding memory", e)
                Toast.makeText(this, "Failed to add memory", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateMemory(oldMemory: UserMemory, newText: String) {
        val user = auth.currentUser ?: return
        val docRef = db.collection("users").document(user.uid)

        // To update an item in an array, we have to remove the old one and add the new one.
        // This is not atomic unless we use a transaction, but for this simple app it's probably fine.
        // Or we can read the whole array, modify it, and write it back.
        // Reading and writing back is safer for "edit" to avoid race conditions where we remove but fail to add?
        // Actually, arrayRemove requires the EXACT object.
        
        // Let's try to do it in a transaction or just read-modify-write.
        
        db.runTransaction { transaction ->
            val snapshot = transaction.get(docRef)
            val memories = snapshot.get("memories") as? MutableList<Map<String, Any>> ?: mutableListOf()
            
            // Find the memory with the same ID
            val index = memories.indexOfFirst { it["id"] == oldMemory.id }
            if (index != -1) {
                val newMemoryMap = hashMapOf(
                    "id" to oldMemory.id,
                    "text" to newText,
                    "source" to oldMemory.source,
                    "createdAt" to oldMemory.createdAt // Keep original creation date
                )
                memories[index] = newMemoryMap
                transaction.update(docRef, "memories", memories)
            }
        }.addOnSuccessListener {
            Toast.makeText(this, "Memory updated", Toast.LENGTH_SHORT).show()
        }.addOnFailureListener { e ->
            Log.e("MemoriesActivity", "Error updating memory", e)
            Toast.makeText(this, "Failed to update memory", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showDeleteConfirmationDialog(memory: UserMemory) {
        AlertDialog.Builder(this)
            .setTitle("Delete Memory")
            .setMessage("Are you sure you want to delete this memory?\n\n\"${memory.text}\"")
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
    
    private fun deleteMemory(memory: UserMemory) {
        val user = auth.currentUser ?: return
        val docRef = db.collection("users").document(user.uid)
        
        // We need to match the object exactly to remove it via arrayRemove.
        // But we might have issues with Timestamp precision or other fields.
        // Safer to read-modify-write.
        
        db.runTransaction { transaction ->
            val snapshot = transaction.get(docRef)
            val memories = snapshot.get("memories") as? MutableList<Map<String, Any>> ?: mutableListOf()
            
            val index = memories.indexOfFirst { it["id"] == memory.id }
            if (index != -1) {
                memories.removeAt(index)
                transaction.update(docRef, "memories", memories)
            }
        }.addOnSuccessListener {
            showSnackbar("Memory deleted", "Undo") {
                // Undo functionality would require adding it back.
                // For now, just re-add it?
                addMemory(memory.text) // This would give it a new ID though if we use the addMemory function.
                // Let's skip complex undo for now.
            }
        }.addOnFailureListener { e ->
            Log.e("MemoriesActivity", "Error deleting memory", e)
            Toast.makeText(this, "Failed to delete memory", Toast.LENGTH_SHORT).show()
            memoriesAdapter.notifyDataSetChanged() // Restore item in UI
        }
    }
    
    private fun showSnackbar(message: String, actionText: String, action: () -> Unit) {
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG)
            //.setAction(actionText) { action() } // Undo disabled for simplicity as discussed
            .show()
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}