package com.beta.auraos.data.local.room

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "vip_arrivals",
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
data class VipArrivalEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val branchId: String,
    val guestName: String,
    val slot: String,
    val membershipId: String? = null
)
