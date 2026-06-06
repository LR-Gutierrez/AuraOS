package com.beta.auraos.data.local.room

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "change_journal",
    indices = [
        Index("entityType"),
        Index("entityId"),
        Index("synced"),
        Index("timestamp")
    ]
)
data class ChangeJournalEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val entityType: String,
    val entityId: String,
    val operation: String,
    val data: String,
    val changedFields: String?,
    val timestamp: Long,
    val deviceId: String,
    val synced: Boolean = false
)
