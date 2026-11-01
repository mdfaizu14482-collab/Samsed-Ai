package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.local.LocalOfflineRepository
import com.example.data.local.SamsedRoomDatabase
import com.example.data.local.entities.LocalConversationEntity
import com.example.data.local.entities.LocalMessageEntity
import com.example.data.local.entities.LocalUserPreferenceEntity
import com.example.data.models.ChatMessage
import com.example.data.models.Conversation
import com.example.data.models.UserProfile
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private const val TAG = "FirestoreRepo"

class FirestoreRepository(
    private val db: FirebaseFirestore,
    val localRepo: LocalOfflineRepository? = null
) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        try {
            val db = SamsedRoomDatabase.getDatabase(context)
            if (db != null) {
                LocalOfflineRepository(
                    db.conversationDao(),
                    db.messageDao(),
                    db.userPreferenceDao()
                )
            } else null
        } catch (e: Throwable) {
            Log.e(TAG, "Local Room database unavailable", e)
            null
        }
    )

    fun observeUserProfile(userId: String): Flow<UserProfile?> = callbackFlow {
        if (userId.isBlank()) {
            trySend(null)
            close()
            return@callbackFlow
        }
        val docRef = db.collection("users").document(userId)
        val registration = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "Error observing user profile: ${error.message}")
                trySend(null)
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                try {
                    val profile = snapshot.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    if (profile != null) {
                        scope.launch {
                            try {
                                localRepo?.saveUserPreferences(
                                    LocalUserPreferenceEntity(
                                        userId = profile.userId,
                                        themeMode = profile.themeMode,
                                        preferredVoice = profile.preferredVoice,
                                        modelPreference = profile.modelPreference,
                                        updatedAt = profile.updatedAt?.toDate()?.time ?: System.currentTimeMillis()
                                    )
                                )
                            } catch (e: Throwable) {
                                Log.w(TAG, "Could not cache user preferences to Room", e)
                            }
                        }
                    }
                    trySend(profile)
                } catch (e: Throwable) {
                    Log.e(TAG, "Error parsing user profile", e)
                    trySend(null)
                }
            } else {
                trySend(null)
            }
        }
        awaitClose { registration.remove() }
    }

    suspend fun syncUserProfile(
        userId: String,
        email: String,
        displayName: String,
        photoUrl: String
    ): Result<Unit> = runCatching {
        val userDoc = db.collection("users").document(userId)
        val snapshot = userDoc.get().await()
        val isAdminEmail = email.equals("mdfaizu14482@gmail.com", ignoreCase = true)
        if (!snapshot.exists()) {
            val initialData = hashMapOf(
                "userId" to userId,
                "email" to email,
                "displayName" to displayName.ifBlank { "User" },
                "photoUrl" to photoUrl,
                "isAdmin" to isAdminEmail,
                "themeMode" to "dark",
                "preferredVoice" to "Natural Friendly",
                "modelPreference" to "gemini-3.8-flash",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            userDoc.set(initialData).await()
            try {
                localRepo?.saveUserPreferences(
                    LocalUserPreferenceEntity(
                        userId = userId,
                        themeMode = "dark",
                        preferredVoice = "Natural Friendly",
                        modelPreference = "gemini-3.8-flash"
                    )
                )
            } catch (e: Throwable) {
                Log.w(TAG, "Could not save user pref to Room", e)
            }
        } else {
            val existingAdmin = snapshot.getBoolean("isAdmin") ?: false
            val updates = hashMapOf<String, Any>(
                "email" to email,
                "displayName" to displayName.ifBlank { "User" },
                "photoUrl" to photoUrl,
                "isAdmin" to (existingAdmin || isAdminEmail),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            userDoc.update(updates).await()
            try {
                localRepo?.saveUserPreferences(
                    LocalUserPreferenceEntity(
                        userId = userId,
                        themeMode = snapshot.getString("themeMode") ?: "dark",
                        preferredVoice = snapshot.getString("preferredVoice") ?: "Natural Friendly",
                        modelPreference = snapshot.getString("modelPreference") ?: "gemini-3.8-flash"
                    )
                )
            } catch (e: Throwable) {
                Log.w(TAG, "Could not update user pref in Room", e)
            }
        }
    }

    suspend fun updateUserPreferences(
        userId: String,
        themeMode: String? = null,
        preferredVoice: String? = null,
        modelPreference: String? = null
    ): Result<Unit> = runCatching {
        val updates = hashMapOf<String, Any>("updatedAt" to FieldValue.serverTimestamp())
        themeMode?.let { updates["themeMode"] = it }
        preferredVoice?.let { updates["preferredVoice"] = it }
        modelPreference?.let { updates["modelPreference"] = it }
        db.collection("users").document(userId).update(updates).await()

        try {
            localRepo?.let { repo ->
                themeMode?.let { repo.saveUserPreferences(LocalUserPreferenceEntity(userId = userId, themeMode = it)) }
                preferredVoice?.let { repo.saveUserPreferences(LocalUserPreferenceEntity(userId = userId, preferredVoice = it)) }
                modelPreference?.let { repo.saveUserPreferences(LocalUserPreferenceEntity(userId = userId, modelPreference = it)) }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Could not update preferences in local Room", e)
        }
    }

    fun observeConversations(userId: String): Flow<List<Conversation>> = callbackFlow {
        if (userId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val query = db.collection("users")
            .document(userId)
            .collection("conversations")
            .orderBy("updatedAt", Query.Direction.DESCENDING)

        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "Error observing conversations: ${error.message}")
                trySend(emptyList())
                return@addSnapshotListener
            }
            if (snapshot != null) {
                try {
                    val list = snapshot.toObjects(Conversation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    scope.launch {
                        try {
                            val localEntities = list.map { conv ->
                                LocalConversationEntity(
                                    id = if (conv.id.isNotBlank()) conv.id else snapshot.documents.find { it.getString("title") == conv.title }?.id ?: "conv_${System.currentTimeMillis()}",
                                    userId = conv.userId,
                                    title = conv.title,
                                    mode = conv.mode,
                                    lastMessage = conv.lastMessage,
                                    messageCount = conv.messageCount,
                                    timestamp = conv.updatedAt?.toDate()?.time ?: System.currentTimeMillis()
                                )
                            }
                            localRepo?.saveConversations(localEntities)
                        } catch (e: Throwable) {
                            Log.w(TAG, "Could not cache conversations to Room", e)
                        }
                    }
                    trySend(list)
                } catch (e: Throwable) {
                    Log.e(TAG, "Error deserializing conversations", e)
                    trySend(emptyList())
                }
            }
        }
        awaitClose { registration.remove() }
    }

    suspend fun createConversation(
        userId: String,
        title: String,
        mode: String = "chat"
    ): Result<String> = runCatching {
        val col = db.collection("users").document(userId).collection("conversations")
        val doc = col.document()
        val data = hashMapOf(
            "id" to doc.id,
            "userId" to userId,
            "title" to title.ifBlank { "New Conversation" },
            "mode" to mode,
            "lastMessage" to "",
            "messageCount" to 0L,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        doc.set(data).await()

        try {
            localRepo?.saveConversation(
                LocalConversationEntity(
                    id = doc.id,
                    userId = userId,
                    title = title.ifBlank { "New Conversation" },
                    mode = mode,
                    lastMessage = "",
                    messageCount = 0L
                )
            )
        } catch (e: Throwable) {
            Log.w(TAG, "Could not cache new conversation to Room", e)
        }

        doc.id
    }

    suspend fun deleteConversation(userId: String, conversationId: String): Result<Unit> = runCatching {
        val convRef = db.collection("users").document(userId).collection("conversations").document(conversationId)
        val messagesSnapshot = convRef.collection("messages").get().await()
        for (doc in messagesSnapshot.documents) {
            doc.reference.delete().await()
        }
        convRef.delete().await()
        try {
            localRepo?.deleteConversation(conversationId)
        } catch (e: Throwable) {
            Log.w(TAG, "Could not delete from Room", e)
        }
    }

    suspend fun renameConversation(userId: String, conversationId: String, newTitle: String): Result<Unit> = runCatching {
        db.collection("users")
            .document(userId)
            .collection("conversations")
            .document(conversationId)
            .update(
                mapOf(
                    "title" to newTitle.trim(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        try {
            localRepo?.renameConversation(conversationId, newTitle.trim())
        } catch (e: Throwable) {
            Log.w(TAG, "Could not rename in Room", e)
        }
    }

    fun observeMessages(userId: String, conversationId: String): Flow<List<ChatMessage>> = callbackFlow {
        if (userId.isBlank() || conversationId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val query = db.collection("users")
            .document(userId)
            .collection("conversations")
            .document(conversationId)
            .collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)

        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "Error observing messages for conv $conversationId: ${error.message}")
                trySend(emptyList())
                return@addSnapshotListener
            }
            if (snapshot != null) {
                try {
                    val list = snapshot.toObjects(ChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    scope.launch {
                        try {
                            val localMessages = list.map { msg ->
                                LocalMessageEntity(
                                    id = if (msg.id.isNotBlank()) msg.id else "msg_${System.currentTimeMillis()}",
                                    conversationId = msg.conversationId,
                                    userId = msg.userId,
                                    role = msg.role,
                                    content = msg.content,
                                    imageUrl = msg.imageUrl,
                                    hasImage = msg.hasImage,
                                    toolUsed = msg.toolUsed,
                                    timestamp = msg.createdAt?.toDate()?.time ?: System.currentTimeMillis()
                                )
                            }
                            localRepo?.saveMessages(localMessages)
                        } catch (e: Throwable) {
                            Log.w(TAG, "Could not cache messages to Room", e)
                        }
                    }
                    trySend(list)
                } catch (e: Throwable) {
                    Log.e(TAG, "Error parsing messages", e)
                    trySend(emptyList())
                }
            }
        }
        awaitClose { registration.remove() }
    }

    suspend fun addMessage(
        userId: String,
        conversationId: String,
        role: String,
        content: String,
        imageUrl: String = "",
        hasImage: Boolean = false,
        toolUsed: String = ""
    ): Result<String> = runCatching {
        val convRef = db.collection("users").document(userId).collection("conversations").document(conversationId)
        val msgRef = convRef.collection("messages").document()

        val msgData = hashMapOf(
            "id" to msgRef.id,
            "conversationId" to conversationId,
            "userId" to userId,
            "role" to role,
            "content" to content,
            "imageUrl" to imageUrl,
            "hasImage" to hasImage,
            "toolUsed" to toolUsed,
            "createdAt" to FieldValue.serverTimestamp()
        )
        msgRef.set(msgData).await()

        val snippet = if (content.length > 80) content.take(77) + "..." else content
        convRef.update(
            mapOf(
                "lastMessage" to snippet,
                "messageCount" to FieldValue.increment(1),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()

        try {
            localRepo?.saveMessage(
                LocalMessageEntity(
                    id = msgRef.id,
                    conversationId = conversationId,
                    userId = userId,
                    role = role,
                    content = content,
                    imageUrl = imageUrl,
                    hasImage = hasImage,
                    toolUsed = toolUsed
                )
            )
        } catch (e: Throwable) {
            Log.w(TAG, "Could not cache new message to Room", e)
        }

        msgRef.id
    }
}
