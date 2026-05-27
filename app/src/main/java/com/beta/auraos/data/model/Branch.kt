package com.beta.auraos.data.model

data class Branch(
    val id: String,
    val name: String,
    val address: String?,
    val motorcycleCapacity: Int,
    val lightVehicleCapacity: Int,
    val heavyVehicleCapacity: Int,
    val motorcycleRate: Double,
    val lightVehicleRate: Double,
    val heavyVehicleRate: Double,
    val motorcycleOvernightRate: Double = 0.0,
    val lightVehicleOvernightRate: Double = 0.0,
    val heavyVehicleOvernightRate: Double = 0.0,
    val openTimeWeekday: String = "07:00",
    val closeTimeWeekday: String = "23:00",
    val openTimeWeekend: String = "09:00",
    val closeTimeWeekend: String = "22:00",
    val currency: String,
    val createdAt: String,
    val updatedAt: String
)

data class CreateBranchInput(
    val name: String,
    val address: String?,
    val motorcycleCapacity: Int,
    val lightVehicleCapacity: Int,
    val heavyVehicleCapacity: Int,
    val motorcycleRate: Double,
    val lightVehicleRate: Double,
    val heavyVehicleRate: Double,
    val motorcycleOvernightRate: Double = 0.0,
    val lightVehicleOvernightRate: Double = 0.0,
    val heavyVehicleOvernightRate: Double = 0.0,
    val openTimeWeekday: String = "07:00",
    val closeTimeWeekday: String = "23:00",
    val openTimeWeekend: String = "09:00",
    val closeTimeWeekend: String = "22:00",
    val currency: String
)

data class UpdateBranchInput(
    val name: String? = null,
    val address: String? = null,
    val motorcycleCapacity: Int? = null,
    val lightVehicleCapacity: Int? = null,
    val heavyVehicleCapacity: Int? = null,
    val motorcycleRate: Double? = null,
    val lightVehicleRate: Double? = null,
    val heavyVehicleRate: Double? = null,
    val motorcycleOvernightRate: Double? = null,
    val lightVehicleOvernightRate: Double? = null,
    val heavyVehicleOvernightRate: Double? = null,
    val openTimeWeekday: String? = null,
    val closeTimeWeekday: String? = null,
    val openTimeWeekend: String? = null,
    val closeTimeWeekend: String? = null,
    val currency: String? = null
)
