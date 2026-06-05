package com.beta.auraos.data.model

data class SyncResponse(
    val branches: List<SyncBranch>,
    val memberships: List<SyncMembership>,
    val vehicleEntries: List<SyncVehicleEntry>,
    val syncTimestamp: String
)

data class SyncBranch(
    val id: String,
    val name: String,
    val address: String?,
    val motorcycleCapacity: Int,
    val lightVehicleCapacity: Int,
    val heavyVehicleCapacity: Int,
    val motorcycleRate: Double,
    val lightVehicleRate: Double,
    val heavyVehicleRate: Double,
    val motorcycleOvernightRate: Double,
    val lightVehicleOvernightRate: Double,
    val heavyVehicleOvernightRate: Double,
    val openTimeWeekday: String,
    val closeTimeWeekday: String,
    val openTimeWeekend: String,
    val closeTimeWeekend: String,
    val currency: String,
    val favorite: Boolean?,
    val createdAt: String,
    val updatedAt: String
)

data class SyncMembership(
    val id: String,
    val memberName: String,
    val tier: String,
    val cardUuid: String?,
    val startDate: String,
    val endDate: String,
    val isActive: Boolean,
    val branchId: String,
    val createdAt: String?,
    val updatedAt: String?
)

data class SyncVehicleEntry(
    val id: String,
    val plate: String,
    val vehicleType: String,
    val isVip: Boolean,
    val membershipId: String?,
    val branchId: String,
    val platePhotoUrl: String?,
    val frontPhotoUrl: String?,
    val rearPhotoUrl: String?,
    val leftPhotoUrl: String?,
    val rightPhotoUrl: String?,
    val createdAt: String,
    val exitedAt: String?
)
