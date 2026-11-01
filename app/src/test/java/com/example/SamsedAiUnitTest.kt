package com.example

import com.example.data.models.AiFeatureMode
import com.example.data.models.ChatMessage
import com.example.data.models.Conversation
import com.example.data.models.StudySubject
import com.example.data.models.UserProfile
import com.example.data.models.WritingFormat
import com.example.data.models.WritingTone
import org.junit.Assert.*
import org.junit.Test

class SamsedAiUnitTest {

    @Test
    fun testUserProfileDefaults() {
        val user = UserProfile(
            userId = "user_123",
            email = "user@example.com",
            displayName = "Alex",
            isAdmin = false
        )
        assertEquals("user_123", user.userId)
        assertEquals("user@example.com", user.email)
        assertEquals("Alex", user.displayName)
        assertFalse(user.isAdmin)
        assertEquals("gemini-3.8-flash", user.modelPreference)
        assertEquals("dark", user.themeMode)
    }

    @Test
    fun testConversationModel() {
        val conv = Conversation(
            id = "conv_abc",
            userId = "user_123",
            title = "Quantum Physics",
            mode = "chat",
            lastMessage = "Quantum mechanics is fascinating.",
            messageCount = 4L
        )
        assertEquals("conv_abc", conv.id)
        assertEquals("user_123", conv.userId)
        assertEquals("Quantum Physics", conv.title)
        assertEquals(4L, conv.messageCount)
    }

    @Test
    fun testChatMessageModel() {
        val message = ChatMessage(
            id = "msg_001",
            conversationId = "conv_abc",
            userId = "user_123",
            role = "user",
            content = "Hello SAMSED AI!",
            hasImage = false
        )
        assertEquals("msg_001", message.id)
        assertEquals("user", message.role)
        assertEquals("Hello SAMSED AI!", message.content)
        assertFalse(message.hasImage)
    }

    @Test
    fun testAiFeatureModes() {
        val chatMode = AiFeatureMode.CHAT
        assertEquals("chat", chatMode.key)
        assertEquals("AI Chat", chatMode.label)

        val voiceMode = AiFeatureMode.VOICE
        assertEquals("voice", voiceMode.key)

        val adminMode = AiFeatureMode.ADMIN
        assertEquals("admin", adminMode.key)

        assertEquals(8, AiFeatureMode.entries.size)
    }

    @Test
    fun testWritingAssistantOptions() {
        assertTrue(WritingTone.entries.contains(WritingTone.PROFESSIONAL))
        assertTrue(WritingFormat.entries.contains(WritingFormat.EMAIL))
        assertTrue(WritingFormat.entries.contains(WritingFormat.CODE))
    }

    @Test
    fun testStudySubjects() {
        val math = StudySubject.MATHEMATICS
        assertEquals("Math", math.label)
        assertTrue(math.promptHint.contains("Solve"))
    }

    @Test
    fun testRoomEntities() {
        val localConv = com.example.data.local.entities.LocalConversationEntity(
            id = "c1",
            userId = "u1",
            title = "Offline AI Chat",
            mode = "chat",
            lastMessage = "Cached offline response",
            messageCount = 5L
        )
        assertEquals("c1", localConv.id)
        assertEquals("u1", localConv.userId)
        assertEquals("Offline AI Chat", localConv.title)

        val localMsg = com.example.data.local.entities.LocalMessageEntity(
            id = "m1",
            conversationId = "c1",
            userId = "u1",
            role = "model",
            content = "This was stored in Room database",
            hasImage = false
        )
        assertEquals("m1", localMsg.id)
        assertEquals("model", localMsg.role)

        val localPref = com.example.data.local.entities.LocalUserPreferenceEntity(
            userId = "u1",
            themeMode = "dark",
            modelPreference = "gemini-3.8-flash",
            offlineModeEnabled = true
        )
        assertEquals("u1", localPref.userId)
        assertTrue(localPref.offlineModeEnabled)
    }
}

