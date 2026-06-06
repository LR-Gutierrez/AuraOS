package com.beta.auraos.data.local.room

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "vehicle_entries",
    indices = [
        Index("branchId"),
        Index("plate")
    ]
)
data class VehicleEntryEntity(
    @PrimaryKey
    val id: String,
    val plate: String,
    val vehicleType: String,
    val isVip: Boolean,
    val membershipId: String? = null,
    val branchId: String,
    val platePhotoUrl: String? = null,
    val frontPhotoUrl: String? = null,
    val rearPhotoUrl: String? = null,
    val leftPhotoUrl: String? = null,
    val rightPhotoUrl: String? = null,
    val createdAt: String,
    val exitedAt: String? = null,
    val duration: String? = null,
    val fee: Double? = null,
    val lastModifiedAt: Long? = null
)
