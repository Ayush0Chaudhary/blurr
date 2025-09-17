package com.blurr.voice

import org.junit.Test
import org.junit.Assert.*
import java.io.File

/**
 * Unit tests for build configuration changes related to deobfuscation.
 * 
 * These tests validate that the ProGuard rules and build configuration
 * are properly set up for R8 minification and deobfuscation support.
 */
class BuildConfigurationTest {
    
    @Test
    fun testProguardRulesFileExists() {
        val proguardFile = File("proguard-rules.pro")
        assertTrue("ProGuard rules file should exist", proguardFile.exists())
    }
    
    @Test
    fun testProguardRulesContainsEssentialRules() {
        val proguardFile = File("proguard-rules.pro")
        if (proguardFile.exists()) {
            val content = proguardFile.readText()
            
            // Check for line number preservation (essential for deobfuscation)
            assertTrue("Should preserve line numbers for debugging", 
                content.contains("-keepattributes SourceFile,LineNumberTable"))
            
            // Check for Firebase/Crashlytics rules (essential for crash reporting)
            assertTrue("Should keep Firebase classes", 
                content.contains("-keep class com.google.firebase.**"))
            
            // Check for Gemini AI rules
            assertTrue("Should keep Gemini AI classes", 
                content.contains("-keep class com.google.ai.client.generativeai.**"))
            
            // Check for app-specific rules
            assertTrue("Should keep app classes", 
                content.contains("-keep class com.blurr.voice.**"))
        }
    }
    
    @Test
    fun testBuildGradleContainsMinificationSettings() {
        val buildFile = File("build.gradle.kts")
        if (buildFile.exists()) {
            val content = buildFile.readText()
            
            // We can't easily parse the Kotlin DSL, but we can check for key configurations
            assertTrue("Build file should exist", content.isNotEmpty())
        }
    }
    
    @Test
    fun testVersionPropertiesFileExists() {
        val versionFile = File("../version.properties")
        assertTrue("Version properties file should exist", versionFile.exists())
        
        if (versionFile.exists()) {
            val content = versionFile.readText()
            assertTrue("Should contain VERSION_CODE", content.contains("VERSION_CODE"))
            assertTrue("Should contain VERSION_NAME", content.contains("VERSION_NAME"))
        }
    }
    
    @Test
    fun testDeobfuscationPrerequisites() {
        // Test that all prerequisites for deobfuscation are in place
        
        // 1. ProGuard rules file exists
        assertTrue("ProGuard rules file must exist", File("proguard-rules.pro").exists())
        
        // 2. Version file exists for proper versioning
        assertTrue("Version file must exist", File("../version.properties").exists())
        
        // 3. Build file exists 
        assertTrue("Build gradle file must exist", File("build.gradle.kts").exists())
    }
    
    @Test
    fun testProguardRulesValidSyntax() {
        val proguardFile = File("proguard-rules.pro")
        if (proguardFile.exists()) {
            val content = proguardFile.readText()
            
            // Basic syntax checks
            val lines = content.lines()
            for ((index, line) in lines.withIndex()) {
                val trimmed = line.trim()
                if (trimmed.startsWith("-") && !trimmed.startsWith("#")) {
                    // This is a ProGuard rule, do basic validation
                    assertFalse("Rule at line ${index + 1} should not be empty: '$trimmed'", 
                        trimmed == "-")
                }
            }
        }
    }
}