package com.simplemobiletools.smsmessenger.databases

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.simplemobiletools.smsmessenger.interfaces.ConversationsDao
import com.simplemobiletools.smsmessenger.models.Conversation

@Database(entities = [(Conversation::class)], version = 2)
abstract class MessagesDatabase : RoomDatabase() {

    abstract fun ConversationsDao(): ConversationsDao

    companion object {
        private var db: MessagesDatabase? = null

        fun getInstance(context: Context): MessagesDatabase {
            if (db == null) {
                synchronized(MessagesDatabase::class) {
                    if (db == null) {
                        db = Room.databaseBuilder(context.applicationContext, MessagesDatabase::class.java, "conversations.db")
                            .addMigrations(MIGRATION_1_2)
                            .build()
                    }
                }
            }
            return db!!
        }

        private val MIGRATION_1_2 : Migration = object : Migration(1,2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE conversations ADD COLUMN is_favorite INTEGER DEFAULT 0 NOT NULL")
                database.execSQL("ALTER TABLE conversations ADD COLUMN is_contact INTEGER DEFAULT 0 NOT NULL")
            }
        }
    }

}
