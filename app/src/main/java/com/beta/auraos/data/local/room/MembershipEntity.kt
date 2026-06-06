package com.beta.auraos.data.local.room

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "memberships",
    indices = [
        Index("branchId"),
        Index("cardUuid")
    ]
)
data class MembershipEntity(
    @PrimaryKey
    val id: String,
    val memberName: String,
    val tier: String,
    val startDate: String,
    val endDate: String,
    val isActive: Boolean,
    val branchId: String,
    val cardUuid: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val lastModifiedAt: Long? = null
)
