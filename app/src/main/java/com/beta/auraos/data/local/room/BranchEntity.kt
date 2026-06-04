package com.beta.auraos.data.local.room

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "branches")
data class BranchEntity(
    @PrimaryKey
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
    val createdAt: String,
    val updatedAt: String
)
