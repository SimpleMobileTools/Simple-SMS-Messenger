package com.simplemobiletools.smsmessenger.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "archived_messages",
    indices = [(Index(value = ["id"], unique = true))]
)
data class ArchivedMessage(
    @PrimaryKey @ColumnInfo(name = "id") var id: Long,
    @ColumnInfo(name = "deleted_ts") var deletedTS: Long,
)
