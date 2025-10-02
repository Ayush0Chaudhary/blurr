package com.blurr.voice

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.blurr.voice.utilities.GeminiKeyManager

class AdvancedSettingsActivity : AppCompatActivity() {

    private lateinit var switchUseCustomKeys: SwitchCompat
    private lateinit var textWarning: TextView
    private lateinit var editGeminiKey: EditText
    private lateinit var buttonAddKey: Button
    private lateinit var recyclerViewKeys: RecyclerView
    private lateinit var geminiKeyManager: GeminiKeyManager
    private lateinit var keyAdapter: GeminiKeyAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_advanced_settings)

        switchUseCustomKeys = findViewById(R.id.switchUseCustomKeys)
        textWarning = findViewById(R.id.textWarning)
        editGeminiKey = findViewById(R.id.editGeminiKey)
        buttonAddKey = findViewById(R.id.buttonAddKey)
        recyclerViewKeys = findViewById(R.id.recyclerViewKeys)
        geminiKeyManager = GeminiKeyManager(this)

        setupRecyclerView()
        setupListeners()

        // Set initial state of the UI based on saved preference
        val useCustomKeys = geminiKeyManager.useCustomKeys()
        switchUseCustomKeys.isChecked = useCustomKeys
        updateUiVisibility(useCustomKeys)
    }

    private fun setupListeners() {
        switchUseCustomKeys.setOnCheckedChangeListener { _, isChecked ->
            geminiKeyManager.setUseCustomKeys(isChecked)
            updateUiVisibility(isChecked)
        }

        buttonAddKey.setOnClickListener {
            val key = editGeminiKey.text.toString().trim()
            if (key.isNotEmpty()) {
                geminiKeyManager.addKey(key)
                editGeminiKey.text.clear()
                updateKeyList()
            }
        }
    }

    private fun setupRecyclerView() {
        val keys = geminiKeyManager.getKeys().toMutableList()
        keyAdapter = GeminiKeyAdapter(keys) { key ->
            geminiKeyManager.deleteKey(key)
            updateKeyList()
        }
        recyclerViewKeys.adapter = keyAdapter
        recyclerViewKeys.layoutManager = LinearLayoutManager(this)
    }

    private fun updateKeyList() {
        keyAdapter.updateKeys(geminiKeyManager.getKeys())
    }

    private fun updateUiVisibility(isCustomKeyMode: Boolean) {
        val visibility = if (isCustomKeyMode) View.VISIBLE else View.GONE
        textWarning.visibility = visibility
        editGeminiKey.visibility = visibility
        buttonAddKey.visibility = visibility
        recyclerViewKeys.visibility = visibility
    }
}