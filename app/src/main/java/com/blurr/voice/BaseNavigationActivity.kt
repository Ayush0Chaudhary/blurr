package com.blurr.voice

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity

abstract class BaseNavigationActivity : AppCompatActivity() {

    protected abstract fun getContentLayoutId(): Int
    protected abstract fun getCurrentNavItem(): NavItem

    enum class NavItem {
        HOME, TRIGGERS, MOMENTS, UPGRADE, SETTINGS
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Disable any default activity transitions
        disableTransitions()
    }
    
    override fun setContentView(layoutResID: Int) {
        super.setContentView(R.layout.activity_base_navigation)
        
        // Inflate the child activity's content into the content container
        val contentContainer = findViewById<LinearLayout>(R.id.content_container)
        layoutInflater.inflate(layoutResID, contentContainer, true)
        
        setupBottomNavigation()
    }

    private fun setupBottomNavigation() {
        val currentItem = getCurrentNavItem()
        
        findViewById<LinearLayout>(R.id.nav_triggers).apply {
            setOnClickListener {
                if (currentItem != NavItem.TRIGGERS) {
                    navigateToActivity(com.blurr.voice.triggers.ui.TriggersActivity::class.java, currentItem)
                }
            }
            alpha = if (currentItem == NavItem.TRIGGERS) 1.0f else 0.7f
        }
        
        findViewById<LinearLayout>(R.id.nav_moments).apply {
            setOnClickListener {
                if (currentItem != NavItem.MOMENTS) {
                    navigateToActivity(MomentsActivity::class.java, currentItem)
                }
            }
            alpha = if (currentItem == NavItem.MOMENTS) 1.0f else 0.7f
        }
        
        findViewById<LinearLayout>(R.id.nav_home).apply {
            setOnClickListener {
                if (currentItem != NavItem.HOME) {
                    navigateToActivity(MainActivity::class.java, currentItem)
                }
            }
            alpha = if (currentItem == NavItem.HOME) 1.0f else 0.7f
        }
        
        findViewById<LinearLayout>(R.id.nav_upgrade).apply {
            setOnClickListener {
                if (currentItem != NavItem.UPGRADE) {
                    navigateToActivity(ProPurchaseActivity::class.java, currentItem)
                }
            }
            alpha = if (currentItem == NavItem.UPGRADE) 1.0f else 0.7f
        }
        
        findViewById<LinearLayout>(R.id.nav_settings).apply {
            setOnClickListener {
                if (currentItem != NavItem.SETTINGS) {
                    navigateToActivity(SettingsActivity::class.java, currentItem)
                }
            }
            alpha = if (currentItem == NavItem.SETTINGS) 1.0f else 0.7f
        }
    }
    
    private fun navigateToActivity(activityClass: Class<*>, currentItem: NavItem) {
        val intent = Intent(this, activityClass)
        startActivity(intent)
        // Disable transition animations
        disableTransitions()
        if (currentItem != NavItem.HOME) {
            finish()
            // Also disable animations when finishing
            disableTransitions()
        }
    }
    
    override fun finish() {
        super.finish()
        // Disable animations when finishing
        disableTransitions()
    }
    
    @Suppress("DEPRECATION")
    private fun disableTransitions() {
        // Use the legacy method for all Android versions since the new API
        // requires more complex setup and this works reliably
        overridePendingTransition(0, 0)
    }
}