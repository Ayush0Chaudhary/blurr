package com.blurr.voice

import android.os.Bundle
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class MomentsActivity : BaseNavigationActivity() {
    
    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_moments_content)
        
        // Setup back button
        findViewById<TextView>(R.id.back_button).setOnClickListener {
            finish()
        }
        
        // Initialize views
        viewPager = findViewById(R.id.viewPager)
        tabLayout = findViewById(R.id.tabLayout)
        
        setupViewPager()
        
        // Check for extra to select specific tab
        val tabIndex = intent.getIntExtra("TAB_INDEX", 0)
        if (tabIndex in 0..1) {
            viewPager.currentItem = tabIndex
        }
    }
    
    private fun setupViewPager() {
        val adapter = MomentsPagerAdapter(this)
        viewPager.adapter = adapter
        
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Memories"
                1 -> "Past Actions"
                else -> ""
            }
        }.attach()
    }
    
    private inner class MomentsPagerAdapter(activity: androidx.fragment.app.FragmentActivity) : FragmentStateAdapter(activity) {
        override fun getItemCount(): Int = 2
        
        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> MemoriesFragment()
                1 -> MomentsFragment()
                else -> throw IllegalArgumentException("Invalid position: $position")
            }
        }
    }
    
    override fun getContentLayoutId(): Int = R.layout.activity_moments_content
    
    override fun getCurrentNavItem(): BaseNavigationActivity.NavItem = BaseNavigationActivity.NavItem.MOMENTS
}