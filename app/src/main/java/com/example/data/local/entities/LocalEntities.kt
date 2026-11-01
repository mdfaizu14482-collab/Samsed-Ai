package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "local_conversations",
    indices = [Index(value = ["userId"]), Index(value = ["timestamp"])]
)
data class LocalConversationEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val title: String,
    val mode: String,
    val lastMessage: String,
    val messageCount: Long,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "local_messages",
    indices = [Index(value = ["conversationId"]), Index(value = ["timestamp"])]
)
data class LocalMessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val userId: String,
    val role: String,
    val content: String,
    val imageUrl: String = "",
    val hasImage: Boolean = false,
    val toolUsed: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "local_user_preferences")
data class LocalUserPreferenceEntity(
    @PrimaryKey val userId: String,
    val themeMode: String = "dark",
    val preferredVoice: String = "Natural Friendly",
    val modelPreference: String = "gemini-3.8-flash",
    val offlineModeEnabled: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)
