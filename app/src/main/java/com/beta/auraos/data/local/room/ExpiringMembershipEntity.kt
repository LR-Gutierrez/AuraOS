package com.beta.auraos.data.local.room

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "expiring_memberships",
    foreignKeys = [
        ForeignKey(
            entity = DashboardEntity::class,
            parentColumns = ["branchId"],
            childColumns = ["branchId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("branchId")]
)
data class ExpiringMembershipEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val branchId: String,
    val memberName: String,
    val tier: String,
    val timeLeft: String
)
