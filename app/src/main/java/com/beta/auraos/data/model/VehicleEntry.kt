package com.beta.auraos.data.model

data class VehicleEntry(
    val id: String,
    val plate: String,
    val vehicleType: String,
    val isVip: Boolean,
    val branchId: String,
    val platePhotoUrl: String?,
    val frontPhotoUrl: String?,
    val rearPhotoUrl: String?,
    val leftPhotoUrl: String?,
    val rightPhotoUrl: String?,
    val createdAt: String,
    val exitedAt: String?,
    val branch: Branch?
)
