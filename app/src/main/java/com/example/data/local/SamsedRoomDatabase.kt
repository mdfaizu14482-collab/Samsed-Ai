package com.example.data.local

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.dao.ConversationDao
import com.example.data.local.dao.MessageDao
import com.example.data.local.dao.UserPreferenceDao
import com.example.data.local.entities.LocalConversationEntity
import com.example.data.local.entities.LocalMessageEntity
import com.example.data.local.entities.LocalUserPreferenceEntity

@Database(
    entities = [
        LocalConversationEntity::class,
        LocalMessageEntity::class,
        LocalUserPreferenceEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class SamsedRoomDatabase : RoomDatabase() {

    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun userPreferenceDao(): UserPreferenceDao

    companion object {
        @Volatile
        private var INSTANCE: SamsedRoomDatabase? = null

        fun getDatabase(context: Context): SamsedRoomDatabase? {
            return INSTANCE ?: synchronized(this) {
                try {
                    val instance = Room.databaseBuilder(
                        context.applicationContext,
                        SamsedRoomDatabase::class.java,
                        "samsed_ai_local.db"
                    )
                        .fallbackToDestructiveMigration()
                        .build()
                    INSTANCE = instance
                    instance
                } catch (e: Throwable) {
                    Log.e("SamsedRoomDatabase", "Could not initialize Room DB", e)
                    null
                }
            }
        }
    }
}
