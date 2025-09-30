/**
 * @file PrivacyActivity.kt
 * @brief Defines an activity that displays static information about privacy and memory.
 *
 * This file contains the `PrivacyActivity`, a simple screen used to present information
 * to the user regarding how the agent's memory and data are handled.
 */
package com.blurr.voice

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar

/**
 * An activity that displays a static layout with information about the app's
 * memory and privacy features.
 */
class PrivacyActivity : AppCompatActivity() {
    
    /**
     * Called when the activity is first created. Sets up the content view and the toolbar.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_privacy)
        
        setupToolbar()
    }
    
    /**
     * Initializes the toolbar, sets the title, and enables the "Up" button for navigation.
     */
    private fun setupToolbar() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "How Panda Remembers"
    }
    
    /**
     * Handles the action when the "Up" button in the toolbar is pressed.
     */
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
} 