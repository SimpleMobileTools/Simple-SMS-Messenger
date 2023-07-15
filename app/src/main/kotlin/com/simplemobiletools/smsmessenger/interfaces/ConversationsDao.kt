package com.simplemobiletools.smsmessenger.interfaces

import androidx.room.*
import com.simplemobiletools.smsmessenger.models.Conversation

@Dao
interface ConversationsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrUpdate(conversation: Conversation): Long

    @Query("SELECT * FROM conversations WHERE (SELECT COUNT(*) FROM messages LEFT OUTER JOIN archived_messages ON messages.id = archived_messages.id WHERE archived_messages.id IS NULL AND messages.thread_id = conversations.thread_id) > 0")
    fun getNonArchived(): List<Conversation>

    @Query("SELECT (SELECT body FROM messages LEFT OUTER JOIN archived_messages ON messages.id = archived_messages.id WHERE archived_messages.id IS NOT NULL AND messages.thread_id = conversations.thread_id ORDER BY date DESC LIMIT 1) as snippet, conversations.* FROM conversations WHERE (SELECT COUNT(*) FROM messages LEFT OUTER JOIN archived_messages ON messages.id = archived_messages.id WHERE archived_messages.id IS NOT NULL AND messages.thread_id = conversations.thread_id) > 0")
    fun getAllArchived(): List<Conversation>

    @Query("INSERT INTO archived_messages SELECT id, :timestamp as deleted_ts FROM messages WHERE thread_id = :threadId")
    fun archiveConversationMessages(threadId: Long, timestamp: Long)

    @Transaction
    fun archiveConversation(threadId: Long, timestamp: Long) {
        archiveConversationMessages(threadId, timestamp)
    }

    @Query("SELECT * FROM conversations WHERE thread_id = :threadId")
    fun getConversationWithThreadId(threadId: Long): Conversation?

    @Query("SELECT * FROM conversations WHERE read = 0")
    fun getUnreadConversations(): List<Conversation>

    @Query("SELECT * FROM conversations WHERE title LIKE :text")
    fun getConversationsWithText(text: String): List<Conversation>

    @Query("UPDATE conversations SET read = 1 WHERE thread_id = :threadId")
    fun markRead(threadId: Long)

    @Query("UPDATE conversations SET read = 0 WHERE thread_id = :threadId")
    fun markUnread(threadId: Long)

    @Query("DELETE FROM conversations WHERE thread_id = :threadId")
    fun deleteThreadFromConversations(threadId: Long)

    @Transaction
    fun deleteThreadFromArchivedConversations(threadId: Long) {
        unarchiveThreadMessages(threadId)
    }

    @Query("DELETE FROM archived_messages WHERE id IN (SELECT id FROM messages WHERE thread_id = :threadId)")
    fun unarchiveThreadMessages(threadId: Long)

    @Transaction
    fun deleteThreadId(threadId: Long) {
        deleteThreadFromConversations(threadId)
    }
}
