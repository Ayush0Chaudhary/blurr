package com.blurr.voice.utilities

import android.content.Context
import android.util.Log

/**
 * Utility class for testing delta symbol functionality
 */
object DeltaSymbolTestUtil {
    
    private const val TAG = "DeltaSymbolTestUtil"
    
    /**
     * Test all state color mappings and log the results
     */
    fun testStateColorMappings(context: Context) {
        Log.d(TAG, "Testing delta symbol state color mappings:")
        
        PandaState.values().forEach { state ->
            val visualState = DeltaStateColorMapper.getDeltaVisualState(context, state)
            Log.d(TAG, "State: ${state.name}")
            Log.d(TAG, "  Color: ${visualState.colorHex}")
            Log.d(TAG, "  Status Text: ${visualState.statusText}")
            Log.d(TAG, "  Is Active: ${DeltaStateColorMapper.isActiveState(state)}")
            Log.d(TAG, "  Priority: ${DeltaStateColorMapper.getStatePriority(state)}")
            Log.d(TAG, "  ---")
        }
    }
    
    /**
     * Test state transitions and verify they work correctly
     */
    fun testStateTransitions(context: Context) {
        Log.d(TAG, "Testing state transitions:")
        
        val stateManager = PandaStateManager.getInstance(context)
        val currentState = stateManager.getCurrentState()
        val visualState = stateManager.getDeltaVisualState()
        
        Log.d(TAG, "Current State: ${currentState.name}")
        Log.d(TAG, "Current Color: ${visualState.colorHex}")
        Log.d(TAG, "Current Status: ${visualState.statusText}")
    }
    
    /**
     * Verify that all required drawable resources exist
     */
    fun verifyDrawableResources(context: Context): Boolean {
        return try {
            // Test main delta symbol
            context.getDrawable(com.blurr.voice.R.drawable.ic_delta_symbol)
            Log.d(TAG, "✓ ic_delta_symbol drawable found")
            
            // Test small delta symbol
            context.getDrawable(com.blurr.voice.R.drawable.ic_delta_small)
            Log.d(TAG, "✓ ic_delta_small drawable found")
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "✗ Drawable resource verification failed", e)
            false
        }
    }
    
    /**
     * Verify that all required color resources exist
     */
    fun verifyColorResources(context: Context): Boolean {
        return try {
            PandaState.values().forEach { state ->
                val colorResId = DeltaStateColorMapper.getColorResourceId(state)
                val color = DeltaStateColorMapper.getColor(context, state)
                Log.d(TAG, "✓ Color for ${state.name}: ${String.format("#%08X", color)}")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "✗ Color resource verification failed", e)
            false
        }
    }
    
    /**
     * Run all tests and return overall success
     */
    fun runAllTests(context: Context): Boolean {
        Log.d(TAG, "Running all delta symbol tests...")
        
        val drawableTest = verifyDrawableResources(context)
        val colorTest = verifyColorResources(context)
        
        if (drawableTest && colorTest) {
            testStateColorMappings(context)
            testStateTransitions(context)
            Log.d(TAG, "✓ All tests passed!")
            return true
        } else {
            Log.e(TAG, "✗ Some tests failed!")
            return false
        }
    }
}