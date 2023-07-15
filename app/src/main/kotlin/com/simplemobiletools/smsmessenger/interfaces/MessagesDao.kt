package com.simplemobiletools.smsmessenger.interfaces

import androidx.room.*
import com.simplemobiletools.smsmessenger.models.ArchivedMessage
import com.simplemobiletools.smsmessenger.models.Message

@Dao
interface MessagesDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrUpdate(message: Message)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertOrIgnore(message: Message): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertMessages(vararg message: Message)

    @Query("SELECT * FROM messages")
    fun getAll(): List<Message>

    @Query("SELECT messages.* FROM messages LEFT OUTER JOIN archived_messages ON messages.id = archived_messages.id WHERE messages.thread_id = :threadId AND archived_messages.id IS NULL")
    fun getThreadMessages(threadId: Long): List<Message>

    @Query("SELECT messages.* FROM messages LEFT OUTER JOIN archived_messages ON messages.id = archived_messages.id WHERE messages.thread_id = :threadId AND archived_messages.id IS NOT NULL")
    fun getArchivedThreadMessages(threadId: Long): List<Message>

    @Query("SELECT messages.* FROM messages LEFT OUTER JOIN archived_messages ON messages.id = archived_messages.id WHERE archived_messages.deleted_ts < :timestamp AND archived_messages.id IS NOT NULL")
    fun getOldArchived(timestamp: Long): List<Message>

    @Query("SELECT * FROM messages WHERE thread_id = :threadId AND is_scheduled = 1")
    fun getScheduledThreadMessages(threadId: Long): List<Message>

    @Query("SELECT * FROM messages WHERE thread_id = :threadId AND id = :messageId AND is_scheduled = 1")
    fun getScheduledMessageWithId(threadId: Long, messageId: Long): Message

    @Query("SELECT * FROM messages WHERE body LIKE :text")
    fun getMessagesWithText(text: String): List<Message>

    @Query("SELECT COUNT(*) FROM archived_messages")
    fun getArchivedCount(): Int

    @Query("UPDATE messages SET read = 1 WHERE id = :id")
    fun markRead(id: Long)

    @Query("UPDATE messages SET read = 1 WHERE thread_id = :threadId")
    fun markThreadRead(threadId: Long)

    @Query("UPDATE messages SET type = :type WHERE id = :id")
    fun updateType(id: Long, type: Int): Int

    @Query("UPDATE messages SET status = :status WHERE id = :id")
    fun updateStatus(id: Long, status: Int): Int

    @Query("DELETE FROM messages WHERE id = :id")
    fun delete(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertArchivedMessage(archivedMessage: ArchivedMessage)

    @Transaction
    fun archiveMessage(id: Long, timestamp: Long) {
        insertArchivedMessage(ArchivedMessage(id, timestamp))
    }

    @Query("DELETE FROM archived_messages WHERE id = :id")
    fun unarchiveMessage(id: Long)

    @Query("DELETE FROM messages WHERE thread_id = :threadId")
    fun deleteThreadMessages(threadId: Long)

    @Query("DELETE FROM messages")
    fun deleteAll()
}
