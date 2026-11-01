package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entities.LocalConversationEntity
import com.example.data.local.entities.LocalMessageEntity
import com.example.data.local.entities.LocalUserPreferenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {

    @Query("SELECT * FROM local_conversations WHERE userId = :userId ORDER BY timestamp DESC")
    fun getConversationsByUser(userId: String): Flow<List<LocalConversationEntity>>

    @Query("SELECT * FROM local_conversations WHERE id = :convId LIMIT 1")
    fun getConversationById(convId: String): Flow<LocalConversationEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: LocalConversationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversations(conversations: List<LocalConversationEntity>)

    @Query("UPDATE local_conversations SET title = :title, lastMessage = :lastMessage, messageCount = :messageCount, timestamp = :timestamp WHERE id = :convId")
    suspend fun updateConversation(convId: String, title: String, lastMessage: String, messageCount: Long, timestamp: Long)

    @Query("UPDATE local_conversations SET title = :newTitle, timestamp = :timestamp WHERE id = :convId")
    suspend fun renameConversation(convId: String, newTitle: String, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM local_conversations WHERE id = :convId")
    suspend fun deleteConversationById(convId: String)

    @Query("DELETE FROM local_conversations WHERE userId = :userId")
    suspend fun clearConversationsForUser(userId: String)
}

@Dao
interface MessageDao {

    @Query("SELECT * FROM local_messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getMessagesByConversation(conversationId: String): Flow<List<LocalMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: LocalMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<LocalMessageEntity>)

    @Query("DELETE FROM local_messages WHERE conversationId = :conversationId")
    suspend fun deleteMessagesForConversation(conversationId: String)

    @Query("DELETE FROM local_messages WHERE id = :messageId")
    suspend fun deleteMessageById(messageId: String)
}

@Dao
interface UserPreferenceDao {

    @Query("SELECT * FROM local_user_preferences WHERE userId = :userId LIMIT 1")
    fun getUserPreferences(userId: String): Flow<LocalUserPreferenceEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(preference: LocalUserPreferenceEntity)

    @Query("UPDATE local_user_preferences SET themeMode = :theme, updatedAt = :timestamp WHERE userId = :userId")
    suspend fun updateTheme(userId: String, theme: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE local_user_preferences SET modelPreference = :model, updatedAt = :timestamp WHERE userId = :userId")
    suspend fun updateModel(userId: String, model: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE local_user_preferences SET preferredVoice = :voice, updatedAt = :timestamp WHERE userId = :userId")
    suspend fun updateVoice(userId: String, voice: String, timestamp: Long = System.currentTimeMillis())
}
