package com.blurr.voice.agent.v2

import com.blurr.voice.agent.v2.prompts.SystemPrompt
import org.junit.Test
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import java.io.File

class SystemPromptTest {

    private val testActionDescription = "click_button, type_text, scroll_screen"
    private val testMaxActions = 5

    @Before
    fun setUp() {
        // Clean up any test files
        cleanupTestFiles()
    }

    @After
    fun tearDown() {
        // Clean up any test files
        cleanupTestFiles()
    }

    @Test
    fun `test SystemPrompt with default parameters`() {
        val systemPrompt = SystemPrompt(testActionDescription)
        val message = systemPrompt.getSystemMessage()
        
        assertNotNull("Message should not be null", message)
        assertNotNull("Message content should not be null", message.content)
        assertTrue("Message content should contain action description", 
                  message.content.contains(testActionDescription))
        assertTrue("Message content should contain max actions", 
                  message.content.contains(testMaxActions.toString()))
    }

    @Test
    fun `test SystemPrompt with custom maxActionsPerStep`() {
        val customMaxActions = 15
        val systemPrompt = SystemPrompt(testActionDescription, maxActionsPerStep = customMaxActions)
        val message = systemPrompt.getSystemMessage()
        
        assertTrue("Message content should contain custom max actions", 
                  message.content.contains(customMaxActions.toString()))
    }

    @Test
    fun `test SystemPrompt with overrideSystemMessage`() {
        val overrideMessage = "This is a custom override message"
        val systemPrompt = SystemPrompt(
            actionDescription = testActionDescription,
            overrideSystemMessage = overrideMessage
        )
        val message = systemPrompt.getSystemMessage()
        
        assertEquals("Message content should match override message", overrideMessage, message.content)
        assertFalse("Message content should not contain action description", 
                   message.content.contains(testActionDescription))
    }

    @Test
    fun `test SystemPrompt with extendSystemMessage`() {
        val extendMessage = "Additional instructions for the agent"
        val systemPrompt = SystemPrompt(
            actionDescription = testActionDescription,
            extendSystemMessage = extendMessage
        )
        val message = systemPrompt.getSystemMessage()
        
        assertTrue("Message content should contain action description", 
                  message.content.contains(testActionDescription))
        assertTrue("Message content should contain extended message", 
                  message.content.contains(extendMessage))
        assertTrue("Extended message should be at the end", 
                  message.content.endsWith(extendMessage))
    }

    @Test
    fun `test SystemPrompt with both override and extend`() {
        val overrideMessage = "Base override message"
        val extendMessage = "Extended message"
        val systemPrompt = SystemPrompt(
            actionDescription = testActionDescription,
            overrideSystemMessage = overrideMessage,
            extendSystemMessage = extendMessage
        )
        val message = systemPrompt.getSystemMessage()
        
        val expectedContent = "$overrideMessage\n$extendMessage"
        assertEquals("Message content should combine override and extend", expectedContent, message.content)
    }

    @Test
    fun `test SystemPrompt template replacement`() {
        val systemPrompt = SystemPrompt(testActionDescription, maxActionsPerStep = 8)
        val message = systemPrompt.getSystemMessage()
        
        // Check that both placeholders are replaced
        assertFalse("Template should not contain {max_actions} placeholder", 
                   message.content.contains("{max_actions}"))
        assertFalse("Template should not contain {actionDescription} placeholder", 
                   message.content.contains("{actionDescription}"))
        
        // Check that values are properly inserted
        assertTrue("Message should contain max actions value", 
                  message.content.contains("8"))
        assertTrue("Message should contain action description", 
                  message.content.contains(testActionDescription))
    }

    @Test
    fun `test SystemPrompt with empty action description`() {
        val systemPrompt = SystemPrompt("")
        val message = systemPrompt.getSystemMessage()
        
        assertNotNull("Message should not be null", message)
        assertNotNull("Message content should not be null", message.content)
        assertTrue("Message content should be empty for action description", 
                  message.content.contains(""))
    }

    @Test
    fun `test SystemPrompt with special characters in action description`() {
        val specialActionDescription = "click_button[0], type_text(\"hello\"), scroll_screen{up}"
        val systemPrompt = SystemPrompt(specialActionDescription)
        val message = systemPrompt.getSystemMessage()
        
        assertTrue("Message should contain special characters", 
                  message.content.contains(specialActionDescription))
    }

    @Test
    fun `test SystemPrompt with very long action description`() {
        val longActionDescription = "action1, action2, action3, action4, action5, action6, action7, action8, action9, action10"
        val systemPrompt = SystemPrompt(longActionDescription)
        val message = systemPrompt.getSystemMessage()
        
        assertTrue("Message should contain long action description", 
                  message.content.contains(longActionDescription))
    }

    @Test
    fun `test SystemPrompt message content structure`() {
        val systemPrompt = SystemPrompt(testActionDescription)
        val message = systemPrompt.getSystemMessage()
        
        val content = message.content
        
        // Check that the message contains expected sections
        assertTrue("Message should contain Functions section", 
                  content.contains("Functions:"))
        assertTrue("Message should contain Example section", 
                  content.contains("Example:"))
        assertTrue("Message should contain AVAILABLE ACTIONS", 
                  content.contains("AVAILABLE ACTIONS:"))
    }

    @Test
    fun `test SystemPrompt with zero max actions`() {
        val systemPrompt = SystemPrompt(testActionDescription, maxActionsPerStep = 0)
        val message = systemPrompt.getSystemMessage()
        
        assertTrue("Message should contain zero max actions", 
                  message.content.contains("0"))
    }

    @Test
    fun `test SystemPrompt with negative max actions`() {
        val systemPrompt = SystemPrompt(testActionDescription, maxActionsPerStep = -5)
        val message = systemPrompt.getSystemMessage()
        
        assertTrue("Message should contain negative max actions", 
                  message.content.contains("-5"))
    }

    @Test
    fun `test SystemPrompt multiple instances`() {
        val systemPrompt1 = SystemPrompt("action1, action2")
        val systemPrompt2 = SystemPrompt("action3, action4")
        
        val message1 = systemPrompt1.getSystemMessage()
        val message2 = systemPrompt2.getSystemMessage()
        
        assertNotEquals("Different instances should have different messages", 
                       message1.content, message2.content)
        assertTrue("First message should contain first actions", 
                  message1.content.contains("action1, action2"))
        assertTrue("Second message should contain second actions", 
                  message2.content.contains("action3, action4"))
    }

    private fun cleanupTestFiles() {
        // Clean up any test files that might have been created
        val testFile = File("test_system_prompt.md")
        if (testFile.exists()) {
            testFile.delete()
        }
    }
}
