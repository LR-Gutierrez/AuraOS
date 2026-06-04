package com.beta.auraos.data.local.room

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_exits")
data class PendingExitEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val entryId: String,
    val exitTime: String,
    val duration: String?,
    val fee: Double,
    val branchId: String,
    val createdAt: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val lastError: String? = null
)
