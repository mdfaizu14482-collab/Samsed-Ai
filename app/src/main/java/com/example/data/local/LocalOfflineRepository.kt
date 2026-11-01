package com.example.data.local

import com.example.data.local.dao.ConversationDao
import com.example.data.local.dao.MessageDao
import com.example.data.local.dao.UserPreferenceDao
import com.example.data.local.entities.LocalConversationEntity
import com.example.data.local.entities.LocalMessageEntity
import com.example.data.local.entities.LocalUserPreferenceEntity
import kotlinx.coroutines.flow.Flow

class LocalOfflineRepository(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val userPreferenceDao: UserPreferenceDao
) {

    fun observeConversations(userId: String): Flow<List<LocalConversationEntity>> {
        return conversationDao.getConversationsByUser(userId)
    }

    fun observeMessages(conversationId: String): Flow<List<LocalMessageEntity>> {
        return messageDao.getMessagesByConversation(conversationId)
    }

    fun observeUserPreferences(userId: String): Flow<LocalUserPreferenceEntity?> {
        return userPreferenceDao.getUserPreferences(userId)
    }

    suspend fun saveConversation(conversation: LocalConversationEntity) {
        conversationDao.insertConversation(conversation)
    }

    suspend fun saveConversations(conversations: List<LocalConversationEntity>) {
        conversationDao.insertConversations(conversations)
    }

    suspend fun renameConversation(conversationId: String, newTitle: String) {
        conversationDao.renameConversation(conversationId, newTitle)
    }

    suspend fun deleteConversation(conversationId: String) {
        messageDao.deleteMessagesForConversation(conversationId)
        conversationDao.deleteConversationById(conversationId)
    }

    suspend fun saveMessage(message: LocalMessageEntity) {
        messageDao.insertMessage(message)
    }

    suspend fun saveMessages(messages: List<LocalMessageEntity>) {
        messageDao.insertMessages(messages)
    }

    suspend fun saveUserPreferences(preferences: LocalUserPreferenceEntity) {
        userPreferenceDao.insertOrUpdate(preferences)
    }
}
