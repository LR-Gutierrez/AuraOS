package com.beta.auraos.data.local.room

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_nfc_entries")
data class PendingNfcEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val cardUuid: String,
    val branchId: String,
    val memberName: String?,
    val membershipId: String?,
    val localEntryId: String?,
    val createdAt: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val lastError: String? = null
)
